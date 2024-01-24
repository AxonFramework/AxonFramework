/*
 * Copyright (c) 2010-2023. Axon Framework
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
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventMessageHandler;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.SimpleEventHandlerInvoker;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.axonframework.eventhandling.TrackingEventProcessorConfiguration;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.integrationtests.utils.MockException;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.axonframework.integrationtests.utils.EventTestUtils.createEvents;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the multi-threaded behavior of the {@link TrackingEventProcessor}.
 *
 * @author Christophe Bouhier
 */
class TrackingEventProcessorTest_MultiThreaded {

    private EmbeddedEventStore eventBus;
    private TokenStore tokenStore;
    private EventHandlerInvoker eventHandlerInvoker;
    private EventMessageHandler mockHandler;

    private TrackingEventProcessor testSubject;

    @BeforeEach
    void setUp() {
        tokenStore = spy(new InMemoryTokenStore());
        mockHandler = mock(EventMessageHandler.class);
        when(mockHandler.canHandle(any())).thenReturn(true);
        eventHandlerInvoker =
                SimpleEventHandlerInvoker.builder()
                                         .eventHandlers(singletonList(mockHandler))
                                         .sequencingPolicy(event -> event instanceof DomainEventMessage
                                                 ? Long.valueOf(((DomainEventMessage<?>) event).getSequenceNumber())
                                                 : event.getIdentifier())
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
    void processorWorkerCount() {
        testSubject.start();
        // Give it some time to split segments from the store and submit to executor service.
        await("Start").atMost(Duration.ofSeconds(1))
                      .pollDelay(Duration.ofMillis(50))
                      .until(() -> testSubject.activeProcessorThreads() == 2);

        assertEquals(2, testSubject.processingStatus().size());
        assertTrue(testSubject.processingStatus().containsKey(0));
        assertTrue(testSubject.processingStatus().containsKey(1));

        await("Segment Zero Caught Up").atMost(Duration.ofSeconds(1))
                                       .pollDelay(Duration.ofMillis(50))
                                       .until(() -> testSubject.processingStatus().get(0).isCaughtUp());
        await("Segment One Caught Up").atMost(Duration.ofSeconds(1))
                                      .pollDelay(Duration.ofMillis(50))
                                      .until(() -> testSubject.processingStatus().get(1).isCaughtUp());
    }

    @Test
    void processorInitializesMoreTokensThanWorkerCount() {
        configureProcessor(TrackingEventProcessorConfiguration.forParallelProcessing(2)
                                                              .andInitialSegmentsCount(4));

        testSubject.start();

        // Give it some time to split segments from the store and submit to executor service.
        await("Start").atMost(Duration.ofSeconds(1))
                      .pollDelay(Duration.ofMillis(50))
                      .until(() -> testSubject.activeProcessorThreads() == 2);

        int[] actual = tokenStore.fetchSegments(testSubject.getName());
        Arrays.sort(actual);
        assertArrayEquals(new int[]{0, 1, 2, 3}, actual);
    }

    // Reproduce issue #508 (https://github.com/AxonFramework/AxonFramework/issues/508)
    @Test
    void processorInitializesAndUsesSameTokens() {
        int segmentAndThreadCount = 6;
        configureProcessor(TrackingEventProcessorConfiguration.forParallelProcessing(segmentAndThreadCount)
                                                              .andInitialSegmentsCount(segmentAndThreadCount));
        testSubject.start();

        await("Start").atMost(Duration.ofSeconds(1))
                      .pollDelay(Duration.ofMillis(50))
                      .until(() -> testSubject.activeProcessorThreads() == segmentAndThreadCount);

        int[] actual = tokenStore.fetchSegments(testSubject.getName());
        Arrays.sort(actual);
        assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5}, actual);
    }

    @Test
    void processorWorkerCountWithMultipleSegments() {
        GlobalSequenceTrackingToken tokenSegmentZero = new GlobalSequenceTrackingToken(1L);
        tokenStore.storeToken(tokenSegmentZero, "test", 0);
        GlobalSequenceTrackingToken tokenSegmentOne = new GlobalSequenceTrackingToken(2L);
        tokenStore.storeToken(tokenSegmentOne, "test", 1);

        testSubject.start();

        await("Start").atMost(Duration.ofSeconds(1))
                      .pollDelay(Duration.ofMillis(50))
                      .until(() -> testSubject.activeProcessorThreads() == 2);

        assertEquals(2, testSubject.processingStatus().size());
        assertTrue(testSubject.processingStatus().containsKey(0));
        assertTrue(testSubject.processingStatus().containsKey(1));

        await("Segment Zero")
                .atMost(Duration.ofMillis(500))
                .pollDelay(Duration.ofMillis(50))
                .until(() -> testSubject.processingStatus().get(0).getTrackingToken().equals(tokenSegmentZero));
        await("Segment One")
                .atMost(Duration.ofMillis(500))
                .pollDelay(Duration.ofMillis(50))
                .until(() -> testSubject.processingStatus().get(1).getTrackingToken().equals(tokenSegmentOne));
    }

    /**
     * This processor won't be able to handle any segments, as claiming a segment will fail.
     */
    @Test
    void processorWorkerCountWithMultipleSegmentsClaimFails() throws InterruptedException {
        tokenStore.storeToken(new GlobalSequenceTrackingToken(1L), "test", 0);
        tokenStore.storeToken(new GlobalSequenceTrackingToken(2L), "test", 1);

        // Will skip segments.
        doThrow(new UnableToClaimTokenException("Failed")).when(tokenStore).extendClaim("test", 0);
        doThrow(new UnableToClaimTokenException("Failed")).when(tokenStore).fetchToken("test", 0);
        doThrow(new UnableToClaimTokenException("Failed")).when(tokenStore).extendClaim("test", 1);
        doThrow(new UnableToClaimTokenException("Failed")).when(tokenStore).fetchToken("test", 1);

        testSubject.start();

        await("Start Fails").atMost(Duration.ofSeconds(1))
                            .pollDelay(Duration.ofMillis(50))
                            .until(() -> testSubject.activeProcessorThreads() == 0);
    }

    @Test
    void processorClaimsSegment() {
        tokenStore.storeToken(new GlobalSequenceTrackingToken(1L), "test", 0);
        tokenStore.storeToken(new GlobalSequenceTrackingToken(2L), "test", 1);

        testSubject.start();

        eventBus.publish(createEvents(10));

        await("Start").atMost(Duration.ofSeconds(1))
                      .pollDelay(Duration.ofMillis(50))
                      .until(() -> testSubject.activeProcessorThreads() == 2);
        verify(tokenStore, atLeast(1)).storeToken(any(), eq("test"), eq(0));
        verify(tokenStore, atLeast(1)).storeToken(any(), eq("test"), eq(1));
    }

    @Test
    void blacklistingSegmentWillHaveProcessorClaimAnotherOne() {
        tokenStore.storeToken(new GlobalSequenceTrackingToken(1L), "test", 0);
        tokenStore.storeToken(new GlobalSequenceTrackingToken(2L), "test", 1);
        tokenStore.storeToken(new GlobalSequenceTrackingToken(2L), "test", 2);

        testSubject.start();

        await("Start").atMost(Duration.ofSeconds(1))
                      .pollDelay(Duration.ofMillis(50))
                      .until(() -> testSubject.availableProcessorThreads() == 0);
        assertEquals(new HashSet<>(asList(0, 1)), testSubject.processingStatus().keySet());

        testSubject.releaseSegment(0);

        await("Post Release").atMost(Duration.ofSeconds(5))
                             .pollDelay(Duration.ofMillis(50))
                             .until(() -> testSubject.processingStatus().containsKey(2));
        assertEquals(new HashSet<>(asList(2, 1)), testSubject.processingStatus().keySet());

        await("Available Threads").atMost(Duration.ofSeconds(2))
                                  .pollDelay(Duration.ofMillis(50))
                                  .until(() -> testSubject.availableProcessorThreads() == 0);
    }

    @Test
    void processorWorkerCountWithMultipleSegmentsWithOneThread() {
        tokenStore.storeToken(new GlobalSequenceTrackingToken(1L), "test", 0);
        tokenStore.storeToken(new GlobalSequenceTrackingToken(2L), "test", 1);

        configureProcessor(TrackingEventProcessorConfiguration.forSingleThreadedProcessing());
        testSubject.start();

        await("Start").atMost(Duration.ofSeconds(1))
                      .pollDelay(Duration.ofMillis(50))
                      .until(() -> testSubject.activeProcessorThreads() == 1);
    }

    @Test
    void multiThreadSegmentsExceedsWorkerCount() throws Exception {
        configureProcessor(TrackingEventProcessorConfiguration.forParallelProcessing(2)
                                                              .andInitialSegmentsCount(4));

        CountDownLatch countDownLatch = new CountDownLatch(2);
        final AcknowledgeByThread acknowledgeByThread = new AcknowledgeByThread();

        doAnswer(invocation -> {
            acknowledgeByThread.addMessage(Thread.currentThread(), (EventMessage<?>) invocation.getArguments()[0]);
            countDownLatch.countDown();
            return null;
        }).when(mockHandler).handleSync(any());

        testSubject.start();
        eventBus.publish(createEvents(4));

        assertTrue(
                countDownLatch.await(5, SECONDS),
                "Expected Handler to have received 2 out of 4 published events. Got " + acknowledgeByThread.eventCount()
        );
        assertEquals(2, acknowledgeByThread.eventCount());
    }

    @Test
    void multiThreadPublishedEventsGetPassedToHandler() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        final AcknowledgeByThread acknowledgeByThread = new AcknowledgeByThread();
        doAnswer(invocation -> {
            acknowledgeByThread.addMessage(Thread.currentThread(), (EventMessage<?>) invocation.getArguments()[0]);
            countDownLatch.countDown();
            return null;
        }).when(mockHandler).handleSync(any());
        testSubject.start();
        eventBus.publish(createEvents(2));
        assertTrue(countDownLatch.await(5, SECONDS), "Expected Handler to have received 2 published events");
        acknowledgeByThread.assertEventsAckedByMultipleThreads();
        assertEquals(2, acknowledgeByThread.eventCount());
    }

    @Test
    void multiThreadTokenIsStoredWhenEventIsRead() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        //noinspection resource
        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceedSync();
        }));
        testSubject.start();
        eventBus.publish(createEvents(2));
        assertTrue(countDownLatch.await(5, SECONDS), "Expected Unit of Work to have reached clean up phase");
        verify(tokenStore, atLeastOnce()).storeToken(any(), any(), anyInt());
        assertNotNull(tokenStore.fetchToken(testSubject.getName(), 0));
        assertNotNull(tokenStore.fetchToken(testSubject.getName(), 1));
    }

    @Test
    void multiThreadContinueFromPreviousToken() throws Exception {
        tokenStore = spy(new InMemoryTokenStore());
        eventBus.publish(createEvents(10));
        //noinspection resource
        TrackedEventMessage<?> firstEvent = eventBus.openStream(null).nextAvailable();
        tokenStore.storeToken(firstEvent.trackingToken(), testSubject.getName(), 0);
        assertEquals(firstEvent.trackingToken(), tokenStore.fetchToken(testSubject.getName(), 0));

        final AcknowledgeByThread acknowledgeByThread = new AcknowledgeByThread();
        CountDownLatch countDownLatch = new CountDownLatch(9);
        doAnswer(invocation -> {
            acknowledgeByThread.addMessage(Thread.currentThread(), (EventMessage<?>) invocation.getArguments()[0]);
            countDownLatch.countDown();
            return null;
        }).when(mockHandler).handleSync(any());

        configureProcessor(TrackingEventProcessorConfiguration.forParallelProcessing(2));
        testSubject.start();

        assertTrue(countDownLatch.await(60, SECONDS),
                   "Expected 9 invocations on Event Handler by now, missing " + countDownLatch.getCount());

        acknowledgeByThread.assertEventsAckedByMultipleThreads();
        assertEquals(9, acknowledgeByThread.eventCount());
    }

    @Test
    @Timeout(value = 10)
    void multiThreadContinueAfterPause() throws Exception {
        final AcknowledgeByThread acknowledgeByThread = new AcknowledgeByThread();

        final List<DomainEventMessage<?>> events = createEvents(4);

        CountDownLatch countDownLatch = new CountDownLatch(2);
        doAnswer(invocation -> {
            acknowledgeByThread.addMessage(Thread.currentThread(), (EventMessage<?>) invocation.getArguments()[0]);
            countDownLatch.countDown();
            return null;
        }).when(mockHandler).handleSync(any());
        testSubject.start();

        eventBus.publish(events.subList(0, 2));

        assertTrue(countDownLatch.await(5, SECONDS), "Expected 2 invocations on Event Handler by now");
        assertEquals(2, acknowledgeByThread.eventCount());

        await("Segment Zero - Phase 1")
                .atMost(Duration.ofSeconds(2))
                .pollDelay(Duration.ofMillis(50))
                .until(() -> {
                    TrackingToken fetchedToken = tokenStore.fetchToken("test", 0);
                    OptionalLong position = fetchedToken.position();
                    return position.isPresent() && position.getAsLong() == 1;
                });
        await("Segment One - Phase 1")
                .atMost(Duration.ofSeconds(2))
                .pollDelay(Duration.ofMillis(50))
                .until(() -> {
                    TrackingToken fetchedToken = tokenStore.fetchToken("test", 1);
                    OptionalLong position = fetchedToken.position();
                    return position.isPresent() && position.getAsLong() == 1;
                });

        CompletableFuture<Void> shutdown = testSubject.shutdownAsync();
        await("Shutdown")
                .atMost(Duration.ofSeconds(2))
                .pollDelay(Duration.ofMillis(50))
                .until(shutdown::isDone);

        CountDownLatch countDownLatch2 = new CountDownLatch(2);
        doAnswer(invocation -> {
            acknowledgeByThread.addMessage(Thread.currentThread(), (EventMessage<?>) invocation.getArguments()[0]);
            countDownLatch2.countDown();
            return null;
        }).when(mockHandler).handleSync(any());

        eventBus.publish(events.subList(2, 4));
        assertEquals(2, countDownLatch2.getCount());

        testSubject.start();
        assertTrue(countDownLatch2.await(5, SECONDS), "Expected 4 invocations on Event Handler by now");
        assertEquals(4, acknowledgeByThread.eventCount());

        GlobalSequenceTrackingToken expectedSubsequentToken = new GlobalSequenceTrackingToken(3);
        await("Segment Zero - Phase 2")
                .atMost(Duration.ofSeconds(2))
                .pollDelay(Duration.ofMillis(50))
                .until(() -> tokenStore.fetchToken("test", 0).equals(expectedSubsequentToken));
        await("Segment One - Phase 2")
                .atMost(Duration.ofSeconds(2))
                .pollDelay(Duration.ofMillis(50))
                .until(() -> tokenStore.fetchToken("test", 1).equals(expectedSubsequentToken));
    }

    @Test
    void multiThreadProcessorGoesToRetryModeWhenOpenStreamFails() throws Exception {
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
        }).when(mockHandler).handleSync(any());

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
        //noinspection resource
        verify(eventBus, times(2)).openStream(any());
    }

    @Test
    void multiThreadTokensAreStoredWhenUnitOfWorkIsRolledBackOnSecondEvent() throws Exception {
        List<? extends EventMessage<?>> events = createEvents(2);
        CountDownLatch countDownLatch = new CountDownLatch(2);
        //noinspection Duplicates,resource
        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCommit(uow -> {
                if (uow.getMessage().equals(events.get(1))) {
                    throw new MockException();
                }
            });
            return interceptorChain.proceedSync();
        }));
        //noinspection resource
        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceedSync();
        }));
        testSubject.start();
        eventBus.publish(events);
        assertTrue(countDownLatch.await(5, SECONDS), "Expected Unit of Work to have reached clean up phase");

        assertNotNull(tokenStore.fetchToken(testSubject.getName(), 0));
        assertNotNull(tokenStore.fetchToken(testSubject.getName(), 1));
    }

    @Test
    void processorIncrementAndDecrementCorrectly() throws InterruptedException {
        configureProcessor(TrackingEventProcessorConfiguration.forParallelProcessing(2)
                                                              .andInitialSegmentsCount(4));
        testSubject.start();
        // It's an edge case, but locally this successfully fails with the previous implementation.
        // It would before prevent to increase available threads because the launcher was running.
        testSubject.shutDown();
        testSubject.start();
        testSubject.shutDown();
        testSubject.start();
        await("Start").atMost(Duration.ofSeconds(1))
                      .pollDelay(Duration.ofMillis(50))
                      .until(() -> testSubject.activeProcessorThreads() == 2);
    }

    // Utility to add up acknowledged messages by Thread (worker) name and assertions facilities.
    static class AcknowledgeByThread {

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
