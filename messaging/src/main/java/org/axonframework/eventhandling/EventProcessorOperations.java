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
import org.axonframework.messaging.Context;
import org.axonframework.messaging.DefaultInterceptorChain;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.ProcessingLifecycle;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.tracing.Span;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;

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
public final class EventProcessorOperations {

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
     * @param streamingProcessor     The boolean indicating whether this processor which uses the operations is a streaming processor.
     */
    public EventProcessorOperations(@Nonnull String name,
                                    @Nonnull EventHandlingComponent eventHandlingComponent,
                                    @Nonnull ErrorHandler errorHandler,
                                    @Nonnull MessageMonitor<? super EventMessage<?>> messageMonitor,
                                    @Nonnull EventProcessorSpanFactory spanFactory,
                                    boolean streamingProcessor
    ) {
        this.name = Objects.requireNonNull(name, "The EventProcessor name is a hard requirement and should be provided");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("The EventProcessor name is a hard requirement and should be provided");
        }
        this.eventHandlingComponent = Objects.requireNonNull(eventHandlingComponent, "EventHandlingComponent may not be null");
        this.errorHandler = Objects.requireNonNull(errorHandler, "ErrorHandler may not be null");
        this.messageMonitor = Objects.requireNonNull(messageMonitor, "MessageMonitor may not be null");
        this.spanFactory = Objects.requireNonNull(spanFactory, "SpanFactory may not be null");
        this.streamingProcessor = streamingProcessor;
        this.segmentMatcher = new SegmentMatcher(e -> Optional.of(eventHandlingComponent.sequenceIdentifierFor(e)));
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

    /**
     * Process a batch of events. The messages are processed in a new {@link UnitOfWork}. Before each message is handled
     * the event processor creates an interceptor chain containing all registered
     * {@link MessageHandlerInterceptor interceptors}.
     *
     * @param eventMessages     The batch of messages that is to be processed
     * @param processingContext The Processing Context that has been prepared to process the messages
     */
    public CompletableFuture<Void> process(List<? extends EventMessage<?>> eventMessages,
                                           ProcessingContext processingContext) {
        return process(eventMessages, processingContext, Segment.ROOT_SEGMENT);
    }

    /**
     * Process a batch of events. The messages are processed in a new {@link UnitOfWork}. Before each message is handled
     * the event processor creates an interceptor chain containing all registered
     * {@link MessageHandlerInterceptor interceptors}.
     *
     * @param eventMessages     The batch of messages that is to be processed
     * @param processingContext The Processing Context that has been prepared to process the messages
     * @param processingSegment The segment for which the events should be processed in this processing context
     */
    public CompletableFuture<Void> process(List<? extends EventMessage<?>> eventMessages,
                                           ProcessingContext processingContext,
                                           Segment processingSegment) {
        trackBatchProcessing(processingContext, eventMessages);
        processingContext.onInvocation(ctx -> {
            CompletableFuture<Void> result = CompletableFuture.completedFuture(null);

            for (EventMessage<?> message : eventMessages) {
                result = result.thenCompose(v -> spanFactory
                        .createProcessEventSpan(streamingProcessor, message)
                        .runSupplierAsync(() -> processMessage(message, ctx, processingSegment))
                );
            }

            return result;
        });

        processingContext.onError((ctx, phase, exception) -> {
            try {
                var cause = exception instanceof CompletionException ? exception.getCause() : exception;
                errorHandler.handleError(new ErrorContext(name(), cause, eventMessages));
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new EventProcessingException("Exception occurred while processing events", ex);
            }
        });

        return FutureUtils.emptyCompletedFuture();
    }

    private CompletableFuture<Void> processMessage(
            EventMessage<?> message,
            ProcessingContext processingContext,
            Segment processingSegment
    ) {
        try {
            var monitorCallback = messageMonitor.onMessageIngested(message);

            DefaultInterceptorChain<EventMessage<?>, ?> chain =
                    new DefaultInterceptorChain<>(
                            null,
                            interceptors,
                            (msg, ctx) -> processIfSegmentMatches(msg, ctx, processingSegment)
                    );
            return chain.proceed(message, processingContext)
                        .ignoreEntries()
                        .asCompletableFuture()
                        .whenComplete((__, ex) -> {
                            if (ex == null) {
                                monitorCallback.reportSuccess();
                            } else {
                                monitorCallback.reportFailure(ex);
                            }
                        }).thenApply(__ -> null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private MessageStream.Empty<?> processIfSegmentMatches(EventMessage<?> message,
                                                           ProcessingContext processingContext,
                                                           Segment processingSegment
    ) {
        return segmentMatcher.matches(processingSegment, message)
                ? eventHandlingComponent.handle(message, processingContext)
                : MessageStream.empty();
    }

    private void trackBatchProcessing(
            ProcessingLifecycle processingLifecycle,
            List<? extends EventMessage<?>> eventMessages
    ) {
        Context.ResourceKey<Span> batchSpanKey = Context.ResourceKey.withLabel("batchSpan");
        processingLifecycle.runOnInvocation(processingContext -> {
            Span batchSpan = spanFactory.createBatchSpan(streamingProcessor, eventMessages);
            batchSpan.start();
            processingContext.putResource(batchSpanKey, batchSpan);
        });
        processingLifecycle.onError((processingContext, phase, error) -> {
            Span batchSpan = processingContext.getResource(batchSpanKey);
            if (batchSpan != null) {
                batchSpan.recordException(error);
            }
        });
        processingLifecycle.doFinally(processingContext -> {
            Span batchSpan = processingContext.getResource(batchSpanKey);
            if (batchSpan != null) {
                batchSpan.end();
            }
        });
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
}
