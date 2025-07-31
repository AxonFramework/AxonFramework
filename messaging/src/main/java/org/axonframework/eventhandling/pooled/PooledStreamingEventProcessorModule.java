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
import org.axonframework.common.annotation.Internal;
import org.axonframework.configuration.BaseModule;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.ComponentDefinition;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.LifecycleRegistry;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventProcessorConfiguration;
import org.axonframework.eventhandling.MonitoringEventHandlingComponent;
import org.axonframework.eventhandling.TracingEventHandlingComponent;
import org.axonframework.eventhandling.configuration.EventProcessorCustomization;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventhandling.interceptors.InterceptingEventHandlingComponent;
import org.axonframework.eventhandling.interceptors.MessageHandlerInterceptors;
import org.axonframework.lifecycle.Phase;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

@Internal
public class PooledStreamingEventProcessorModule
        extends BaseModule<PooledStreamingEventProcessorModule>
        implements EventProcessorModule,
        EventProcessorModule.CustomizationPhase<PooledStreamingEventProcessorModule, PooledStreamingEventProcessorConfiguration> {

    private final String processorName;
    private ComponentBuilder<PooledStreamingEventProcessorConfiguration> configurationBuilder;

    // todo: defaults - should be configurable
    private final MessageHandlerInterceptors messageHandlerInterceptors = new MessageHandlerInterceptors();

    public PooledStreamingEventProcessorModule(@Nonnull String processorName) {
        super(processorName);
        this.processorName = processorName;
    }

    @Override
    public Configuration build(@Nonnull Configuration parent, @Nonnull LifecycleRegistry lifecycleRegistry) {
        var configuration = configurationBuilder.build(parent);

        var spanFactory = configuration.spanFactory();
        var messageMonitor = configuration.messageMonitor();

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

        var eventHandlingComponents = configuration.eventHandlingComponents();

        // TODO: Move it somewhere else! Like a decorator if certain enhancer applied.
        List<EventHandlingComponent> decoratedEventHandlingComponents = eventHandlingComponents
                .stream()
                .map(c -> new TracingEventHandlingComponent(
                        (event) -> spanFactory.createProcessEventSpan(true, event),
                        new MonitoringEventHandlingComponent(
                                messageMonitor,
                                new InterceptingEventHandlingComponent(
                                        messageHandlerInterceptors,
                                        c
                                )
                        )
                )).collect(Collectors.toUnmodifiableList());

        var processor = new PooledStreamingEventProcessor(
                processorName,
                configuration.eventHandlingComponents(decoratedEventHandlingComponents)
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

    @Override
    public PooledStreamingEventProcessorModule configure(
            @Nonnull ComponentBuilder<PooledStreamingEventProcessorConfiguration> configurationBuilder
    ) {
        this.configurationBuilder = configurationBuilder;
        return this;
    }

    @Override
    public PooledStreamingEventProcessorModule customize(
            @Nonnull ComponentBuilder<UnaryOperator<PooledStreamingEventProcessorConfiguration>> customizationBuilder
    ) {
        configure(
                cfg -> sharedCustomizationOrNoOp(cfg).apply(
                        cfg,
                        customizationBuilder.build(cfg).apply(defaultEventProcessorsConfiguration(cfg))
                )
        );
        return this;
    }

    private static PooledStreamingEventProcessorConfiguration defaultEventProcessorsConfiguration(Configuration cfg) {
        return new PooledStreamingEventProcessorConfiguration(
                parentSharedCustomizationOrDefault(cfg).apply(cfg, new EventProcessorConfiguration())
        );
    }

    private static PooledStreamingEventProcessorModule.Customization sharedCustomizationOrNoOp(
            Configuration cfg
    ) {
        return cfg.getOptionalComponent(PooledStreamingEventProcessorModule.Customization.class, "pooledStreamingEventProcessorCustomization")
                  .orElseGet(PooledStreamingEventProcessorModule.Customization::noOp);
    }

    private static EventProcessorCustomization parentSharedCustomizationOrDefault(
            Configuration cfg
    ) {
        return cfg.getOptionalComponent(EventProcessorCustomization.class)
                  .orElseGet(EventProcessorCustomization::noOp);
    }

    @Override
    public PooledStreamingEventProcessorModule build() {
        return this;
    }

    @Internal
    @FunctionalInterface
    interface Customization extends
            BiFunction<Configuration, PooledStreamingEventProcessorConfiguration, PooledStreamingEventProcessorConfiguration> {

        static Customization noOp() {
            return (config, pConfig) -> pConfig;
        }

        default Customization andThen(Customization other) {
            return (config, pConfig) -> other.apply(config, this.apply(config, pConfig));
        }
    }
}
