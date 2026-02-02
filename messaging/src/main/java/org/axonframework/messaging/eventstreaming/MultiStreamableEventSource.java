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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.Assert;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.core.DelayedMessageStream;
import org.axonframework.messaging.core.MergedMessageStream;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
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
     * Constructs a MultiStreamableEventSource from the collected sources and comparator.
     *
     * @param eventSources    The map of event sources, keyed by their unique names.
     * @param eventComparator The comparator to use when deciding which message to process first.
     */
    protected MultiStreamableEventSource(Map<String, StreamableEventSource> eventSources,
                                         Comparator<MessageStream.Entry<EventMessage>> eventComparator) {
        this.eventSources = Collections.unmodifiableMap(new LinkedHashMap<>(eventSources));
        this.eventComparator = eventComparator;
    }

    /**
     * Creates a new MultiStreamableEventSource by combining multiple event sources.
     * This is the starting point for the fluent builder API.
     * <p>
     * Example usage:
     * <pre>{@code
     * // With default timestamp-based comparison
     * MultiStreamableEventSource source = MultiStreamableEventSource
     *     .combining("eventStore1", eventSource1)
     *     .and("eventStore2", eventSource2)
     *     .comparingTimestamps();
     *
     * // With custom comparator
     * MultiStreamableEventSource source = MultiStreamableEventSource
     *     .combining("eventStore1", eventSource1)
     *     .and("eventStore2", eventSource2)
     *     .comparingUsing(customComparator);
     * }</pre>
     *
     * @param sourceName A unique name identifying the first source.
     * @param source     The first event source to include.
     * @return A SourceCollector for adding more sources and configuring the comparator.
     */
    public static SourceCollector combining(@Nonnull String sourceName, @Nonnull StreamableEventSource source) {
        return new SourceCollectorImpl(sourceName, source);
    }

    @Override
    public MessageStream<EventMessage> open(@Nonnull StreamingCondition condition,
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
    public CompletableFuture<TrackingToken> tokenAt(@Nonnull Instant at, @Nullable ProcessingContext context) {
        return createToken(s -> s.tokenAt(at, context)).thenApply(t -> t);
    }

    /**
     * Returns the map of event sources managed by this MultiStreamableEventSource.
     * Package-private for testing purposes.
     *
     * @return An unmodifiable map of source names to their corresponding event sources.
     */
    Map<String, StreamableEventSource> sources() {
        return eventSources;
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
     * Intermediate builder for collecting event sources before creating a MultiStreamableEventSource.
     * Allows adding multiple sources and provides terminal operations for creating the final instance.
     */
    public interface SourceCollector {

        /**
         * Adds another event source to the collection.
         *
         * @param sourceName A unique name identifying the source.
         * @param source     The event source to add.
         * @return This SourceCollector for fluent chaining.
         * @throws IllegalArgumentException if the sourceName is already used.
         */
        SourceCollector and(@Nonnull String sourceName, @Nonnull StreamableEventSource source);

        /**
         * Creates a MultiStreamableEventSource using timestamp-based comparison (oldest event first). This is the
         * default behavior, comparing events based on their timestamp.
         *
         * @return A configured MultiStreamableEventSource instance.
         */
        MultiStreamableEventSource comparingTimestamps();

        /**
         * Creates a MultiStreamableEventSource using a custom comparator.
         *
         * @param comparator The comparator to use when deciding which message to process first.
         *                   A result of {@code <= 0} means the message from the first stream
         *                   is consumed first.
         * @return A configured MultiStreamableEventSource instance.
         */
        MultiStreamableEventSource comparingUsing(@Nonnull Comparator<MessageStream.Entry<EventMessage>> comparator);
    }

    /**
     * Implementation of the SourceCollector that collects event sources and creates the final instance.
     */
    private static class SourceCollectorImpl implements SourceCollector {

        private final Map<String, StreamableEventSource> eventSourceMap;

        /**
         * Creates a new SourceCollectorImpl with the initial source.
         *
         * @param initialName   The name of the first source.
         * @param initialSource The first source.
         */
        SourceCollectorImpl(String initialName, StreamableEventSource initialSource) {
            this.eventSourceMap = new LinkedHashMap<>();
            addSource(initialName, initialSource);
        }

        @Override
        public SourceCollector and(@Nonnull String sourceName, @Nonnull StreamableEventSource source) {
            addSource(sourceName, source);
            return this;
        }

        private void addSource(String name, StreamableEventSource source) {
            Objects.requireNonNull(name, "sourceName must not be null");
            Objects.requireNonNull(source, "source must not be null");
            Assert.isFalse(eventSourceMap.containsKey(name),
                           () -> "Source name '" + name + "' is already used. Source names must be unique.");
            eventSourceMap.put(name, source);
        }

        @Override
        public MultiStreamableEventSource comparingTimestamps() {
            return comparingUsing(Comparator.comparing(entry -> entry.message().timestamp()));
        }

        @Override
        public MultiStreamableEventSource comparingUsing(
                @Nonnull Comparator<MessageStream.Entry<EventMessage>> comparator) {
            Objects.requireNonNull(comparator, "comparator must not be null");
            return new MultiStreamableEventSource(eventSourceMap, comparator);
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
        public void setCallback(@Nonnull Runnable callback) {
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
