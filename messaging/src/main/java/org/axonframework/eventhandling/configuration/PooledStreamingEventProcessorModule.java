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

package org.axonframework.eventhandling.configuration;

import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.LifecycleRegistry;
import org.axonframework.eventhandling.ErrorHandler;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessorSpanFactory;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventstreaming.StreamableEventSource;
import org.axonframework.monitoring.MessageMonitor;

import java.util.concurrent.ScheduledExecutorService;

/**
 * Module for configuring a PooledStreamingEventProcessor.
 */
public class PooledStreamingEventProcessorModule extends EventProcessorModule {
    private final ComponentBuilder<TokenStore> tokenStore;
    private final ComponentBuilder<StreamableEventSource<? extends EventMessage<?>>> eventSource;
    private final Integer batchSize;
    private final Integer initialSegmentCount;
    private final ComponentBuilder<ScheduledExecutorService> workerExecutor;
    private final ComponentBuilder<ScheduledExecutorService> coordinatorExecutor;

    PooledStreamingEventProcessorModule(
            String name,
            ComponentBuilder<EventHandlingComponent> eventHandlingComponent,
            ComponentBuilder<ErrorHandler> errorHandler,
            ComponentBuilder<MessageMonitor<? super EventMessage<?>>> messageMonitor,
            ComponentBuilder<EventProcessorSpanFactory> spanFactory,
            ComponentBuilder<TransactionManager> transactionManager,
            ComponentBuilder<TokenStore> tokenStore,
            ComponentBuilder<StreamableEventSource<? extends EventMessage<?>>> eventSource,
            Integer batchSize,
            Integer initialSegmentCount,
            ComponentBuilder<ScheduledExecutorService> workerExecutor,
            ComponentBuilder<ScheduledExecutorService> coordinatorExecutor
    ) {
        super(name, eventHandlingComponent, errorHandler, messageMonitor, spanFactory, transactionManager);
        this.tokenStore = tokenStore;
        this.eventSource = eventSource;
        this.batchSize = batchSize;
        this.initialSegmentCount = initialSegmentCount;
        this.workerExecutor = workerExecutor;
        this.coordinatorExecutor = coordinatorExecutor;
    }

    @Override
    public Configuration build(Configuration config, LifecycleRegistry lifecycleRegistry) {
        var errorHandlerInstance = errorHandler != null ? errorHandler.build(config) : null;
        var messageMonitorInstance = messageMonitor != null ? messageMonitor.build(config) : null;
        var spanFactoryInstance = spanFactory != null ? spanFactory.build(config) : null;
        var transactionManagerInstance = transactionManager != null ? transactionManager.build(config) : null;
        var builder = org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor.builder()
                .name(name)
                .eventHandlingComponent(eventHandlingComponent.build(config));
        if (errorHandlerInstance != null) builder.errorHandler(errorHandlerInstance);
        if (messageMonitorInstance != null) builder.messageMonitor(messageMonitorInstance);
        if (spanFactoryInstance != null) builder.spanFactory(spanFactoryInstance);
        if (transactionManagerInstance != null) builder.transactionManager(transactionManagerInstance);
        if (eventSource != null) builder.eventSource(eventSource.build(config));
        if (tokenStore != null) builder.tokenStore(tokenStore.build(config));
        if (batchSize != null) builder.batchSize(batchSize);
        if (initialSegmentCount != null) builder.initialSegmentCount(initialSegmentCount);
        if (workerExecutor != null) builder.workerExecutor(workerExecutor.build(config));
        if (coordinatorExecutor != null) builder.coordinatorExecutor(coordinatorExecutor.build(config));
        var processor = builder.build();
        lifecycleRegistry.onStart(processor::start);
        lifecycleRegistry.onShutdown(processor::shutDown);
        return config;
    }

    public static class Builder extends EventProcessorModule.Builder<Builder> implements EventProcessingModule.StreamingPhase {
        private ComponentBuilder<TokenStore> tokenStore;
        private ComponentBuilder<StreamableEventSource<? extends EventMessage<?>>> eventSource;
        private Integer batchSize;
        private Integer initialSegmentCount;
        private ComponentBuilder<ScheduledExecutorService> workerExecutor;
        private ComponentBuilder<ScheduledExecutorService> coordinatorExecutor;

        public Builder(String name) { super(name); }
        @Override
        public Builder tokenStore(ComponentBuilder<TokenStore> tokenStore) { this.tokenStore = tokenStore; return this; }
        @Override
        public Builder eventSource(ComponentBuilder<StreamableEventSource<? extends EventMessage<?>>> eventSource) { this.eventSource = eventSource; return this; }
        @Override
        public Builder batchSize(int batchSize) { this.batchSize = batchSize; return this; }
        @Override
        public Builder initialSegmentCount(int initialSegmentCount) { this.initialSegmentCount = initialSegmentCount; return this; }
        @Override
        public Builder workerExecutor(ComponentBuilder<ScheduledExecutorService> workerExecutor) { this.workerExecutor = workerExecutor; return this; }
        @Override
        public Builder coordinatorExecutor(ComponentBuilder<ScheduledExecutorService> coordinatorExecutor) { this.coordinatorExecutor = coordinatorExecutor; return this; }
        @Override
        public PooledStreamingEventProcessorModule build() {
            if (name == null || name.isBlank()) throw new IllegalStateException("Processor name must be provided");
            if (eventHandlingComponent == null) throw new IllegalStateException("EventHandlingComponent must be provided");
            if (tokenStore == null) throw new IllegalStateException("TokenStore must be provided for streaming processor");
            if (eventSource == null) throw new IllegalStateException("EventSource must be provided for streaming processor");
            return new PooledStreamingEventProcessorModule(name, eventHandlingComponent, errorHandler, messageMonitor, spanFactory, transactionManager, tokenStore, eventSource, batchSize, initialSegmentCount, workerExecutor, coordinatorExecutor);
        }
    }
} 