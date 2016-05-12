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

package org.axonframework.eventstore;

import org.axonframework.eventstore.inmemory.InMemoryEventStorageEngine;
import org.junit.Before;

import static org.mockito.Mockito.spy;

/**
 * @author Rene de Waele
 */
public class EmbeddedEventStoreTest {

    private EventStore subject;
    private EventStorageEngine storageEngine;

    @Before
    public void setUp() {
        storageEngine = spy(new InMemoryEventStorageEngine());
        subject = new EmbeddedEventStore(storageEngine);
    }

//    @Test
//    public void testExistingEventIsPassedToReader() throws InterruptedException {
//        subject.publish(createEvent());
//        Thread t = new Thread(() -> {
//            Optional<? extends TrackedEventMessage<?>> first = subject.streamEvents(null).findFirst();
//            assertTrue(first.isPresent());
//        });
//        t.start();
//        t.join();
//    }
//
//    @Test
//    public void testNewEventIsPassedToReader() throws InterruptedException {
//        Stream<? extends TrackedEventMessage<?>> stream = subject.streamEvents(null);
//        Thread t = new Thread(() -> {
//            Optional<? extends TrackedEventMessage<?>> first = stream.findFirst();
//            assertTrue(first.isPresent());
//        });
//        t.start();
//        subject.publish(createEvent());
//        t.join();
//    }
//
//    @Test
//    public void testReadingIsBlockedWhenStoreIsEmpty() throws InterruptedException {
//        CountDownLatch lock = new CountDownLatch(1);
//        Stream<? extends TrackedEventMessage<?>> stream = subject.streamEvents(null);
//        Thread t = new Thread(() -> {
//            stream.findFirst();
//            lock.countDown();
//        });
//        t.start();
//        assertFalse(lock.await(100, TimeUnit.MILLISECONDS));
//        subject.publish(createEvent());
//        t.join();
//        assertEquals(0, lock.getCount());
//    }
//
//    @Test
//    public void testReadingIsBlockedWhenEndOfStreamIsReached() throws InterruptedException {
//        subject.publish(createEvent());
//        CountDownLatch lock = new CountDownLatch(2);
//        Stream<? extends TrackedEventMessage<?>> stream = subject.streamEvents(null);
//        Thread t = new Thread(() -> {
//            stream.limit(2).forEach(event -> lock.countDown());
//        });
//        t.start();
//        assertFalse(lock.await(100, TimeUnit.MILLISECONDS));
//        assertEquals(1, lock.getCount());
//        subject.publish(createEvent());
//        t.join();
//        assertEquals(0, lock.getCount());
//    }
//
//    @Test
//    public void testReadingCanBeContinuedUsingLastToken() {
//        subject.publish(createEvents(2));
//        Optional<? extends TrackedEventMessage<?>> first = subject.streamEvents(null).findFirst();
//        assertTrue(first.isPresent());
//        TrackingToken firstToken = first.get().trackingToken();
//        Optional<? extends TrackedEventMessage<?>> second = subject.streamEvents(firstToken).findFirst();
//        assertTrue(second.isPresent());
//        assertTrue(second.get().trackingToken().isAfter(firstToken));
//    }
//
//    @Test
//    public void testEventIsFetchedFromCacheWhenFetchedASecondTime() throws InterruptedException {
//        CountDownLatch lock = new CountDownLatch(2);
//        List<TrackedEventMessage<?>> events = new CopyOnWriteArrayList<>();
//        Thread t = new Thread(() -> {
//            subject.streamEvents(null).limit(2).forEach(event -> {
//                lock.countDown();
//                events.add(event);
//            });
//        });
//        t.start();
//        subject.publish(createEvents(2));
//
//        t.join();
//        Optional<? extends TrackedEventMessage<?>> secondEvent = subject
//                .streamEvents(events.get(0).trackingToken()).findFirst();
//        assertTrue(secondEvent.isPresent());
//        assertEquals(events.get(1), secondEvent.get());
//        verify(storageEngine).readEvents((TrackingToken) null);
//    }


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
