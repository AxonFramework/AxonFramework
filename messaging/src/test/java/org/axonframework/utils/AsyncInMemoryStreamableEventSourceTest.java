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
import org.axonframework.eventhandling.processors.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.processors.streaming.token.TrackingToken;
import org.axonframework.eventstreaming.StreamingCondition;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This test verifies that both implementations produce equivalent results when processing the same sequence of events,
 * including error scenarios.
 */
class AsyncInMemoryStreamableEventSourceTest {

    private ProcessingContext processingContext = null;

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
            MessageStream<EventMessage> stream = eventSource.open(condition, processingContext);
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
            MessageStream<EventMessage> stream = eventSource.open(condition, processingContext);
            assertFalse(stream.hasNextAvailable(),
                        "Stream should have no events when starting beyond available events");
            assertTrue(stream.next().isEmpty(), "next() should return empty Optional");
        }
    }
}