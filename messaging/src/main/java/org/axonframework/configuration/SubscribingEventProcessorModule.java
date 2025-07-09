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

package org.axonframework.configuration;

import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.ErrorHandler;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessorSpanFactory;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.monitoring.MessageMonitor;

/**
 * Module for configuring a SubscribingEventProcessor.
 */
public class SubscribingEventProcessorModule implements Module {
    private final String name;
    private final ComponentBuilder<EventHandlingComponent> eventHandlingComponent;
    private final ComponentBuilder<ErrorHandler> errorHandler;
    private final ComponentBuilder<MessageMonitor<? super EventMessage<?>>> messageMonitor;
    private final ComponentBuilder<EventProcessorSpanFactory> spanFactory;
    private final ComponentBuilder<TransactionManager> transactionManager;
    private final ComponentBuilder<SubscribableMessageSource<? extends EventMessage<?>>> messageSource;

    SubscribingEventProcessorModule(
            String name,
            ComponentBuilder<EventHandlingComponent> eventHandlingComponent,
            ComponentBuilder<ErrorHandler> errorHandler,
            ComponentBuilder<MessageMonitor<? super EventMessage<?>>> messageMonitor,
            ComponentBuilder<EventProcessorSpanFactory> spanFactory,
            ComponentBuilder<TransactionManager> transactionManager,
            ComponentBuilder<SubscribableMessageSource<? extends EventMessage<?>>> messageSource
    ) {
        this.name = name;
        this.eventHandlingComponent = eventHandlingComponent;
        this.errorHandler = errorHandler;
        this.messageMonitor = messageMonitor;
        this.spanFactory = spanFactory;
        this.transactionManager = transactionManager;
        this.messageSource = messageSource;
    }

    @Override
    public String name() { return name; }

    @Override
    public Configuration build(Configuration config, LifecycleRegistry lifecycleRegistry) {
        var errorHandlerInstance = errorHandler != null ? errorHandler.build(config) : null;
        var messageMonitorInstance = messageMonitor != null ? messageMonitor.build(config) : null;
        var spanFactoryInstance = spanFactory != null ? spanFactory.build(config) : null;
        var transactionManagerInstance = transactionManager != null ? transactionManager.build(config) : null;
        var builder = org.axonframework.eventhandling.SubscribingEventProcessor.builder()
                .name(name)
                .eventHandlingComponent(eventHandlingComponent.build(config));
        if (errorHandlerInstance != null) builder.errorHandler(errorHandlerInstance);
        if (messageMonitorInstance != null) builder.messageMonitor(messageMonitorInstance);
        if (spanFactoryInstance != null) builder.spanFactory(spanFactoryInstance);
        if (transactionManagerInstance != null) builder.transactionManager(transactionManagerInstance);
        if (messageSource != null) builder.messageSource(messageSource.build(config));
        var processor = builder.build();
        lifecycleRegistry.onStart(processor::start);
        lifecycleRegistry.onShutdown(processor::shutDown);
        return config;
    }

    public static class Builder implements EventProcessingModule.SubscribingPhase {
        private final String name;
        private ComponentBuilder<EventHandlingComponent> eventHandlingComponent;
        private ComponentBuilder<ErrorHandler> errorHandler;
        private ComponentBuilder<MessageMonitor<? super EventMessage<?>>> messageMonitor;
        private ComponentBuilder<EventProcessorSpanFactory> spanFactory;
        private ComponentBuilder<TransactionManager> transactionManager;
        private ComponentBuilder<SubscribableMessageSource<? extends EventMessage<?>>> messageSource;

        public Builder(String name) { this.name = name; }
        @Override
        public Builder errorHandler(ComponentBuilder<ErrorHandler> errorHandler) { this.errorHandler = errorHandler; return this; }
        @Override
        public Builder eventHandlingComponent(ComponentBuilder<EventHandlingComponent> eventHandlingComponent) { this.eventHandlingComponent = eventHandlingComponent; return this; }
        @Override
        public Builder messageMonitor(ComponentBuilder<MessageMonitor<? super EventMessage<?>>> messageMonitor) { this.messageMonitor = messageMonitor; return this; }
        @Override
        public Builder spanFactory(ComponentBuilder<EventProcessorSpanFactory> spanFactory) { this.spanFactory = spanFactory; return this; }
        @Override
        public Builder transactionManager(ComponentBuilder<TransactionManager> transactionManager) { this.transactionManager = transactionManager; return this; }
        @Override
        public Builder messageSource(ComponentBuilder<SubscribableMessageSource<? extends EventMessage<?>>> messageSource) { this.messageSource = messageSource; return this; }
        @Override
        public SubscribingEventProcessorModule build() {
            if (name == null || name.isBlank()) throw new IllegalStateException("Processor name must be provided");
            if (eventHandlingComponent == null) throw new IllegalStateException("EventHandlingComponent must be provided");
            if (messageSource == null) throw new IllegalStateException("MessageSource must be provided for subscribing processor");
            return new SubscribingEventProcessorModule(name, eventHandlingComponent, errorHandler, messageMonitor, spanFactory, transactionManager, messageSource);
        }
    }
} 