/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.fail;
import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.createEvent;
import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.createEvents;
import static org.axonframework.eventsourcing.eventstore.EventUtils.asStream;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Rene de Waele
 */
public class EmbeddedEventStoreTest {

    private static final int CACHED_EVENTS = 10;
    private static final long FETCH_DELAY = 1000;
    private static final long CLEANUP_DELAY = 10000;

    private EmbeddedEventStore testSubject;
    private EventStorageEngine storageEngine;

    @Before
    public void setUp() {
        storageEngine = spy(new InMemoryEventStorageEngine());
        newTestSubject(CACHED_EVENTS, FETCH_DELAY, CLEANUP_DELAY);
    }

    private void newTestSubject(int cachedEvents, long fetchDelay, long cleanupDelay) {
        Optional.ofNullable(testSubject).ifPresent(EmbeddedEventStore::shutDown);
        testSubject = new EmbeddedEventStore(storageEngine, cachedEvents, fetchDelay, cleanupDelay, MILLISECONDS);
        testSubject.initialize();
    }

    @After
    public void tearDown() {
        testSubject.shutDown();
    }

    @Test
    public void testExistingEventIsPassedToReader() throws Exception {
        DomainEventMessage<?> expected = createEvent();
        testSubject.publish(expected);
        TrackingEventStream stream = testSubject.streamEvents(null);
        assertTrue(stream.hasNextAvailable());
        TrackedEventMessage<?> actual = stream.nextAvailable();
        assertEquals(expected.getIdentifier(), actual.getIdentifier());
        assertEquals(expected.getPayload(), actual.getPayload());
        assertTrue(actual instanceof DomainEventMessage<?>);
        assertEquals(expected.getAggregateIdentifier(), ((DomainEventMessage<?>) actual).getAggregateIdentifier());
    }

    @Test(timeout = FETCH_DELAY / 10)
    public void testEventPublishedAfterOpeningStreamIsPassedToReaderImmediately() throws Exception {
        TrackingEventStream stream = testSubject.streamEvents(null);
        assertFalse(stream.hasNextAvailable());
        DomainEventMessage<?> expected = createEvent();
        Thread t = new Thread(() -> {
            try {
                assertEquals(expected.getIdentifier(), stream.nextAvailable().getIdentifier());
            } catch (InterruptedException e) {
                fail();
            }
        });
        t.start();
        testSubject.publish(expected);
        t.join();
    }

    @Test(timeout = 1000)
    public void testReadingIsBlockedWhenStoreIsEmpty() throws Exception {
        CountDownLatch lock = new CountDownLatch(1);
        TrackingEventStream stream = testSubject.streamEvents(null);
        Thread t = new Thread(() -> asStream(stream).findFirst().ifPresent(event -> lock.countDown()));
        t.start();
        assertFalse(lock.await(100, MILLISECONDS));
        testSubject.publish(createEvent());
        t.join();
        assertEquals(0, lock.getCount());
    }

    @Test(timeout = 1000)
    public void testReadingIsBlockedWhenEndOfStreamIsReached() throws Exception {
        testSubject.publish(createEvent());
        CountDownLatch lock = new CountDownLatch(2);
        TrackingEventStream stream = testSubject.streamEvents(null);
        Thread t = new Thread(() -> asStream(stream).limit(2).forEach(event -> lock.countDown()));
        t.start();
        assertFalse(lock.await(100, MILLISECONDS));
        assertEquals(1, lock.getCount());
        testSubject.publish(createEvent());
        t.join();
        assertEquals(0, lock.getCount());
    }

    @Test(timeout = 1000)
    public void testReadingCanBeContinuedUsingLastToken() throws Exception {
        testSubject.publish(createEvents(2));
        TrackedEventMessage<?> first = testSubject.streamEvents(null).nextAvailable();
        TrackingToken firstToken = first.trackingToken();
        TrackedEventMessage<?> second = testSubject.streamEvents(firstToken).nextAvailable();
        assertTrue(second.trackingToken().isAfter(firstToken));
    }

    @Test(timeout = 1000)
    public void testEventIsFetchedFromCacheWhenFetchedASecondTime() throws Exception {
        CountDownLatch lock = new CountDownLatch(2);
        List<TrackedEventMessage<?>> events = new CopyOnWriteArrayList<>();
        Thread t = new Thread(() -> {
            asStream(testSubject.streamEvents(null)).limit(2).forEach(event -> {
                lock.countDown();
                events.add(event);
            });
        });
        t.start();
        assertFalse(lock.await(100, MILLISECONDS));
        testSubject.publish(createEvents(2));
        t.join();
        reset(storageEngine);
        TrackedEventMessage<?> second = testSubject.streamEvents(events.get(0).trackingToken()).nextAvailable();
        assertSame(events.get(1), second);
        verifyNoMoreInteractions(storageEngine);
    }

    @Test(timeout = 1000)
    public void testPeriodicPollingWhenEventStorageIsUpdatedIndependently() throws Exception {
        newTestSubject(CACHED_EVENTS, 20, CLEANUP_DELAY);
        TrackingEventStream stream = testSubject.streamEvents(null);
        CountDownLatch lock = new CountDownLatch(1);
        Thread t = new Thread(() -> asStream(stream).findFirst().ifPresent(event -> lock.countDown()));
        t.start();
        assertFalse(lock.await(100, MILLISECONDS));
        storageEngine.appendEvents(createEvent());
        t.join();
        assertTrue(lock.await(100, MILLISECONDS));
    }

    @Test(timeout = 1000)
    public void testConsumerStopsTailingWhenItFallsBehindTheCache() throws Exception {
        newTestSubject(CACHED_EVENTS, FETCH_DELAY, 20);
        TrackingEventStream stream = testSubject.streamEvents(null);
        assertFalse(stream.hasNextAvailable()); //now we should be tailing
        testSubject.publish(createEvents(CACHED_EVENTS)); //triggers event producer to open a stream
        Thread.sleep(100);
        reset(storageEngine);
        assertTrue(stream.hasNextAvailable());
        TrackedEventMessage<?> firstEvent = stream.nextAvailable();
        verifyZeroInteractions(storageEngine);
        testSubject.publish(createEvent(CACHED_EVENTS), createEvent(CACHED_EVENTS + 1));
        Thread.sleep(100); //allow the cleaner thread to evict the consumer
        reset(storageEngine);
        assertTrue(stream.hasNextAvailable());
        verify(storageEngine).readEvents(firstEvent.trackingToken(), false);
    }

    //TODO test non-tracking features of event store

//    @Test
//    @Transactional
//    public void testLoad_LargeAmountOfEventsWithSnapshot() {
//        testSubject.appendEvents(createEvents(110));
//        testSubject.storeSnapshot(createEvent(30));
//        entityManager.flush();
//        entityManager.clear();
//
//        DomainEventStream events = testSubject.readEvents(aggregateIdentifier);
//        long t = 30L;
//        while (events.hasNext()) {
//            DomainEventMessage event = events.next();
//            assertEquals(t, event.getSequenceNumber());
//            t++;
//        }
//        assertEquals(110L, t);
//    }
//
//    @Test
//    @Transactional
//    public void testLoadWithSnapshotEvent() {
//        testSubject.appendEvents(new GenericDomainEventMessage<>(type, "id", 0, "payload"),
//                                 new GenericDomainEventMessage<>(type, "id", 1, "payload"),
//                                 new GenericDomainEventMessage<>(type, "id", 2, "payload"),
//                                 new GenericDomainEventMessage<>(type, "id", 3, "payload"));
//        entityManager.flush();
//        entityManager.clear();
//        testSubject.appendSnapshotEvent(new GenericDomainEventMessage<>(type, "id", 3, "snapshot"));
//        entityManager.flush();
//        entityManager.clear();
//        testSubject.appendEvents(new GenericDomainEventMessage<>(type, "id", 4, "payload"));
//
//        DomainEventStream actualEventStream = testSubject.readEvents("id");
//        List<DomainEventMessage> domainEvents = new ArrayList<>();
//        while (actualEventStream.hasNext()) {
//            DomainEventMessage next = actualEventStream.next();
//            domainEvents.add(next);
//            assertEquals("id", next.getAggregateIdentifier());
//        }
//
//        assertEquals(2, domainEvents.size());
//    }

//    @Test
//    @Transactional
//    public void testEntireStreamIsReadOnUnserializableSnapshot_WithException() {
//        testSubject.appendEvents(createEvents(110));
//
//        entityManager.persist(new SnapshotEventEntry(createEvent(30), StubSerializer.EXCEPTION_ON_SERIALIZATION));
//        entityManager.flush();
//        entityManager.clear();
//
//        assertEquals(0L, testSubject.readEvents(AGGREGATE).findFirst().get().getSequenceNumber());
//    }
//
//    @Test
//    @Transactional
//    public void testEntireStreamIsReadOnUnserializableSnapshot_WithError() {
//        testSubject.appendEvents(createEvents(110));
//
//        entityManager.persist(new SnapshotEventEntry(createEvent(30), StubSerializer.ERROR_ON_SERIALIZATION));
//        entityManager.flush();
//        entityManager.clear();
//
//        assertEquals(0L, testSubject.readEvents(AGGREGATE).findFirst().get().getSequenceNumber());
//    }
//
//    @DirtiesContext
//    @Test
//    @Transactional
//    public void testPrunesSnapshotsWhenNumberOfSnapshotsExceedsConfiguredMaxSnapshotsArchived() {
//        testSubject.setMaxSnapshotsArchived(1);
//
//        testSubject.appendEvents(new GenericDomainEventMessage<>(type, "id", 0, "payload"),
//                                 new GenericDomainEventMessage<>(type, "id", 1, "payload"),
//                                 new GenericDomainEventMessage<>(type, "id", 2, "payload"),
//                                 new GenericDomainEventMessage<>(type, "id", 3, "payload"));
//        entityManager.flush();
//        entityManager.clear();
//
//        testSubject.appendSnapshotEvent(new GenericDomainEventMessage<>(type, "id", 3, "snapshot"));
//        entityManager.flush();
//        entityManager.clear();
//
//        testSubject.appendEvents(new GenericDomainEventMessage<>(type, "id", 4, "payload"));
//        entityManager.flush();
//        entityManager.clear();
//
//        testSubject.appendSnapshotEvent(new GenericDomainEventMessage<>(type, "id", 4, "snapshot"));
//        entityManager.flush();
//        entityManager.clear();
//
//        @SuppressWarnings({"unchecked"})
//        List<SnapshotEventEntry> snapshots =
//                entityManager.createQuery("SELECT e FROM SnapshotEventEntry e "
//                                                  + "WHERE e.aggregateIdentifier = :aggregateIdentifier")
//                        .setParameter("aggregateIdentifier", "id")
//                        .getResultList();
//        assertEquals("archived snapshot count", 1L, snapshots.size());
//        assertEquals("archived snapshot sequence", 4L, snapshots.iterator().next().getSequenceNumber());
//    }
}
