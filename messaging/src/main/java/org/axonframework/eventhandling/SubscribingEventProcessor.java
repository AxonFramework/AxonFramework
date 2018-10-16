/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.eventhandling;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.common.io.IOUtils;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.unitofwork.BatchingUnitOfWork;
import org.axonframework.messaging.unitofwork.RollbackConfiguration;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;

import java.util.List;
import java.util.function.Consumer;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Event processor implementation that {@link EventBus#subscribe(Consumer) subscribes} to the {@link EventBus} for
 * events. Events published on the event bus are supplied to this processor in the publishing thread.
 * <p>
 * Depending on the given {@link EventProcessingStrategy} the events are processed directly (in the publishing thread)
 * or asynchronously.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public class SubscribingEventProcessor extends AbstractEventProcessor {

    private final SubscribableMessageSource<? extends EventMessage<?>> messageSource;
    private final EventProcessingStrategy processingStrategy;

    private volatile Registration eventBusRegistration;

    /**
     * Instantiate a {@link SubscribingEventProcessor} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the Event Processor {@code name}, {@link EventHandlerInvoker} and
     * {@link SubscribableMessageSource} are not {@code null}, and will throw an {@link AxonConfigurationException} if
     * any of them is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link SubscribingEventProcessor} instance
     */
    protected SubscribingEventProcessor(Builder builder) {
        super(builder);
        this.messageSource = builder.messageSource;
        this.processingStrategy = builder.processingStrategy;
    }

    /**
     * Instantiate a Builder to be able to create a {@link SubscribingEventProcessor}.
     * <p>
     * The {@link RollbackConfigurationType} defaults to a {@link RollbackConfigurationType#ANY_THROWABLE}, the
     * {@link ErrorHandler} is defaulted to a {@link PropagatingErrorHandler}, the {@link MessageMonitor} defaults to a
     * {@link NoOpMessageMonitor} and the {@link EventProcessingStrategy} defaults to a
     * {@link DirectEventProcessingStrategy}. The Event Processor {@code name}, {@link EventHandlerInvoker} and
     * {@link SubscribableMessageSource} are <b>hard requirements</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link SubscribingEventProcessor}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Start this processor. This will register the processor with the {@link EventBus}.
     */
    @SuppressWarnings("unchecked")
    @Override
    public void start() {
        // prevent double registration
        if (eventBusRegistration == null) {
            eventBusRegistration =
                    messageSource.subscribe(eventMessages -> processingStrategy.handle(eventMessages,
                                                                                       this::process));
        }
    }

    /**
     * Process the given messages. A Unit of Work must be created for this processing.
     * <p>
     * This implementation creates a Batching unit of work for the given batch of {@code eventMessages}.
     *
     * @param eventMessages The messages to process
     */
    protected void process(List<? extends EventMessage<?>> eventMessages) {
        try {
            processInUnitOfWork(eventMessages, new BatchingUnitOfWork<>(eventMessages), Segment.ROOT_SEGMENT);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new EventProcessingException("Exception occurred while processing events", e);
        }
    }

    /**
     * Shut down this processor. This will deregister the processor with the {@link EventBus}.
     */
    @Override
    public void shutDown() {
        IOUtils.closeQuietly(eventBusRegistration);
        eventBusRegistration = null;
    }

    /**
     * Builder class to instantiate a {@link SubscribingEventProcessor}.
     * <p>
     * The {@link RollbackConfigurationType} defaults to a {@link RollbackConfigurationType#ANY_THROWABLE}, the
     * {@link ErrorHandler} is defaulted to a {@link PropagatingErrorHandler}, the {@link MessageMonitor} defaults to a
     * {@link NoOpMessageMonitor} and the {@link EventProcessingStrategy} defaults to a
     * {@link DirectEventProcessingStrategy}. The Event Processor {@code name}, {@link EventHandlerInvoker} and
     * {@link SubscribableMessageSource} are <b>hard requirements</b> and as such should be provided.
     */
    public static class Builder extends AbstractEventProcessor.Builder {

        private SubscribableMessageSource<? extends EventMessage<?>> messageSource;
        private EventProcessingStrategy processingStrategy = DirectEventProcessingStrategy.INSTANCE;

        public Builder() {
            super.rollbackConfiguration(RollbackConfigurationType.ANY_THROWABLE);
        }

        @Override
        public Builder name(String name) {
            super.name(name);
            return this;
        }

        @Override
        public Builder eventHandlerInvoker(EventHandlerInvoker eventHandlerInvoker) {
            super.eventHandlerInvoker(eventHandlerInvoker);
            return this;
        }

        /**
         * {@inheritDoc}. Defaults to a {@link RollbackConfigurationType#ANY_THROWABLE})
         */
        @Override
        public Builder rollbackConfiguration(RollbackConfiguration rollbackConfiguration) {
            super.rollbackConfiguration(rollbackConfiguration);
            return this;
        }

        @Override
        public Builder errorHandler(ErrorHandler errorHandler) {
            super.errorHandler(errorHandler);
            return this;
        }

        @Override
        public Builder messageMonitor(MessageMonitor<? super EventMessage<?>> messageMonitor) {
            super.messageMonitor(messageMonitor);
            return this;
        }

        /**
         * Sets the {@link SubscribableMessageSource} (e.g. the {@link EventBus}) to which this {@link EventProcessor}
         * implementation will subscribe itself to receive {@link EventMessage}s.
         *
         * @param messageSource the {@link SubscribableMessageSource} (e.g. the {@link EventBus}) to which this
         *                      {@link EventProcessor} implementation will subscribe itself to receive
         *                      {@link EventMessage}s
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder messageSource(SubscribableMessageSource<? extends EventMessage<?>> messageSource) {
            assertNonNull(messageSource, "SubscribableMessageSource may not be null");
            this.messageSource = messageSource;
            return this;
        }

        /**
         * Sets the {@link EventProcessingStrategy} determining whether events are processed directly or asynchronously.
         * Defaults to a {@link DirectEventProcessingStrategy}.
         *
         * @param processingStrategy the {@link EventProcessingStrategy} determining whether events are processed
         *                           directly or asynchronously
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder processingStrategy(EventProcessingStrategy processingStrategy) {
            assertNonNull(processingStrategy, "EventProcessingStrategy may not be null");
            this.processingStrategy = processingStrategy;
            return this;
        }

        /**
         * Initializes a {@link SubscribingEventProcessor} as specified through this Builder.
         *
         * @return a {@link SubscribingEventProcessor} as specified through this Builder
         */
        public SubscribingEventProcessor build() {
            return new SubscribingEventProcessor(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        @Override
        protected void validate() throws AxonConfigurationException {
            super.validate();
            assertNonNull(messageSource, "The SubscribableMessageSource is a hard requirement and should be provided");
        }
    }
}
