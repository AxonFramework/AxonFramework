/*
 * Copyright (c) 2010-2022. Axon Framework
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
import org.axonframework.messaging.DefaultInterceptorChain;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.unitofwork.RollbackConfiguration;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.tracing.NoOpSpanFactory;
import org.axonframework.tracing.Span;
import org.axonframework.tracing.SpanFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertThat;

/**
 * Abstract implementation of an {@link EventProcessor}. Before processing of a batch of messages this implementation
 * creates a Unit of Work to process the batch.
 * <p>
 * Actual handling of events is deferred to an {@link EventHandlerInvoker}. Before each message is handled by the
 * invoker this event processor creates an interceptor chain containing all registered {@link MessageHandlerInterceptor
 * interceptors}.
 * <p>
 * Implementations are in charge of providing the events that need to be processed. Once these events are obtained they
 * can be passed to method {@link #processInUnitOfWork(List, UnitOfWork, Collection)} for processing.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public abstract class AbstractEventProcessor implements EventProcessor {

    private static final List<Segment> ROOT_SEGMENT = Collections.singletonList(Segment.ROOT_SEGMENT);
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final String name;
    private final EventHandlerInvoker eventHandlerInvoker;
    private final RollbackConfiguration rollbackConfiguration;
    private final ErrorHandler errorHandler;
    private final MessageMonitor<? super EventMessage<?>> messageMonitor;
    private final List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptors = new CopyOnWriteArrayList<>();
    protected final SpanFactory spanFactory;

    /**
     * Instantiate a {@link AbstractEventProcessor} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the Event Processor {@code name}, {@link EventHandlerInvoker} and {@link ErrorHandler} are not
     * {@code null}, and will throw an {@link AxonConfigurationException} if any of them is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link AbstractEventProcessor} instance
     */
    protected AbstractEventProcessor(Builder builder) {
        builder.validate();
        this.name = builder.name;
        this.eventHandlerInvoker = builder.eventHandlerInvoker;
        this.rollbackConfiguration = builder.rollbackConfiguration;
        this.errorHandler = builder.errorHandler;
        this.messageMonitor = builder.messageMonitor;
        this.spanFactory = builder.spanFactory;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Registration registerHandlerInterceptor(
            @Nonnull MessageHandlerInterceptor<? super EventMessage<?>> interceptor) {
        interceptors.add(interceptor);
        return () -> interceptors.remove(interceptor);
    }

    @Override
    public List<MessageHandlerInterceptor<? super EventMessage<?>>> getHandlerInterceptors() {
        return Collections.unmodifiableList(interceptors);
    }

    @Override
    public String toString() {
        return getName();
    }

    /**
     * Indicates whether the processor can/should handle the given {@code eventMessage} for the given {@code segment}.
     * <p>
     * This implementation will delegate the decision to the {@link EventHandlerInvoker}.
     *
     * @param eventMessage The message for which to identify if the processor can handle it
     * @param segment      The segment for which the event should be processed
     * @return {@code true} if the event message should be handled, otherwise {@code false}
     * @throws Exception if the {@code errorHandler} throws an Exception back on the
     *                   {@link ErrorHandler#handleError(ErrorContext)} call
     */
    protected boolean canHandle(EventMessage<?> eventMessage, Segment segment) throws Exception {
        try {
            return eventHandlerInvoker.canHandle(eventMessage, segment);
        } catch (Exception e) {
            errorHandler.handleError(new ErrorContext(getName(), e, Collections.singletonList(eventMessage)));
            return false;
        }
    }

    protected boolean canHandleType(Class<?> payloadType)  {
        try {
            return eventHandlerInvoker.canHandleType(payloadType);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Process a batch of events. The messages are processed in a new {@link UnitOfWork}. Before each message is handled
     * the event processor creates an interceptor chain containing all registered {@link MessageHandlerInterceptor
     * interceptors}.
     *
     * @param eventMessages      The batch of messages that is to be processed
     * @param unitOfWork         The Unit of Work that has been prepared to process the messages
     * @throws Exception when an exception occurred during processing of the batch
     */
    protected final void processInUnitOfWork(List<? extends EventMessage<?>> eventMessages,
                                       UnitOfWork<? extends EventMessage<?>> unitOfWork) throws Exception {
        processInUnitOfWork(eventMessages, unitOfWork, ROOT_SEGMENT);
    }

    /**
     * Process a batch of events. The messages are processed in a new {@link UnitOfWork}. Before each message is handled
     * the event processor creates an interceptor chain containing all registered {@link MessageHandlerInterceptor
     * interceptors}.
     *
     * @param eventMessages      The batch of messages that is to be processed
     * @param unitOfWork         The Unit of Work that has been prepared to process the messages
     * @param processingSegments The segments for which the events should be processed in this unit of work
     * @throws Exception when an exception occurred during processing of the batch
     */
    protected void processInUnitOfWork(List<? extends EventMessage<?>> eventMessages,
                                       UnitOfWork<? extends EventMessage<?>> unitOfWork,
                                       Collection<Segment> processingSegments) throws Exception {
        ResultMessage<?> resultMessage = unitOfWork.executeWithResult(() -> {
            MessageMonitor.MonitorCallback monitorCallback =
                    messageMonitor.onMessageIngested(unitOfWork.getMessage());
            return new DefaultInterceptorChain<>(unitOfWork, interceptors, m -> {
                Span span = spanFactory.createInternalSpan(String.format("%s[%s].process",
                                                                         getClass().getSimpleName(),
                                                                         getName()), m);
                span.start();
                try {
                    for (Segment processingSegment : processingSegments) {
                        eventHandlerInvoker.handle(m, processingSegment);
                    }
                    monitorCallback.reportSuccess();
                    return null;
                } catch (Exception exception) {
                    monitorCallback.reportFailure(exception);
                    span.recordException(exception);
                    throw exception;
                } finally {
                    span.end();
                }
            }).proceed();
        }, rollbackConfiguration);

        if (resultMessage.isExceptional()) {
            Throwable e = resultMessage.exceptionResult();
            if (unitOfWork.isRolledBack()) {
                errorHandler.handleError(new ErrorContext(getName(), e, eventMessages));
            } else {
                logger.info("Exception occurred while processing a message, but unit of work was committed. {}",
                            e.getClass().getName());
            }
        }
    }

    /**
     * Returns the invoker assigned to this processor. The invoker is responsible for invoking the correct handler
     * methods for any given message.
     *
     * @return the invoker assigned to this processor
     */
    public EventHandlerInvoker eventHandlerInvoker() {
        return eventHandlerInvoker;
    }

    /**
     * Report the given {@code eventMessage} as ignored. Any registered {@link MessageMonitor} shall be notified of the
     * ignored message.
     * <p>
     * Typically, messages are ignored when they are received by a processor that has no suitable Handler for the type
     * of Event received.
     *
     * @param eventMessage the message that has been ignored.
     */
    protected void reportIgnored(EventMessage<?> eventMessage) {
        messageMonitor.onMessageIngested(eventMessage).reportIgnored();
    }

    /**
     * Abstract Builder class to instantiate a {@link AbstractEventProcessor}.
     * <p>
     * The {@link ErrorHandler} is defaulted to a {@link PropagatingErrorHandler}, the {@link MessageMonitor} defaults
     * to a {@link NoOpMessageMonitor} and the {@link SpanFactory} defaults to a {@link NoOpSpanFactory}. The Event
     * Processor {@code name}, {@link EventHandlerInvoker} and {@link RollbackConfiguration} are <b>hard
     * requirements</b> and as such should be provided.
     */
    public abstract static class Builder {

        protected String name;
        private EventHandlerInvoker eventHandlerInvoker;
        private RollbackConfiguration rollbackConfiguration;
        private ErrorHandler errorHandler = PropagatingErrorHandler.INSTANCE;
        private MessageMonitor<? super EventMessage<?>> messageMonitor = NoOpMessageMonitor.INSTANCE;
        private SpanFactory spanFactory = NoOpSpanFactory.INSTANCE;

        /**
         * Sets the {@code name} of this {@link EventProcessor} implementation.
         *
         * @param name a {@link String} defining this {@link EventProcessor} implementation
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder name(@Nonnull String name) {
            assertEventProcessorName(name, "The EventProcessor name may not be null or empty");
            this.name = name;
            return this;
        }

        /**
         * Sets the {@link EventHandlerInvoker} which will handle all the individual {@link EventMessage}s.
         *
         * @param eventHandlerInvoker the {@link EventHandlerInvoker} which will handle all the individual {@link
         *                            EventMessage}s
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder eventHandlerInvoker(@Nonnull EventHandlerInvoker eventHandlerInvoker) {
            assertNonNull(eventHandlerInvoker, "EventHandlerInvoker may not be null");
            this.eventHandlerInvoker = eventHandlerInvoker;
            return this;
        }

        /**
         * Sets the {@link RollbackConfiguration} specifying the rollback behavior of the {@link UnitOfWork} while
         * processing a batch of events.
         *
         * @param rollbackConfiguration the {@link RollbackConfiguration} specifying the rollback behavior of the {@link
         *                              UnitOfWork} while processing a batch of events.
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder rollbackConfiguration(@Nonnull RollbackConfiguration rollbackConfiguration) {
            assertNonNull(rollbackConfiguration, "RollbackConfiguration may not be null");
            this.rollbackConfiguration = rollbackConfiguration;
            return this;
        }

        /**
         * Sets the {@link ErrorHandler} invoked when an {@link UnitOfWork} is rolled back during processing. Defaults
         * to a {@link PropagatingErrorHandler}.
         *
         * @param errorHandler the {@link ErrorHandler} invoked when an {@link UnitOfWork} is rolled back during
         *                     processing
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder errorHandler(@Nonnull ErrorHandler errorHandler) {
            assertNonNull(errorHandler, "ErrorHandler may not be null");
            this.errorHandler = errorHandler;
            return this;
        }

        /**
         * Sets the {@link MessageMonitor} to monitor {@link EventMessage}s before and after they're processed. Defaults
         * to a {@link NoOpMessageMonitor}.
         *
         * @param messageMonitor a {@link MessageMonitor} to monitor {@link EventMessage}s before and after they're
         *                       processed
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder messageMonitor(@Nonnull MessageMonitor<? super EventMessage<?>> messageMonitor) {
            assertNonNull(messageMonitor, "MessageMonitor may not be null");
            this.messageMonitor = messageMonitor;
            return this;
        }

        /**
         * Sets the {@link SpanFactory} implementation to use for providing tracing capabilities. Defaults to a
         * {@link NoOpSpanFactory} by default, which provides no tracing capabilities.
         *
         * @param spanFactory The {@link SpanFactory} implementation
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder spanFactory(@Nonnull SpanFactory spanFactory) {
            assertNonNull(spanFactory, "SpanFactory may not be null");
            this.spanFactory = spanFactory;
            return this;
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertEventProcessorName(name, "The EventProcessor name is a hard requirement and should be provided");
            assertNonNull(eventHandlerInvoker, "The EventHandlerInvoker is a hard requirement and should be provided");
            assertNonNull(rollbackConfiguration,
                          "The RollbackConfiguration is a hard requirement and should be provided");
        }

        private void assertEventProcessorName(String eventProcessorName, String exceptionMessage) {
            assertThat(eventProcessorName, name -> Objects.nonNull(name) && !"".equals(name), exceptionMessage);
        }
    }
}
