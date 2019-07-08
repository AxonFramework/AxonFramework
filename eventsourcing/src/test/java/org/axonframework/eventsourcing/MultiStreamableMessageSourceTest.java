package org.axonframework.eventsourcing;

import org.axonframework.common.stream.BlockingStream;
import org.axonframework.eventhandling.*;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class MultiStreamableMessageSourceTest {

    private MultiStreamableMessageSource testSubject;

    private EmbeddedEventStore eventStoreA;
    private EmbeddedEventStore eventStoreB;

    @Before
    public void setUp() {
        eventStoreA = EmbeddedEventStore.builder().storageEngine(new InMemoryEventStorageEngine()).build();
        eventStoreB = EmbeddedEventStore.builder().storageEngine(new InMemoryEventStorageEngine()).build();

        testSubject = MultiStreamableMessageSource.builder()
                                                  .addMessageSource("eventStoreA", eventStoreA)
                                                  .addMessageSource("eventStoreB", eventStoreB)
                                                  .longPollingSource("eventStoreA")
                                                  .build();
    }

    @Test
    public void simplePublishAndConsume() throws InterruptedException {
        EventMessage publishedEvent = GenericEventMessage.asEventMessage("Event1");

        eventStoreA.publish(publishedEvent);

        BlockingStream<TrackedEventMessage<?>> singleEventStream = testSubject.openStream(testSubject
                                                                                                  .createTokenAt(Instant.now()));

        assertTrue(singleEventStream.hasNextAvailable());
        assertEquals(publishedEvent.getPayload(), singleEventStream.nextAvailable().getPayload());

        singleEventStream.close();
    }

    @Test(expected = IllegalArgumentException.class)
    public void openStreamWithWrongToken() throws InterruptedException {
        testSubject.openStream(new GlobalSequenceTrackingToken(0L));
    }

    @Test
    public void longPoll() throws InterruptedException {
        BlockingStream<TrackedEventMessage<?>> singleEventStream = testSubject.openStream(testSubject
                                                                                                  .createTokenAt(Instant.now()));

        long beforePollTime = System.currentTimeMillis();
        assertFalse(singleEventStream.hasNextAvailable(100, TimeUnit.MILLISECONDS));
        long afterPollTime = System.currentTimeMillis();
        assertTrue(afterPollTime - beforePollTime > 90);
        assertTrue(afterPollTime - beforePollTime < 105);

        singleEventStream.close();
    }

    @Test
    public void longPollMessageImmediatelyAvailable() throws InterruptedException {
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
    public void multiPublishAndConsume() throws InterruptedException {
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
    public void peek() throws InterruptedException {
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
    public void peekWithMultipleStreams() throws InterruptedException {
        EventMessage pubToStreamA = GenericEventMessage.asEventMessage("Event1");
        eventStoreA.publish(pubToStreamA);

        Thread.sleep(20);

        EventMessage pubToStreamB = GenericEventMessage.asEventMessage("Event2");
        eventStoreB.publish(pubToStreamB);

        BlockingStream<TrackedEventMessage<?>> singleEventStream = testSubject.openStream(testSubject
                                                                                                  .createTokenAt(Instant.now()));

        assertTrue(singleEventStream.peek().isPresent());
        TrackedEventMessage peekedMessageA = singleEventStream.peek().get();
        assertEquals(pubToStreamA.getPayload(), peekedMessageA.getPayload());

        //message is still consumable and consumed message equal to peeked
        assertEquals(peekedMessageA.getPayload(), singleEventStream.nextAvailable().getPayload());

        //peek and consume another
        assertTrue(singleEventStream.peek().isPresent());
        TrackedEventMessage peekedMessageB = singleEventStream.peek().get();
        assertEquals(pubToStreamB.getPayload(), peekedMessageB.getPayload());

        assertEquals(peekedMessageB.getPayload(), singleEventStream.nextAvailable().getPayload());

        singleEventStream.close();
    }

    @Test
    public void createTailToken() {
        EventMessage pubToStreamA = GenericEventMessage.asEventMessage("Event1");
        eventStoreA.publish(pubToStreamA);

        EventMessage pubToStreamB = GenericEventMessage.asEventMessage("Event2");
        eventStoreB.publish(pubToStreamB);

        MultiSourceTrackingToken tailToken = testSubject.createTailToken();

        assertEquals(-1L, tailToken.getTokenForStream("eventStoreA").position().getAsLong());
        assertEquals(-1L, tailToken.getTokenForStream("eventStoreB").position().getAsLong());
    }

    @Test
    public void createHeadToken() {
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
    public void createTokenAt() throws InterruptedException {
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
    public void createTokenSince() throws InterruptedException {
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
    public void configuredDifferentComparator() throws InterruptedException {
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
