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
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.monitoring.MessageMonitor;

/**
 * Module for configuring a SubscribingEventProcessor.
 */
public class SubscribingEventProcessorModule extends EventProcessorModule {
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
        super(name, eventHandlingComponent, errorHandler, messageMonitor, spanFactory, transactionManager);
        this.messageSource = messageSource;
    }

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

    public static class Builder extends EventProcessorModule.Builder<Builder> implements EventProcessingModule.SubscribingPhase {
        private ComponentBuilder<SubscribableMessageSource<? extends EventMessage<?>>> messageSource;

        public Builder(String name) { super(name); }
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