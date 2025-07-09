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

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.ErrorHandler;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessorSpanFactory;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.monitoring.MessageMonitor;

import java.util.concurrent.ScheduledExecutorService;

/**
 * Configuration module for event processing, supporting both subscribing and streaming event processors.
 * <p>
 * Usage example:
 * <pre>
 * EventProcessingModule.named("myProcessor")
 *     .subscribing()
 *     .messageSource(c -> ...)
 *     .errorHandler(c -> ...)
 *     .eventHandlingComponent(c -> ...)
 *     .build();
 * </pre>
 * or
 * <pre>
 * EventProcessingModule.named("myProcessor")
 *     .streaming()
 *     .tokenStore(c -> ...)
 *     .errorHandler(c -> ...)
 *     .eventHandlingComponent(c -> ...)
 *     .build();
 * </pre>
 *
 * @author ...
 * @since 5.0.0
 */
public interface EventProcessingModule extends Module, ModuleBuilder<EventProcessingModule> {

    /**
     * Start building an EventProcessingModule for the given processor name.
     * @param name the processor name
     * @return the phase to choose processor type
     */
    static ProcessorTypePhase named(@Nonnull String name) {
        return new Builder(name);
    }

    /**
     * Phase to choose processor type (subscribing or streaming).
     */
    interface ProcessorTypePhase {
        SubscribingPhase subscribing();
        StreamingPhase streaming();
    }

    /**
     * Shared configuration phase for common event processor options.
     * @param <T> next phase type
     */
    interface SharedConfigPhase<T> {
        T errorHandler(@Nonnull ComponentBuilder<ErrorHandler> errorHandler);
        T eventHandlingComponent(@Nonnull ComponentBuilder<EventHandlingComponent> eventHandlingComponent);
        T messageMonitor(@Nonnull ComponentBuilder<MessageMonitor<? super EventMessage<?>>> messageMonitor);
        T spanFactory(@Nonnull ComponentBuilder<EventProcessorSpanFactory> spanFactory);
    }

    /**
     * Subscribing event processor configuration phase.
     */
    interface SubscribingPhase extends SharedConfigPhase<SubscribingPhase> {
        SubscribingPhase messageSource(@Nonnull ComponentBuilder<SubscribableMessageSource<? extends EventMessage<?>>> messageSource);
        EventProcessingModule build();
    }

    /**
     * Streaming (pooled) event processor configuration phase.
     */
    interface StreamingPhase extends SharedConfigPhase<StreamingPhase> {
        StreamingPhase tokenStore(@Nonnull ComponentBuilder<TokenStore> tokenStore);
        StreamingPhase batchSize(int batchSize);
        StreamingPhase initialSegmentCount(int initialSegmentCount);
        StreamingPhase workerExecutor(@Nonnull ComponentBuilder<ScheduledExecutorService> workerExecutor);
        StreamingPhase coordinatorExecutor(@Nonnull ComponentBuilder<ScheduledExecutorService> coordinatorExecutor);
        EventProcessingModule build();
    }

    // --- Internal builder implementation ---
    class Builder implements ProcessorTypePhase {
        private final String name;
        // Shared config
        private ComponentBuilder<ErrorHandler> errorHandler;
        private ComponentBuilder<EventHandlingComponent> eventHandlingComponent;
        private ComponentBuilder<MessageMonitor<? super EventMessage<?>>> messageMonitor;
        private ComponentBuilder<EventProcessorSpanFactory> spanFactory;
        // Subscribing-specific
        private ComponentBuilder<SubscribableMessageSource<? extends EventMessage<?>>> messageSource;
        // Streaming-specific
        private ComponentBuilder<TokenStore> tokenStore;
        private Integer batchSize;
        private Integer initialSegmentCount;
        private ComponentBuilder<ScheduledExecutorService> workerExecutor;
        private ComponentBuilder<ScheduledExecutorService> coordinatorExecutor;

        Builder(String name) {
            this.name = name;
        }

        @Override
        public SubscribingPhase subscribing() {
            return new SubscribingBuilder();
        }
        @Override
        public StreamingPhase streaming() {
            return new StreamingBuilder();
        }

        // Inner builder for SubscribingPhase
        private class SubscribingBuilder implements SubscribingPhase {
            @Override
            public SubscribingPhase errorHandler(ComponentBuilder<ErrorHandler> errorHandler) {
                Builder.this.errorHandler = errorHandler;
                return this;
            }
            @Override
            public SubscribingPhase eventHandlingComponent(ComponentBuilder<EventHandlingComponent> eventHandlingComponent) {
                Builder.this.eventHandlingComponent = eventHandlingComponent;
                return this;
            }
            @Override
            public SubscribingPhase messageMonitor(ComponentBuilder<MessageMonitor<? super EventMessage<?>>> messageMonitor) {
                Builder.this.messageMonitor = messageMonitor;
                return this;
            }
            @Override
            public SubscribingPhase spanFactory(ComponentBuilder<EventProcessorSpanFactory> spanFactory) {
                Builder.this.spanFactory = spanFactory;
                return this;
            }
            @Override
            public SubscribingPhase messageSource(ComponentBuilder<SubscribableMessageSource<? extends EventMessage<?>>> messageSource) {
                Builder.this.messageSource = messageSource;
                return this;
            }
            @Override
            public EventProcessingModule build() {
                // Validate required fields
                if (name == null || name.isBlank()) {
                    throw new IllegalStateException("Processor name must be provided");
                }
                if (eventHandlingComponent == null) {
                    throw new IllegalStateException("EventHandlingComponent must be provided");
                }
                if (messageSource == null) {
                    throw new IllegalStateException("MessageSource must be provided for subscribing processor");
                }
                return new SimpleEventProcessingModule(
                        name,
                        eventHandlingComponent,
                        errorHandler,
                        messageMonitor,
                        spanFactory,
                        messageSource,
                        null, // tokenStore
                        null, // batchSize
                        null, // initialSegmentCount
                        null, // workerExecutor
                        null, // coordinatorExecutor
                        ProcessorKind.SUBSCRIBING
                );
            }
        }

        // Inner builder for StreamingPhase
        private class StreamingBuilder implements StreamingPhase {
            @Override
            public StreamingPhase errorHandler(ComponentBuilder<ErrorHandler> errorHandler) {
                Builder.this.errorHandler = errorHandler;
                return this;
            }
            @Override
            public StreamingPhase eventHandlingComponent(ComponentBuilder<EventHandlingComponent> eventHandlingComponent) {
                Builder.this.eventHandlingComponent = eventHandlingComponent;
                return this;
            }
            @Override
            public StreamingPhase messageMonitor(ComponentBuilder<MessageMonitor<? super EventMessage<?>>> messageMonitor) {
                Builder.this.messageMonitor = messageMonitor;
                return this;
            }
            @Override
            public StreamingPhase spanFactory(ComponentBuilder<EventProcessorSpanFactory> spanFactory) {
                Builder.this.spanFactory = spanFactory;
                return this;
            }
            @Override
            public StreamingPhase tokenStore(ComponentBuilder<TokenStore> tokenStore) {
                Builder.this.tokenStore = tokenStore;
                return this;
            }
            @Override
            public StreamingPhase batchSize(int batchSize) {
                Builder.this.batchSize = batchSize;
                return this;
            }
            @Override
            public StreamingPhase initialSegmentCount(int initialSegmentCount) {
                Builder.this.initialSegmentCount = initialSegmentCount;
                return this;
            }
            @Override
            public StreamingPhase workerExecutor(ComponentBuilder<ScheduledExecutorService> workerExecutor) {
                Builder.this.workerExecutor = workerExecutor;
                return this;
            }
            @Override
            public StreamingPhase coordinatorExecutor(ComponentBuilder<ScheduledExecutorService> coordinatorExecutor) {
                Builder.this.coordinatorExecutor = coordinatorExecutor;
                return this;
            }
            @Override
            public EventProcessingModule build() {
                // Validate required fields
                if (name == null || name.isBlank()) {
                    throw new IllegalStateException("Processor name must be provided");
                }
                if (eventHandlingComponent == null) {
                    throw new IllegalStateException("EventHandlingComponent must be provided");
                }
                if (tokenStore == null) {
                    throw new IllegalStateException("TokenStore must be provided for streaming processor");
                }
                return new SimpleEventProcessingModule(
                        name,
                        eventHandlingComponent,
                        errorHandler,
                        messageMonitor,
                        spanFactory,
                        null, // messageSource
                        tokenStore,
                        batchSize,
                        initialSegmentCount,
                        workerExecutor,
                        coordinatorExecutor,
                        ProcessorKind.STREAMING
                );
            }
        }

        private enum ProcessorKind { SUBSCRIBING, STREAMING }

        /**
         * Concrete implementation of EventProcessingModule that registers the processor with the parent configurer.
         */
        private static class SimpleEventProcessingModule implements EventProcessingModule {
            private final String name;
            private final ComponentBuilder<EventHandlingComponent> eventHandlingComponent;
            private final ComponentBuilder<ErrorHandler> errorHandler;
            private final ComponentBuilder<MessageMonitor<? super EventMessage<?>>> messageMonitor;
            private final ComponentBuilder<EventProcessorSpanFactory> spanFactory;
            private final ComponentBuilder<SubscribableMessageSource<? extends EventMessage<?>>> messageSource;
            private final ComponentBuilder<TokenStore> tokenStore;
            private final Integer batchSize;
            private final Integer initialSegmentCount;
            private final ComponentBuilder<ScheduledExecutorService> workerExecutor;
            private final ComponentBuilder<ScheduledExecutorService> coordinatorExecutor;
            private final ProcessorKind kind;

            SimpleEventProcessingModule(
                    String name,
                    ComponentBuilder<EventHandlingComponent> eventHandlingComponent,
                    ComponentBuilder<ErrorHandler> errorHandler,
                    ComponentBuilder<MessageMonitor<? super EventMessage<?>>> messageMonitor,
                    ComponentBuilder<EventProcessorSpanFactory> spanFactory,
                    ComponentBuilder<SubscribableMessageSource<? extends EventMessage<?>>> messageSource,
                    ComponentBuilder<TokenStore> tokenStore,
                    Integer batchSize,
                    Integer initialSegmentCount,
                    ComponentBuilder<ScheduledExecutorService> workerExecutor,
                    ComponentBuilder<ScheduledExecutorService> coordinatorExecutor,
                    ProcessorKind kind
            ) {
                this.name = name;
                this.eventHandlingComponent = eventHandlingComponent;
                this.errorHandler = errorHandler;
                this.messageMonitor = messageMonitor;
                this.spanFactory = spanFactory;
                this.messageSource = messageSource;
                this.tokenStore = tokenStore;
                this.batchSize = batchSize;
                this.initialSegmentCount = initialSegmentCount;
                this.workerExecutor = workerExecutor;
                this.coordinatorExecutor = coordinatorExecutor;
                this.kind = kind;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public EventProcessingModule build() {
                return this;
            }

            @Override
            public Configuration build(Configuration config, LifecycleRegistry lifecycleRegistry) {
                // Prepare optional components only if their builders are not null
                var errorHandlerInstance = errorHandler != null ? errorHandler.build(config) : null;
                var messageMonitorInstance = messageMonitor != null ? messageMonitor.build(config) : null;
                var spanFactoryInstance = spanFactory != null ? spanFactory.build(config) : null;
                switch (kind) {
                    case SUBSCRIBING -> {
                        var builder = org.axonframework.eventhandling.SubscribingEventProcessor.builder()
                                .name(name)
                                .eventHandlingComponent(eventHandlingComponent.build(config))
                                .messageSource(messageSource.build(config));
                        if (errorHandlerInstance != null) {
                            builder.errorHandler(errorHandlerInstance);
                        }
                        if (messageMonitorInstance != null) {
                            builder.messageMonitor(messageMonitorInstance);
                        }
                        if (spanFactoryInstance != null) {
                            builder.spanFactory(spanFactoryInstance);
                        }
                        var processor = builder.build();
                        // Register processor lifecycle
                        lifecycleRegistry.onStart(processor::start);
                        lifecycleRegistry.onShutdown(processor::shutDown);
                        return config;
                    }
                    case STREAMING -> {
                        var builder = org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor.builder()
                                .name(name)
                                .eventHandlingComponent(eventHandlingComponent.build(config))
                                .tokenStore(tokenStore.build(config));
                        if (errorHandlerInstance != null) {
                            builder.errorHandler(errorHandlerInstance);
                        }
                        if (messageMonitorInstance != null) {
                            builder.messageMonitor(messageMonitorInstance);
                        }
                        if (spanFactoryInstance != null) {
                            builder.spanFactory(spanFactoryInstance);
                        }
                        if (batchSize != null) builder.batchSize(batchSize);
                        if (initialSegmentCount != null) builder.initialSegmentCount(initialSegmentCount);
                        if (workerExecutor != null) builder.workerExecutor(workerExecutor.build(config));
                        if (coordinatorExecutor != null) builder.coordinatorExecutor(coordinatorExecutor.build(config));
                        var processor = builder.build();
                        // Register processor lifecycle
                        lifecycleRegistry.onStart(processor::start);
                        lifecycleRegistry.onShutdown(processor::shutDown);
                        return config;
                    }
                }
                return config;
            }
        }
    }
}
