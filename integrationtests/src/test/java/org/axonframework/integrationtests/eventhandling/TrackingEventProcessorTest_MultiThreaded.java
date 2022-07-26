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

package org.axonframework.integrationtests.eventhandling;

import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.eventhandling.*;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.integrationtests.utils.MockException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static org.axonframework.integrationtests.utils.AssertUtils.assertWithin;
import static org.axonframework.integrationtests.utils.EventTestUtils.createEvents;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.*;

/**
 * @author Christophe Bouhier
 */
class TrackingEventProcessorTest_MultiThreaded {

    private TrackingEventProcessor testSubject;
    private EmbeddedEventStore eventBus;
    private TokenStore tokenStore;
    private EventHandlerInvoker eventHandlerInvoker;
    private EventMessageHandler mockHandler;

    @BeforeEach
    void setUp() {
        tokenStore = spy(new InMemoryTokenStore());
        mockHandler = mock(EventMessageHandler.class);
        when(mockHandler.canHandle(any())).thenReturn(true);
        eventHandlerInvoker = SimpleEventHandlerInvoker.builder()
                                                       .eventHandlers(singletonList(mockHandler))
                                                       .sequencingPolicy(event -> {
                                                           if (event instanceof DomainEventMessage) {
                                                               return ((DomainEventMessage) event)
                                                                       .getSequenceNumber();
                                                           }
                                                           return event.getIdentifier();
                                                       })
                                                       .build();
        eventBus = EmbeddedEventStore.builder().storageEngine(new InMemoryEventStorageEngine()).build();

        // A processor config, with a policy which guarantees segmenting by using the sequence number.
        configureProcessor(TrackingEventProcessorConfiguration.forParallelProcessing(2)
                                                              .andEventAvailabilityTimeout(500, MILLISECONDS));
    }

    private void configureProcessor(TrackingEventProcessorConfiguration processorConfiguration) {
        testSubject = TrackingEventProcessor.builder()
                                            .name("test")
                                            .eventHandlerInvoker(eventHandlerInvoker)
                                            .messageSource(eventBus)
                                            .tokenStore(tokenStore)
                                            .transactionManager(NoTransactionManager.INSTANCE)
                                            .trackingEventProcessorConfiguration(processorConfiguration)
                                            .build();
    }

    @AfterEach
    void tearDown() {
        testSubject.shutDown();
        eventBus.shutDown();
    }

    @Test
    void testProcessorWorkerCount() {
        testSubject.start();
        // give it some time to split segments from the store and submit to executor service.
        assertWithin(1, SECONDS, () -> assertEquals(2, testSubject.activeProcessorThreads()));
        assertEquals(2, testSubject.processingStatus().size());
        assertTrue(testSubject.processingStatus().containsKey(0));
        assertTrue(testSubject.processingStatus().containsKey(1));
        assertWithin(1, SECONDS, () -> assertTrue(testSubject.processingStatus().get(0).isCaughtUp()));
        assertWithin(1, SECONDS, () -> assertTrue(testSubject.processingStatus().get(1).isCaughtUp()));
    }

    @Test
    void testProcessorInitializesMoreTokensThanWorkerCount() throws InterruptedException {
        configureProcessor(TrackingEventProcessorConfiguration.forParallelProcessing(2)
                                                              .andInitialSegmentsCount(4));
        testSubject.start();
        // give it some time to split segments from the store and submit to executor service.
        Thread.sleep(200);
        assertEquals(2, testSubject.activeProcessorThreads());
        int[] actual = tokenStore.fetchSegments(testSubject.getName());
        Arrays.sort(actual);
        assertArrayEquals(new int[]{0, 1, 2, 3}, actual);
    }

    // Reproduce issue #508 (https://github.com/AxonFramework/AxonFramework/issues/508)
    @Test
    void testProcessorInitializesAndUsesSameTokens() {
        configureProcessor(TrackingEventProcessorConfiguration.forParallelProcessing(6)
                                                              .andInitialSegmentsCount(6));
        testSubject.start();

        assertWithin(5, SECONDS, () -> assertEquals(6, testSubject.activeProcessorThreads()));
        int[] actual = tokenStore.fetchSegments(testSubject.getName());
        Arrays.sort(actual);
        assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5}, actual);
    }

    @Test
    void testProcessorWorkerCountWithMultipleSegments() {

        tokenStore.storeToken(new GlobalSequenceTrackingToken(1L), "test", 0);
        tokenStore.storeToken(new GlobalSequenceTrackingToken(2L), "test", 1);

        testSubject.start();

        assertWithin(20, SECONDS, () -> assertEquals(2, testSubject.activeProcessorThreads()));
        assertEquals(2, testSubject.processingStatus().size());
        assertTrue(testSubject.processingStatus().containsKey(0));
        assertTrue(testSubject.processingStatus().containsKey(1));
        assertWithin(
                10, MILLISECONDS,
                () -> assertEquals(new GlobalSequenceTrackingToken(1L),
                                   testSubject.processingStatus().get(0).getTrackingToken())
        );
        assertWithin(
                10, MILLISECONDS,
                () -> assertEquals(new GlobalSequenceTrackingToken(2L),
                                   testSubject.processingStatus().get(1).getTrackingToken())
        );
    }

    /**
     * This processor won't be able to handle any segments, as claiming a segment will fail.
     */
    @Test
    void testProcessorWorkerCountWithMultipleSegmentsClaimFails() throws InterruptedException {

        tokenStore.storeToken(new GlobalSequenceTrackingToken(1L), "test", 0);
        tokenStore.storeToken(new GlobalSequenceTrackingToken(2L), "test", 1);

        // Will skip segments.
        doThrow(new UnableToClaimTokenException("Failed")).when(tokenStore).extendClaim("test", 0);
        doThrow(new UnableToClaimTokenException("Failed")).when(tokenStore).fetchToken("test", 0);
        doThrow(new UnableToClaimTokenException("Failed")).when(tokenStore).extendClaim("test", 1);
        doThrow(new UnableToClaimTokenException("Failed")).when(tokenStore).fetchToken("test", 1);

        testSubject.start();
        // give it some time to split segments from the store and submit to executor service.
        Thread.sleep(200);

        assertWithin(1, SECONDS, () -> assertEquals(0, testSubject.activeProcessorThreads()));
    }

    @Test
    void testProcessorClaimsSegment() {
        tokenStore.storeToken(new GlobalSequenceTrackingToken(1L), "test", 0);
        tokenStore.storeToken(new GlobalSequenceTrackingToken(2L), "test", 1);

        testSubject.start();

        eventBus.publish(createEvents(10));

        assertWithin(1, SECONDS, () -> assertEquals(2, testSubject.activeProcessorThreads()));
        assertWithin(200, MILLISECONDS, () -> verify(tokenStore, atLeast(1)).storeToken(any(), eq("test"), eq(0)));
        assertWithin(200, MILLISECONDS, () -> verify(tokenStore, atLeast(1)).storeToken(any(), eq("test"), eq(1)));
    }

    @Test
    void testBlacklistingSegmentWillHaveProcessorClaimAnotherOne() {
        tokenStore.storeToken(new GlobalSequenceTrackingToken(1L), "test", 0);
        tokenStore.storeToken(new GlobalSequenceTrackingToken(2L), "test", 1);
        tokenStore.storeToken(new GlobalSequenceTrackingToken(2L), "test", 2);

        testSubject.start();

        assertWithin(1, SECONDS, () -> assertEquals(0, testSubject.availableProcessorThreads()));
        assertEquals(new HashSet<>(asList(0, 1)), testSubject.processingStatus().keySet());

        testSubject.releaseSegment(0);

        assertWithin(5, SECONDS, () -> assertTrue(testSubject.processingStatus().containsKey(2)));
        assertEquals(new HashSet<>(asList(2, 1)), testSubject.processingStatus().keySet());

        assertWithin(2, SECONDS, () -> assertEquals(0, testSubject.availableProcessorThreads()));
    }

    @Test
    void testProcessorWorkerCountWithMultipleSegmentsWithOneThread() throws InterruptedException {

        tokenStore.storeToken(new GlobalSequenceTrackingToken(1L), "test", 0);
        tokenStore.storeToken(new GlobalSequenceTrackingToken(2L), "test", 1);

        configureProcessor(TrackingEventProcessorConfiguration.forSingleThreadedProcessing());
        testSubject.start();

        // give it some time to split segments from the store and submit to executor service.
        Thread.sleep(200);
        assertEquals(1, testSubject.activeProcessorThreads());
    }

    @Test
    void testMultiThreadSegmentsExceedsWorkerCount() throws Exception {
        configureProcessor(TrackingEventProcessorConfiguration.forParallelProcessing(2)
                                                              .andInitialSegmentsCount(4));

        CountDownLatch countDownLatch = new CountDownLatch(2);
        final AcknowledgeByThread acknowledgeByThread = new AcknowledgeByThread();

        doAnswer(invocation -> {
            acknowledgeByThread.addMessage(Thread.currentThread(), (EventMessage<?>) invocation.getArguments()[0]);
            countDownLatch.countDown();
            return null;
        }).when(mockHandler).handle(any());

        testSubject.start();
        eventBus.publish(createEvents(4));

        assertTrue(countDownLatch.await(5, SECONDS),
                "Expected Handler to have received 2 out of 4 published events. Got " + acknowledgeByThread.eventCount());
        assertEquals(2, acknowledgeByThread.eventCount());
    }

    @Test
    void testMultiThreadPublishedEventsGetPassedToHandler() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        final AcknowledgeByThread acknowledgeByThread = new AcknowledgeByThread();
        doAnswer(invocation -> {
            acknowledgeByThread.addMessage(Thread.currentThread(), (EventMessage<?>) invocation.getArguments()[0]);
            countDownLatch.countDown();
            return null;
        }).when(mockHandler).handle(any());
        testSubject.start();
        eventBus.publish(createEvents(2));
        assertTrue(countDownLatch.await(5, SECONDS), "Expected Handler to have received 2 published events");
        acknowledgeByThread.assertEventsAckedByMultipleThreads();
        assertEquals(2, acknowledgeByThread.eventCount());
    }

    @Test
    void testMultiThreadTokenIsStoredWhenEventIsRead() throws Exception {

        CountDownLatch countDownLatch = new CountDownLatch(2);
        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceed();
        }));
        testSubject.start();
        eventBus.publish(createEvents(2));
        assertTrue(countDownLatch.await(5, SECONDS), "Expected Unit of Work to have reached clean up phase");
        verify(tokenStore, atLeastOnce()).storeToken(any(), any(), anyInt());
        assertNotNull(tokenStore.fetchToken(testSubject.getName(), 0));
        assertNotNull(tokenStore.fetchToken(testSubject.getName(), 1));
    }

    @Test
    void testMultiThreadContinueFromPreviousToken() throws Exception {

        tokenStore = spy(new InMemoryTokenStore());
        eventBus.publish(createEvents(10));
        TrackedEventMessage<?> firstEvent = eventBus.openStream(null).nextAvailable();
        tokenStore.storeToken(firstEvent.trackingToken(), testSubject.getName(), 0);
        assertEquals(firstEvent.trackingToken(), tokenStore.fetchToken(testSubject.getName(), 0));


        final AcknowledgeByThread acknowledgeByThread = new AcknowledgeByThread();
        CountDownLatch countDownLatch = new CountDownLatch(9);
        doAnswer(invocation -> {
            acknowledgeByThread.addMessage(Thread.currentThread(), (EventMessage<?>) invocation.getArguments()[0]);
            countDownLatch.countDown();
            return null;
        }).when(mockHandler).handle(any());

        configureProcessor(TrackingEventProcessorConfiguration.forParallelProcessing(2));
        testSubject.start();

        assertTrue(countDownLatch.await(60, SECONDS),
                "Expected 9 invocations on Event Handler by now, missing " + countDownLatch.getCount());

        acknowledgeByThread.assertEventsAckedByMultipleThreads();
        assertEquals(9, acknowledgeByThread.eventCount());
    }

    @Test
    @Timeout(value = 10)
    void testMultiThreadContinueAfterPause() throws Exception {

        final AcknowledgeByThread acknowledgeByThread = new AcknowledgeByThread();

        final List<DomainEventMessage<?>> events = createEvents(4);

        CountDownLatch countDownLatch = new CountDownLatch(2);
        doAnswer(invocation -> {
            acknowledgeByThread.addMessage(Thread.currentThread(), (EventMessage<?>) invocation.getArguments()[0]);
            countDownLatch.countDown();
            return null;
        }).when(mockHandler).handle(any());
        testSubject.start();

        eventBus.publish(events.subList(0, 2));

        assertTrue(countDownLatch.await(5, SECONDS), "Expected 2 invocations on Event Handler by now");
        assertEquals(2, acknowledgeByThread.eventCount());

        assertWithin(
                1, SECONDS,
                () -> assertEquals(new GlobalSequenceTrackingToken(1), tokenStore.fetchToken("test", 0))
        );
        assertWithin(
                1, SECONDS,
                () -> assertEquals(new GlobalSequenceTrackingToken(1), tokenStore.fetchToken("test", 1))
        );

        testSubject.shutDown();
        // The thread may block for 1 second waiting for a next event to pop up
        while (testSubject.activeProcessorThreads() > 0) {
            Thread.sleep(1);
            // wait...
        }

        CountDownLatch countDownLatch2 = new CountDownLatch(2);
        doAnswer(invocation -> {
            acknowledgeByThread.addMessage(Thread.currentThread(), (EventMessage<?>) invocation.getArguments()[0]);
            countDownLatch2.countDown();
            return null;
        }).when(mockHandler).handle(any());

        eventBus.publish(events.subList(2, 4));

        assertEquals(2, countDownLatch2.getCount());

        testSubject.start();
        assertTrue(countDownLatch2.await(5, SECONDS), "Expected 4 invocations on Event Handler by now");
        assertEquals(4, acknowledgeByThread.eventCount());

        assertWithin(
                1, SECONDS,
                () -> assertEquals(new GlobalSequenceTrackingToken(3), tokenStore.fetchToken("test", 0))
        );
        assertWithin(
                1, SECONDS,
                () -> assertEquals(new GlobalSequenceTrackingToken(3), tokenStore.fetchToken("test", 1))
        );
    }

    @Test
    void testMultiThreadProcessorGoesToRetryModeWhenOpenStreamFails() throws Exception {
        eventBus = spy(eventBus);

        tokenStore = new InMemoryTokenStore();
        eventBus.publish(createEvents(5));
        when(eventBus.openStream(any())).thenThrow(new MockException()).thenCallRealMethod();

        final AcknowledgeByThread acknowledgeByThread = new AcknowledgeByThread();
        CountDownLatch countDownLatch = new CountDownLatch(5);
        doAnswer(invocation -> {
            acknowledgeByThread.addMessage(Thread.currentThread(), (EventMessage<?>) invocation.getArguments()[0]);
            countDownLatch.countDown();
            return null;
        }).when(mockHandler).handle(any());

        testSubject = TrackingEventProcessor.builder()
                                            .name("test")
                                            .eventHandlerInvoker(eventHandlerInvoker)
                                            .messageSource(eventBus)
                                            .tokenStore(tokenStore)
                                            .transactionManager(NoTransactionManager.INSTANCE)
                                            .build();
        testSubject.start();
        assertTrue(countDownLatch.await(10, SECONDS), "Expected 5 invocations on Event Handler by now");
        assertEquals(5, acknowledgeByThread.eventCount());
        verify(eventBus, times(2)).openStream(any());
    }

    @Test
    void testMultiThreadTokensAreStoredWhenUnitOfWorkIsRolledBackOnSecondEvent() throws Exception {
        List<? extends EventMessage<?>> events = createEvents(2);
        CountDownLatch countDownLatch = new CountDownLatch(2);
        //noinspection Duplicates
        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCommit(uow -> {
                if (uow.getMessage().equals(events.get(1))) {
                    throw new MockException();
                }
            });
            return interceptorChain.proceed();
        }));
        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceed();
        }));
        testSubject.start();
        eventBus.publish(events);
        assertTrue(countDownLatch.await(5, SECONDS), "Expected Unit of Work to have reached clean up phase");

        assertNotNull(tokenStore.fetchToken(testSubject.getName(), 0));
        assertNotNull(tokenStore.fetchToken(testSubject.getName(), 1));
    }

    @Test
    void testProcessorIncrementAndDecrementCorrectly() throws InterruptedException {
        configureProcessor(TrackingEventProcessorConfiguration.forParallelProcessing(2)
                                                              .andInitialSegmentsCount(4));
        testSubject.start();
        // It's an edge case, but locally this successfully fails with the previous implementation.
        // It would before prevent to increase available threads because the launcher was running.
        testSubject.shutDown();
        testSubject.start();
        testSubject.shutDown();
        testSubject.start();
        Thread.sleep(200);
        assertEquals(2, testSubject.activeProcessorThreads());
    }

    // Utility to add up acknowledged messages by Thread (worker) name and assertions facilities.
    class AcknowledgeByThread {

        Map<String, List<EventMessage<?>>> ackedEventsByThreadMap = new ConcurrentHashMap<>();

        void addMessage(Thread handlingThread, EventMessage<?> msg) {
            ackedEventsByThreadMap.computeIfAbsent(handlingThread.getName(), k -> new ArrayList<>()).add(msg);
        }

        void assertEventsAckedByMultipleThreads() {
            ackedEventsByThreadMap.values().forEach(l -> assertFalse(l.isEmpty()));
        }

        long eventCount() {
            return ackedEventsByThreadMap.values().stream().mapToLong(Collection::size).sum();
        }
    }
}
