/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventstreaming;

import org.axonframework.common.Assert;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.core.DelayedMessageStream;
import org.axonframework.messaging.core.MergedMessageStream;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * A {@link StreamableEventSource} implementation that allows streaming event processors to process events from multiple
 * underlying event sources. This is useful when events need to be consumed from:
 * <ul>
 *     <li>Multiple event stores</li>
 *     <li>Multiple Axon Server contexts</li>
 *     <li>Different storage types (for example, an Event Store and a Kafka topic)</li>
 * </ul>
 * <p>
 * Events from the different sources are merged using a {@link MergedMessageStream}, with the order of event consumption
 * determined by a configurable {@link Comparator}. The default comparator returns the oldest event available (based on
 * the event's timestamp). Each source is tracked independently using a {@link MultiSourceTrackingToken}, which maintains
 * the position for each individual source.
 *
 * @author Allard Buijze, Greg Woods
 * @since 5.1.0
 */
public class MultiStreamableEventSource implements StreamableEventSource {

    private final Map<String, StreamableEventSource> eventSources;
    private final Comparator<MessageStream.Entry<EventMessage>> eventComparator;
    /**
     * Resource key used to identify which source an event originated from. This key can be accessed in the
     * {@code eventComparator} to determine the source of an event and apply custom ordering logic, such as giving
     * precedence to events from a specific source.
     */
    public final Context.ResourceKey<String> SOURCE_ID_RESOURCE = Context.ResourceKey.withLabel("SourceId");

    /**
     * Instantiate a MultiStreamableEventSource based on the fields contained in the {@link Builder}.
     *
     * @param builder The {@link Builder} used to instantiate a {@code MultiStreamableEventSource} instance.
     */
    protected MultiStreamableEventSource(Builder builder) {
        this.eventSources = builder.eventSourceMap;
        this.eventComparator = builder.eventComparator;
    }

    /**
     * Instantiate a Builder to be able to create a {@code MultiStreamableEventSource}. The configurable field
     * {@code eventComparator}, which decides which message to process first if there is a choice, defaults to the
     * oldest message available (using the event's timestamp).
     *
     * @return A Builder to be able to create a {@code MultiStreamableEventSource}.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public MessageStream<EventMessage> open(@NonNull StreamingCondition condition,
                                            @Nullable ProcessingContext context) {
        TrackingToken trackingToken = condition.position();
        if (trackingToken == null) {
            // Start from the beginning
            return DelayedMessageStream.create(createToken(m -> m.firstToken(context))
                                                       .thenApply(s -> open(s, condition, context)));
        } else if (trackingToken instanceof MultiSourceTrackingToken multiSourceToken) {
            return open(multiSourceToken, condition, context);
        }
        throw new IllegalArgumentException(
                "Incompatible token type provided. Expected MultiSourceTrackingToken but got: "
                        + trackingToken.getClass().getName());
    }

    private MessageStream<EventMessage> open(MultiSourceTrackingToken token,
                                             StreamingCondition condition,
                                             ProcessingContext context) {
        MessageStream<EventMessage> result = null;
        try {
            for (Map.Entry<String, StreamableEventSource> entry : eventSources.entrySet()) {
                TrackingToken sourceToken = token.getTokenForStream(entry.getKey());
                StreamingCondition sourceCondition = StreamingCondition.conditionFor(sourceToken, condition.criteria());
                MessageStream<EventMessage> stream = entry.getValue()
                                                          .open(sourceCondition, context)
                                                          .map(e -> e.withResource(SOURCE_ID_RESOURCE, entry.getKey()));

                if (result == null) {
                    result = stream;
                } else {
                    result = new MergedMessageStream<>(eventComparator, result, stream);
                }
            }
            return new MultiStreamEventStream(token, result);
        } catch (Exception e) {
            // Close all opened streams on failure
            if (result != null) {
                result.close();
            }
            throw e;
        }
    }

    @Override
    public CompletableFuture<TrackingToken> firstToken(@Nullable ProcessingContext context) {
        return createToken(s -> s.firstToken(context)).thenApply(t -> t);
    }

    @Override
    public CompletableFuture<TrackingToken> latestToken(@Nullable ProcessingContext context) {
        return createToken(s -> s.latestToken(context)).thenApply(t -> t);
    }

    @Override
    public CompletableFuture<TrackingToken> tokenAt(@NonNull Instant at, @Nullable ProcessingContext context) {
        return createToken(s -> s.tokenAt(at, context)).thenApply(t -> t);
    }

    private CompletableFuture<MultiSourceTrackingToken> createToken(
            Function<StreamableEventSource, CompletableFuture<TrackingToken>> tokenProvider) {
        CompletableFuture<MultiSourceTrackingToken> combinedToken = CompletableFuture.completedFuture(new MultiSourceTrackingToken(
                Collections.emptyMap()));
        for (Map.Entry<String, StreamableEventSource> entry : eventSources.entrySet()) {
            combinedToken = combinedToken.thenCombine(tokenProvider.apply(entry.getValue()),
                                                      (t1, t2) -> t1.advancedTo(entry.getKey(), t2));
        }
        return combinedToken;
    }

    /**
     * Builder class to instantiate a {@link MultiStreamableEventSource}. The configurable field
     * {@code eventComparator}, which decides which message to process first if there is a choice, defaults to the
     * oldest message available (using the event's timestamp).
     */
    public static class Builder {

        private final Map<String, StreamableEventSource> eventSourceMap = new LinkedHashMap<>();
        private Comparator<MessageStream.Entry<EventMessage>> eventComparator =
                Comparator.comparing(entry -> entry.message().timestamp());

        /**
         * Constructs a new Builder instance with default values.
         */
        protected Builder() {
        }

        /**
         * Adds an event source to the list of sources.
         *
         * @param eventSourceId A unique name identifying the source.
         * @param eventSource   The event source to be added.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder addEventSource(String eventSourceId, StreamableEventSource eventSource) {
            Assert.isFalse(eventSourceMap.containsKey(eventSourceId),
                           () -> "The event source name must be unique");
            eventSourceMap.put(eventSourceId, eventSource);
            return this;
        }

        /**
         * Overrides the default eventComparator. The default eventComparator returns the oldest event available:
         * {@code Comparator.comparing(entry -> entry.message().timestamp())}.
         *
         * @param eventComparator The comparator to use when deciding on which message to return.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder eventComparator(
                Comparator<MessageStream.Entry<EventMessage>> eventComparator) {
            this.eventComparator = eventComparator;
            return this;
        }

        /**
         * Initializes a {@link MultiStreamableEventSource} as specified through this Builder.
         *
         * @return a {@link MultiStreamableEventSource} as specified through this Builder.
         */
        public MultiStreamableEventSource build() {
            return new MultiStreamableEventSource(this);
        }
    }

    private class MultiStreamEventStream implements MessageStream<EventMessage> {

        private final AtomicReference<MultiSourceTrackingToken> currentToken;
        private final MessageStream<EventMessage> delegate;

        public MultiStreamEventStream(
                MultiSourceTrackingToken token, MessageStream<EventMessage> delegate) {
            this.currentToken = new AtomicReference<>(token);
            this.delegate = delegate;
        }

        @Override
        public Optional<Entry<EventMessage>> next() {
            return delegate.next().map(e -> e.withResource(TrackingToken.RESOURCE_KEY,
                                                           currentToken.updateAndGet(t -> t.advancedTo(e.getResource(
                                                                                                               SOURCE_ID_RESOURCE),
                                                                                                       e.getResource(
                                                                                                               TrackingToken.RESOURCE_KEY)))));
        }

        @Override
        public Optional<Entry<EventMessage>> peek() {
            return delegate.peek().map(e -> e.withResource(TrackingToken.RESOURCE_KEY,
                                                           currentToken.get()
                                                                       .advancedTo(e.getResource(SOURCE_ID_RESOURCE),
                                                                                   e.getResource(TrackingToken.RESOURCE_KEY))));
        }

        @Override
        public void setCallback(@NonNull Runnable callback) {
            delegate.setCallback(callback);
        }

        @Override
        public Optional<Throwable> error() {
            return delegate.error();
        }

        @Override
        public boolean isCompleted() {
            return delegate.isCompleted();
        }

        @Override
        public boolean hasNextAvailable() {
            return delegate.hasNextAvailable();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
