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
import org.axonframework.common.annotation.Internal;
import org.axonframework.eventhandling.interceptors.InterceptingEventHandlingComponent;
import org.axonframework.eventhandling.interceptors.MessageHandlerInterceptors;
import org.axonframework.eventhandling.pipeline.ErrorHandlingEventProcessingPipeline;
import org.axonframework.eventhandling.pipeline.EventProcessingPipeline;
import org.axonframework.eventhandling.pipeline.HandlingEventProcessingPipeline;
import org.axonframework.eventhandling.pipeline.TrackingEventProcessingPipeline;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;

import java.util.List;
import java.util.Objects;

/**
 * Support class containing common {@link EventProcessor} functionality.
 * <p>
 * The {@link EventProcessor} implementations are in charge of providing the events that need to be processed. Once
 * these events are obtained they can be passed to method {@link #process(List, ProcessingContext, Segment)} for
 * processing.
 * <p>
 * Actual handling of events is deferred to an {@link EventHandlingComponent}. Before each message is handled by the
 * component this event processor creates an interceptor chain containing all registered
 * {@link MessageHandlerInterceptor interceptors}.
 *
 * @author Matuesz Nowak
 * @since 5.0.0
 */
@Internal
public final class DefaultEventProcessingPipeline implements EventProcessingPipeline {

    private final String name;
    private final EventHandlingComponent eventHandlingComponent;
    private final ErrorHandler errorHandler;
    private final MessageMonitor<? super EventMessage<?>> messageMonitor;
    private final MessageHandlerInterceptors messageHandlerInterceptors;
    private final EventProcessorSpanFactory spanFactory;
    private final boolean streamingProcessor;
    private final SegmentMatcher segmentMatcher;

    /**
     * Instantiate a {@link DefaultEventProcessingPipeline} directly with the required components.
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
     * @param segmentMatcher         The {@link SegmentMatcher} used to determine if an event should be processed by
     *                               this segment.
     */
    public DefaultEventProcessingPipeline(@Nonnull String name,
                                          @Nonnull EventHandlingComponent eventHandlingComponent,
                                          @Nonnull ErrorHandler errorHandler,
                                          @Nonnull MessageMonitor<? super EventMessage<?>> messageMonitor,
                                          @Nonnull EventProcessorSpanFactory spanFactory,
                                          @Nonnull SegmentMatcher segmentMatcher,
                                          @Nonnull MessageHandlerInterceptors messageHandlerInterceptors,
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
        this.messageHandlerInterceptors = Objects.requireNonNull(messageHandlerInterceptors,
                                                                 "MessageHandlerInterceptors may not be null");
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
        var eventHandlingComponent =
                new TrackingEventHandlingComponent(
                        new MonitoringEventHandlingComponent(
                                new InterceptingEventHandlingComponent(
                                        new SegmentMatchingEventHandlingComponent(
                                                this.eventHandlingComponent, segmentMatcher, () -> segment
                                        ),
                                        messageHandlerInterceptors
                                ),
                                messageMonitor
                        ),
                        (event) -> spanFactory.createProcessEventSpan(streamingProcessor, event)
                );
        var pipeline =
                new ErrorHandlingEventProcessingPipeline(
                        // todo: add pipeline that parallelize processing for events with different sequence identifiers!
                        new TrackingEventProcessingPipeline(
                                new HandlingEventProcessingPipeline(eventHandlingComponent),
                                (eventsList) -> spanFactory.createBatchSpan(streamingProcessor, eventsList)
                        ),
                        name,
                        errorHandler
                );
        return pipeline.process(events, context, segment).cast();
    }
}
