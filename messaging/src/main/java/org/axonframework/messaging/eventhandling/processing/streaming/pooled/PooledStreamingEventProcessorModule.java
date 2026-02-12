/*
 * Copyright (c) 2010-2026. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging.eventhandling.processing.streaming.pooled;

import jakarta.annotation.Nonnull;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.configuration.BaseModule;
import org.axonframework.common.configuration.ComponentBuilder;
import org.axonframework.common.configuration.ComponentDefinition;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.configuration.ModuleBuilder;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.configuration.DefaultEventHandlingComponentsConfigurer;
import org.axonframework.messaging.eventhandling.configuration.EventHandlingComponentsConfigurer;
import org.axonframework.messaging.eventhandling.configuration.EventProcessingConfigurer;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorConfiguration;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorCustomization;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule;
import org.axonframework.messaging.eventhandling.deadletter.CachingSequencedDeadLetterQueue;
import org.axonframework.messaging.eventhandling.deadletter.DeadLetterQueueConfiguration;
import org.axonframework.messaging.eventhandling.deadletter.DeadLetteringEventHandlingComponent;
import org.axonframework.messaging.eventhandling.deadletter.SequencedDeadLetterQueueFactory;
import org.axonframework.messaging.eventhandling.interception.InterceptingEventHandlingComponent;
import org.axonframework.messaging.deadletter.SequencedDeadLetterProcessor;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.eventhandling.processing.streaming.StreamingEventProcessor;
import org.axonframework.messaging.eventhandling.processing.streaming.segmenting.SequenceCachingEventHandlingComponent;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.TokenStore;
import org.axonframework.common.lifecycle.Phase;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * A configuration module for configuring and registering a single {@link PooledStreamingEventProcessor} component.
 * <p>
 * The main capabilities provided by this module include:
 * <ul>
 * <li>Automatic thread pool configuration for coordinator and worker executors</li>
 * <li>Event handling component decoration with tracing, monitoring, and interception</li>
 * <li>Integration with shared configuration customizations from parent modules</li>
 * <li>Lifecycle management for the created processor and its executors</li>
 * </ul>
 * <p>
 * This module is typically not instantiated directly but created through
 * {@link EventProcessorModule#pooledStreaming(String)} or registered via
 * {@link PooledStreamingEventProcessorsConfigurer#defaultProcessor} methods.
 * <p>
 * The module applies shared defaults from {@link PooledStreamingEventProcessorsConfigurer} and
 * {@link EventProcessingConfigurer} before applying
 * processor-specific customizations.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class PooledStreamingEventProcessorModule extends BaseModule<PooledStreamingEventProcessorModule>
        implements EventProcessorModule, ModuleBuilder<PooledStreamingEventProcessorModule>,
        EventProcessorModule.EventHandlingPhase<PooledStreamingEventProcessorModule, PooledStreamingEventProcessorConfiguration>,
        EventProcessorModule.CustomizationPhase<PooledStreamingEventProcessorModule, PooledStreamingEventProcessorConfiguration> {

    private final String processorName;
    private List<ComponentBuilder<EventHandlingComponent>> eventHandlingComponentBuilders;
    private ComponentBuilder<PooledStreamingEventProcessorConfiguration> customizedProcessorConfigurationBuilder;

    /**
     * Constructs a module with the given processor name.
     * <p>
     * The processor name will be used as the module name and as the unique identifier for the
     * {@link PooledStreamingEventProcessor} component created by this module.
     *
     * @param processorName The unique name for the pooled streaming event processor.
     */
    public PooledStreamingEventProcessorModule(@Nonnull String processorName) {
        super(processorName);
        this.processorName = processorName;
    }

    @Override
    public PooledStreamingEventProcessorModule build() {
        registerCustomizedConfiguration();
        registerSequencedDeadLetterQueueFactory();
        registerCachingDeadLetterQueues();
        registerTokenStore();
        registerUnitOfWorkFactory();
        registerEventHandlingComponents();
        registerEventProcessor();
        return this;
    }

    private void registerCustomizedConfiguration() {
        componentRegistry(cr -> cr.registerComponent(
                ComponentDefinition
                        .ofType(PooledStreamingEventProcessorConfiguration.class)
                        .withBuilder(cfg -> {
                            var configuration = customizedProcessorConfigurationBuilder.build(cfg);
                            configuration.workerExecutor(
                                    Optional.ofNullable(configuration.workerExecutor())
                                            .orElseGet(() -> defaultExecutor(4, "WorkPackage[" + processorName + "]"))
                            );
                            configuration.coordinatorExecutor(
                                    Optional.ofNullable(configuration.coordinatorExecutor())
                                            .orElseGet(() -> defaultExecutor(1, "Coordinator[" + processorName + "]"))
                            );
                            var dlqEnabled = configuration.deadLetterQueue().isEnabled();
                            if (dlqEnabled) {
                                var segmentReleasedAction = configuration.segmentReleasedAction();
                                configuration.segmentReleasedAction(segment -> {
                                    // Invalidate cache for ALL event handling component DLQs
                                    for (int idx = 0; idx < eventHandlingComponentBuilders.size(); idx++) {
                                        var dlq = cfg.getComponent(CachingSequencedDeadLetterQueue.class,
                                                                   processorComponentCachingDlqName(idx));
                                        dlq.invalidateCache();
                                    }
                                    segmentReleasedAction.accept(segment);
                                });
                            } else {
                                configuration.segmentReleasedAction(configuration.segmentReleasedAction());
                            }
                            return configuration;
                        }).onShutdown(Phase.LOCAL_MESSAGE_HANDLER_REGISTRATIONS, (cfg, processor) -> {
                            processor.workerExecutor().shutdown();
                            return FutureUtils.emptyCompletedFuture();
                        }).onShutdown(Phase.LOCAL_MESSAGE_HANDLER_REGISTRATIONS, (cfg, processor) -> {
                            processor.coordinatorExecutor().shutdown();
                            return FutureUtils.emptyCompletedFuture();
                        })
        ));
    }

    private void registerSequencedDeadLetterQueueFactory() {
        componentRegistry(cr -> cr.registerFactory(new SequencedDeadLetterQueueFactory(
                (name, config) -> config.getComponent(PooledStreamingEventProcessorConfiguration.class)
                                        .deadLetterQueue()
                                        .factory()
                                        .apply(name)
        )));
    }

    @SuppressWarnings("unchecked")
    private void registerCachingDeadLetterQueues() {
        for (int i = 0; i < eventHandlingComponentBuilders.size(); i++) {
            final int componentIndex = i;
            var cachingDlqName = processorComponentCachingDlqName(componentIndex);
            componentRegistry(cr -> cr.registerComponent(
                    ComponentDefinition
                            .ofTypeAndName(CachingSequencedDeadLetterQueue.class, cachingDlqName)
                            .withBuilder(cfg -> {
                                DeadLetterQueueConfiguration dlqConfig =
                                        cfg.getComponent(PooledStreamingEventProcessorConfiguration.class)
                                           .deadLetterQueue();
                                if (dlqConfig.isEnabled()) {
                                    var underlyingDlq = cfg.getComponent(
                                            SequencedDeadLetterQueue.class,
                                            processorComponentDlqName(componentIndex)
                                    );
                                    return new CachingSequencedDeadLetterQueue<EventMessage>(
                                            underlyingDlq,
                                            dlqConfig.cacheMaxSize()
                                    );
                                }
                                return null;
                            })
            ));
        }
    }

    private void registerTokenStore() {
        componentRegistry(cr -> cr.registerComponent(
                ComponentDefinition
                        .ofTypeAndName(TokenStore.class, "TokenStore[" + processorName + "]")
                        .withBuilder(cfg -> cfg.getComponent(PooledStreamingEventProcessorConfiguration.class)
                                               .tokenStore())
        ));
    }

    private void registerUnitOfWorkFactory() {
        componentRegistry(cr -> cr.registerComponent(
                ComponentDefinition
                        .ofTypeAndName(UnitOfWorkFactory.class, "UnitOfWorkFactory[" + processorName + "]")
                        .withBuilder(cfg -> cfg.getComponent(PooledStreamingEventProcessorConfiguration.class)
                                               .unitOfWorkFactory())
        ));
    }

    private void registerEventProcessor() {
        var processorComponentDefinition = ComponentDefinition
                .ofTypeAndName(StreamingEventProcessor.class, processorName)
                .withBuilder(cfg -> new PooledStreamingEventProcessor(
                        processorName,
                        getEventHandlingComponents(cfg),
                        cfg.getComponent(PooledStreamingEventProcessorConfiguration.class)
                ))
                .onStart(Phase.INBOUND_EVENT_CONNECTORS, (cfg, component) -> {
                    return component.start();
                })
                .onShutdown(Phase.INBOUND_EVENT_CONNECTORS, (cfg, component) -> {
                    return component.shutdown();
                });

        componentRegistry(cr -> cr.registerComponent(processorComponentDefinition));
    }

    private void registerEventHandlingComponents() {
        for (int i = 0; i < eventHandlingComponentBuilders.size(); i++) {
            final int componentIndex = i;
            var componentBuilder = eventHandlingComponentBuilders.get(i);
            var componentName = processorEventHandlingComponentName(i);
            componentRegistry(cr -> {
                cr.registerComponent(EventHandlingComponent.class, componentName,
                                     cfg -> {
                                         var component = componentBuilder.build(cfg);
                                         return new SequenceCachingEventHandlingComponent(component);
                                     });
                cr.registerDecorator(EventHandlingComponent.class, componentName,
                                     InterceptingEventHandlingComponent.DECORATION_ORDER,
                                     (config, name, delegate) -> {
                                         var configuration =
                                                 config.getComponent(PooledStreamingEventProcessorConfiguration.class);
                                         return new InterceptingEventHandlingComponent(
                                                 configuration.interceptors(),
                                                 delegate
                                         );
                                     });
                cr.registerDecorator(EventHandlingComponent.class, componentName,
                                     DeadLetteringEventHandlingComponent.DECORATION_ORDER,
                                     (config, name, delegate) -> {
                                         var processorConfig = config.getComponent(PooledStreamingEventProcessorConfiguration.class);
                                         var dlqConfig = processorConfig.deadLetterQueue();
                                         // Check if DLQ is enabled first
                                         if (!dlqConfig.isEnabled()) {
                                             return delegate;
                                         }
                                         // When DLQ is enabled, the component is required (not optional)
                                         var cachingDlqName = processorComponentCachingDlqName(componentIndex);
                                         var cachingDlq = config.getComponent(
                                                 CachingSequencedDeadLetterQueue.class,
                                                 cachingDlqName
                                         );
                                         //noinspection unchecked
                                         return new DeadLetteringEventHandlingComponent(
                                                 delegate,
                                                 cachingDlq,
                                                 dlqConfig.enqueuePolicy(),
                                                 processorConfig.unitOfWorkFactory(), dlqConfig.clearOnReset()
                                         );
                                     });
                // Register the decorated component also as SequencedDeadLetterProcessor when DLQ is enabled
                cr.registerComponent(SequencedDeadLetterProcessor.class, componentName,
                                     cfg -> {
                                         var eventHandlingComponent = cfg.getComponent(
                                                 EventHandlingComponent.class, componentName
                                         );
                                         if (eventHandlingComponent instanceof SequencedDeadLetterProcessor<?> dlp) {
                                             return dlp;
                                         }
                                         return null;
                                     });
            });
        }
    }

    private List<EventHandlingComponent> getEventHandlingComponents(Configuration configuration) {
        return IntStream.range(0, eventHandlingComponentBuilders.size())
                        .mapToObj(i -> {
                            String componentName = processorEventHandlingComponentName(i);
                            return configuration.getComponent(EventHandlingComponent.class, componentName);
                        })
                        .toList();
    }

    @Nonnull
    private String processorEventHandlingComponentName(int index) {
        return "EventHandlingComponent[" + processorName + "][" + index + "]";
    }

    @Nonnull
    private String processorComponentDlqName(int index) {
        return "DeadLetterQueue[" + processorName + "][" + index + "]";
    }

    @Nonnull
    private String processorComponentCachingDlqName(int index) {
        return "CachingDeadLetterQueue[" + processorName + "][" + index + "]";
    }

    private static ScheduledExecutorService defaultExecutor(int poolSize, String factoryName) {
        return Executors.newScheduledThreadPool(poolSize, new AxonThreadFactory(factoryName));
    }

    @Override
    public PooledStreamingEventProcessorModule customized(
            @Nonnull BiFunction<Configuration, PooledStreamingEventProcessorConfiguration, PooledStreamingEventProcessorConfiguration> instanceCustomization
    ) {
        this.customizedProcessorConfigurationBuilder = cfg -> {
            var typeCustomization = typeSpecificCustomizationOrNoOp(cfg).apply(cfg,
                                                                               defaultEventProcessorsConfiguration(cfg));
            return instanceCustomization.apply(cfg, typeCustomization);
        };
        return this;
    }

    private static PooledStreamingEventProcessorModule.Customization typeSpecificCustomizationOrNoOp(
            Configuration cfg
    ) {
        return cfg.getOptionalComponent(PooledStreamingEventProcessorModule.Customization.class)
                  .orElseGet(PooledStreamingEventProcessorModule.Customization::noOp);
    }

    private static PooledStreamingEventProcessorConfiguration defaultEventProcessorsConfiguration(Configuration cfg) {
        return new PooledStreamingEventProcessorConfiguration(
                parentSharedCustomizationOrDefault(cfg)
                        .apply(cfg, new EventProcessorConfiguration(cfg)),
                cfg
        );
    }

    private static EventProcessorCustomization parentSharedCustomizationOrDefault(
            Configuration cfg
    ) {
        return cfg.getOptionalComponent(EventProcessorCustomization.class)
                  .orElseGet(EventProcessorCustomization::noOp);
    }

    @Override
    public PooledStreamingEventProcessorModule notCustomized() {
        if (customizedProcessorConfigurationBuilder == null) {
            customized((cfg, config) -> config);
        }
        return this;
    }

    @Override
    public CustomizationPhase<PooledStreamingEventProcessorModule, PooledStreamingEventProcessorConfiguration> eventHandlingComponents(
            @Nonnull Function<EventHandlingComponentsConfigurer.RequiredComponentPhase, EventHandlingComponentsConfigurer.CompletePhase> configurerTask
    ) {
        Objects.requireNonNull(configurerTask, "configurerTask may not be null");
        var componentsConfigurer = new DefaultEventHandlingComponentsConfigurer();
        this.eventHandlingComponentBuilders = configurerTask.apply(componentsConfigurer).toList();
        return this;
    }

    /**
     * Allows customizing the {@link PooledStreamingEventProcessorConfiguration}.
     * <p>
     * The interface provides composition capabilities through {@link #andThen(Customization)} to allow chaining
     * multiple customizations in a specific order.
     *
     * @author Mateusz Nowak
     * @since 5.0.0
     */
    @FunctionalInterface
    public interface Customization extends
            BiFunction<Configuration, PooledStreamingEventProcessorConfiguration, PooledStreamingEventProcessorConfiguration> {

        /**
         * Creates a no-operation customization that returns the processor configuration unchanged.
         *
         * @return A customization that applies no changes to the processor configuration.
         */
        static Customization noOp() {
            return (axonConfig, processorConfig) -> processorConfig;
        }

        /**
         * Returns a composed customization that applies this customization first, then applies the other
         * customization.
         * <p>
         * This allows for chaining multiple customizations together, with each subsequent customization receiving the
         * result of the previous one.
         *
         * @param other The customization to apply after this one.
         * @return A composed customization that applies both customizations in sequence.
         */
        default Customization andThen(@Nonnull Customization other) {
            Objects.requireNonNull(other, "other may not be null");
            return (axonConfig, processorConfig) -> other.apply(axonConfig, this.apply(axonConfig, processorConfig));
        }
    }
}
