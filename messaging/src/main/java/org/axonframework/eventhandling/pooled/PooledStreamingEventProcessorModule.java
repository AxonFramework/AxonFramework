/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.eventhandling.pooled;

import jakarta.annotation.Nonnull;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.common.FutureUtils;
import org.axonframework.configuration.BaseModule;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.ComponentDefinition;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.LifecycleRegistry;
import org.axonframework.configuration.ModuleBuilder;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventProcessorConfiguration;
import org.axonframework.eventhandling.MonitoringEventHandlingComponent;
import org.axonframework.eventhandling.SequenceCachingEventHandlingComponent;
import org.axonframework.eventhandling.TracingEventHandlingComponent;
import org.axonframework.eventhandling.configuration.DefaultEventHandlingComponentsConfigurer;
import org.axonframework.eventhandling.configuration.EventHandlingComponentsConfigurer;
import org.axonframework.eventhandling.configuration.EventProcessingConfigurer;
import org.axonframework.eventhandling.configuration.EventProcessorCustomization;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventhandling.interceptors.InterceptingEventHandlingComponent;
import org.axonframework.lifecycle.Phase;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A configuration module for configuring and registering a single {@link PooledStreamingEventProcessor} component.
 * <p>
 * The main capabilities provided by this module include:
 * <ul>
 * <li>Automatic thread pool configuration for coordinator and worker executors</li>
 * <li>Event handling component decoration with tracing, monitoring, and interceptors</li>
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
    private ComponentBuilder<PooledStreamingEventProcessorConfiguration> configurationBuilder;

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
    public Configuration build(@Nonnull Configuration parent, @Nonnull LifecycleRegistry lifecycleRegistry) {
        var configuration = configurationBuilder.build(parent);

        // TODO #3098 - Clean-up this part.
        if (configuration.workerExecutorBuilder() == null) {
            Function<String, ScheduledExecutorService> workerExecutorBuilder = processorName -> {
                ScheduledExecutorService workerExecutor =
                        defaultExecutor(4, "WorkPackage[" + processorName + "]");
                lifecycleRegistry.onShutdown(workerExecutor::shutdown);
                return workerExecutor;
            };
            configuration.workerExecutor(workerExecutorBuilder);
        }

        if (configuration.coordinatorExecutorBuilder() == null) {
            Function<String, ScheduledExecutorService> coordinatorExecutorBuilder = processorName -> {
                ScheduledExecutorService coordinatorExecutor =
                        defaultExecutor(1, "Coordinator[" + processorName + "]");
                lifecycleRegistry.onShutdown(coordinatorExecutor::shutdown);
                return coordinatorExecutor;
            };
            configuration.coordinatorExecutor(coordinatorExecutorBuilder);
        }

        var eventHandlingComponents = eventHandlingComponentBuilders.stream()
                                              .map(c -> c.build(parent))
                                              .toList();
        List<EventHandlingComponent> decoratedEventHandlingComponents = eventHandlingComponents
                .stream()
                .map(c -> withDefaultDecoration(c, configuration))
                .collect(Collectors.toUnmodifiableList());

        var processor = new PooledStreamingEventProcessor(
                processorName,
                decoratedEventHandlingComponents,
                configuration
        );

        var processorComponentDefinition = ComponentDefinition
                .ofTypeAndName(PooledStreamingEventProcessor.class, processorName)
                .withBuilder(c -> processor)
                .onStart(Phase.INBOUND_EVENT_CONNECTORS, (cfg, component) -> {
                    component.start();
                    return FutureUtils.emptyCompletedFuture();
                }).onShutdown(Phase.INBOUND_EVENT_CONNECTORS, (cfg, component) -> {
                    return component.shutdownAsync();
                });

        componentRegistry(cr -> cr.registerComponent(processorComponentDefinition));

        return super.build(parent, lifecycleRegistry);
    }

    private static ScheduledExecutorService defaultExecutor(int poolSize, String factoryName) {
        return Executors.newScheduledThreadPool(poolSize, new AxonThreadFactory(factoryName));
    }

    // TODO #3098 - Move it somewhere else! Like a decorator if certain enhancer applied.
    @Nonnull
    private static TracingEventHandlingComponent withDefaultDecoration(
            EventHandlingComponent c,
            EventProcessorConfiguration configuration
    ) {
        return new TracingEventHandlingComponent(
                (event) -> configuration.spanFactory().createProcessEventSpan(false, event),
                new MonitoringEventHandlingComponent(
                        configuration.messageMonitor(),
                        new InterceptingEventHandlingComponent(
                                configuration.interceptors(),
                                new SequenceCachingEventHandlingComponent(c)
                        )
                )
        );
    }

    private PooledStreamingEventProcessorModule configure(
            @Nonnull ComponentBuilder<PooledStreamingEventProcessorConfiguration> configurationBuilder
    ) {
        this.configurationBuilder = configurationBuilder;
        return this;
    }

    @Override
    public PooledStreamingEventProcessorModule customized(
            @Nonnull BiFunction<Configuration, PooledStreamingEventProcessorConfiguration, PooledStreamingEventProcessorConfiguration> instanceCustomization
    ) {
        return configure(
                cfg -> {
                    var typeCustomization = typeSpecificCustomizationOrNoOp(cfg).apply(cfg, defaultEventProcessorsConfiguration(cfg));
                    return instanceCustomization.apply(cfg, typeCustomization);
                }
        );
    }

    private static PooledStreamingEventProcessorConfiguration defaultEventProcessorsConfiguration(Configuration cfg) {
        return new PooledStreamingEventProcessorConfiguration(
                parentSharedCustomizationOrDefault(cfg).apply(cfg, new EventProcessorConfiguration())
        );
    }

    private static PooledStreamingEventProcessorModule.Customization typeSpecificCustomizationOrNoOp(
            Configuration cfg
    ) {
        return cfg.getOptionalComponent(PooledStreamingEventProcessorModule.Customization.class,
                                        "pooledStreamingEventProcessorCustomization")
                  .orElseGet(PooledStreamingEventProcessorModule.Customization::noOp);
    }

    private static EventProcessorCustomization parentSharedCustomizationOrDefault(
            Configuration cfg
    ) {
        return cfg.getOptionalComponent(EventProcessorCustomization.class)
                  .orElseGet(EventProcessorCustomization::noOp);
    }

    @Override
    public PooledStreamingEventProcessorModule notCustomized() {
        if (configurationBuilder == null) {
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

    @Override
    public PooledStreamingEventProcessorModule build() {
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
    interface Customization extends
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
        default Customization andThen(Customization other) {
            return (axonConfig, processorConfig) -> other.apply(axonConfig, this.apply(axonConfig, processorConfig));
        }
    }
}
