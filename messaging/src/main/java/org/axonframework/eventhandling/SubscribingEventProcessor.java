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

package org.axonframework.eventhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.Registration;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.interceptors.MessageHandlerInterceptors;
import org.axonframework.eventhandling.pipeline.DefaultEventProcessorHandlingComponent;
import org.axonframework.lifecycle.Phase;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;
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
public class SubscribingEventProcessor implements EventProcessor {

    private final String name;
    private final SubscribableMessageSource<? extends EventMessage<?>> messageSource;
    private final UnitOfWorkFactory unitOfWorkFactory;
    private final ProcessorEventHandlingComponents eventHandlingComponents;
    private final EventProcessingStrategy processingStrategy;
    private final ErrorHandler errorHandler;

    private volatile Registration eventBusRegistration;

    public SubscribingEventProcessor(
            @Nonnull String name,
            @Nonnull SubscribableMessageSource<? extends EventMessage<?>> messageSource,
            @Nonnull List<EventHandlingComponent> eventHandlingComponents,
            @Nonnull UnitOfWorkFactory unitOfWorkFactory,
            @Nonnull UnaryOperator<Customization> configurationOverride
    ) {
        this.name = name;
        this.messageSource = messageSource;
        this.eventHandlingComponents = new ProcessorEventHandlingComponents(eventHandlingComponents);
        this.unitOfWorkFactory = unitOfWorkFactory;
        var customization = requireNonNull(configurationOverride, "configurationOverride may not be null")
                .apply(Customization.defaultValues());
        this.processingStrategy = customization.processingStrategy();
        this.errorHandler = customization.errorHandler();
    }

    public record Customization(@Nonnull EventProcessingStrategy processingStrategy,
                                @Nonnull ErrorHandler errorHandler) {

        static Customization defaultValues() {
            return new Customization(
                    DirectEventProcessingStrategy.INSTANCE,
                    PropagatingErrorHandler.INSTANCE
            );
        }

        public Customization processingStrategy(@Nonnull EventProcessingStrategy processingStrategy) {
            return new Customization(processingStrategy, this.errorHandler);
        }

        public Customization errorHandler(@Nonnull ErrorHandler errorHandler) {
            return new Customization(this.processingStrategy,
                                     requireNonNull(errorHandler, "errorHandler may not be null"));
        }
    }

    /**
     * Instantiate a {@code SubscribingEventProcessor} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the Event Processor {@code name}, {@link EventHandlerInvoker} and
     * {@link SubscribableMessageSource} are not {@code null}, and will throw an {@link AxonConfigurationException} if
     * any of them is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@code SubscribingEventProcessor} instance
     */
    protected SubscribingEventProcessor(Builder builder) {
        builder.validate();
        this.name = builder.name;
        this.messageSource = builder.messageSource;
        this.processingStrategy = builder.processingStrategy;
        this.unitOfWorkFactory = builder.unitOfWorkFactory;
        this.errorHandler = builder.errorHandler;
        var messageHandlerInterceptors = new MessageHandlerInterceptors(builder.interceptors());
        var eventHandlingComponent = new DefaultEventProcessorHandlingComponent(
                builder.spanFactory,
                builder.messageMonitor,
                messageHandlerInterceptors,
                builder.eventHandlingComponent(),
                false
        );
        this.eventHandlingComponents = new ProcessorEventHandlingComponents(List.of(eventHandlingComponent));
    }

    /**
     * Instantiate a Builder to be able to create a {@code SubscribingEventProcessor}.
     * <p>
     * {@link ErrorHandler} is defaulted to a {@link PropagatingErrorHandler}, the {@link MessageMonitor} defaults to a
     * {@link NoOpMessageMonitor}, the {@link EventProcessingStrategy} defaults to a
     * {@link DirectEventProcessingStrategy}, the {@link EventProcessorSpanFactory} defaults to a
     * {@link DefaultEventProcessorSpanFactory} backed by a {@link org.axonframework.tracing.NoOpSpanFactory}, and the
     * {@link TransactionManager} defaults to the {@link NoTransactionManager#INSTANCE}. The Event Processor
     * {@code name}, {@link EventHandlerInvoker} and {@link SubscribableMessageSource} are <b>hard requirements</b> and
     * as such should be provided.
     *
     * @return a Builder to be able to create a {@code SubscribingEventProcessor}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String getName() {
        return name;
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
            var unitOfWork = unitOfWorkFactory.create();
            unitOfWork.onInvocation(processingContext -> processWithErrorHandling(eventMessages, processingContext).asCompletableFuture());
            FutureUtils.joinAndUnwrap(unitOfWork.execute());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new EventProcessingException("Exception occurred while processing events", e);
        }
    }

    private MessageStream.Empty<Message<Void>> processWithErrorHandling(List<? extends EventMessage<?>> events, ProcessingContext context) {
        return eventHandlingComponents.handle(events, context)
                                      .onErrorContinue(ex -> {
                                          try {
                                              errorHandler.handleError(new ErrorContext(name, ex, events));
                                          } catch (RuntimeException re) {
                                              return MessageStream.failed(re);
                                          } catch (Exception e) {
                                              return MessageStream.failed(new EventProcessingException(
                                                      "Exception occurred while processing events",
                                                      e));
                                          }
                                          return MessageStream.empty().cast();
                                      })
                                      .ignoreEntries()
                                      .cast();
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
     * {@link ErrorHandler} is defaulted to a {@link PropagatingErrorHandler}, the {@link MessageMonitor} defaults to a
     * {@link EventProcessorSpanFactory} is defaulted to a {@link DefaultEventProcessorSpanFactory} backed by a
     * {@link org.axonframework.tracing.NoOpSpanFactory}, the {@link MessageMonitor} defaults to a
     * {@link NoOpMessageMonitor}, the {@link EventProcessingStrategy} defaults to a
     * {@link DirectEventProcessingStrategy} and the {@link TransactionManager} defaults to the
     * {@link NoTransactionManager#INSTANCE}. The Event Processor {@code name}, {@link EventHandlerInvoker} and
     * {@link SubscribableMessageSource} are <b>hard requirements</b> and as such should be provided.
     */
    public static class Builder extends EventProcessorBuilder {

        private SubscribableMessageSource<? extends EventMessage<?>> messageSource;
        private EventProcessingStrategy processingStrategy = DirectEventProcessingStrategy.INSTANCE;

        public Builder() {
            super();
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

        @Override
        public Builder eventHandlingComponent(@Nonnull EventHandlingComponent eventHandlingComponent) {
            super.eventHandlingComponent(eventHandlingComponent);
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

        @Override
        public Builder unitOfWorkFactory(@Nonnull UnitOfWorkFactory unitOfWorkFactory) {
            super.unitOfWorkFactory(unitOfWorkFactory);
            return this;
        }

        @Override
        public Builder interceptors(
                @Nonnull List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptors) {
            super.interceptors(interceptors);
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
