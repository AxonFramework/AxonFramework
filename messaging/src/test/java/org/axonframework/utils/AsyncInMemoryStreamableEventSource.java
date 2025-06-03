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

package org.axonframework.utils;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventstreaming.StreamableEventSource;
import org.axonframework.eventstreaming.StreamingCondition;
import org.axonframework.eventstreaming.Tag;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.SimpleEntry;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An in-memory implementation of {@link StreamableEventSource} designed for testing purposes,
 * particularly with the {@link org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor}.
 * <p>
 * This implementation provides similar functionality to {@link InMemoryStreamableEventSource} but is
 * adapted to work with the {@link MessageStream} API used by the pooled processor. It supports
 * event publishing, ignored event tracking, and optional callback mechanisms for testing
 * asynchronous event processing scenarios.
 *
 * @author Steven van Beelen
 * @since 4.5
 */
public class AsyncInMemoryStreamableEventSource implements StreamableEventSource<EventMessage<?>> {

    /**
     * An {@link EventMessage#getPayload()} representing a failed event.
     */
    private static final String FAIL_PAYLOAD = "FAIL";

    /**
     * An {@link EventMessage} representing a failed event.
     */
    public static final EventMessage<String> FAIL_EVENT = EventTestUtils.asEventMessage(FAIL_PAYLOAD);

    private final NavigableMap<Long, EventMessage<?>> eventStorage = new ConcurrentSkipListMap<>();
    private final AtomicLong nextIndex = new AtomicLong(0);
    private final boolean streamCallbackSupported;
    private final List<EventMessage<?>> ignoredEvents = new CopyOnWriteArrayList<>();
    private final Set<AsyncMessageStream> openStreams = new CopyOnWriteArraySet<>();

    /**
     * Construct a default {@link AsyncInMemoryStreamableEventSource}. If stream callbacks should be supported for testing,
     * use {@link AsyncInMemoryStreamableEventSource#AsyncInMemoryStreamableEventSource(boolean)}.
     */
    public AsyncInMemoryStreamableEventSource() {
        this(false);
    }

    /**
     * Construct a {@link AsyncInMemoryStreamableEventSource} toggling {@code streamCallbackSupported}.
     *
     * @param streamCallbackSupported A {@code boolean} dictating whether the {@link StreamableEventSource} should
     *                                support callbacks.
     */
    public AsyncInMemoryStreamableEventSource(boolean streamCallbackSupported) {
        this.streamCallbackSupported = streamCallbackSupported;
    }

    @Override
    public MessageStream<EventMessage<?>> open(@Nonnull StreamingCondition condition) {
        AsyncMessageStream stream = new AsyncMessageStream(condition);
        openStreams.add(stream);
        return stream;
    }

    @Override
    public CompletableFuture<TrackingToken> tailToken() {
        return CompletableFuture.completedFuture(
                eventStorage.isEmpty()
                        ? null
                        : new GlobalSequenceTrackingToken(eventStorage.firstKey() - 1)
        );
    }

    @Override
    public CompletableFuture<TrackingToken> headToken() {
        return CompletableFuture.completedFuture(
                eventStorage.isEmpty()
                        ? null
                        : new GlobalSequenceTrackingToken(eventStorage.lastKey())
        );
    }

    @Override
    public CompletableFuture<TrackingToken> tokenAt(@Nonnull Instant at) {
        return eventStorage.entrySet()
                .stream()
                .filter(positionToEventEntry -> {
                    EventMessage<?> event = positionToEventEntry.getValue();
                    Instant eventTimestamp = event.getTimestamp();
                    return eventTimestamp.equals(at) || eventTimestamp.isAfter(at);
                })
                .map(Map.Entry::getKey)
                .min(Comparator.comparingLong(Long::longValue))
                .map(position -> position - 1)
                .map(GlobalSequenceTrackingToken::new)
                .map(tt -> (TrackingToken) tt)
                .map(CompletableFuture::completedFuture)
                .orElseGet(this::headToken);
    }

    /**
     * Publishes the given {@code event} on this {@link StreamableEventSource}. This allows consumers of this message
     * source to receive the event as if it was published through actual channels.
     *
     * @param event The event to publish on this {@link StreamableEventSource}.
     */
    public synchronized void publishMessage(EventMessage<?> event) {
        long position = nextIndex.getAndIncrement();
        eventStorage.put(position, event);

        // Notify all open streams
        openStreams.forEach(AsyncMessageStream::notifyEventAvailable);
    }

    /**
     * Return a {@link List} of {@link EventMessage EventMessages} that have been ignored by the stream
     * consumer.
     *
     * @return A {@link List} of {@link EventMessage EventMessages} that have been ignored by the stream
     * consumer.
     */
    public List<EventMessage<?>> getIgnoredEvents() {
        return Collections.unmodifiableList(ignoredEvents);
    }

    /**
     * Manually toggles the callback that's triggered when new events are present.
     */
    public void runOnAvailableCallback() {
        openStreams.forEach(AsyncMessageStream::runCallback);
    }

    /**
     * Internal implementation of {@link MessageStream} that provides event streaming functionality.
     */
    private class AsyncMessageStream implements MessageStream<EventMessage<?>> {

        private final AtomicLong currentPosition;
        private final AtomicReference<Runnable> callback = new AtomicReference<>(() -> {
        });
        private final StreamingCondition condition;
        private volatile boolean closed = false;

        public AsyncMessageStream(StreamingCondition condition) {
            this.condition = condition;

            // Handle null tracking token properly
            TrackingToken startToken = condition.position();
            if (startToken == null) {
                // Start from the beginning
                this.currentPosition = new AtomicLong(0);
            } else {
                // Start from the position after the given token
                long tokenPosition = startToken.position().orElse(-1);
                this.currentPosition = new AtomicLong(tokenPosition + 1);
            }
        }

        @Override
        public Optional<Entry<EventMessage<?>>> next() {
            if (closed) {
                return Optional.empty();
            }

            // Find the next matching event
            while (true) {
                long position = currentPosition.get();
                EventMessage<?> event = eventStorage.get(position);

                if (event == null) {
                    return Optional.empty();
                }

                // Advance position for next call
                currentPosition.incrementAndGet();

                // Check for failure event
                if (FAIL_PAYLOAD.equals(event.getPayload())) {
                    throw new IllegalStateException("Cannot retrieve event at position [" + position + "].");
                }

                // Check if event matches the condition
                if (matches(event, condition)) {
                    // Create context with tracking token and tags
                    Context context = Context.empty();
                    context = TrackingToken.addToContext(context, new GlobalSequenceTrackingToken(position));
                    context = Tag.addToContext(context, Collections.emptySet()); // No tags for simple events

                    return Optional.of(new SimpleEntry<>(event, context));
                } else {
                    // Event doesn't match, record as ignored and continue searching
                    ignoredEvents.add(event);
                }
            }
        }

        @Override
        public void onAvailable(@Nonnull Runnable callback) {
            this.callback.set(callback);
            if (streamCallbackSupported && hasNextAvailable()) {
                callback.run();
            }
        }

        @Override
        public Optional<Throwable> error() {
            return Optional.empty();
        }

        @Override
        public boolean isCompleted() {
            return closed;
        }

        @Override
        public boolean hasNextAvailable() {
            if (closed) {
                return false;
            }

            // Check if there's any matching event from current position onwards
            long position = currentPosition.get();
            return eventStorage.tailMap(position).values().stream()
                    .anyMatch(event -> !FAIL_PAYLOAD.equals(event.getPayload()) && matches(event, condition));
        }

        @Override
        public void close() {
            closed = true;
            openStreams.remove(this);
        }

        public void notifyEventAvailable() {
            if (!closed && streamCallbackSupported) {
                Runnable currentCallback = callback.get();
                if (currentCallback != null) {
                    currentCallback.run();
                }
            }
        }

        public void runCallback() {
            if (!closed) {
                Runnable currentCallback = callback.get();
                if (currentCallback != null) {
                    currentCallback.run();
                }
            }
        }
    }

    /**
     * Checks whether the given event matches the streaming condition.
     * Similar to the match method in InMemoryEventStorageEngine.
     */
    private static boolean matches(EventMessage<?> event, StreamingCondition condition) {
        QualifiedName qualifiedName = event.type().qualifiedName();
        Set<Tag> tags = Collections.emptySet(); // Simple events have no tags
        return condition.matches(qualifiedName, tags);
    }
}