/*
 * Copyright (c) 2010-2016. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.common.MockException;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.fail;
import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.*;
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
        testSubject = new EmbeddedEventStore(storageEngine, NoOpMessageMonitor.INSTANCE, cachedEvents, fetchDelay,
                                             cleanupDelay, MILLISECONDS);
    }

    @After
    public void tearDown() {
        testSubject.shutDown();
    }

    @Test
    public void testExistingEventIsPassedToReader() throws Exception {
        DomainEventMessage<?> expected = createEvent();
        testSubject.publish(expected);
        TrackingEventStream stream = testSubject.openStream(null);
        assertTrue(stream.hasNextAvailable());
        TrackedEventMessage<?> actual = stream.nextAvailable();
        assertEquals(expected.getIdentifier(), actual.getIdentifier());
        assertEquals(expected.getPayload(), actual.getPayload());
        assertTrue(actual instanceof DomainEventMessage<?>);
        assertEquals(expected.getAggregateIdentifier(), ((DomainEventMessage<?>) actual).getAggregateIdentifier());
    }

    @Test(timeout = FETCH_DELAY / 10)
    public void testEventPublishedAfterOpeningStreamIsPassedToReaderImmediately() throws Exception {
        TrackingEventStream stream = testSubject.openStream(null);
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

    @Test(timeout = 5000)
    public void testReadingIsBlockedWhenStoreIsEmpty() throws Exception {
        CountDownLatch lock = new CountDownLatch(1);
        TrackingEventStream stream = testSubject.openStream(null);
        Thread t = new Thread(() -> stream.asStream().findFirst().ifPresent(event -> lock.countDown()));
        t.start();
        assertFalse(lock.await(100, MILLISECONDS));
        testSubject.publish(createEvent());
        t.join();
        assertEquals(0, lock.getCount());
    }

    @Test(timeout = 5000)
    public void testReadingIsBlockedWhenEndOfStreamIsReached() throws Exception {
        testSubject.publish(createEvent());
        CountDownLatch lock = new CountDownLatch(2);
        TrackingEventStream stream = testSubject.openStream(null);
        Thread t = new Thread(() -> stream.asStream().limit(2).forEach(event -> lock.countDown()));
        t.start();
        assertFalse(lock.await(100, MILLISECONDS));
        assertEquals(1, lock.getCount());
        testSubject.publish(createEvent());
        t.join();
        assertEquals(0, lock.getCount());
    }

    @Test(timeout = 5000)
    public void testReadingCanBeContinuedUsingLastToken() throws Exception {
        List<? extends EventMessage<?>> events = createEvents(2);
        testSubject.publish(events);
        TrackedEventMessage<?> first = testSubject.openStream(null).nextAvailable();
        TrackingToken firstToken = first.trackingToken();
        TrackedEventMessage<?> second = testSubject.openStream(firstToken).nextAvailable();
        assertEquals(events.get(0).getIdentifier(), first.getIdentifier());
        assertEquals(events.get(1).getIdentifier(), second.getIdentifier());
    }

    @Test(timeout = 5000)
    public void testEventIsFetchedFromCacheWhenFetchedASecondTime() throws Exception {
        CountDownLatch lock = new CountDownLatch(2);
        List<TrackedEventMessage<?>> events = new CopyOnWriteArrayList<>();
        Thread t = new Thread(() -> testSubject.openStream(null).asStream().limit(2).forEach(event -> {
            lock.countDown();
            events.add(event);
        }));
        t.start();
        assertFalse(lock.await(100, MILLISECONDS));
        testSubject.publish(createEvents(2));
        t.join();

        TrackedEventMessage<?> second = testSubject.openStream(events.get(0).trackingToken()).nextAvailable();
        assertSame(events.get(1), second);
    }

    @Test(timeout = 5000)
    public void testPeriodicPollingWhenEventStorageIsUpdatedIndependently() throws Exception {
        newTestSubject(CACHED_EVENTS, 20, CLEANUP_DELAY);
        TrackingEventStream stream = testSubject.openStream(null);
        CountDownLatch lock = new CountDownLatch(1);
        Thread t = new Thread(() -> stream.asStream().findFirst().ifPresent(event -> lock.countDown()));
        t.start();
        assertFalse(lock.await(100, MILLISECONDS));
        storageEngine.appendEvents(createEvent());
        t.join();
        assertTrue(lock.await(100, MILLISECONDS));
    }

    @Test(timeout = 5000)
    public void testConsumerStopsTailingWhenItFallsBehindTheCache() throws Exception {
        newTestSubject(CACHED_EVENTS, FETCH_DELAY, 20);
        TrackingEventStream stream = testSubject.openStream(null);
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

    @Test
    public void testLoadWithoutSnapshot() {
        testSubject.publish(createEvents(110));
        List<DomainEventMessage<?>> eventMessages = testSubject.readEvents(AGGREGATE).asStream().collect(toList());
        assertEquals(110, eventMessages.size());
        assertEquals(109, eventMessages.get(eventMessages.size() - 1).getSequenceNumber());
    }

    @Test
    public void testLoadWithSnapshot() {
        testSubject.publish(createEvents(110));
        storageEngine.storeSnapshot(createEvent(30));
        List<DomainEventMessage<?>> eventMessages = testSubject.readEvents(AGGREGATE).asStream().collect(toList());
        assertEquals(110 - 30, eventMessages.size());
        assertEquals(30, eventMessages.get(0).getSequenceNumber());
        assertEquals(109, eventMessages.get(eventMessages.size() - 1).getSequenceNumber());
    }

    @Test
    public void testLoadWithFailingSnapshot() {
        testSubject.publish(createEvents(110));
        storageEngine.storeSnapshot(createEvent(30));
        when(storageEngine.readSnapshot(AGGREGATE)).thenThrow(new MockException());
        List<DomainEventMessage<?>> eventMessages = testSubject.readEvents(AGGREGATE).asStream().collect(toList());
        assertEquals(110, eventMessages.size());
        assertEquals(0, eventMessages.get(0).getSequenceNumber());
        assertEquals(109, eventMessages.get(eventMessages.size() - 1).getSequenceNumber());
    }
}
