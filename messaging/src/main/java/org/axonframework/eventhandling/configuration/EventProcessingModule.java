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

import jakarta.annotation.Nonnull;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.Module;
import org.axonframework.configuration.ModuleBuilder;
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
     * Start building a SubscribingEventProcessor module with the given processor name.
     * The subscribing event processor will register with a message source to receive events.
     *
     * @param name the processor name, must not be null or empty
     * @return a builder phase to configure a subscribing event processor
     */
    static SubscribingPhase subscribing(String name) {
        return new SubscribingEventProcessorModule.Builder(name);
    }

    /**
     * Start building a PooledStreamingEventProcessor module with the given processor name.
     * The pooled streaming processor manages multiple segments to process events from a stream.
     *
     * @param name the processor name, must not be null or empty
     * @return a builder phase to configure a pooled streaming event processor
     */
    static StreamingPhase pooledStreaming(String name) {
        return new PooledStreamingEventProcessorModule.Builder(name);
    }

    /**
     * Shared configuration phase for common event processor options.
     *
     * @param <T> next phase type
     */
    interface SharedConfigPhase<T> {

        /**
         * Sets the error handler for the event processor.
         * The error handler defines how exceptions during event processing are handled.
         *
         * @param errorHandler the component builder that provides the error handler
         * @return the current builder instance, for fluent interfacing
         */
        T errorHandler(@Nonnull ComponentBuilder<ErrorHandler> errorHandler);

        /**
         * Sets the event handling component for the event processor.
         * The event handling component contains the actual event handlers that process events.
         *
         * @param eventHandlingComponent the component builder that provides the event handling component
         * @return the current builder instance, for fluent interfacing
         */
        T eventHandlingComponent(@Nonnull ComponentBuilder<EventHandlingComponent> eventHandlingComponent);

        /**
         * Sets the message monitor for the event processor.
         * The message monitor is used to monitor the processing of event messages.
         *
         * @param messageMonitor the component builder that provides the message monitor
         * @return the current builder instance, for fluent interfacing
         */
        T messageMonitor(@Nonnull ComponentBuilder<MessageMonitor<? super EventMessage<?>>> messageMonitor);

        /**
         * Sets the span factory for the event processor.
         * The span factory is used to create spans for tracing event processing.
         *
         * @param spanFactory the component builder that provides the span factory
         * @return the current builder instance, for fluent interfacing
         */
        T spanFactory(@Nonnull ComponentBuilder<EventProcessorSpanFactory> spanFactory);
    }

    /**
     * Subscribing event processor configuration phase.
     */
    interface SubscribingPhase extends SharedConfigPhase<SubscribingPhase> {

        /**
         * Sets the transaction manager for the subscribing event processor.
         * The transaction manager is used to manage transactions when processing events.
         *
         * @param transactionManager the component builder that provides the transaction manager
         * @return the current builder instance, for fluent interfacing
         */
        SubscribingPhase transactionManager(ComponentBuilder<TransactionManager> transactionManager);

        /**
         * Sets the message source for the subscribing event processor.
         * The message source provides the events to be processed.
         * This is a required configuration for a subscribing event processor.
         *
         * @param messageSource the component builder that provides the message source
         * @return the current builder instance, for fluent interfacing
         */
        SubscribingPhase messageSource(
                @Nonnull ComponentBuilder<SubscribableMessageSource<? extends EventMessage<?>>> messageSource);

        /**
         * Builds and returns the SubscribingEventProcessorModule with the current configuration.
         * This method validates that all required components are configured.
         *
         * @return a fully configured SubscribingEventProcessorModule
         * @throws IllegalStateException if any required configuration is missing
         */
        SubscribingEventProcessorModule build();
    }

    /**
     * Streaming (pooled) event processor configuration phase.
     */
    interface StreamingPhase extends SharedConfigPhase<StreamingPhase> {

        /**
         * Sets the event source for the streaming event processor.
         * The event source provides the events to be processed.
         * This is a required configuration for a streaming event processor.
         *
         * @param eventSource the component builder that provides the event source
         * @return the current builder instance, for fluent interfacing
         */
        StreamingPhase eventSource(ComponentBuilder<StreamableEventSource<? extends EventMessage<?>>> eventSource);

        /**
         * Sets the token store for the streaming event processor.
         * The token store is used to track the processor's progress through the event stream.
         * This is a required configuration for a streaming event processor.
         *
         * @param tokenStore the component builder that provides the token store
         * @return the current builder instance, for fluent interfacing
         */
        StreamingPhase tokenStore(@Nonnull ComponentBuilder<TokenStore> tokenStore);

        /**
         * Sets the batch size for the streaming event processor.
         * The batch size determines the maximum number of events processed in a single batch.
         *
         * @param batchSize the maximum number of events to process in a single batch
         * @return the current builder instance, for fluent interfacing
         */
        StreamingPhase batchSize(int batchSize);

        /**
         * Sets the initial segment count for the streaming event processor.
         * The segment count determines how many parallel processing segments will be created.
         *
         * @param initialSegmentCount the initial number of segments
         * @return the current builder instance, for fluent interfacing
         */
        StreamingPhase initialSegmentCount(int initialSegmentCount);

        /**
         * Sets the worker executor for the streaming event processor.
         * The worker executor is used to process events in the segments.
         *
         * @param workerExecutor the component builder that provides the worker executor
         * @return the current builder instance, for fluent interfacing
         */
        StreamingPhase workerExecutor(@Nonnull ComponentBuilder<ScheduledExecutorService> workerExecutor);

        /**
         * Sets the coordinator executor for the streaming event processor.
         * The coordinator executor is used to coordinate the segments.
         *
         * @param coordinatorExecutor the component builder that provides the coordinator executor
         * @return the current builder instance, for fluent interfacing
         */
        StreamingPhase coordinatorExecutor(@Nonnull ComponentBuilder<ScheduledExecutorService> coordinatorExecutor);

        /**
         * Sets the transaction manager for the streaming event processor.
         * The transaction manager is used to manage transactions when processing events.
         *
         * @param transactionManager the component builder that provides the transaction manager
         * @return the current builder instance, for fluent interfacing
         */
        StreamingPhase transactionManager(ComponentBuilder<TransactionManager> transactionManager);

        /**
         * Builds and returns the PooledStreamingEventProcessorModule with the current configuration.
         * This method validates that all required components are configured.
         *
         * @return a fully configured PooledStreamingEventProcessorModule
         * @throws IllegalStateException if any required configuration is missing
         */
        PooledStreamingEventProcessorModule build();
    }
}