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
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.configuration.BaseModule;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.LifecycleRegistry;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessorModule;
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.eventstreaming.StreamableEventSource;
import org.axonframework.messaging.QualifiedName;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class PooledStreamingEventProcessorModule
        extends BaseModule<PooledStreamingEventProcessorModule>
        implements EventProcessorModule,
        EventProcessorModule.StreamingSourcePhase,
        EventProcessorModule.EventHandlersPhase {

    private final String processorName;
    private final Map<QualifiedName, ComponentBuilder<EventHandler>> handlerBuilders;
    private ComponentBuilder<StreamableEventSource<? extends EventMessage<?>>> streamableEventSourceBuilder;

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

        var processor = PooledStreamingEventProcessor.builder()
                .name(processorName)
                .eventHandlingComponent(eventHandlingComponent)
                .eventSource(eventSource)
                .tokenStore(parent.getOptionalComponent(TokenStore.class).orElse(new InMemoryTokenStore()))
                .transactionManager(parent.getOptionalComponent(TransactionManager.class).orElse(NoTransactionManager.instance()))
                .workerExecutor(processorName -> {
                    ScheduledExecutorService workerExecutor =
                            defaultExecutor(4, "WorkPackage[" + processorName + "]");
                    lifecycleRegistry.onShutdown(1, workerExecutor::shutdown);
                    return workerExecutor;
                })
                .coordinatorExecutor(processorName -> {
                    ScheduledExecutorService coordinatorExecutor =
                            defaultExecutor(1, "Coordinator[" + processorName + "]");
                    lifecycleRegistry.onShutdown(1, coordinatorExecutor::shutdown);
                    return coordinatorExecutor;
                })
                .build();
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
