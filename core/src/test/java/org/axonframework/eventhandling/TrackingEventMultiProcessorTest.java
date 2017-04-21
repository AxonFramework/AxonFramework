/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.eventhandling;

import org.axonframework.common.MockException;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.GlobalSequenceTrackingToken;
import org.axonframework.eventsourcing.eventstore.TrackingEventStream;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.Theories;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static junit.framework.TestCase.*;
import static org.axonframework.eventhandling.TrackingEventProcessorTest.trackingEventStreamOf;
import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.createEvent;
import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.createEvents;
import static org.axonframework.eventsourcing.eventstore.EventUtils.asTrackedEventMessage;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

/**
 * @author Christophe Bouhier
 */
public class TrackingEventMultiProcessorTest {

    private TrackingEventProcessor testSubject;
    private EmbeddedEventStore eventBus;
    private TokenStore tokenStore;
    private EventHandlerInvoker eventHandlerInvoker;
    private EventListener mockListener;

    @Before
    public void setUp() throws Exception {
        tokenStore = spy(new InMemoryTokenStore());
        mockListener = mock(EventListener.class);
        eventHandlerInvoker = new SimpleEventHandlerInvoker(mockListener);
        eventBus = new EmbeddedEventStore(new InMemoryEventStorageEngine());

        // A processor, with a policy which guarantees segmenting with
        testSubject = new TrackingEventProcessor("test", eventHandlerInvoker, RollbackConfigurationType.ANY_THROWABLE, PropagatingErrorHandler.INSTANCE,
                eventBus, tokenStore, new MessageSequenceNumberSequentialPolicy(), 2, NoTransactionManager.INSTANCE, 1, NoOpMessageMonitor.INSTANCE);
        testSubject.tweakThreadPool(2,2);
    }

    @After
    public void tearDown() throws Exception {
        testSubject.shutDown();
        eventBus.shutDown();
    }

    @Test
    @DirtiesContext
    public void testProcessorWorkerCount() throws InterruptedException {
        testSubject.start();
        // give it some time to split segments from the store and submit to executor service.
        Thread.sleep(200);
        assertThat(testSubject.activeProcessorThreads(), is(2));
    }

    @Test
    @DirtiesContext
    public void testProcessorWorkerCountWithMultipleSegments() throws InterruptedException {

        tokenStore.storeToken(new GlobalSequenceTrackingToken(1L), "test", 0);
        tokenStore.storeToken(new GlobalSequenceTrackingToken(2L), "test", 1);

        testSubject.start();
        // give it some time to split segments from the store and submit to executor service.
        Thread.sleep(200);
        assertThat(testSubject.activeProcessorThreads(), is(2));
    }

    @Test
    @DirtiesContext
    public void testProcessorWorkerCountWithMultipleSegmentsWithOneThread() throws InterruptedException {

        tokenStore.storeToken(new GlobalSequenceTrackingToken(1L), "test", 0);
        tokenStore.storeToken(new GlobalSequenceTrackingToken(2L), "test", 1);

        testSubject = new TrackingEventProcessor("test", eventHandlerInvoker, RollbackConfigurationType.ANY_THROWABLE, PropagatingErrorHandler.INSTANCE,
                eventBus, tokenStore, new MessageSequenceNumberSequentialPolicy(), 1, NoTransactionManager.INSTANCE, 1, NoOpMessageMonitor.INSTANCE);
        testSubject.start();

        // give it some time to split segments from the store and submit to executor service.
        Thread.sleep(200);
        assertThat(testSubject.activeProcessorThreads(), is(1));
    }

    @Test
    @DirtiesContext
    public void testMultiThreadSegmentsExceedsWorkerCount() throws Exception {

        testSubject = new TrackingEventProcessor("test", eventHandlerInvoker, RollbackConfigurationType.ANY_THROWABLE, PropagatingErrorHandler.INSTANCE,
                eventBus, tokenStore, new MessageSequenceNumberSequentialPolicy(), 3, NoTransactionManager.INSTANCE, 1, NoOpMessageMonitor.INSTANCE);
        testSubject.tweakThreadPool(2,2);
        CountDownLatch countDownLatch = new CountDownLatch(2);
        final AcknowledgeByThread acknowledgeByThread = new AcknowledgeByThread();

        doAnswer(invocation -> {
            acknowledgeByThread.addMessage(Thread.currentThread(),(EventMessage<?>) invocation.getArguments()[0]);
            countDownLatch.countDown();
            return null;
        }).when(mockListener).handle(any());

        testSubject.start();
        eventBus.publish(createEvents(3));

        assertTrue("Expected listener to have received (only) 2 out of 3 published events", countDownLatch.await(5, TimeUnit.SECONDS));
        acknowledgeByThread.assertEventsAddUpTo(2);
    }

    @Test
    @DirtiesContext
    public void testMultiThreadPublishedEventsGetPassedToListener() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        final AcknowledgeByThread acknowledgeByThread = new AcknowledgeByThread();
        doAnswer(invocation -> {
            acknowledgeByThread.addMessage(Thread.currentThread(),(EventMessage<?>) invocation.getArguments()[0]);
            countDownLatch.countDown();
            return null;
        }).when(mockListener).handle(any());
        testSubject.start();
        eventBus.publish(createEvents(2));
        assertTrue("Expected listener to have received 2 published events", countDownLatch.await(5, TimeUnit.SECONDS));
        acknowledgeByThread.assertEventsAckedByMultipleThreads();
        acknowledgeByThread.assertEventsAddUpTo(2);
    }

    @Test
    @DirtiesContext
    public void testMultiThreadTokenIsStoredWhenEventIsRead() throws Exception {

        CountDownLatch countDownLatch = new CountDownLatch(2);
        testSubject.registerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceed();
        }));
        testSubject.start();
        eventBus.publish(createEvents(2));
        assertTrue("Expected Unit of Work to have reached clean up phase", countDownLatch.await(5, TimeUnit.SECONDS));
        verify(tokenStore, atLeastOnce()).storeToken(any(), any(), anyInt());
        assertThat(tokenStore.fetchToken(testSubject.getName(), 0), notNullValue());
        assertThat(tokenStore.fetchToken(testSubject.getName(), 1), notNullValue());
    }

    @Test
    @DirtiesContext
    public void testMultiThreadedTokenIsNotStoredWhenUnitOfWorkIsRolledBack() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        testSubject.registerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCommit(uow -> {
                throw new MockException();
            });
            return interceptorChain.proceed();
        }));
        testSubject.registerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceed();
        }));
        testSubject.start();
        eventBus.publish(createEvent());
        assertTrue("Expected Unit of Work to have reached clean up phase", countDownLatch.await(5, TimeUnit.SECONDS));
        assertNull(tokenStore.fetchToken(testSubject.getName(), 0));
        assertNull(tokenStore.fetchToken(testSubject.getName(), 1));
    }

    @Test
    @DirtiesContext
    public void testMultiThreadContinueFromPreviousToken() throws Exception {

        tokenStore = spy(new InMemoryTokenStore());
        eventBus.publish(createEvents(10));
        TrackedEventMessage<?> firstEvent = eventBus.openStream(null).nextAvailable();
        tokenStore.storeToken(firstEvent.trackingToken(), testSubject.getName(), 0);
        assertEquals(firstEvent.trackingToken(), tokenStore.fetchToken(testSubject.getName(), 0));


        final AcknowledgeByThread acknowledgeByThread = new AcknowledgeByThread();
        CountDownLatch countDownLatch = new CountDownLatch(9);
        doAnswer(invocation -> {
            acknowledgeByThread.addMessage(Thread.currentThread(),(EventMessage<?>) invocation.getArguments()[0]);
            countDownLatch.countDown();
            return null;
        }).when(mockListener).handle(any());

        testSubject = new TrackingEventProcessor("test", eventHandlerInvoker, RollbackConfigurationType.ANY_THROWABLE, PropagatingErrorHandler.INSTANCE,
                eventBus, tokenStore, new MessageSequenceNumberSequentialPolicy(),2,  NoTransactionManager.INSTANCE, 1, NoOpMessageMonitor.INSTANCE);
        testSubject.tweakThreadPool(2,2);
        testSubject.start();

        assertTrue("Expected 9 invocations on event listener by now", countDownLatch.await(60, TimeUnit.SECONDS));

        acknowledgeByThread.assertEventsAckedByMultipleThreads();
        acknowledgeByThread.assertEventsAddUpTo(9);
    }

    @Test(timeout = 10000)
    @DirtiesContext
    public void testMultiThreadContinueAfterPause() throws Exception {

        final AcknowledgeByThread acknowledgeByThread = new AcknowledgeByThread();

        final List<DomainEventMessage<?>> events = createEvents(4);

        CountDownLatch countDownLatch = new CountDownLatch(2);
        doAnswer(invocation -> {
            acknowledgeByThread.addMessage(Thread.currentThread(), (EventMessage<?>) invocation.getArguments()[0]);
            countDownLatch.countDown();
            return null;
        }).when(mockListener).handle(any());
        testSubject.start();

        eventBus.publish(events.subList(0,2));

        assertTrue("Expected 2 invocations on event listener by now", countDownLatch.await(5, TimeUnit.SECONDS));
        acknowledgeByThread.assertEventsAddUpTo(2);

        verify(tokenStore, times(2)).storeToken(any(), anyString(), anyInt());

        testSubject.pause();
        // The thread may block for 1 second waiting for a next event to pop up
        while (testSubject.activeProcessorThreads() > 0) {
            Thread.sleep(1);
            // wait...
        }

        CountDownLatch countDownLatch2 = new CountDownLatch(2);
        doAnswer(invocation -> {
            acknowledgeByThread.addMessage(Thread.currentThread(),(EventMessage<?>) invocation.getArguments()[0]);
            countDownLatch2.countDown();
            return null;
        }).when(mockListener).handle(any());

        eventBus.publish(events.subList(2,4));

        assertEquals(2, countDownLatch2.getCount());

        testSubject.start();
        assertTrue("Expected 4 invocations on event listener by now", countDownLatch2.await(5, TimeUnit.SECONDS));
        acknowledgeByThread.assertEventsAddUpTo(4);

        // batch size = 1
        verify(tokenStore, times(4)).storeToken(any(), anyString(), anyInt());
    }

    @Test
    @DirtiesContext
    public void testMultiThreadProcessorGoesToRetryModeWhenOpenStreamFails() throws Exception {
        eventBus = spy(eventBus);

        tokenStore = new InMemoryTokenStore();
        eventBus.publish(createEvents(5));
        when(eventBus.openStream(any())).thenThrow(new MockException()).thenCallRealMethod();

        final AcknowledgeByThread acknowledgeByThread = new AcknowledgeByThread();
        CountDownLatch countDownLatch = new CountDownLatch(5);
        doAnswer(invocation -> {
            acknowledgeByThread.addMessage(Thread.currentThread(),(EventMessage<?>) invocation.getArguments()[0]);
            countDownLatch.countDown();
            return null;
        }).when(mockListener).handle(any());

        testSubject = new TrackingEventProcessor("test", eventHandlerInvoker, eventBus, tokenStore, NoTransactionManager.INSTANCE);
        testSubject.start();
        assertTrue("Expected 5 invocations on event listener by now", countDownLatch.await(10, TimeUnit.SECONDS));
        acknowledgeByThread.assertEventsAddUpTo(5);
        verify(eventBus, times(2)).openStream(any());
    }

    @Test
    public void testMultiThreadTokensAreStoredWhenUnitOfWorkIsRolledBackOnSecondEvent() throws Exception {
        List<? extends EventMessage<?>> events = createEvents(2);
        CountDownLatch countDownLatch = new CountDownLatch(2);
        testSubject.registerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCommit(uow -> {
                if (uow.getMessage().equals(events.get(1))) {
                    throw new MockException();
                }
            });
            return interceptorChain.proceed();
        }));
        testSubject.registerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceed();
        }));
        testSubject.start();
        eventBus.publish(events);
        assertTrue("Expected Unit of Work to have reached clean up phase", countDownLatch.await(5, TimeUnit.SECONDS));

        verify(tokenStore, atLeastOnce()).storeToken(any(), any(), Mockito.eq(0));
        verify(tokenStore, atLeastOnce()).storeToken(any(), any(), Mockito.eq(1));

        assertNotNull(tokenStore.fetchToken(testSubject.getName(), 0));
        assertNotNull(tokenStore.fetchToken(testSubject.getName(), 1));
    }

    @Test
    @DirtiesContext
    @SuppressWarnings("unchecked")
    public void testMultiThreadEventsWithTheSameTokenAreProcessedInTheSameBatch() throws Exception {
        eventBus.shutDown();

        eventBus = mock(EmbeddedEventStore.class);
        TrackingToken trackingToken = new GlobalSequenceTrackingToken(0);


        List<TrackedEventMessage<?>> events =
                createEvents(2).stream().map(event -> asTrackedEventMessage(event, trackingToken)).collect(toList());

        when(eventBus.openStream(null)).then(i -> trackingEventStreamOf(events.iterator()));

        testSubject = new TrackingEventProcessor("test", eventHandlerInvoker, RollbackConfigurationType.ANY_THROWABLE, PropagatingErrorHandler.INSTANCE,
                eventBus, tokenStore, new MessageSequenceNumberSequentialPolicy(), 2, NoTransactionManager.INSTANCE, 1, NoOpMessageMonitor.INSTANCE);
        testSubject.tweakThreadPool(2,2);

        testSubject.registerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCommit(uow -> {
                if (uow.getMessage().equals(events.get(1))) {
                    throw new MockException();
                }
            });
            return interceptorChain.proceed();
        }));

        CountDownLatch countDownLatch = new CountDownLatch(2);

        testSubject.registerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceed();
        }));

        testSubject.start();

        assertTrue("Expected Unit of Work to have reached clean up phase", countDownLatch.await(5, TimeUnit.SECONDS));
        verify(tokenStore, atLeastOnce()).storeToken(any(), any(), anyInt());
        assertNull(tokenStore.fetchToken(testSubject.getName(), 0));
        assertNull(tokenStore.fetchToken(testSubject.getName(), 1));

    }

    // Utility to add up acknowledged messages by Thread (worker) name and assertions facilities.
    class AcknowledgeByThread {
        Map<String, List<EventMessage<?>>> ackedEventsByThreadMap = new ConcurrentHashMap<>();

        void addMessage(Thread handlingThread, EventMessage<?> msg) {
            ackedEventsByThreadMap.computeIfAbsent(handlingThread.getName(), k -> new ArrayList<>()).add(msg);
        }

        void assertEventsAckedByMultipleThreads() {
            ackedEventsByThreadMap.values().forEach(l -> assertThat(l.isEmpty(), is(false)));
        }

        void assertEventsAddUpTo(int eventCount) {
            assertThat(ackedEventsByThreadMap.values().stream().mapToLong(Collection::size).sum(), is(new Integer(eventCount).longValue()));
        }
    }

}