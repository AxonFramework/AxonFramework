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
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.DefaultInterceptorChain;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.tracing.NoOpSpanFactory;
import org.axonframework.tracing.SpanFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertThat;

/**
 * Support class containing common {@link EventProcessor} functionality.
 * <p>
 * The {@link EventProcessor} implementations are in charge of providing the events that need to be processed. Once these events are obtained they
 * can be passed to method {@link #processInUnitOfWork(List, UnitOfWork, Collection)} for processing.
 * <p>
 * Actual handling of events is deferred to an {@link EventHandlerInvoker}. Before each message is handled by the
 * invoker this event processor creates an interceptor chain containing all registered
 * {@link MessageHandlerInterceptor interceptors}.
 *
 * @author Rene de Waele
 * @since 3.0
 */
@Internal
public final class EventProcessorOperations {

    private static final List<Segment> ROOT_SEGMENT = Collections.singletonList(Segment.ROOT_SEGMENT);

    private final String name;
    private final EventHandlingComponent eventHandlingComponent;
    private final ErrorHandler errorHandler;
    private final MessageMonitor<? super EventMessage<?>> messageMonitor;
    private final List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptors = new CopyOnWriteArrayList<>();
    private final EventProcessorSpanFactory spanFactory;
    private final boolean streamingProcessor;
    private final SegmentMatcher segmentMatcher;

    /**
     * Instantiate a {@link EventProcessorOperations} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the Event Processor {@code name}, {@link EventHandlerInvoker} and {@link ErrorHandler} are not
     * {@code null}, and will throw an {@link AxonConfigurationException} if any of them is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link EventProcessorOperations} instance
     */
    public EventProcessorOperations(Builder builder) {
        builder.validate();
        this.name = builder.name;
        this.eventHandlingComponent = builder.eventHandlingComponent;
        this.errorHandler = builder.errorHandler;
        this.messageMonitor = builder.messageMonitor;
        this.spanFactory = builder.spanFactory;
        this.streamingProcessor = builder.streamingProcessor;
        this.segmentMatcher = new SegmentMatcher(e -> Optional.of(eventHandlingComponent.sequenceIdentifierFor(e)));
    }

    /**
     * Returns the name of the event processor. This name is used to detect distributed instances of the
     * same event processor. Multiple instances referring to the same logical event processor (on different JVM's)
     * must have the same name.
     *
     * @return the name of this event processor
     */
    public String name() {
        return name;
    }

    public Registration registerHandlerInterceptor(
            @Nonnull MessageHandlerInterceptor<? super EventMessage<?>> interceptor) {
        interceptors.add(interceptor);
        return () -> interceptors.remove(interceptor);
    }

    /**
     * Return the list of already registered {@link MessageHandlerInterceptor}s for the event processor.
     * To register a new interceptor use {@link EventProcessor#registerHandlerInterceptor(MessageHandlerInterceptor)}
     *
     * @return The list of registered interceptors of the event processor.
     */
    public List<MessageHandlerInterceptor<? super EventMessage<?>>> handlerInterceptors() {
        return Collections.unmodifiableList(interceptors);
    }

    public String toString() {
        return name();
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
    public boolean canHandle(EventMessage<?> eventMessage, @Nonnull ProcessingContext context, Segment segment)
            throws Exception {
        try {
            var eventMessageQualifiedName = eventMessage.type().qualifiedName();
            var eventSupported = eventHandlingComponent.supports(eventMessageQualifiedName);
            return eventSupported && segmentMatcher.matches(segment, eventMessage);
        } catch (Exception e) {
            errorHandler.handleError(new ErrorContext(name(), e, Collections.singletonList(eventMessage)));
            return false;
        }
    }

    public boolean canHandleType(MessageType messageType) {
        try {
            var eventMessageQualifiedName = messageType.qualifiedName();
            return eventHandlingComponent.supports(eventMessageQualifiedName);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Process a batch of events. The messages are processed in a new {@link UnitOfWork}. Before each message is handled
     * the event processor creates an interceptor chain containing all registered
     * {@link MessageHandlerInterceptor interceptors}.
     *
     * @param eventMessages The batch of messages that is to be processed
     * @param unitOfWork    The Unit of Work that has been prepared to process the messages
     * @throws Exception when an exception occurred during processing of the batch
     */
    public void processInUnitOfWork(List<? extends EventMessage<?>> eventMessages,
                                             UnitOfWork unitOfWork) throws Exception {
        processInUnitOfWork(eventMessages, unitOfWork, ROOT_SEGMENT).join();
    }

    /**
     * Process a batch of events. The messages are processed in a new {@link UnitOfWork}. Before each message is handled
     * the event processor creates an interceptor chain containing all registered
     * {@link MessageHandlerInterceptor interceptors}.
     *
     * @param eventMessages      The batch of messages that is to be processed
     * @param unitOfWork         The Unit of Work that has been prepared to process the messages
     * @param processingSegments The segments for which the events should be processed in this unit of work
     * @throws Exception when an exception occurred during processing of the batch
     */
    public CompletableFuture<Void> processInUnitOfWork(List<? extends EventMessage<?>> eventMessages,
                                                       UnitOfWork unitOfWork,
                                                       Collection<Segment> processingSegments) throws Exception {
        unitOfWork.onInvocation(processingContext -> {
            CompletableFuture<Void> result = CompletableFuture.completedFuture(null);

            for (EventMessage<?> message : eventMessages) {
                result = result.thenCompose(v -> spanFactory
                        .createProcessEventSpan(streamingProcessor, message)
                        .runSupplierAsync(() -> processMessage(processingSegments, processingContext, message))
                );
            }

            return result;
        });

        return spanFactory.createBatchSpan(streamingProcessor, eventMessages)
                          .runSupplierAsync(() -> unitOfWork.execute().exceptionally(e -> {
                              try {
                                  var cause = e instanceof CompletionException ? e.getCause() : e;
                                  errorHandler.handleError(new ErrorContext(name(), cause, eventMessages));
                              } catch (RuntimeException ex) {
                                  throw ex;
                              } catch (Exception ex) {
                                  throw new EventProcessingException("Exception occurred while processing events", ex);
                              }
                              return null;
                          }));
    }

    private MessageStream.Empty<?> processMessageInUnitOfWork(Collection<Segment> processingSegments,
                                                              EventMessage<?> message,
                                                              ProcessingContext processingContext,
                                                              MessageMonitor.MonitorCallback monitorCallback
    ) throws Exception {
        try {
            for (Segment processingSegment : processingSegments) {
                if (segmentMatcher.matches(processingSegment, message)) {
                    FutureUtils.joinAndUnwrap(
                            eventHandlingComponent.handle(message, processingContext).asCompletableFuture()
                    );
                }
            }
            monitorCallback.reportSuccess();
            return MessageStream.empty();
        } catch (Exception exception) {
            monitorCallback.reportFailure(exception);
            throw exception;
        }
    }

    private CompletableFuture<Void> processMessage(Collection<Segment> processingSegments,
                                                   ProcessingContext processingContext,
                                                   EventMessage<?> message
    ) {
        try {
            var monitorCallback = messageMonitor.onMessageIngested(message);

            DefaultInterceptorChain<EventMessage<?>, ?> chain =
                    new DefaultInterceptorChain<>(
                            null,
                            interceptors,
                            (msg, ctx) -> processMessageInUnitOfWork(processingSegments,
                                                                     msg,
                                                                     ctx,
                                                                     monitorCallback));
            return chain.proceed(message, processingContext)
                        .ignoreEntries()
                        .asCompletableFuture()
                        .thenApply(e -> null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
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
    public void reportIgnored(EventMessage<?> eventMessage) {
        messageMonitor.onMessageIngested(eventMessage).reportIgnored();
    }

    /**
     * Builder class to instantiate a {@link EventProcessorOperations}.
     * <p>
     * The {@link ErrorHandler} is defaulted to a {@link PropagatingErrorHandler}, the {@link MessageMonitor} defaults
     * to a {@link NoOpMessageMonitor} and the {@link EventProcessorSpanFactory} defaults to
     * {@link DefaultEventProcessorSpanFactory} backed by a {@link NoOpSpanFactory}. The Event Processor {@code name}
     * and {@link EventHandlerInvoker} are <b>hard requirements</b> and as such should be provided.
     */
    public final static class Builder {

        private String name;
        private EventHandlingComponent eventHandlingComponent;
        private ErrorHandler errorHandler = PropagatingErrorHandler.INSTANCE;
        private MessageMonitor<? super EventMessage<?>> messageMonitor = NoOpMessageMonitor.INSTANCE;
        private EventProcessorSpanFactory spanFactory = DefaultEventProcessorSpanFactory.builder()
                                                                                        .spanFactory(NoOpSpanFactory.INSTANCE)
                                                                                        .build();
        private boolean streamingProcessor = false;

        /**
         * Sets the {@code name} of the {@link EventProcessor} implementation.
         *
         * @param name a {@link String} defining the {@link EventProcessor} implementation
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
         * @param eventHandlerInvoker the {@link EventHandlerInvoker} which will handle all the individual
         *                            {@link EventMessage}s
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder eventHandlerInvoker(@Nonnull EventHandlerInvoker eventHandlerInvoker) {
            assertNonNull(eventHandlerInvoker, "EventHandlerInvoker may not be null");
            return this;
        }

        /**
         * Sets the {@link EventHandlerInvoker} which will handle all the individual {@link EventMessage}s.
         *
         * @param eventHandlingComponent the {@link EventHandlingComponent} which will handle all the individual
         *                            {@link EventMessage}s
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder eventHandlingComponent(@Nonnull EventHandlingComponent eventHandlingComponent) {
            assertNonNull(eventHandlingComponent, "EventHandlingComponent may not be null");
            this.eventHandlingComponent = eventHandlingComponent;
            return this;
        }

        /**
         * Sets the {@link ErrorHandler} invoked when an {@link UnitOfWork} throws an exception during processing.
         * Defaults to a {@link PropagatingErrorHandler}.
         *
         * @param errorHandler the {@link ErrorHandler} invoked when an {@link UnitOfWork} throws an exception during
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
         * Sets the {@link EventProcessorSpanFactory} implementation to use for providing tracing capabilities. Defaults
         * to a {@link DefaultEventProcessorSpanFactory} backed by a {@link NoOpSpanFactory} by default, which provides
         * no tracing capabilities.
         *
         * @param spanFactory The {@link SpanFactory} implementation
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder spanFactory(@Nonnull EventProcessorSpanFactory spanFactory) {
            assertNonNull(spanFactory, "SpanFactory may not be null");
            this.spanFactory = spanFactory;
            return this;
        }

        /**
         * Sets whether the {@link EventProcessor} is a streaming processor.
         * @param streamingProcessor - Weather the {@link EventProcessor} is a streaming processor.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder streamingProcessor(boolean streamingProcessor) {
            this.streamingProcessor = streamingProcessor;
            return this;
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        private void validate() throws AxonConfigurationException {
            assertEventProcessorName(name, "The EventProcessor name is a hard requirement and should be provided");
            assertNonNull(eventHandlingComponent, "The EventHandlingComponent is a hard requirement and should be provided");
        }

        private void assertEventProcessorName(String eventProcessorName, String exceptionMessage) {
            assertThat(eventProcessorName, name -> Objects.nonNull(name) && !"".equals(name), exceptionMessage);
        }

        /**
         * Initializes a {@link EventProcessorOperations} as specified through this Builder.
         *
         * @return a {@link EventProcessorOperations} as specified through this Builder
         */
        public EventProcessorOperations build() {
            return new EventProcessorOperations(this);
        }
    }
}
