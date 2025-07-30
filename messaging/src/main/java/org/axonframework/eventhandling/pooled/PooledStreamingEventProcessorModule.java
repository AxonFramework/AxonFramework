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
import org.axonframework.configuration.BaseModule;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.LifecycleRegistry;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.MonitoringEventHandlingComponent;
import org.axonframework.eventhandling.TracingEventHandlingComponent;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventhandling.interceptors.InterceptingEventHandlingComponent;
import org.axonframework.eventhandling.interceptors.MessageHandlerInterceptors;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventstreaming.StreamableEventSource;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class PooledStreamingEventProcessorModule
        extends BaseModule<PooledStreamingEventProcessorModule>
        implements EventProcessorModule,
        EventProcessorModule.StreamingSourcePhase<PooledStreamingEventProcessorsCustomization>,
        EventProcessorModule.EventHandlingPhase<PooledStreamingEventProcessorsCustomization>,
        EventProcessorModule.EventHandlingComponentsPhase<PooledStreamingEventProcessorsCustomization>,
        EventProcessorModule.BuildPhase {

    private final String processorName;
    private final List<ComponentBuilder<EventHandlingComponent>> eventHandlingBuilders;
    private ComponentBuilder<StreamableEventSource<? extends EventMessage<?>>> streamableEventSourceBuilder;
    private UnaryOperator<PooledStreamingEventProcessorsCustomization> customizationOverride = c -> c;

    // todo: defaults - should be configurable
    private final MessageHandlerInterceptors messageHandlerInterceptors = new MessageHandlerInterceptors();


    public PooledStreamingEventProcessorModule(@Nonnull String processorName) {
        super(processorName);
        this.processorName = processorName;
        this.eventHandlingBuilders = new ArrayList<>();
    }

    @Override
    public EventHandlingPhase<PooledStreamingEventProcessorsCustomization> eventSource(
            @Nonnull ComponentBuilder<StreamableEventSource<? extends EventMessage<?>>> streamableEventSourceBuilder) {
        this.streamableEventSourceBuilder = streamableEventSourceBuilder;
        return this;
    }

    @Override
    public Configuration build(@Nonnull Configuration parent, @Nonnull LifecycleRegistry lifecycleRegistry) {
        // todo: move it to the component registry!
        var eventSource = streamableEventSourceBuilder.build(parent);

        // todo: get from global configuration, but allow overriding
        var tokenStore = parent.getComponent(TokenStore.class);
        var unitOfWorkFactory = parent.getComponent(UnitOfWorkFactory.class);
        Function<String, ScheduledExecutorService> workerExecutorBuilder = processorName -> {
            ScheduledExecutorService workerExecutor =
                    defaultExecutor(4, "WorkPackage[" + processorName + "]");
            lifecycleRegistry.onShutdown(1, workerExecutor::shutdown);
            return workerExecutor;
        };
        Function<String, ScheduledExecutorService> coordinatorExecutorBuilder = processorName -> {
            ScheduledExecutorService coordinatorExecutor =
                    defaultExecutor(1, "Coordinator[" + processorName + "]");
            lifecycleRegistry.onShutdown(1, coordinatorExecutor::shutdown);
            return coordinatorExecutor;
        };

        var eventProcessorsCustomization = customizationOverride.apply(
                parent.getComponent(PooledStreamingEventProcessorsCustomization.class)
        ); // todo: write customization here!

        var spanFactory = eventProcessorsCustomization.spanFactory();
        var messageMonitor = eventProcessorsCustomization.messageMonitor();

        var eventHandlingComponents = eventHandlingBuilders.stream()
                                                           .map(hb -> hb.build(parent))
                                                           .toList();
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
                eventSource,
                decoratedEventHandlingComponents,
                unitOfWorkFactory,
                tokenStore,
                coordinatorExecutorBuilder,
                workerExecutorBuilder,
                eventProcessorsCustomization
        );

        lifecycleRegistry.onStart(2, processor::start);
        lifecycleRegistry.onShutdown(2, processor::shutDown);
        return super.build(parent, lifecycleRegistry);
    }

    private static ScheduledExecutorService defaultExecutor(int poolSize, String factoryName) {
        return Executors.newScheduledThreadPool(poolSize, new AxonThreadFactory(factoryName));
    }

    @Override
    public EventHandlingComponentsPhase<PooledStreamingEventProcessorsCustomization> component(
            @Nonnull ComponentBuilder<EventHandlingComponent> eventHandlingComponentBuilder) {
        eventHandlingBuilders.add(eventHandlingComponentBuilder);
        return this;
    }

    @Override
    public EventHandlingComponentsPhase<PooledStreamingEventProcessorsCustomization> eventHandling() {
        return this;
    }

    @Override
    public BuildPhase customized(
            @Nonnull ComponentBuilder<UnaryOperator<PooledStreamingEventProcessorsCustomization>> customizationOverride) {
        this.customizationOverride = customizationOverride.build(null);
        return this;
    }

    @Override
    public EventProcessorModule build() {
        return this;
    }
}
