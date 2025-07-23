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
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.ProcessorEventHandlingComponents;
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventhandling.interceptors.MessageHandlerInterceptors;
import org.axonframework.eventhandling.pipeline.DefaultEventProcessingPipeline;
import org.axonframework.eventhandling.pipeline.DefaultEventProcessorHandlingComponent;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.eventstreaming.StreamableEventSource;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;

public class PooledStreamingEventProcessorModule
        extends BaseModule<PooledStreamingEventProcessorModule>
        implements EventProcessorModule,
        EventProcessorModule.StreamingSourcePhase,
        EventProcessorModule.EventHandlersPhase {

    private final String processorName;
    private final Map<QualifiedName, ComponentBuilder<EventHandler>> handlerBuilders;
    private ComponentBuilder<StreamableEventSource<? extends EventMessage<?>>> streamableEventSourceBuilder;

    // todo: defaults - should be configurable
    private final PooledStreamingEventProcessorsCustomization eventProcessorsCustomization = new PooledStreamingEventProcessorsCustomization();
    private final MessageHandlerInterceptors messageHandlerInterceptors = new MessageHandlerInterceptors();


    public PooledStreamingEventProcessorModule(@Nonnull String processorName) {
        super(processorName);
        this.processorName = processorName;
        this.handlerBuilders = new HashMap<>();
    }

    @Override
    public EventHandlersPhase eventSource(
            @Nonnull ComponentBuilder<StreamableEventSource<? extends EventMessage<?>>> streamableEventSourceBuilder) {
        this.streamableEventSourceBuilder = streamableEventSourceBuilder;
        return this;
    }

    @Override
    public EventHandlersPhase eventHandler(@Nonnull QualifiedName eventName,
                                           @Nonnull ComponentBuilder<EventHandler> eventHandler) {
        handlerBuilders.put(eventName, eventHandler);
        return this;
    }

    @Override
    public Configuration build(@Nonnull Configuration parent, @Nonnull LifecycleRegistry lifecycleRegistry) {
        // todo: move it to the component registry!
        var eventHandlingComponent = new SimpleEventHandlingComponent();
        handlerBuilders.forEach((key, value) -> eventHandlingComponent.subscribe(key, value.build(parent)));

        var eventSource = streamableEventSourceBuilder.build(parent);

        // todo: get from global configuration, but allow overriding
        var tokenStore = parent.getOptionalComponent(TokenStore.class).orElse(new InMemoryTokenStore());
        var unitOfWorkFactory = parent.getOptionalComponent(UnitOfWorkFactory.class).orElse(new SimpleUnitOfWorkFactory()); // todo: default - transcaitonal
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

        var spanFactory = eventProcessorsCustomization.spanFactory();
        var messageMonitor = eventProcessorsCustomization.messageMonitor();
        var errorHandler = eventProcessorsCustomization.errorHandler();
        var decoratedEventHandlingComponent = new DefaultEventProcessorHandlingComponent(
                spanFactory,
                messageMonitor,
                messageHandlerInterceptors,
                eventHandlingComponent,
                true
        );
        var eventHandlingComponents =
                new ProcessorEventHandlingComponents(List.of(decoratedEventHandlingComponent));
        var decoratedEventProcessingPipeline = new DefaultEventProcessingPipeline(
                processorName,
                errorHandler,
                spanFactory,
                eventHandlingComponents,
                true
        );
        var processor = new PooledStreamingEventProcessor(
                processorName,
                eventSource,
                decoratedEventProcessingPipeline,
                eventHandlingComponents,
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
    public EventProcessorModule build() {
        return this;
    }
}
