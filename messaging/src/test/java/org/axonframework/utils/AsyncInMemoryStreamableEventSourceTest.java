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

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventstreaming.StreamingCondition;
import org.axonframework.messaging.MessageStream;
import org.junit.jupiter.api.*;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that demonstrates the compatibility between {@link AsyncInMemoryStreamableEventSource} and
 * {@link InMemoryStreamableEventSource}.
 * <p>
 * This test verifies that both implementations produce equivalent results when processing the same sequence of events,
 * including error scenarios.
 */
class AsyncInMemoryStreamableEventSourceTest {

    @Test
    @DisplayName("Both implementations handle normal event flow identically")
    void normalEventFlowCompatibility() throws ExecutionException, InterruptedException {
        // Given - Same events for both implementations
        EventMessage event1 = EventTestUtils.asEventMessage("Test Event 1");
        EventMessage event2 = EventTestUtils.asEventMessage("Test Event 2");

        AsyncInMemoryStreamableEventSource asyncSource = new AsyncInMemoryStreamableEventSource();
        InMemoryStreamableEventSource legacySource = new InMemoryStreamableEventSource();

        // When - Publish same events to both
        asyncSource.publishMessage(event1);
        asyncSource.publishMessage(event2);

        legacySource.publishMessage(event1);
        legacySource.publishMessage(event2);

        // Then - Both should report same head token
        TrackingToken asyncHead = asyncSource.firstToken().get();
        TrackingToken legacyHead = legacySource.createHeadToken();

        assertEquals(legacyHead.position(), asyncHead.position());

        // And both should deliver same events in same order
        StreamingCondition condition = StreamingCondition.startingFrom(null);
        MessageStream<EventMessage> asyncStream = asyncSource.open(condition);
        try (var legacyStream = legacySource.openStream(null)) {

            // First event
            assertTrue(asyncStream.hasNextAvailable());
            assertTrue(legacyStream.hasNextAvailable(0, java.util.concurrent.TimeUnit.MILLISECONDS));

            var asyncEntry1 = asyncStream.next().orElseThrow();
            TrackedEventMessage legacyEvent1 = legacyStream.nextAvailable();

            assertEquals(legacyEvent1.payload(), asyncEntry1.message().payload());
            assertEquals(legacyEvent1.trackingToken().position(),
                         TrackingToken.fromContext(asyncEntry1).orElseThrow().position());

            // Second event
            var asyncEntry2 = asyncStream.next().orElseThrow();
            TrackedEventMessage legacyEvent2 = legacyStream.nextAvailable();

            assertEquals(legacyEvent2.payload(), asyncEntry2.message().payload());
            assertEquals(legacyEvent2.trackingToken().position(),
                         TrackingToken.fromContext(asyncEntry2).orElseThrow().position());

            // No more events
            assertFalse(asyncStream.hasNextAvailable());
            assertFalse(legacyStream.hasNextAvailable(0, java.util.concurrent.TimeUnit.MILLISECONDS));
        }
    }

    @Test
    @DisplayName("Both implementations handle FAIL_EVENT identically")
    void failEventCompatibility() {
        // Given
        AsyncInMemoryStreamableEventSource asyncSource = new AsyncInMemoryStreamableEventSource();
        InMemoryStreamableEventSource legacySource = new InMemoryStreamableEventSource();

        // When - Publish FAIL_EVENT to both
        asyncSource.publishMessage(AsyncInMemoryStreamableEventSource.FAIL_EVENT);
        legacySource.publishMessage(InMemoryStreamableEventSource.FAIL_EVENT);

        // Then - Both should throw identical exceptions
        StreamingCondition condition = StreamingCondition.startingFrom(null);

        // Test async implementation
        MessageStream<EventMessage> asyncStream = asyncSource.open(condition);
        IllegalStateException asyncException = assertThrows(IllegalStateException.class,
                                                            asyncStream::next);
        assertTrue(asyncException.getMessage().contains("Cannot retrieve event at position [0]"));

        // Test legacy implementation
        try (var legacyStream = legacySource.openStream(null)) {
            IllegalStateException legacyException = assertThrows(IllegalStateException.class,
                                                                 legacyStream::nextAvailable);
            assertTrue(legacyException.getMessage().contains("Cannot retrieve event at position"));
        }
    }

    @Test
    @DisplayName("Both implementations handle destructive close behavior identically")
    void destructiveCloseBehaviorCompatibility() throws ExecutionException, InterruptedException {
        // Given
        AsyncInMemoryStreamableEventSource asyncSource = new AsyncInMemoryStreamableEventSource();
        InMemoryStreamableEventSource legacySource = new InMemoryStreamableEventSource();

        // Publish events to both
        EventMessage event = EventTestUtils.asEventMessage("Test Event");
        asyncSource.publishMessage(event);
        legacySource.publishMessage(event);

        // Verify events exist
        assertNotNull(asyncSource.firstToken().get());
        assertNotNull(legacySource.createHeadToken());

        // When - Open and close streams (triggers destructive behavior)
        StreamingCondition condition = StreamingCondition.startingFrom(null);
        MessageStream<EventMessage> asyncStream = asyncSource.open(condition);
        asyncStream.close();
        try (var legacyStream = legacySource.openStream(null)) {
            // Just open and close
        }

        // Then - Both should have cleared their events
        assertNull(asyncSource.firstToken().get());
        assertNull(legacySource.createHeadToken());

        // And new streams should see no events
        MessageStream<EventMessage> newAsyncStream = asyncSource.open(condition);
        assertFalse(newAsyncStream.hasNextAvailable());

        try (var newLegacyStream = legacySource.openStream(null)) {
            assertFalse(newLegacyStream.hasNextAvailable(0, java.util.concurrent.TimeUnit.MILLISECONDS));
        }
    }

    @Nested
    class TrackingTokenHandling {

        @Test
        @DisplayName("Starting from null token reads all events from beginning")
        void nullTrackingTokenStartsFromBeginning() {
            // Given
            AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();

            EventMessage event1 = EventTestUtils.asEventMessage("Event 1");
            EventMessage event2 = EventTestUtils.asEventMessage("Event 2");

            eventSource.publishMessage(event1);
            eventSource.publishMessage(event2);

            // When - Start stream with null token
            StreamingCondition condition = StreamingCondition.startingFrom(null);

            // Then - Should read all events from position 1
            MessageStream<EventMessage> stream = eventSource.open(condition);
            Optional<MessageStream.Entry<EventMessage>> entry1 = stream.next();
            assertTrue(entry1.isPresent());
            assertEquals("Event 1", entry1.get().message().payload());
            assertEquals(1, TrackingToken.fromContext(entry1.get()).orElseThrow().position().orElse(-1));

            Optional<MessageStream.Entry<EventMessage>> entry2 = stream.next();
            assertTrue(entry2.isPresent());
            assertEquals("Event 2", entry2.get().message().payload());
            assertEquals(2, TrackingToken.fromContext(entry2.get()).orElseThrow().position().orElse(-1));
        }

        @Test
        @DisplayName("Starting from token beyond available events returns no events")
        void trackingTokenBeyondAvailableEvents() {
            // Given
            AsyncInMemoryStreamableEventSource eventSource = new AsyncInMemoryStreamableEventSource();

            EventMessage event1 = EventTestUtils.asEventMessage("Event 1");
            eventSource.publishMessage(event1);

            // When - Start from token position 5 (beyond available events)
            TrackingToken futureToken = new GlobalSequenceTrackingToken(5);
            StreamingCondition condition = StreamingCondition.startingFrom(futureToken);

            // Then - Should have no events available
            MessageStream<EventMessage> stream = eventSource.open(condition);
            assertFalse(stream.hasNextAvailable(),
                        "Stream should have no events when starting beyond available events");
            assertTrue(stream.next().isEmpty(), "next() should return empty Optional");
        }
    }
}