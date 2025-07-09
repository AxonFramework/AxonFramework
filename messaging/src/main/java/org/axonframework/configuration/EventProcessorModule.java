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
import org.axonframework.monitoring.MessageMonitor;

/**
 * Abstract base class for event processor modules (subscribing/streaming).
 * Holds shared configuration and builder logic.
 */
public abstract class EventProcessorModule implements Module {
    protected final String name;
    protected final ComponentBuilder<EventHandlingComponent> eventHandlingComponent;
    protected final ComponentBuilder<ErrorHandler> errorHandler;
    protected final ComponentBuilder<MessageMonitor<? super EventMessage<?>>> messageMonitor;
    protected final ComponentBuilder<EventProcessorSpanFactory> spanFactory;
    protected final ComponentBuilder<TransactionManager> transactionManager;

    protected EventProcessorModule(
            String name,
            ComponentBuilder<EventHandlingComponent> eventHandlingComponent,
            ComponentBuilder<ErrorHandler> errorHandler,
            ComponentBuilder<MessageMonitor<? super EventMessage<?>>> messageMonitor,
            ComponentBuilder<EventProcessorSpanFactory> spanFactory,
            ComponentBuilder<TransactionManager> transactionManager
    ) {
        this.name = name;
        this.eventHandlingComponent = eventHandlingComponent;
        this.errorHandler = errorHandler;
        this.messageMonitor = messageMonitor;
        this.spanFactory = spanFactory;
        this.transactionManager = transactionManager;
    }

    @Override
    public String name() { return name; }

    /**
     * Abstract builder for event processor modules.
     * @param <T> concrete builder type
     */
    public static abstract class Builder<T extends Builder<T>> {
        protected final String name;
        protected ComponentBuilder<EventHandlingComponent> eventHandlingComponent;
        protected ComponentBuilder<ErrorHandler> errorHandler;
        protected ComponentBuilder<MessageMonitor<? super EventMessage<?>>> messageMonitor;
        protected ComponentBuilder<EventProcessorSpanFactory> spanFactory;
        protected ComponentBuilder<TransactionManager> transactionManager;

        protected Builder(String name) { this.name = name; }
        @SuppressWarnings("unchecked")
        public T errorHandler(ComponentBuilder<ErrorHandler> errorHandler) { this.errorHandler = errorHandler; return (T) this; }
        @SuppressWarnings("unchecked")
        public T eventHandlingComponent(ComponentBuilder<EventHandlingComponent> eventHandlingComponent) { this.eventHandlingComponent = eventHandlingComponent; return (T) this; }
        @SuppressWarnings("unchecked")
        public T messageMonitor(ComponentBuilder<MessageMonitor<? super EventMessage<?>>> messageMonitor) { this.messageMonitor = messageMonitor; return (T) this; }
        @SuppressWarnings("unchecked")
        public T spanFactory(ComponentBuilder<EventProcessorSpanFactory> spanFactory) { this.spanFactory = spanFactory; return (T) this; }
        @SuppressWarnings("unchecked")
        public T transactionManager(ComponentBuilder<TransactionManager> transactionManager) { this.transactionManager = transactionManager; return (T) this; }
    }
} 