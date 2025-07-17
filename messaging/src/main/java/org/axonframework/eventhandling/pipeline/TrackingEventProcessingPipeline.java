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

package org.axonframework.eventhandling.pipeline;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.Segment;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.tracing.Span;
import org.axonframework.tracing.SpanScope;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * An {@link EventProcessingPipeline} that tracks the processing of the event batch using a {@link Span} provider.
 * <p>
 * It delegates the actual event processing to another {@link EventProcessingPipeline} while tracking the event batch.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class TrackingEventProcessingPipeline implements EventProcessingPipeline {

    private final EventProcessingPipeline next;
    private final Function<List<? extends EventMessage<?>>, Span> spanProvider;

    /**
     * Constructs the {@link TrackingEventProcessingPipeline} with given {@code next} pipeline to delegate calls to and
     * a {@code spanProvider} to create spans for the event batch.
     *
     * @param next         The instance to delegate calls to.
     * @param spanProvider The provider of {@link Span} to track the event batch.
     */
    public TrackingEventProcessingPipeline(EventProcessingPipeline next,
                                           Function<List<? extends EventMessage<?>>, Span> spanProvider) {
        this.next = Objects.requireNonNull(next, "Next may not be null");
        this.spanProvider = Objects.requireNonNull(spanProvider, "Span provider may not be null");
    }

    @Override
    public MessageStream.Empty<Message<Void>> process(
            List<? extends EventMessage<?>> events,
            ProcessingContext context,
            Segment segment
    ) {
        Span span = spanProvider.apply(events);
        span.start();
        try (SpanScope ignored = span.makeCurrent()) {
            return next.process(events, context, segment)
                       .whenComplete(span::end)
                       .onErrorContinue(ex -> {
                           span.recordException(ex);
                           span.end();
                           return MessageStream.failed(ex);
                       }).ignoreEntries().cast();
        } catch (Exception e) {
            span.recordException(e);
            throw e;
        }
    }
}
