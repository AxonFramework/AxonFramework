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

import org.axonframework.common.stream.BlockingStream;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.eventhandling.GenericTrackedEventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.StreamableMessageSource;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * An in-memory representation of the {@link StreamableMessageSource}. Should be used to test event handling components
 * requiring test flexibility when events are published.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 */
public class InMemoryStreamableEventSource implements StreamableMessageSource<TrackedEventMessage<?>> {

    /**
     * An {@link EventMessage#getPayload()} representing a failed event.
     */
    private static final String FAIL_PAYLOAD = "FAIL";
    /**
     * An {@link EventMessage} representing a failed event.
     */
    public static final EventMessage<String> FAIL_EVENT = EventTestUtils.asEventMessage(FAIL_PAYLOAD);

    private List<TrackedEventMessage<?>> messages = new CopyOnWriteArrayList<>();
    private final boolean streamCallbackSupported;
    private final List<TrackedEventMessage<?>> ignoredEvents = new CopyOnWriteArrayList<>();
    private Runnable onAvailableCallback = null;

    /**
     * Construct a default {@link InMemoryStreamableEventSource}. If stream callbacks should be supported for testing,
     * use {@link InMemoryStreamableEventSource#InMemoryStreamableEventSource(boolean)}.
     */
    public InMemoryStreamableEventSource() {
        this(false);
    }

    /**
     * Construct a {@link InMemoryStreamableEventSource} toggling {@code streamCallbackSupported}.
     *
     * @param streamCallbackSupported A {@code boolean} dictating whether the {@link StreamableMessageSource} should
     *                                support callbacks.
     */
    public InMemoryStreamableEventSource(boolean streamCallbackSupported) {
        this.streamCallbackSupported = streamCallbackSupported;
    }

    @Override
    public BlockingStream<TrackedEventMessage<?>> openStream(TrackingToken trackingToken) {
        return new BlockingStream<TrackedEventMessage<?>>() {

            private int lastToken;

            @Override
            public Optional<TrackedEventMessage<?>> peek() {
                if (messages.size() > lastToken) {
                    return Optional.of(messages.get(lastToken));
                }
                return Optional.empty();
            }

            @Override
            public boolean hasNextAvailable(int timeout, TimeUnit unit) {
                return peek().isPresent();
            }

            @Override
            public TrackedEventMessage<?> nextAvailable() {
                TrackedEventMessage<?> next = peek().orElseThrow(
                        () -> new RuntimeException("The processor should never perform a blocking call")
                );
                this.lastToken = (int) next.trackingToken()
                                           .position()
                                           .orElseThrow(() -> new UnsupportedOperationException("Not supported"));

                if (next.getPayload().equals(FAIL_PAYLOAD)) {
                    throw new IllegalStateException("Cannot retrieve event at position [" + lastToken + "].");
                }

                return next;
            }

            @Override
            public void close() {
                clearAllMessages();
            }

            @Override
            public void skipMessagesWithPayloadTypeOf(TrackedEventMessage<?> ignoredEvent) {
                ignoredEvents.add(ignoredEvent);
            }

            @Override
            public boolean setOnAvailableCallback(Runnable callback) {
                onAvailableCallback = callback;
                return streamCallbackSupported;
            }
        };
    }

    private synchronized void clearAllMessages() {
        messages = new CopyOnWriteArrayList<>();
    }

    @Override
    public TrackingToken createTailToken() {
        return null;
    }

    @Override
    public TrackingToken createHeadToken() {
        if (messages.isEmpty()) {
            return null;
        }
        return messages.get(messages.size() - 1).trackingToken();
    }

    @Override
    public TrackingToken createTokenAt(Instant dateTime) {
        throw new UnsupportedOperationException("Not supported for InMemoryStreamableEventSource");
    }

    @Override
    public TrackingToken createTokenSince(Duration duration) {
        throw new UnsupportedOperationException("Not supported for InMemoryStreamableEventSource");
    }

    /**
     * Publishes the given {@code event} on this {@link StreamableMessageSource}. This allows consumers of this message
     * source to receive the event as if it was published through actual channels.
     *
     * @param event The event to publish on this {@link StreamableMessageSource}.
     */
    public synchronized void publishMessage(EventMessage<?> event) {
        int nextToken = messages.size();
        messages.add(new GenericTrackedEventMessage<>(new GlobalSequenceTrackingToken(nextToken + 1), event));
    }

    /**
     * Return a {@link List} of {@link TrackedEventMessage TrackedEventMessages} that have been ignored the stream
     * consumer.
     *
     * @return A {@link List} of {@link TrackedEventMessage TrackedEventMessages} that have been ignored the stream
     * consumer.
     */
    public List<TrackedEventMessage<?>> getIgnoredEvents() {
        return Collections.unmodifiableList(ignoredEvents);
    }

    /**
     * Manually toggles the callback that's triggered when new events are present.
     */
    public void runOnAvailableCallback() {
        onAvailableCallback.run();
    }
}
