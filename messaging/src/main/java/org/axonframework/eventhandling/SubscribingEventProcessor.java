/*
 * Copyright (c) 2010-2023. Axon Framework
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
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.lifecycle.Lifecycle;
import org.axonframework.lifecycle.Phase;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.unitofwork.BatchingUnitOfWork;
import org.axonframework.messaging.unitofwork.RollbackConfiguration;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;

import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

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
public class SubscribingEventProcessor extends AbstractEventProcessor implements Lifecycle {

    private final SubscribableMessageSource<? extends EventMessage<?>> messageSource;
    private final EventProcessingStrategy processingStrategy;
    private final TransactionManager transactionManager;

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
        this.transactionManager = builder.transactionManager;
    }

    /**
     * Instantiate a Builder to be able to create a {@link SubscribingEventProcessor}.
     * <p>
     * The {@link RollbackConfigurationType} defaults to a {@link RollbackConfigurationType#ANY_THROWABLE}, the
     * {@link ErrorHandler} is defaulted to a {@link PropagatingErrorHandler}, the {@link MessageMonitor} defaults to a
     * {@link NoOpMessageMonitor}, the {@link EventProcessingStrategy} defaults to a
     * {@link DirectEventProcessingStrategy}, the {@link EventProcessorSpanFactory} defaults to a
     * {@link DefaultEventProcessorSpanFactory} backed by a {@link org.axonframework.tracing.NoOpSpanFactory}, and the
     * {@link TransactionManager} defaults to the {@link NoTransactionManager#INSTANCE}. The Event Processor
     * {@code name}, {@link EventHandlerInvoker} and {@link SubscribableMessageSource} are <b>hard requirements</b> and
     * as such should be provided.
     *
     * @return a Builder to be able to create a {@link SubscribingEventProcessor}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void registerLifecycleHandlers(@Nonnull LifecycleRegistry handle) {
        handle.onStart(Phase.LOCAL_MESSAGE_HANDLER_REGISTRATIONS, this::start);
        handle.onShutdown(Phase.LOCAL_MESSAGE_HANDLER_REGISTRATIONS, this::shutDown);
    }

    /**
     * Start this processor. This will register the processor with the {@link EventBus}.
     * <p>
     * Upon start up of an application, this method will be invoked in the
     * {@link Phase#LOCAL_MESSAGE_HANDLER_REGISTRATIONS} phase.
     */
    @Override
    public void start() {
        if (eventBusRegistration != null) {
            // This event processor has already been started
            return;
        }
        eventBusRegistration =
                messageSource.subscribe(eventMessages -> processingStrategy.handle(eventMessages, this::process));
    }

    @Override
    public boolean isRunning() {
        return eventBusRegistration != null;
    }

    @Override
    public boolean isError() {
        // this implementation will never stop because of an error
        return false;
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
            BatchingUnitOfWork<? extends EventMessage<?>> unitOfWork = new BatchingUnitOfWork<>(eventMessages);
            unitOfWork.attachTransaction(transactionManager);
            processInUnitOfWork(eventMessages, unitOfWork);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new EventProcessingException("Exception occurred while processing events", e);
        }
    }

    /**
     * Shut down this processor. This will deregister the processor with the {@link EventBus}.
     * <p>
     * Upon shutdown of an application, this method will be invoked in the
     * {@link Phase#LOCAL_MESSAGE_HANDLER_REGISTRATIONS} phase.
     */
    @Override
    public void shutDown() {
        if (eventBusRegistration != null) {
            eventBusRegistration.cancel();
        }
        eventBusRegistration = null;
    }

    /**
     * Returns the message source from which this processor receives its events
     *
     * @return the MessageSource from which the processor receives its events
     */
    public SubscribableMessageSource<? extends EventMessage<?>> getMessageSource() {
        return messageSource;
    }

    /**
     * Builder class to instantiate a {@link SubscribingEventProcessor}.
     * <p>
     * The {@link RollbackConfigurationType} defaults to a {@link RollbackConfigurationType#ANY_THROWABLE}, the
     * {@link ErrorHandler} is defaulted to a {@link PropagatingErrorHandler}, the {@link MessageMonitor} defaults to a
     * {@link EventProcessorSpanFactory} is defaulted to a {@link DefaultEventProcessorSpanFactory} backed by a
     * {@link org.axonframework.tracing.NoOpSpanFactory}, the {@link MessageMonitor} defaults to a
     * {@link NoOpMessageMonitor}, the {@link EventProcessingStrategy} defaults to a
     * {@link DirectEventProcessingStrategy} and the {@link TransactionManager} defaults to the
     * {@link NoTransactionManager#INSTANCE}. The Event Processor {@code name}, {@link EventHandlerInvoker} and
     * {@link SubscribableMessageSource} are <b>hard requirements</b> and as such should be provided.
     */
    public static class Builder extends AbstractEventProcessor.Builder {

        private SubscribableMessageSource<? extends EventMessage<?>> messageSource;
        private EventProcessingStrategy processingStrategy = DirectEventProcessingStrategy.INSTANCE;
        private TransactionManager transactionManager = NoTransactionManager.INSTANCE;

        public Builder() {
            super.rollbackConfiguration(RollbackConfigurationType.ANY_THROWABLE);
        }

        @Override
        public Builder name(@Nonnull String name) {
            super.name(name);
            return this;
        }

        @Override
        public Builder eventHandlerInvoker(@Nonnull EventHandlerInvoker eventHandlerInvoker) {
            super.eventHandlerInvoker(eventHandlerInvoker);
            return this;
        }

        /**
         * {@inheritDoc}. Defaults to a {@link RollbackConfigurationType#ANY_THROWABLE})
         */
        @Override
        public Builder rollbackConfiguration(@Nonnull RollbackConfiguration rollbackConfiguration) {
            super.rollbackConfiguration(rollbackConfiguration);
            return this;
        }

        @Override
        public Builder errorHandler(@Nonnull ErrorHandler errorHandler) {
            super.errorHandler(errorHandler);
            return this;
        }

        @Override
        public Builder messageMonitor(@Nonnull MessageMonitor<? super EventMessage<?>> messageMonitor) {
            super.messageMonitor(messageMonitor);
            return this;
        }

        @Override
        public Builder spanFactory(@Nonnull EventProcessorSpanFactory spanFactory) {
            super.spanFactory(spanFactory);
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
        public Builder messageSource(@Nonnull SubscribableMessageSource<? extends EventMessage<?>> messageSource) {
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
        public Builder processingStrategy(@Nonnull EventProcessingStrategy processingStrategy) {
            assertNonNull(processingStrategy, "EventProcessingStrategy may not be null");
            this.processingStrategy = processingStrategy;
            return this;
        }

        /**
         * Sets the {@link TransactionManager} used when processing {@link EventMessage}s.
         *
         * @param transactionManager the {@link TransactionManager} used when processing {@link EventMessage}s
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder transactionManager(@Nonnull TransactionManager transactionManager) {
            assertNonNull(transactionManager, "TransactionManager may not be null");
            this.transactionManager = transactionManager;
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
