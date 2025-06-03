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
import org.axonframework.eventstreaming.EventCriteria;
import org.axonframework.eventstreaming.StreamableEventSource;
import org.axonframework.eventstreaming.StreamingCondition;
import org.axonframework.eventstreaming.Tag;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * An in-memory implementation of {@link StreamableEventSource} for testing purposes.
 * Provides functionality to publish events and stream them with filtering capabilities.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
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

    private final List<StoredEvent> events = new CopyOnWriteArrayList<>();
    private final AtomicLong sequenceNumber = new AtomicLong(0);
    private final boolean streamCallbackSupported;
    private final List<EventMessage<?>> ignoredEvents = new CopyOnWriteArrayList<>();
    private volatile Runnable onAvailableCallback = null;

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
        // Handle null condition case (can happen in tests with Mockito)
        if (condition == null) {
            return createStreamFromAllEvents();
        }

        // Find starting position based on tracking token
        TrackingToken startPosition = condition.position();
        int startIndex = findStartingIndex(startPosition);

        // Process events and track ignored ones
        List<EventMessage<?>> matchingEvents = new ArrayList<>();

        events.stream()
              .skip(startIndex)
              .map(StoredEvent::event)
              .forEach(event -> {
                  if (matchesCriteria(event, condition)) {
                      matchingEvents.add(event);
                  } else {
                      // Track events that don't match criteria as ignored
                      ignoredEvents.add(event);
                  }
              });

        // Create MessageStream with matching events only
        return MessageStream.fromStream(
                matchingEvents.stream().filter(this::checkForFailure),
                event -> event,
                this::createContextForEvent
        );
    }

    private MessageStream<EventMessage<?>> createStreamFromAllEvents() {
        // When no condition is provided, all events are considered matching
        // No events are added to ignoredEvents in this case
        Stream<? extends EventMessage<?>> eventStream = events.stream()
                                                              .map(StoredEvent::event)
                                                              .filter(event -> checkForFailure(event));

        return MessageStream.fromStream(
                eventStream,
                event -> event,
                this::createContextForEvent
        );
    }

    private int findStartingIndex(TrackingToken startPosition) {
        if (startPosition == null) {
            return 0;
        }

        if (startPosition instanceof GlobalSequenceTrackingToken globalToken) {
            long position = globalToken.getGlobalIndex();
            for (int i = 0; i < events.size(); i++) {
                if (events.get(i).trackingToken instanceof GlobalSequenceTrackingToken eventToken) {
                    if (eventToken.getGlobalIndex() > position) {
                        return i;
                    }
                }
            }
            return events.size(); // Start at end if position is beyond all events
        }
        return 0;
    }

    private boolean matchesCriteria(EventMessage<?> event, StreamingCondition condition) {
        EventCriteria criteria = condition.criteria();

        // If criteria has no actual criteria defined (like havingAnyTag()), accept all events
        if (!criteria.hasCriteria()) {
            return true;
        }

        // Get the QualifiedName from the event type
        QualifiedName eventType = event.type().qualifiedName();

        // For testing purposes, create empty tags set or simulate based on event
        // In a real implementation, tags would come from how the event was published
        Set<Tag> eventTags = simulateTagsForEvent(event);

        return criteria.matches(eventType, eventTags);
    }

    private Set<Tag> simulateTagsForEvent(EventMessage<?> event) {
        // For testing purposes, we can simulate tags based on event characteristics
        // In most tests, EventCriteria.havingAnyTag() is used which has no criteria anyway

        // Example: Could create tags based on payload type, identifier, etc.
        // Uncomment and modify as needed for specific test scenarios:

        // Example 1: Tag based on payload type
        // return Set.of(Tag.of("type", event.getPayloadType().getSimpleName()));

        // Example 2: Tag based on payload value (for Integer payloads)
        // if (event.getPayload() instanceof Integer) {
        //     return Set.of(Tag.of("segment", String.valueOf((Integer) event.getPayload())));
        // }

        // For now, return empty set since most tests use havingAnyTag()
        return Set.of();
    }

    private boolean checkForFailure(EventMessage<?> event) {
        // Check for failure simulation
        if (FAIL_PAYLOAD.equals(event.getPayload())) {
            throw new IllegalStateException("Cannot retrieve event at position [" + findTokenForEvent(event) + "].");
        }
        return true;
    }

    private Context createContextForEvent(EventMessage<?> event) {
        TrackingToken token = findTokenForEvent(event);
        Context context = TrackingToken.addToContext(Context.empty(), token);

        // Add tags to context so they're available in MessageStream.Entry
        Set<Tag> eventTags = simulateTagsForEvent(event);
        context = Tag.addToContext(context, eventTags);

        return context;
    }

    private TrackingToken findTokenForEvent(EventMessage<?> event) {
        return events.stream()
                     .filter(storedEvent -> storedEvent.event.equals(event))
                     .map(StoredEvent::trackingToken)
                     .findFirst()
                     .orElse(new GlobalSequenceTrackingToken(0));
    }

    @Override
    public CompletableFuture<TrackingToken> headToken() {
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.completedFuture(new GlobalSequenceTrackingToken(0));
    }

    @Override
    public CompletableFuture<TrackingToken> tailToken() {
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.completedFuture(events.get(events.size() - 1).trackingToken);
    }

    @Override
    public CompletableFuture<TrackingToken> tokenAt(@Nonnull Instant at) {
        // For testing purposes, we'll return the tail token
        // In a real implementation, this would find the token closest to the given instant
        return tailToken();
    }

    /**
     * Publishes the given {@code event} on this {@link StreamableEventSource}. This allows consumers of this message
     * source to receive the event as if it was published through actual channels.
     *
     * @param event The event to publish on this {@link StreamableEventSource}.
     */
    public synchronized void publishMessage(EventMessage<?> event) {
        long nextSequence = sequenceNumber.incrementAndGet();
        TrackingToken token = new GlobalSequenceTrackingToken(nextSequence);
        StoredEvent storedEvent = new StoredEvent(event, token);
        events.add(storedEvent);

        // Notify any listeners if callbacks are supported
        if (streamCallbackSupported && onAvailableCallback != null) {
            onAvailableCallback.run();
        }
    }

    /**
     * Return a {@link List} of {@link EventMessage EventMessages} that have been ignored by the stream consumer.
     *
     * @return A {@link List} of {@link EventMessage EventMessages} that have been ignored by the stream consumer.
     */
    public List<EventMessage<?>> getIgnoredEvents() {
        return Collections.unmodifiableList(ignoredEvents);
    }

    /**
     * Manually toggles the callback that's triggered when new events are present.
     */
    public void runOnAvailableCallback() {
        if (onAvailableCallback != null) {
            onAvailableCallback.run();
        }
    }

    /**
     * Clear all events from memory.
     */
    public synchronized void clearAllMessages() {
        events.clear();
        sequenceNumber.set(0);
        ignoredEvents.clear();
    }

    /**
     * Internal storage for events with their tracking tokens.
     */
    private static record StoredEvent(EventMessage<?> event, TrackingToken trackingToken) {

    }
}