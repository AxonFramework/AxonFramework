/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.eventsourcing;

import org.axonframework.common.stream.BlockingStream;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.MultiSourceTrackingToken;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.eventsourcing.utils.MockException;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.StreamableMessageSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MultiStreamableMessageSourceTest {

    private MultiStreamableMessageSource testSubject;

    private EmbeddedEventStore eventStoreA;
    private EmbeddedEventStore eventStoreB;

    @BeforeEach
    void setUp() {
        eventStoreA = EmbeddedEventStore.builder().storageEngine(new InMemoryEventStorageEngine()).build();
        eventStoreB = EmbeddedEventStore.builder().storageEngine(new InMemoryEventStorageEngine()).build();

        testSubject = MultiStreamableMessageSource.builder()
                                                  .addMessageSource("eventStoreA", eventStoreA)
                                                  .addMessageSource("eventStoreB", eventStoreB)
                                                  .longPollingSource("eventStoreA")
                                                  .build();
    }

    @Test
    void simplePublishAndConsume() throws InterruptedException {
        EventMessage publishedEvent = GenericEventMessage.asEventMessage("Event1");

        eventStoreA.publish(publishedEvent);

        BlockingStream<TrackedEventMessage<?>> singleEventStream = testSubject.openStream(testSubject.createTailToken());

        assertTrue(singleEventStream.hasNextAvailable());
        assertEquals(publishedEvent.getPayload(), singleEventStream.nextAvailable().getPayload());

        singleEventStream.close();
    }

    @Test
    void testConnectionsAreClosedWhenOpeningFails() {
        StreamableMessageSource<TrackedEventMessage<?>> source1 = mock(StreamableMessageSource.class);
        StreamableMessageSource<TrackedEventMessage<?>> source2 = mock(StreamableMessageSource.class);
        testSubject = MultiStreamableMessageSource.builder()
                                                  .addMessageSource("source1", source1)
                                                  .addMessageSource("source2", source2)
                                                  .build();
        BlockingStream<TrackedEventMessage<?>> mockStream = mock(BlockingStream.class);
        when(source1.openStream(any())).thenReturn(mockStream);
        when(source2.openStream(any())).thenThrow(new MockException());

        assertThrows(MockException.class, () ->
                testSubject.openStream(null));

        verify(mockStream).close();
        verify(source1).openStream(null);
        verify(source2).openStream(null);
    }

    @Test
    void simplePublishAndConsumeDomainEventMessage() throws InterruptedException {
        EventMessage<?> publishedEvent = new GenericDomainEventMessage<>("Aggregate", "id", 0, "Event1");

        eventStoreA.publish(publishedEvent);
        BlockingStream<TrackedEventMessage<?>> singleEventStream = testSubject.openStream(testSubject.createTailToken());

        assertTrue(singleEventStream.hasNextAvailable());
        TrackedEventMessage<?> actual = singleEventStream.nextAvailable();

        assertEquals(publishedEvent.getPayload(), actual.getPayload());
        assertTrue(actual instanceof DomainEventMessage);

        singleEventStream.close();
    }

    @Test
    void testPeekingLastMessageKeepsItAvailable() throws InterruptedException {
        EventMessage<?> publishedEvent1 = GenericEventMessage.asEventMessage("Event1");


        eventStoreA.publish(publishedEvent1);

        BlockingStream<TrackedEventMessage<?>> stream = testSubject.openStream(null);
        assertEquals("Event1", stream.peek().map(Message::getPayload).map(Object::toString).orElse("None"));
        assertTrue(stream.hasNextAvailable());
        assertTrue(stream.hasNextAvailable(10, TimeUnit.SECONDS));
    }

    @Test
    void openStreamWithWrongToken() {
        assertThrows(IllegalArgumentException.class, () -> testSubject.openStream(new GlobalSequenceTrackingToken(0L)));
    }

    @Test
    void openStreamWithNullTokenReturnsFirstEvent() throws InterruptedException {
        EventMessage<Object> message = GenericEventMessage.asEventMessage("Event1");
        eventStoreA.publish(message);

        BlockingStream<TrackedEventMessage<?>> actual = testSubject.openStream(null);
        assertNotNull(actual);
        TrackedEventMessage<?> trackedEventMessage = actual.nextAvailable();
        assertEquals(message.getIdentifier(), trackedEventMessage.getIdentifier());
        assertEquals(message.getPayload(), trackedEventMessage.getPayload());
    }

    @Test
    void longPoll() throws InterruptedException {
        BlockingStream<TrackedEventMessage<?>> singleEventStream = testSubject.openStream(testSubject
                                                                                                  .createTokenAt(Instant.now()));

        long beforePollTime = System.currentTimeMillis();
        assertFalse(singleEventStream.hasNextAvailable(100, TimeUnit.MILLISECONDS));
        long pollTime = System.currentTimeMillis() - beforePollTime;
        // allow for some deviation in polling time
        assertTrue(pollTime > 80, "Poll time too short: " + pollTime + "ms");
        assertTrue(pollTime < 120, "Poll time too long: " + pollTime + "ms");

        singleEventStream.close();
    }

    @Test
    void longPollMessageImmediatelyAvailable() throws InterruptedException {
        BlockingStream<TrackedEventMessage<?>> singleEventStream = testSubject.openStream(testSubject
                                                                                                  .createTokenAt(Instant.now()));

        EventMessage pubToStreamB = GenericEventMessage.asEventMessage("Event1");
        eventStoreB.publish(pubToStreamB);

        long beforePollTime = System.currentTimeMillis();
        boolean hasNextAvailable = singleEventStream.hasNextAvailable(100, TimeUnit.MILLISECONDS);
        long afterPollTime = System.currentTimeMillis();
        assertTrue(hasNextAvailable);
        assertTrue(afterPollTime - beforePollTime < 10);

        singleEventStream.close();
    }

    @Test
    void multiPublishAndConsume() throws InterruptedException {
        EventMessage pubToStreamA = GenericEventMessage.asEventMessage("Event1");
        eventStoreA.publish(pubToStreamA);

        Thread.sleep(20);

        EventMessage pubToStreamB = GenericEventMessage.asEventMessage("Event2");
        eventStoreB.publish(pubToStreamB);

        BlockingStream<TrackedEventMessage<?>> singleEventStream = testSubject.openStream(testSubject
                                                                                                  .createTokenAt(Instant.now()));

        assertTrue(singleEventStream.hasNextAvailable());

        //order published must be same as order consumed
        assertEquals(pubToStreamA.getPayload(), singleEventStream.nextAvailable().getPayload());
        assertEquals(pubToStreamB.getPayload(), singleEventStream.nextAvailable().getPayload());
        assertFalse(singleEventStream.hasNextAvailable());

        singleEventStream.close();
    }

    @Test
    void peek() throws InterruptedException {
        EventMessage publishedEvent = GenericEventMessage.asEventMessage("Event1");

        eventStoreA.publish(publishedEvent);

        BlockingStream<TrackedEventMessage<?>> singleEventStream = testSubject.openStream(testSubject
                                                                                                  .createTokenAt(Instant.now()));

        assertTrue(singleEventStream.peek().isPresent());
        assertEquals(publishedEvent.getPayload(), singleEventStream.peek().get().getPayload());

        //message is still consumable
        assertEquals(publishedEvent.getPayload(), singleEventStream.nextAvailable().getPayload());

        singleEventStream.close();
    }

    @Test
    void peekWithMultipleStreams() throws InterruptedException {
        EventMessage pubToStreamA = GenericEventMessage.asEventMessage("Event1");
        eventStoreA.publish(pubToStreamA);

        Thread.sleep(20);

        EventMessage pubToStreamB = GenericEventMessage.asEventMessage("Event2");
        eventStoreB.publish(pubToStreamB);

        BlockingStream<TrackedEventMessage<?>> singleEventStream = testSubject.openStream(testSubject
                                                                                                  .createTokenAt(Instant.now()));

        assertTrue(singleEventStream.peek().isPresent());
        TrackedEventMessage peekedMessageA = singleEventStream.peek().get();
        MultiSourceTrackingToken tokenA = (MultiSourceTrackingToken) peekedMessageA.trackingToken();
        assertEquals(pubToStreamA.getPayload(), peekedMessageA.getPayload());

        //message is still consumable and consumed message equal to peeked
        assertEquals(peekedMessageA.getPayload(), singleEventStream.nextAvailable().getPayload());

        //peek and consume another
        assertTrue(singleEventStream.peek().isPresent());
        TrackedEventMessage peekedMessageB = singleEventStream.peek().get();
        MultiSourceTrackingToken tokenB = (MultiSourceTrackingToken) peekedMessageB.trackingToken();
        assertEquals(pubToStreamB.getPayload(), peekedMessageB.getPayload());

        assertEquals(peekedMessageB.getPayload(), singleEventStream.nextAvailable().getPayload());

        //consuming from second stream doesn't alter token from first stream
        assertEquals(tokenA.getTokenForStream("eventStoreA"), tokenB.getTokenForStream("eventStoreA"));

        singleEventStream.close();
    }

    @Test
    void createTailToken() {
        EventMessage pubToStreamA = GenericEventMessage.asEventMessage("Event1");
        eventStoreA.publish(pubToStreamA);

        EventMessage pubToStreamB = GenericEventMessage.asEventMessage("Event2");
        eventStoreB.publish(pubToStreamB);

        MultiSourceTrackingToken tailToken = testSubject.createTailToken();

        assertEquals(-1L, tailToken.getTokenForStream("eventStoreA").position().getAsLong());
        assertEquals(-1L, tailToken.getTokenForStream("eventStoreB").position().getAsLong());
    }

    @Test
    void createHeadToken() {
        EventMessage pubToStreamA = GenericEventMessage.asEventMessage("Event1");
        eventStoreA.publish(pubToStreamA);

        EventMessage pubToStreamB = GenericEventMessage.asEventMessage("Event2");
        eventStoreB.publish(pubToStreamB);
        eventStoreB.publish(pubToStreamB);

        MultiSourceTrackingToken headToken = testSubject.createHeadToken();

        assertEquals(0L, headToken.getTokenForStream("eventStoreA").position().getAsLong());
        assertEquals(1L, headToken.getTokenForStream("eventStoreB").position().getAsLong());
    }

    @Test
    void createTokenAt() throws InterruptedException {
        EventMessage pubToStreamA = GenericEventMessage.asEventMessage("Event1");
        eventStoreA.publish(pubToStreamA);
        eventStoreA.publish(pubToStreamA);

        Thread.sleep(20);

        EventMessage pubToStreamB = GenericEventMessage.asEventMessage("Event2");
        eventStoreB.publish(pubToStreamB);

        MultiSourceTrackingToken createdAtToken = testSubject.createTokenAt(Instant.now().minus(10, ChronoUnit.MILLIS));
        //token should track events in eventStoreB and skip those in eventStoreA
        assertNull(createdAtToken.getTokenForStream("eventStoreA"));
        assertEquals(-1L, createdAtToken.getTokenForStream("eventStoreB").position().getAsLong());
    }

    @Test
    void createTokenSince() throws InterruptedException {
        EventMessage pubToStreamA = GenericEventMessage.asEventMessage("Event1");
        eventStoreA.publish(pubToStreamA);
        eventStoreA.publish(pubToStreamA);

        Thread.sleep(20);

        EventMessage pubToStreamB = GenericEventMessage.asEventMessage("Event2");
        eventStoreB.publish(pubToStreamB);

        MultiSourceTrackingToken createdSinceToken = testSubject.createTokenSince(Duration.ofMillis(10));
        //token should track events in eventStoreB and skip those in eventStoreA
        assertNull(createdSinceToken.getTokenForStream("eventStoreA"));
        assertEquals(-1L, createdSinceToken.getTokenForStream("eventStoreB").position().getAsLong());
    }

    @Test
    void configuredDifferentComparator() throws InterruptedException {
        Comparator<Map.Entry<String, TrackedEventMessage<?>>> eventStoreAPriority =
                Comparator.comparing((Map.Entry<String, TrackedEventMessage<?>> e) -> !e.getKey().equals("eventStoreA")).
                        thenComparing(e -> e.getValue().getTimestamp());

        EmbeddedEventStore eventStoreC = EmbeddedEventStore.builder().storageEngine(new InMemoryEventStorageEngine())
                                                           .build();

        MultiStreamableMessageSource prioritySourceTestSubject =
                MultiStreamableMessageSource.builder()
                                            .addMessageSource("eventStoreA", eventStoreA)
                                            .addMessageSource("eventStoreB", eventStoreB)
                                            .addMessageSource("eventStoreC", eventStoreC)
                                            .trackedEventComparator(eventStoreAPriority)
                                            .build();

        EventMessage pubToStreamA = GenericEventMessage.asEventMessage("Event1");
        eventStoreA.publish(pubToStreamA);
        eventStoreA.publish(pubToStreamA);
        eventStoreA.publish(pubToStreamA);

        EventMessage pubToStreamC = GenericEventMessage.asEventMessage("Event2");
        eventStoreC.publish(pubToStreamC);

        Thread.sleep(5);

        EventMessage pubToStreamB = GenericEventMessage.asEventMessage("Event3");
        eventStoreB.publish(pubToStreamB);

        BlockingStream<TrackedEventMessage<?>> singleEventStream = prioritySourceTestSubject.openStream(
                prioritySourceTestSubject.createTailToken());

        singleEventStream.nextAvailable();
        singleEventStream.nextAvailable();
        singleEventStream.nextAvailable();
        assertTrue(singleEventStream.nextAvailable().getPayload().equals(pubToStreamC.getPayload()));
        assertTrue(singleEventStream.nextAvailable().getPayload().equals(pubToStreamB.getPayload()));
    }
}
