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

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.common.AxonThreadFactory;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericTrackedEventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingEventStream;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.eventsourcing.utils.MockException;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.tracing.TestSpanFactory;
import org.junit.jupiter.api.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;
import static org.axonframework.eventsourcing.utils.EventStoreTestUtils.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link EmbeddedEventStore}.
 *
 * @author Rene de Waele
 */
class EmbeddedEventStoreTest {

    private static final int CACHED_EVENTS = 10;
    private static final long FETCH_DELAY = 1000;
    private static final long CLEANUP_DELAY = 10000;
    private static final boolean OPTIMIZE_EVENT_CONSUMPTION = true;

    private EmbeddedEventStore testSubject;
    private EventStorageEngine storageEngine;
    private ThreadFactory threadFactory;
    private TestSpanFactory spanFactory;

    @BeforeEach
    void setUp() {
        spanFactory = new TestSpanFactory();
        storageEngine = spy(new InMemoryEventStorageEngine());
        threadFactory = spy(new AxonThreadFactory(EmbeddedEventStore.class.getSimpleName()));
        newTestSubject(CACHED_EVENTS, FETCH_DELAY, CLEANUP_DELAY, OPTIMIZE_EVENT_CONSUMPTION);
    }

    private void newTestSubject(int cachedEvents,
                                long fetchDelay,
                                long cleanupDelay,
                                boolean optimizeEventConsumption) {
        Optional.ofNullable(testSubject).ifPresent(EmbeddedEventStore::shutDown);
        testSubject = EmbeddedEventStore.builder()
                                        .storageEngine(storageEngine)
                                        .cachedEvents(cachedEvents)
                                        .fetchDelay(fetchDelay)
                                        .cleanupDelay(cleanupDelay)
                                        .threadFactory(threadFactory)
                                        .optimizeEventConsumption(optimizeEventConsumption)
                                        .spanFactory(spanFactory)
                                        .build();
    }

    @AfterEach
    void tearDown() {
        testSubject.shutDown();
    }

    @Test
    void testExistingEventIsPassedToReader() throws Exception {
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

    @Test
    @Timeout(value = FETCH_DELAY / 10, unit = MILLISECONDS)
    void testEventPublishedAfterOpeningStreamIsPassedToReaderImmediately() throws Exception {
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

    @Test
    @Timeout(value = 5)
    void testReadingIsBlockedWhenStoreIsEmpty() throws Exception {
        CountDownLatch lock = new CountDownLatch(1);
        TrackingEventStream stream = testSubject.openStream(null);
        Thread t = new Thread(() -> stream.asStream().findFirst().ifPresent(event -> lock.countDown()));
        t.start();
        assertFalse(lock.await(100, MILLISECONDS));
        testSubject.publish(createEvent());
        t.join();
        assertEquals(0, lock.getCount());
    }

    @Test
    @Timeout(value = 5)
    void testReadingIsBlockedWhenEndOfStreamIsReached() throws Exception {
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

    @Test
    @Timeout(value = 5)
    void testReadingCanBeContinuedUsingLastToken() throws Exception {
        List<? extends EventMessage<?>> events = createEvents(2);
        testSubject.publish(events);
        TrackedEventMessage<?> first = testSubject.openStream(null).nextAvailable();
        TrackingToken firstToken = first.trackingToken();
        TrackedEventMessage<?> second = testSubject.openStream(firstToken).nextAvailable();
        assertEquals(events.get(0).getIdentifier(), first.getIdentifier());
        assertEquals(events.get(1).getIdentifier(), second.getIdentifier());
    }

    @Test
    @Timeout(value = 5)
    void testEventIsFetchedFromCacheWhenFetchedASecondTime() throws Exception {
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

    @Test
    @Timeout(value = 5)
    void testPeriodicPollingWhenEventStorageIsUpdatedIndependently() throws Exception {
        newTestSubject(CACHED_EVENTS, 20, CLEANUP_DELAY, OPTIMIZE_EVENT_CONSUMPTION);
        TrackingEventStream stream = testSubject.openStream(null);
        CountDownLatch lock = new CountDownLatch(1);
        Thread t = new Thread(() -> stream.asStream().findFirst().ifPresent(event -> lock.countDown()));
        t.start();
        assertFalse(lock.await(100, MILLISECONDS));
        storageEngine.appendEvents(createEvent());
        t.join();
        assertTrue(lock.await(100, MILLISECONDS));
    }

    @Test
    @Timeout(value = 5)
    void testConsumerStopsTailingWhenItFallsBehindTheCache() throws Exception {
        newTestSubject(CACHED_EVENTS, FETCH_DELAY, 20, OPTIMIZE_EVENT_CONSUMPTION);
        TrackingEventStream stream = testSubject.openStream(null);
        assertFalse(stream.hasNextAvailable()); //now we should be tailing
        testSubject.publish(createEvents(CACHED_EVENTS)); //triggers event producer to open a stream
        Thread.sleep(100);
        reset(storageEngine);
        assertTrue(stream.hasNextAvailable());
        TrackedEventMessage<?> firstEvent = stream.nextAvailable();
        verifyNoInteractions(storageEngine);
        testSubject.publish(createEvent(CACHED_EVENTS), createEvent(CACHED_EVENTS + 1));
        Thread.sleep(100); //allow the cleaner thread to evict the consumer
        reset(storageEngine);
        assertTrue(stream.hasNextAvailable());
        verify(storageEngine).readEvents(firstEvent.trackingToken(), false);
    }

    @Test
    void testLoadWithoutSnapshot() {
        testSubject.publish(createEvents(110));
        List<DomainEventMessage<?>> eventMessages = testSubject.readEvents(AGGREGATE).asStream().collect(toList());
        assertEquals(110, eventMessages.size());
        assertEquals(109, eventMessages.get(eventMessages.size() - 1).getSequenceNumber());
    }

    @Test
    void testLoadWithSnapshot() {
        testSubject.publish(createEvents(110));
        storageEngine.storeSnapshot(createEvent(30));
        List<DomainEventMessage<?>> eventMessages = testSubject.readEvents(AGGREGATE).asStream().collect(toList());
        assertEquals(110 - 30, eventMessages.size());
        assertEquals(30, eventMessages.get(0).getSequenceNumber());
        assertEquals(109, eventMessages.get(eventMessages.size() - 1).getSequenceNumber());
    }

    /* Reproduces issue reported in https://github.com/AxonFramework/AxonFramework/issues/485 */
    @Test
    void testStreamEventsShouldNotReturnDuplicateTokens() throws InterruptedException {
        newTestSubject(0, 1000, 1000, OPTIMIZE_EVENT_CONSUMPTION);
        //noinspection rawtypes
        Stream mockStream = mock(Stream.class);
        //noinspection unchecked
        Iterator<GenericTrackedEventMessage<String>> mockIterator = mock(Iterator.class);
        when(mockStream.iterator()).thenReturn(mockIterator);
        //noinspection unchecked
        when(storageEngine.readEvents(any(TrackingToken.class), eq(false))).thenReturn(mockStream);
        when(mockIterator.hasNext()).thenAnswer(new SynchronizedBooleanAnswer(false))
                                    .thenAnswer(new SynchronizedBooleanAnswer(true));
        when(mockIterator.next()).thenReturn(new GenericTrackedEventMessage<>(new GlobalSequenceTrackingToken(1),
                                                                              createEvent()));
        TrackingEventStream stream = testSubject.openStream(null);
        assertFalse(stream.hasNextAvailable());
        testSubject.publish(createEvent());
        // give some time consumer to consume the event
        Thread.sleep(200);
        // if the stream correctly updates the token internally, it should not find events anymore
        assertFalse(stream.hasNextAvailable());
    }

    @Test
    void testLoadWithFailingSnapshot() {
        testSubject.publish(createEvents(110));
        storageEngine.storeSnapshot(createEvent(30));
        when(storageEngine.readSnapshot(AGGREGATE)).thenThrow(new MockException());
        List<DomainEventMessage<?>> eventMessages = testSubject.readEvents(AGGREGATE).asStream().collect(toList());
        assertEquals(110, eventMessages.size());
        assertEquals(0, eventMessages.get(0).getSequenceNumber());
        assertEquals(109, eventMessages.get(eventMessages.size() - 1).getSequenceNumber());
    }

    @Test
    void testLoadEventsAfterPublishingInSameUnitOfWork() {
        List<DomainEventMessage<?>> events = createEvents(10);
        testSubject.publish(events.subList(0, 2));
        DefaultUnitOfWork.startAndGet(null)
                         .execute(() -> {
                             assertEquals(2, testSubject.readEvents(AGGREGATE).asStream().count());

                             testSubject.publish(events.subList(2, events.size()));
                             assertEquals(10, testSubject.readEvents(AGGREGATE).asStream().count());
                         });
    }

    @Test
    void testLoadEventsWithOffsetAfterPublishingInSameUnitOfWork() {
        List<DomainEventMessage<?>> events = createEvents(10);
        testSubject.publish(events.subList(0, 2));
        DefaultUnitOfWork.startAndGet(null)
                         .execute(() -> {
                             assertEquals(2, testSubject.readEvents(AGGREGATE).asStream().count());

                             testSubject.publish(events.subList(2, events.size()));
                             assertEquals(8, testSubject.readEvents(AGGREGATE, 2).asStream().count());
                         });
    }

    @Test
    void testEventsAppendedInvisibleUntilUnitOfWorkIsCommitted() {
        List<DomainEventMessage<?>> events = createEvents(10);
        testSubject.publish(events.subList(0, 2));
        DefaultUnitOfWork<Message<?>> unitOfWork = DefaultUnitOfWork.startAndGet(null);
        testSubject.publish(events.subList(2, events.size()));

        CurrentUnitOfWork.clear(unitOfWork);
        // working outside the context of the UoW now
        assertEquals(2, testSubject.readEvents(AGGREGATE).asStream().count());

        CurrentUnitOfWork.set(unitOfWork);
        // Back in the context
        assertEquals(10, testSubject.readEvents(AGGREGATE).asStream().count());
        unitOfWork.rollback();

        assertEquals(2, testSubject.readEvents(AGGREGATE).asStream().count());
    }

    @Test
    void testAppendEventsCreatesCorrectSpans() {
        List<DomainEventMessage<?>> events = createEvents(10);
        DefaultUnitOfWork<Message<?>> unitOfWork = DefaultUnitOfWork.startAndGet(null);
        testSubject.publish(events);
        events.forEach(e -> spanFactory.verifySpanCompleted("EmbeddedEventStore.publish", e));
        spanFactory.verifyNotStarted("EmbeddedEventStore.commit");

        CurrentUnitOfWork.commit();
        spanFactory.verifySpanCompleted("EmbeddedEventStore.commit");
    }

    @Test
    void testStagedEventsNotDuplicatedAfterCommit() {
        List<DomainEventMessage<?>> events = createEvents(10);
        testSubject.publish(events.subList(0, 2));
        DefaultUnitOfWork<Message<?>> outerUoW = DefaultUnitOfWork.startAndGet(null);
        testSubject.publish(events.subList(2, 4));
        DefaultUnitOfWork<Message<?>> innerUoW = DefaultUnitOfWork.startAndGet(null);
        testSubject.publish(events.subList(4, events.size()));

        Consumer<UnitOfWork<Message<?>>> assertCorrectEventCount =
                uow -> assertEquals(10, testSubject.readEvents(AGGREGATE).asStream().count());

        innerUoW.onPrepareCommit(assertCorrectEventCount);
        innerUoW.afterCommit(assertCorrectEventCount);
        innerUoW.onCommit(assertCorrectEventCount);
        outerUoW.onPrepareCommit(assertCorrectEventCount);
        outerUoW.afterCommit(assertCorrectEventCount);
        outerUoW.onCommit(assertCorrectEventCount);

        innerUoW.commit();
        outerUoW.commit();
    }

    @Test
    @Timeout(value = 5)
    void testCustomThreadFactoryIsUsed() throws Exception {
        CountDownLatch lock = new CountDownLatch(1);
        TrackingEventStream stream = testSubject.openStream(null);
        Thread t = new Thread(() -> stream.asStream().findFirst().ifPresent(event -> lock.countDown()));
        t.start();
        assertFalse(lock.await(100, MILLISECONDS));
        testSubject.publish(createEvent());
        t.join();
        assertEquals(0, lock.getCount());

        verify(threadFactory, atLeastOnce()).newThread(any(Runnable.class));
    }

    @Test
    void testOpenStreamReadsEventsFromAnEventProducedByVerifyThreadFactoryOperation()
            throws InterruptedException {
        TrackingEventStream eventStream = testSubject.openStream(null);

        assertFalse(eventStream.hasNextAvailable()); // There are no events published yet, so stream will tail
        testSubject.publish(createEvents(5));// Publish some events which should be returned to the stream by a producer

        Thread.sleep(100); // Give the Event Producer thread time to fill the cache
        assertTrue(eventStream.hasNextAvailable()); // Stream should contain events again, from the producer

        // Consume events until the end
        while (eventStream.hasNextAvailable()) {
            eventStream.nextAvailable();
        }

        assertFalse(eventStream.hasNextAvailable()); // Should have reached the end, hence returned false

        verify(threadFactory, atLeastOnce()).newThread(any(Runnable.class)); // Verify a producer thread was created
    }

    @Test
    void testTailingConsumptionThreadIsNeverCreatedIfEventConsumptionOptimizationIsSwitchedOff()
            throws InterruptedException {
        boolean doNotOptimizeEventConsumption = false;
        //noinspection ConstantConditions
        newTestSubject(CACHED_EVENTS, FETCH_DELAY, CLEANUP_DELAY, doNotOptimizeEventConsumption);

        TrackingEventStream eventStream = testSubject.openStream(null);
        testSubject.publish(createEvents(5));

        // Consume some events
        while (eventStream.hasNextAvailable()) {
            eventStream.nextAvailable();
        }

        // No tailing-consumer Producer thread has ever been created
        verifyNoInteractions(threadFactory);
    }

    @Test
    void testEventStreamKeepsReturningEventsIfEventConsumptionOptimizationIsSwitchedOff()
            throws InterruptedException {
        boolean doNotOptimizeEventConsumption = false;
        //noinspection ConstantConditions
        newTestSubject(CACHED_EVENTS, FETCH_DELAY, CLEANUP_DELAY, doNotOptimizeEventConsumption);

        TrackingEventStream eventStream = testSubject.openStream(null);

        assertFalse(eventStream.hasNextAvailable()); // There are no events published yet, so should be false

        testSubject.publish(createEvents(5)); // Publish some events which should be returned to the stream

        assertTrue(eventStream.hasNextAvailable()); // There are new events, so should be true
        // Consume until the end
        while (eventStream.hasNextAvailable()) {
            eventStream.nextAvailable();
        }
        assertFalse(eventStream.hasNextAvailable()); // Should have no events anymore
    }

    private static class SynchronizedBooleanAnswer implements Answer<Boolean> {

        private final boolean answer;

        private SynchronizedBooleanAnswer(boolean answer) {
            this.answer = answer;
        }

        @Override
        public synchronized Boolean answer(InvocationOnMock invocation) {
            return answer;
        }
    }
}
