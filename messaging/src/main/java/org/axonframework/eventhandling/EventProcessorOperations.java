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
import org.axonframework.common.annotation.Internal;
import org.axonframework.eventhandling.pipeline.EventProcessingPipeline;
import org.axonframework.messaging.DefaultInterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.tracing.Span;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Support class containing common {@link EventProcessor} functionality.
 * <p>
 * The {@link EventProcessor} implementations are in charge of providing the events that need to be processed. Once
 * these events are obtained they can be passed to method {@link #process(List, ProcessingContext, Segment)} for
 * processing.
 * <p>
 * Actual handling of events is deferred to an {@link EventHandlerInvoker}. Before each message is handled by the
 * invoker this event processor creates an interceptor chain containing all registered
 * {@link MessageHandlerInterceptor interceptors}.
 *
 * @author Rene de Waele
 * @since 3.0
 */
@Internal
public final class EventProcessorOperations implements EventProcessingPipeline {

    private final String name;
    private final EventHandlingComponent eventHandlingComponent;
    private final ErrorHandler errorHandler;
    private final MessageMonitor<? super EventMessage<?>> messageMonitor;
    private final List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptors = new CopyOnWriteArrayList<>();
    private final EventProcessorSpanFactory spanFactory;
    private final boolean streamingProcessor;
    private final SegmentMatcher segmentMatcher;

    /**
     * Instantiate a {@link EventProcessorOperations} directly with the required components.
     * <p>
     * Will assert that the Event Processor {@code name}, {@link EventHandlingComponent}, {@link ErrorHandler},
     * {@link MessageMonitor} and {@link EventProcessorSpanFactory} are not {@code null}, and will throw an
     * {@link AxonConfigurationException} if any of them is {@code null}.
     *
     * @param name                   The {@link String} defining the name of the event processor.
     * @param eventHandlingComponent The {@link EventHandlingComponent} which will handle all the individual
     *                               {@link EventMessage}s.
     * @param errorHandler           The {@link ErrorHandler} invoked when an {@link UnitOfWork} throws an exception
     *                               during processing.
     * @param messageMonitor         The {@link MessageMonitor} to monitor {@link EventMessage}s before and after
     *                               they're processed.
     * @param spanFactory            The {@link EventProcessorSpanFactory} implementation to use for providing tracing
     *                               capabilities.
     * @param streamingProcessor     The boolean indicating whether this processor which uses the operations is a
     *                               streaming processor.
     * @param segmentMatcher         The {@link SegmentMatcher} used to determine if an event should be processed by this segment.
     */
    public EventProcessorOperations(@Nonnull String name,
                                    @Nonnull EventHandlingComponent eventHandlingComponent,
                                    @Nonnull ErrorHandler errorHandler,
                                    @Nonnull MessageMonitor<? super EventMessage<?>> messageMonitor,
                                    @Nonnull EventProcessorSpanFactory spanFactory,
                                    @Nonnull SegmentMatcher segmentMatcher,
                                    boolean streamingProcessor
    ) {
        this.name = Objects.requireNonNull(name,
                                           "The EventProcessor name is a hard requirement and should be provided");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("The EventProcessor name is a hard requirement and should be provided");
        }
        this.eventHandlingComponent = Objects.requireNonNull(eventHandlingComponent,
                                                             "EventHandlingComponent may not be null");
        this.errorHandler = Objects.requireNonNull(errorHandler, "ErrorHandler may not be null");
        this.messageMonitor = Objects.requireNonNull(messageMonitor, "MessageMonitor may not be null");
        this.spanFactory = Objects.requireNonNull(spanFactory, "SpanFactory may not be null");
        this.streamingProcessor = streamingProcessor;
        this.segmentMatcher = Objects.requireNonNull(segmentMatcher, "SegmentMatcher may not be null");
    }

    /**
     * Returns the name of the event processor. This name is used to detect distributed instances of the same event
     * processor. Multiple instances referring to the same logical event processor (on different JVM's) must have the
     * same name.
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
     * Return the list of already registered {@link MessageHandlerInterceptor}s for the event processor. To register a
     * new interceptor use {@link EventProcessor#registerHandlerInterceptor(MessageHandlerInterceptor)}
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
     * Process a batch of events. The messages are processed in a new {@link UnitOfWork}. Before each message is handled
     * the event processor creates an interceptor chain containing all registered
     * {@link MessageHandlerInterceptor interceptors}.
     *
     * @param eventMessages     The batch of messages that is to be processed
     * @param processingContext The Processing Context that has been prepared to process the messages
     */
    public MessageStream.Empty<Message<Void>> process(List<? extends EventMessage<?>> eventMessages,
                                                      ProcessingContext processingContext) {
        return process(eventMessages, processingContext, Segment.ROOT_SEGMENT);
    }

    /**
     * Process a batch of events. The messages are processed in a new {@link UnitOfWork}. Before each message is handled
     * the event processor creates an interceptor chain containing all registered
     * {@link MessageHandlerInterceptor interceptors}.
     *
     * @param events  The batch of messages that is to be processed
     * @param context The Processing Context that has been prepared to process the messages
     * @param segment The segment for which the events should be processed in this processing context
     */
    @Override
    public MessageStream.Empty<Message<Void>> process(List<? extends EventMessage<?>> events,
                                                      ProcessingContext context,
                                                      Segment segment) {
        return trackBatchProcessing(events, () -> processEach(events, context, segment))
                .onErrorContinue(ex -> {
                    try {
                        errorHandler.handleError(new ErrorContext(name(), ex, events));
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

    @Nonnull
    private MessageStream<Message<?>> processEach(List<? extends EventMessage<?>> events, ProcessingContext ctx,
                                                  Segment segment) {
        return events.stream()
                     .map(event -> processEvent(event, ctx, segment))
                     .reduce(MessageStream.empty().cast(), MessageStream::concatWith);
    }

    private MessageStream<Message<?>> processEvent(
            EventMessage<?> event,
            ProcessingContext context,
            Segment segment
    ) {
        return trackEventProcessing(event, () -> {
            try {
                var monitorCallback = messageMonitor.onMessageIngested(event);

                DefaultInterceptorChain<EventMessage<?>, ?> chain =
                        new DefaultInterceptorChain<>(
                                null,
                                interceptors,
                                (msg, ctx) -> processIfSegmentMatches(msg, ctx, segment)
                        );
                return chain.proceed(event, context)
                            .whenComplete(monitorCallback::reportSuccess)
                            .onErrorContinue(ex -> {
                                monitorCallback.reportFailure(ex);
                                return MessageStream.failed(ex);
                            }).cast();
            } catch (Exception e) {
                return MessageStream.failed(e);
            }
        });
    }

    private MessageStream<?> processIfSegmentMatches(EventMessage<?> message,
                                                     ProcessingContext processingContext,
                                                     Segment processingSegment) {
        return segmentMatcher.matches(processingSegment, message)
                ? eventHandlingComponent.handle(message, processingContext)
                : MessageStream.empty();
    }

    // todo: I'm not sure about that, it had some thread local used inside runSupplierAsync
    private MessageStream<Message<?>> trackBatchProcessing(List<? extends EventMessage<?>> eventMessages,
                                                           Supplier<MessageStream<Message<?>>> messageStreamSupplier) {
        var span = spanFactory.createBatchSpan(streamingProcessor, eventMessages);
        return trackMessageStream(span, messageStreamSupplier);
    }

    private MessageStream<Message<?>> trackMessageStream(Span span,
                                                         Supplier<MessageStream<Message<?>>> messageStreamSupplier) {
        span.start();
        var messageStream = messageStreamSupplier.get();
        return messageStream
                .whenComplete(span::end)
                .onErrorContinue(ex -> {
                    span.recordException(ex);
                    span.end();
                    return MessageStream.failed(ex);
                });
    }

    private MessageStream<Message<?>> trackEventProcessing(EventMessage<?> event,
                                                           Supplier<MessageStream<Message<?>>> messageStreamSupplier) {
        var span = spanFactory.createProcessEventSpan(streamingProcessor, event);
        return trackMessageStream(span, messageStreamSupplier);
    }
}
