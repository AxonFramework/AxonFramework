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
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.ErrorHandler;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessorSpanFactory;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventstreaming.StreamableEventSource;
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
        return new ProcessorTypePhase() {
            @Override
            public SubscribingPhase subscribing() {
                return new org.axonframework.configuration.SubscribingEventProcessorModule.Builder(name);
            }
            @Override
            public StreamingPhase streaming() { // todo: pooledStreaming()
                return new org.axonframework.configuration.PooledStreamingEventProcessorModule.Builder(name);
            }
        };
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
        /**
         * Sets the transaction manager for the subscribing event processor.
         */
        SubscribingPhase transactionManager(ComponentBuilder<TransactionManager> transactionManager);
        SubscribingPhase messageSource(@Nonnull ComponentBuilder<SubscribableMessageSource<? extends EventMessage<?>>> messageSource);
        SubscribingEventProcessorModule build();
    }

    /**
     * Streaming (pooled) event processor configuration phase.
     */
    interface StreamingPhase extends SharedConfigPhase<StreamingPhase> {
        /**
         * Sets the event source (StreamableEventSource) for the streaming event processor.
         */
        StreamingPhase eventSource(ComponentBuilder<StreamableEventSource<? extends EventMessage<?>>> eventSource);
        StreamingPhase tokenStore(@Nonnull ComponentBuilder<TokenStore> tokenStore);
        StreamingPhase batchSize(int batchSize);
        StreamingPhase initialSegmentCount(int initialSegmentCount);
        StreamingPhase workerExecutor(@Nonnull ComponentBuilder<ScheduledExecutorService> workerExecutor);
        StreamingPhase coordinatorExecutor(@Nonnull ComponentBuilder<ScheduledExecutorService> coordinatorExecutor);
        /**
         * Sets the transaction manager for the streaming event processor.
         */
        StreamingPhase transactionManager(ComponentBuilder<TransactionManager> transactionManager);
        PooledStreamingEventProcessorModule build();
    }
}
