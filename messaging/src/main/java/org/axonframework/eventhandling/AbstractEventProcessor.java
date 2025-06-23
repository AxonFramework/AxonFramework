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
import org.axonframework.common.Registration;
import org.axonframework.messaging.DefaultInterceptorChain;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Abstract implementation of an {@link EventProcessor}. Before processing of a batch of messages this implementation
 * creates a Unit of Work to process the batch.
 * <p>
 * Actual handling of events is deferred to an {@link EventHandlerInvoker}. Before each message is handled by the
 * invoker this event processor creates an interceptor chain containing all registered
 * {@link MessageHandlerInterceptor interceptors}.
 * <p>
 * Implementations are in charge of providing the events that need to be processed. Once these events are obtained they
 * can be passed to method {@link #processInUnitOfWork(List, UnitOfWork, Collection)} for processing.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public abstract class AbstractEventProcessor implements EventProcessor {

    private static final List<Segment> ROOT_SEGMENT = Collections.singletonList(Segment.ROOT_SEGMENT);

    private final String name;
    private final EventHandlerInvoker eventHandlerInvoker;
    private final ErrorHandler errorHandler;
    private final MessageMonitor<? super EventMessage<?>> messageMonitor;
    private final List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptors = new CopyOnWriteArrayList<>();
    protected final EventProcessorSpanFactory spanFactory;

    /**
     * Instantiate a {@link AbstractEventProcessor} based on the fields contained in the {@link EventProcessorBuilder}.
     * <p>
     * Will assert that the Event Processor {@code name}, {@link EventHandlerInvoker} and {@link ErrorHandler} are not
     * {@code null}, and will throw an {@link AxonConfigurationException} if any of them is {@code null}.
     *
     * @param builder the {@link EventProcessorBuilder} used to instantiate a {@link AbstractEventProcessor} instance
     */
    protected AbstractEventProcessor(EventProcessorBuilder builder) {
        builder.validate();
        this.name = builder.name;
        this.eventHandlerInvoker = builder.eventHandlerInvoker;
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
    protected boolean canHandle(EventMessage<?> eventMessage, @Nonnull ProcessingContext context, Segment segment)
            throws Exception {
        try {
            return eventHandlerInvoker.canHandle(eventMessage, context, segment);
        } catch (Exception e) {
            errorHandler.handleError(new ErrorContext(getName(), e, Collections.singletonList(eventMessage)));
            return false;
        }
    }

    protected boolean canHandleType(Class<?> payloadType) {
        try {
            return eventHandlerInvoker.canHandleType(payloadType);
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
    protected final void processInUnitOfWork(List<? extends EventMessage<?>> eventMessages,
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
    protected CompletableFuture<Void> processInUnitOfWork(List<? extends EventMessage<?>> eventMessages,
                                                          UnitOfWork unitOfWork,
                                                          Collection<Segment> processingSegments) throws Exception {
        unitOfWork.onInvocation(processingContext -> {
            CompletableFuture<Void> result = CompletableFuture.completedFuture(null);

            for (EventMessage<?> message : eventMessages) {
                result = result.thenCompose(v -> spanFactory
                        .createProcessEventSpan(this instanceof StreamingEventProcessor, message)
                        .runSupplierAsync(() -> processMessage(processingSegments, processingContext, message))
                );
            }

            return result;
        });

        return spanFactory.createBatchSpan(this instanceof StreamingEventProcessor, eventMessages)
                          .runSupplierAsync(() -> unitOfWork.execute().exceptionally(e -> {
                              try {
                                  var cause = e instanceof CompletionException ? e.getCause() : e;
                                  errorHandler.handleError(new ErrorContext(getName(), cause, eventMessages));
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
                eventHandlerInvoker.handle(message, processingContext, processingSegment);
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
}
