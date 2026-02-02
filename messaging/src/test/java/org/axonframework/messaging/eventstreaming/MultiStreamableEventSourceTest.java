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
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.AsyncInMemoryStreamableEventSource;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventTestUtils;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class MultiStreamableEventSourceTest {

    private MultiStreamableEventSource testSubject;
    private AsyncInMemoryStreamableEventSource eventSourceA;
    private AsyncInMemoryStreamableEventSource eventSourceB;

    @BeforeEach
    void setUp() {
        eventSourceA = new AsyncInMemoryStreamableEventSource();
        eventSourceB = new AsyncInMemoryStreamableEventSource();

        testSubject = MultiStreamableEventSource.builder()
                                                .addEventSource("sourceA", eventSourceA)
                                                .addEventSource("sourceB", eventSourceB)
                                                .build();
    }

    @Test
    void simplePublishAndConsumeFromSingleSource() {
        EventMessage publishedEvent = EventTestUtils.asEventMessage("Event1");
        eventSourceA.publishMessage(publishedEvent);

        MessageStream<EventMessage> stream = testSubject.open(
                StreamingCondition.startingFrom(null), null
        );

        assertTrue(stream.hasNextAvailable());
        MessageStream.Entry<EventMessage> entry = stream.next().orElseThrow();
        assertEquals(publishedEvent.payload(), entry.message().payload());

        stream.close();
    }

    @Test
    void publishAndConsumeFromMultipleSources() {
        EventMessage event1 = EventTestUtils.asEventMessage("Event1");
        EventMessage event2 = EventTestUtils.asEventMessage("Event2");

        eventSourceA.publishMessage(event1);
        eventSourceB.publishMessage(event2);

        MessageStream<EventMessage> stream = testSubject.open(
                StreamingCondition.startingFrom(null), null
        );

        assertTrue(stream.hasNextAvailable());

        List<String> payloads = new ArrayList<>();
        stream.next().ifPresent(e -> payloads.add((String) e.message().payload()));
        stream.next().ifPresent(e -> payloads.add((String) e.message().payload()));

        assertEquals(2, payloads.size());
        assertTrue(payloads.contains("Event1"));
        assertTrue(payloads.contains("Event2"));

        stream.close();
    }

    @Test
    void messagesAreOrderedByTimestampByDefault() throws InterruptedException {
        EventMessage event1 = EventTestUtils.asEventMessage("Event1");
        eventSourceA.publishMessage(event1);

        // Ensure event2 has a later timestamp
        Thread.sleep(10);

        EventMessage event2 = EventTestUtils.asEventMessage("Event2");
        eventSourceB.publishMessage(event2);

        MessageStream<EventMessage> stream = testSubject.open(
                StreamingCondition.startingFrom(null), null
        );

        MessageStream.Entry<EventMessage> first = stream.next().orElseThrow();
        MessageStream.Entry<EventMessage> second = stream.next().orElseThrow();

        assertEquals("Event1", first.message().payload());
        assertEquals("Event2", second.message().payload());

        stream.close();
    }

    @Test
    void customComparatorIsUsed() {
        // Create a comparator that prioritizes sourceB
        Comparator<MessageStream.Entry<EventMessage>> customComparator =
                Comparator.comparing((MessageStream.Entry<EventMessage> entry) ->
                                             !entry.message().payload().toString().contains("B"))
                          .thenComparing(entry -> entry.message().timestamp());

        testSubject = MultiStreamableEventSource.builder()
                                                .addEventSource("sourceA", eventSourceA)
                                                .addEventSource("sourceB", eventSourceB)
                                                .eventComparator(customComparator)
                                                .build();

        eventSourceA.publishMessage(EventTestUtils.asEventMessage("EventA"));
        eventSourceB.publishMessage(EventTestUtils.asEventMessage("EventB"));

        MessageStream<EventMessage> stream = testSubject.open(
                StreamingCondition.startingFrom(null), null
        );

        MessageStream.Entry<EventMessage> first = stream.next().orElseThrow();

        // EventB should come first due to custom comparator
        assertEquals("EventB", first.message().payload());

        stream.close();
    }

    @Test
    void peekDoesNotConsumeMessage() {
        EventMessage event = EventTestUtils.asEventMessage("Event1");
        eventSourceA.publishMessage(event);

        MessageStream<EventMessage> stream = testSubject.open(
                StreamingCondition.startingFrom(null), null
        );

        MessageStream.Entry<EventMessage> peeked = stream.peek().orElseThrow();
        assertEquals("Event1", peeked.message().payload());

        // Message should still be available
        assertTrue(stream.hasNextAvailable());

        MessageStream.Entry<EventMessage> consumed = stream.next().orElseThrow();
        assertEquals("Event1", consumed.message().payload());

        stream.close();
    }

    @Test
    void firstTokenReturnsMultiSourceToken() {
        TrackingToken token = testSubject.firstToken(null).join();

        assertInstanceOf(MultiSourceTrackingToken.class, token);
        MultiSourceTrackingToken multiToken = (MultiSourceTrackingToken) token;

        assertNotNull(multiToken.getTokenForStream("sourceA"));
        assertNotNull(multiToken.getTokenForStream("sourceB"));
    }

    @Test
    void latestTokenReturnsMultiSourceToken() {
        eventSourceA.publishMessage(EventTestUtils.asEventMessage("Event1"));
        eventSourceB.publishMessage(EventTestUtils.asEventMessage("Event2"));

        TrackingToken token = testSubject.latestToken(null).join();

        assertInstanceOf(MultiSourceTrackingToken.class, token);
        MultiSourceTrackingToken multiToken = (MultiSourceTrackingToken) token;

        assertNotNull(multiToken.getTokenForStream("sourceA"));
        assertNotNull(multiToken.getTokenForStream("sourceB"));
    }

    @Test
    void tokenAtReturnsMultiSourceToken() {
        Instant now = Instant.now();
        eventSourceA.publishMessage(EventTestUtils.asEventMessage("Event1"));
        eventSourceB.publishMessage(EventTestUtils.asEventMessage("Event2"));

        TrackingToken token = testSubject.tokenAt(now, null).join();

        assertInstanceOf(MultiSourceTrackingToken.class, token);
        MultiSourceTrackingToken multiToken = (MultiSourceTrackingToken) token;

        assertNotNull(multiToken.getTokenForStream("sourceA"));
        assertNotNull(multiToken.getTokenForStream("sourceB"));
    }

    @Test
    void openWithNullTokenStartsFromBeginning() {
        eventSourceA.publishMessage(EventTestUtils.asEventMessage("Event1"));
        eventSourceB.publishMessage(EventTestUtils.asEventMessage("Event2"));

        MessageStream<EventMessage> stream = testSubject.open(
                StreamingCondition.startingFrom(null), null
        );

        assertTrue(stream.hasNextAvailable());
        assertNotNull(stream.next().orElse(null));

        stream.close();
    }

    @Test
    void openWithMultiSourceTokenStartsFromToken() {
        // Create a token manually representing position after the first event in each source
        Map<String, TrackingToken> tokenMap = new HashMap<>();
        tokenMap.put("sourceA", new GlobalSequenceTrackingToken(1));
        tokenMap.put("sourceB", new GlobalSequenceTrackingToken(1));
        MultiSourceTrackingToken token = new MultiSourceTrackingToken(tokenMap);

        // Publish events - positions 0, 1, 2
        eventSourceA.publishMessage(EventTestUtils.asEventMessage("Event1")); // position 0
        eventSourceA.publishMessage(EventTestUtils.asEventMessage("Event2")); // position 1
        eventSourceA.publishMessage(EventTestUtils.asEventMessage("Event3")); // position 2

        // Open stream with token at position 1 - should only see Event3 (position 2)
        MessageStream<EventMessage> stream = testSubject.open(
                StreamingCondition.startingFrom(token), null
        );

        // Should see Event3
        assertTrue(stream.hasNextAvailable());
        MessageStream.Entry<EventMessage> entry = stream.next().orElseThrow();
        assertEquals("Event3", entry.message().payload());

        stream.close();
    }

    @Test
    void openWithIncompatibleTokenThrowsException() {
        GlobalSequenceTrackingToken incompatibleToken = new GlobalSequenceTrackingToken(0);

        assertThrows(IllegalArgumentException.class, () ->
                testSubject.open(StreamingCondition.startingFrom(incompatibleToken), null)
        );
    }

    @Test
    void trackingTokenIsUpdatedAsMessagesAreConsumed() {
        eventSourceA.publishMessage(EventTestUtils.asEventMessage("Event1"));
        eventSourceB.publishMessage(EventTestUtils.asEventMessage("Event2"));

        MessageStream<EventMessage> stream = testSubject.open(
                StreamingCondition.startingFrom(null), null
        );

        MessageStream.Entry<EventMessage> entry1 = stream.next().orElseThrow();
        TrackingToken token1 = TrackingToken.fromContext(entry1).orElseThrow();

        assertInstanceOf(MultiSourceTrackingToken.class, token1);

        MessageStream.Entry<EventMessage> entry2 = stream.next().orElseThrow();
        TrackingToken token2 = TrackingToken.fromContext(entry2).orElseThrow();

        assertInstanceOf(MultiSourceTrackingToken.class, token2);
        assertNotEquals(token1, token2);

        stream.close();
    }

    @Test
    void streamCompletesWhenAllSourcesComplete() {
        eventSourceA.publishMessage(EventTestUtils.asEventMessage("Event1"));

        MessageStream<EventMessage> stream = testSubject.open(
                StreamingCondition.startingFrom(null), null
        );

        stream.next();

        // Close source streams to complete them
        assertFalse(stream.isCompleted());

        stream.close();
    }

    @Test
    void callbackIsInvokedWhenMessagesAreAvailable() {
        AtomicBoolean callbackInvoked = new AtomicBoolean(false);

        MessageStream<EventMessage> stream = testSubject.open(
                StreamingCondition.startingFrom(null), null
        );

        stream.setCallback(() -> callbackInvoked.set(true));

        eventSourceA.publishMessage(EventTestUtils.asEventMessage("Event1"));
        eventSourceA.runOnAvailableCallback();

        assertTrue(callbackInvoked.get());

        stream.close();
    }

    @Test
    void builderRejectsNonUniqueSourceIds() {
        assertThrows(Exception.class, () ->
                MultiStreamableEventSource.builder()
                                          .addEventSource("source", eventSourceA)
                                          .addEventSource("source", eventSourceB)
                                          .build()
        );
    }

    @Test
    void streamsAreClosedWhenOneSourceFailsToOpen() {
        AtomicBoolean streamClosed = new AtomicBoolean(false);
        AtomicBoolean streamOpened = new AtomicBoolean(false);
        StreamableEventSource source1 = new AsyncInMemoryStreamableEventSource() {
            @Override
            public MessageStream<EventMessage> open(@Nonnull StreamingCondition condition,
                                                    @Nullable ProcessingContext context) {
                if (streamOpened.compareAndSet(false, true)) {
                    return super.open(condition, context).onClose(() -> streamClosed.set(true));
                }
                throw new RuntimeException("Simulating failure in second stream");
            }
        };
        StreamableEventSource source2 = new AsyncInMemoryStreamableEventSource() {
            @Override
            public MessageStream<EventMessage> open(@Nonnull StreamingCondition condition,
                                                    @Nullable ProcessingContext context) {
                if (streamOpened.compareAndSet(false, true)) {
                    return super.open(condition, context).onClose(() -> streamClosed.set(true));
                }
                throw new RuntimeException("Simulating failure in second stream");
            }
        };

        testSubject = MultiStreamableEventSource.builder()
                                                .addEventSource("source1", source1)
                                                .addEventSource("source2", source2)
                                                .build();

        // Create a MultiSourceTrackingToken with both source tokens
        Map<String, TrackingToken> tokenMap = new HashMap<>();
        tokenMap.put("source1", new GlobalSequenceTrackingToken(0));
        tokenMap.put("source2", new GlobalSequenceTrackingToken(0));
        MultiSourceTrackingToken token = new MultiSourceTrackingToken(tokenMap);

        // Opening should throw when source2 fails
        assertThrows(RuntimeException.class, () ->
                testSubject.open(StreamingCondition.startingFrom(token), null)
        );

        // Verify that source1's stream was closed due to the failure
        assertTrue(streamClosed.get(), "Stream from source1 should be closed when source2 fails to open");
    }

    @Test
    void emptyStreamReturnsNoMessages() {
        MessageStream<EventMessage> stream = testSubject.open(
                StreamingCondition.startingFrom(null), null
        );

        assertFalse(stream.hasNextAvailable());
        assertTrue(stream.next().isEmpty());

        stream.close();
    }

    @Test
    void multipleStreamsCanBeOpenedConcurrently() {
        eventSourceA.publishMessage(EventTestUtils.asEventMessage("Event1"));
        eventSourceB.publishMessage(EventTestUtils.asEventMessage("Event2"));

        MessageStream<EventMessage> stream1 = testSubject.open(
                StreamingCondition.startingFrom(null), null
        );
        MessageStream<EventMessage> stream2 = testSubject.open(
                StreamingCondition.startingFrom(null), null
        );

        assertNotNull(stream1.next().orElse(null));
        assertNotNull(stream2.next().orElse(null));

        stream1.close();
        stream2.close();
    }
}
