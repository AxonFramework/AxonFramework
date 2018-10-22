/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.integrationtests.eventhandling;

import junit.framework.TestCase;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.*;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.integrationtests.utils.MockException;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.serialization.SerializationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySortedSet;
import static java.util.stream.Collectors.toList;
import static junit.framework.TestCase.*;
import static org.axonframework.eventhandling.EventUtils.asTrackedEventMessage;
import static org.axonframework.integrationtests.utils.AssertUtils.assertWithin;
import static org.axonframework.integrationtests.utils.EventTestUtils.createEvent;
import static org.axonframework.integrationtests.utils.EventTestUtils.createEvents;
import static org.mockito.Mockito.*;

/**
 * @author Rene de Waele
 * @author Nakul Mishra
 */
public class TrackingEventProcessorTest {

    private TrackingEventProcessor testSubject;
    private EmbeddedEventStore eventBus;
    private TokenStore tokenStore;
    private EventHandlerInvoker eventHandlerInvoker;
    private EventMessageHandler mockHandler;
    private List<Long> sleepInstructions;
    private TransactionManager mockTransactionManager;
    private Transaction mockTransaction;

    static TrackingEventStream trackingEventStreamOf(Iterator<TrackedEventMessage<?>> iterator) {
        return new TrackingEventStream() {
            private boolean hasPeeked;
            private TrackedEventMessage<?> peekEvent;

            @Override
            public Optional<TrackedEventMessage<?>> peek() {
                if (!hasPeeked) {
                    if (!hasNextAvailable()) {
                        return Optional.empty();
                    }
                    peekEvent = iterator.next();
                    hasPeeked = true;
                }
                return Optional.of(peekEvent);
            }

            @Override
            public boolean hasNextAvailable(int timeout, TimeUnit unit) {
                return hasPeeked || iterator.hasNext();
            }

            @Override
            public TrackedEventMessage nextAvailable() {
                if (!hasPeeked) {
                    return iterator.next();
                }
                TrackedEventMessage<?> result = peekEvent;
                peekEvent = null;
                hasPeeked = false;
                return result;
            }

            @Override
            public void close() {

            }
        };
    }

    @Before
    public void setUp() {
        tokenStore = spy(new InMemoryTokenStore());
        mockHandler = mock(EventMessageHandler.class);
        when(mockHandler.canHandle(any())).thenReturn(true);
        when(mockHandler.supportsReset()).thenReturn(true);
        eventHandlerInvoker = Mockito.spy(SimpleEventHandlerInvoker.builder().eventHandlers(mockHandler).build());
        mockTransaction = mock(Transaction.class);
        mockTransactionManager = mock(TransactionManager.class);
        when(mockTransactionManager.startTransaction()).thenReturn(mockTransaction);
        when(mockTransactionManager.fetchInTransaction(any(Supplier.class))).thenAnswer(i -> {
            Supplier s = i.getArgument(0);
            return s.get();
        });
        doAnswer(i -> {
            Runnable r = i.getArgument(0);
            r.run();
            return null;
        }).when(mockTransactionManager).executeInTransaction(any(Runnable.class));
        eventBus = EmbeddedEventStore.builder().storageEngine(new InMemoryEventStorageEngine()).build();
        sleepInstructions = new ArrayList<>();
        TrackingEventProcessor.Builder eventProcessorBuilder =
                TrackingEventProcessor.builder()
                                      .name("test")
                                      .eventHandlerInvoker(eventHandlerInvoker)
                                      .messageSource(eventBus)
                                      .tokenStore(tokenStore)
                                      .transactionManager(mockTransactionManager);
        testSubject = new TrackingEventProcessor(eventProcessorBuilder) {
            @Override
            protected void doSleepFor(long millisToSleep) {
                if (isRunning()) {
                    sleepInstructions.add(millisToSleep);
                }
            }
        };
    }

    @After
    public void tearDown() {
        testSubject.shutDown();
        eventBus.shutDown();
    }

    @Test
    public void testPublishedEventsGetPassedToHandler() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        doAnswer(invocation -> {
            countDownLatch.countDown();
            return null;
        }).when(mockHandler).handle(any());
        testSubject.start();
        // give it a bit of time to start
        Thread.sleep(200);
        eventBus.publish(createEvents(2));
        assertTrue("Expected Handler to have received 2 published events", countDownLatch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testHandlerIsInvokedInTransactionScope() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        AtomicInteger counter = new AtomicInteger();
        AtomicInteger counterAtHandle = new AtomicInteger();
        when(mockTransactionManager.startTransaction()).thenAnswer(i -> {
            counter.incrementAndGet();
            return mockTransaction;
        });
        doAnswer(i -> counter.decrementAndGet()).when(mockTransaction).rollback();
        doAnswer(i -> counter.decrementAndGet()).when(mockTransaction).commit();
        doAnswer(invocation -> {
            counterAtHandle.set(counter.get());
            countDownLatch.countDown();
            return null;
        }).when(mockHandler).handle(any());
        testSubject.start();
        // give it a bit of time to start
        Thread.sleep(200);
        eventBus.publish(createEvents(2));
        assertTrue("Expected Handler to have received 2 published events", countDownLatch.await(5, TimeUnit.SECONDS));
        assertEquals(1, counterAtHandle.get());
    }

    @Test
    public void testProcessorStopsOnNonTransientExceptionWhenLoadingToken() {
        when(tokenStore.fetchToken("test", 0)).thenThrow(new SerializationException("Faking a serialization issue"));

        testSubject.start();

        assertWithin(
                1, TimeUnit.SECONDS,
                () -> assertFalse("Expected processor to have stopped", testSubject.isRunning())
        );
        assertWithin(
                1, TimeUnit.SECONDS,
                () -> assertTrue("Expected processor to set the error flag", testSubject.isError())
        );
        assertEquals(Collections.emptyList(), sleepInstructions);
    }

    @Test
    public void testProcessorRetriesOnTransientExceptionWhenLoadingToken() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            countDownLatch.countDown();
            return null;
        }).when(mockHandler).handle(any());

        when(tokenStore.fetchToken("test", 0)).thenThrow(new RuntimeException("Faking a recoverable issue"))
                                              .thenCallRealMethod();

        testSubject.start();

        // give it a bit of time to start
        Thread.sleep(200);
        eventBus.publish(createEvent());

        assertTrue("Expected Handler to have received published event", countDownLatch.await(5, TimeUnit.SECONDS));
        assertTrue(testSubject.isRunning());
        assertFalse(testSubject.isError());
        assertEquals(Collections.singletonList(5000L), sleepInstructions);
    }

    @Test
    public void testTokenIsStoredWhenEventIsRead() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceed();
        }));
        testSubject.start();
        // give it a bit of time to start
        Thread.sleep(200);
        eventBus.publish(createEvent());
        assertTrue("Expected Unit of Work to have reached clean up phase", countDownLatch.await(5, TimeUnit.SECONDS));
        verify(tokenStore).extendClaim(eq(testSubject.getName()), anyInt());
        verify(tokenStore).storeToken(any(), any(), anyInt());
        assertNotNull(tokenStore.fetchToken(testSubject.getName(), 0));
    }

    @Test
    public void testTokenIsStoredOncePerEventBatch() throws Exception {
        testSubject = TrackingEventProcessor.builder()
                                            .name("test")
                                            .eventHandlerInvoker(eventHandlerInvoker)
                                            .messageSource(eventBus)
                                            .tokenStore(tokenStore)
                                            .transactionManager(NoTransactionManager.INSTANCE)
                                            .build();
        CountDownLatch countDownLatch = new CountDownLatch(2);
        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceed();
        }));
        testSubject.start();
        // give it a bit of time to start
        Thread.sleep(200);
        eventBus.publish(createEvents(2));
        assertTrue("Expected Unit of Work to have reached clean up phase for 2 messages",
                   countDownLatch.await(5, TimeUnit.SECONDS));
        InOrder inOrder = inOrder(tokenStore);
        inOrder.verify(tokenStore, times(1)).extendClaim(eq(testSubject.getName()), anyInt());
        inOrder.verify(tokenStore, times(1)).storeToken(any(), any(), anyInt());

        assertNotNull(tokenStore.fetchToken(testSubject.getName(), 0));
    }

    @Test
    public void testTokenIsNotStoredWhenUnitOfWorkIsRolledBack() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCommit(uow -> {
                throw new MockException();
            });
            return interceptorChain.proceed();
        }));
        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceed();
        }));
        testSubject.start();
        // give it a bit of time to start
        Thread.sleep(200);
        eventBus.publish(createEvent());
        assertTrue("Expected Unit of Work to have reached clean up phase", countDownLatch.await(5, TimeUnit.SECONDS));
        assertNull(tokenStore.fetchToken(testSubject.getName(), 0));
    }

    @Test
    @DirtiesContext
    public void testContinueFromPreviousToken() throws Exception {

        tokenStore = new InMemoryTokenStore();
        eventBus.publish(createEvents(10));
        TrackedEventMessage<?> firstEvent = eventBus.openStream(null).nextAvailable();
        tokenStore.storeToken(firstEvent.trackingToken(), testSubject.getName(), 0);
        assertEquals(firstEvent.trackingToken(), tokenStore.fetchToken(testSubject.getName(), 0));

        List<EventMessage<?>> ackedEvents = new ArrayList<>();
        CountDownLatch countDownLatch = new CountDownLatch(9);
        doAnswer(invocation -> {
            ackedEvents.add((EventMessage<?>) invocation.getArguments()[0]);
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
        // give it a bit of time to start
        Thread.sleep(200);
        assertTrue("Expected 9 invocations on Event Handler by now", countDownLatch.await(5, TimeUnit.SECONDS));
        assertEquals(9, ackedEvents.size());
    }

    @Test(timeout = 10000)
    @DirtiesContext
    public void testContinueAfterPause() throws Exception {
        List<EventMessage<?>> ackedEvents = new ArrayList<>();
        CountDownLatch countDownLatch = new CountDownLatch(2);
        doAnswer(invocation -> {
            ackedEvents.add((EventMessage<?>) invocation.getArguments()[0]);
            countDownLatch.countDown();
            return null;
        }).when(mockHandler).handle(any());
        testSubject.start();
        // give it a bit of time to start
        Thread.sleep(200);

        eventBus.publish(createEvents(2));

        assertTrue("Expected 2 invocations on Event Handler by now", countDownLatch.await(5, TimeUnit.SECONDS));
        assertEquals(2, ackedEvents.size());

        testSubject.shutDown();
        // The thread may block for 1 second waiting for a next event to pop up
        while (testSubject.activeProcessorThreads() > 0) {
            Thread.sleep(1);
            // wait...
        }

        CountDownLatch countDownLatch2 = new CountDownLatch(2);
        doAnswer(invocation -> {
            ackedEvents.add((EventMessage<?>) invocation.getArguments()[0]);
            countDownLatch2.countDown();
            return null;
        }).when(mockHandler).handle(any());

        eventBus.publish(createEvents(2));

        assertEquals(2, countDownLatch2.getCount());

        testSubject.start();
        // give it a bit of time to start
        Thread.sleep(200);
        assertTrue("Expected 4 invocations on Event Handler by now", countDownLatch2.await(5, TimeUnit.SECONDS));
        assertEquals(4, ackedEvents.size());
    }

    @Test
    @DirtiesContext
    public void testProcessorGoesToRetryModeWhenOpenStreamFails() throws Exception {
        eventBus = spy(eventBus);

        tokenStore = new InMemoryTokenStore();
        eventBus.publish(createEvents(5));
        when(eventBus.openStream(any())).thenThrow(new MockException()).thenCallRealMethod();

        List<EventMessage<?>> ackedEvents = new ArrayList<>();
        CountDownLatch countDownLatch = new CountDownLatch(5);
        doAnswer(invocation -> {
            ackedEvents.add((EventMessage<?>) invocation.getArguments()[0]);
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
        // give it a bit of time to start
        Thread.sleep(200);
        assertTrue("Expected 5 invocations on Event Handler by now", countDownLatch.await(10, TimeUnit.SECONDS));
        assertEquals(5, ackedEvents.size());
        verify(eventBus, times(2)).openStream(any());
    }

    @Test
    public void testFirstTokenIsStoredWhenUnitOfWorkIsRolledBackOnSecondEvent() throws Exception {
        List<? extends EventMessage<?>> events = createEvents(2);
        CountDownLatch countDownLatch = new CountDownLatch(2);
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
        // give it a bit of time to start
        Thread.sleep(200);
        eventBus.publish(events);
        assertTrue("Expected Unit of Work to have reached clean up phase", countDownLatch.await(5, TimeUnit.SECONDS));
        verify(tokenStore, atLeastOnce()).storeToken(any(), any(), anyInt());
        assertNotNull(tokenStore.fetchToken(testSubject.getName(), 0));
    }

    @Test
    @DirtiesContext
    @SuppressWarnings("unchecked")
    public void testEventsWithTheSameTokenAreProcessedInTheSameBatch() throws Exception {
        eventBus.shutDown();

        eventBus = mock(EmbeddedEventStore.class);
        TrackingToken trackingToken = new GlobalSequenceTrackingToken(0);
        List<TrackedEventMessage<?>> events =
                createEvents(2).stream().map(event -> asTrackedEventMessage(event, trackingToken)).collect(toList());
        when(eventBus.openStream(null)).thenReturn(trackingEventStreamOf(events.iterator()));
        testSubject = TrackingEventProcessor.builder()
                                            .name("test")
                                            .eventHandlerInvoker(eventHandlerInvoker)
                                            .messageSource(eventBus)
                                            .tokenStore(tokenStore)
                                            .transactionManager(NoTransactionManager.INSTANCE)
                                            .build();

        //noinspection Duplicates
        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCommit(uow -> {
                if (uow.getMessage().equals(events.get(1))) {
                    throw new MockException();
                }
            });
            return interceptorChain.proceed();
        }));

        CountDownLatch countDownLatch = new CountDownLatch(2);

        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceed();
        }));

        testSubject.start();
        // Give it a bit of time to start
        Thread.sleep(200);

        assertTrue("Expected Unit of Work to have reached clean up phase", countDownLatch.await(5, TimeUnit.SECONDS));
        verify(tokenStore, atLeastOnce()).storeToken(any(), any(), anyInt());
        assertNull(tokenStore.fetchToken(testSubject.getName(), 0));
    }

    @Test
    public void testResetCausesEventsToBeReplayed() throws Exception {
        when(mockHandler.supportsReset()).thenReturn(true);
        final List<String> handled = new CopyOnWriteArrayList<>();
        final List<String> handledInRedelivery = new CopyOnWriteArrayList<>();
        //noinspection Duplicates
        doAnswer(i -> {
            EventMessage message = i.getArgument(0);
            handled.add(message.getIdentifier());
            if (ReplayToken.isReplay(message)) {
                handledInRedelivery.add(message.getIdentifier());
            }
            return null;
        }).when(mockHandler).handle(any());

        eventBus.publish(createEvents(4));
        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(4, handled.size()));
        testSubject.shutDown();
        testSubject.resetTokens();
        // Resetting twice caused problems (see issue #559)
        testSubject.resetTokens();
        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(8, handled.size()));
        assertEquals(handled.subList(0, 3), handled.subList(4, 7));
        assertEquals(handled.subList(4, 7), handledInRedelivery);
        assertTrue(testSubject.processingStatus().get(0).isReplaying());
        eventBus.publish(createEvents(1));
        assertWithin(1, TimeUnit.SECONDS, () -> assertFalse(testSubject.processingStatus().get(0).isReplaying()));
    }

    @Test
    public void testResetToPositionCausesCertainEventsToBeReplayed() throws Exception {
        when(mockHandler.supportsReset()).thenReturn(true);
        final List<String> handled = new CopyOnWriteArrayList<>();
        final List<String> handledInRedelivery = new CopyOnWriteArrayList<>();
        //noinspection Duplicates
        doAnswer(i -> {
            EventMessage message = i.getArgument(0);
            handled.add(message.getIdentifier());
            if (ReplayToken.isReplay(message)) {
                handledInRedelivery.add(message.getIdentifier());
            }
            return null;
        }).when(mockHandler).handle(any());

        eventBus.publish(createEvents(4));
        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(4, handled.size()));
        testSubject.shutDown();
        testSubject.resetTokens(source -> new GlobalSequenceTrackingToken(1L));
        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(6, handled.size()));
        assertFalse(handledInRedelivery.contains(handled.get(0)));
        assertFalse(handledInRedelivery.contains(handled.get(1)));
        assertEquals(handled.subList(2, 3), handled.subList(4, 5));
        assertEquals(handled.subList(4, 5), handledInRedelivery);
        assertTrue(testSubject.processingStatus().get(0).isReplaying());
        eventBus.publish(createEvents(1));
        assertWithin(1, TimeUnit.SECONDS, () -> assertFalse(testSubject.processingStatus().get(0).isReplaying()));
    }

    @Test
    public void testResetOnInitializeWithTokenResetToThatToken() throws Exception {
        TrackingEventProcessorConfiguration config =
                TrackingEventProcessorConfiguration.forSingleThreadedProcessing()
                                                   .andInitialTrackingToken(ms -> new GlobalSequenceTrackingToken(1L));
        TrackingEventProcessor.Builder eventProcessorBuilder =
                TrackingEventProcessor.builder()
                                      .name("test")
                                      .eventHandlerInvoker(eventHandlerInvoker)
                                      .messageSource(eventBus)
                                      .tokenStore(tokenStore)
                                      .transactionManager(NoTransactionManager.INSTANCE)
                                      .trackingEventProcessorConfiguration(config);
        testSubject = new TrackingEventProcessor(eventProcessorBuilder) {
            @Override
            protected void doSleepFor(long millisToSleep) {
                if (isRunning()) {
                    sleepInstructions.add(millisToSleep);
                }
            }
        };
        when(mockHandler.supportsReset()).thenReturn(true);
        final List<String> handled = new CopyOnWriteArrayList<>();
        final List<String> handledInRedelivery = new CopyOnWriteArrayList<>();
        //noinspection Duplicates
        doAnswer(i -> {
            EventMessage message = i.getArgument(0);
            handled.add(message.getIdentifier());
            if (ReplayToken.isReplay(message)) {
                handledInRedelivery.add(message.getIdentifier());
            }
            return null;
        }).when(mockHandler).handle(any());

        eventBus.publish(createEvents(4));
        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(2, handled.size()));
        testSubject.shutDown();
        testSubject.resetTokens();
        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(4, handled.size()));
        assertEquals(handled.subList(0, 1), handled.subList(2, 3));
        assertEquals(handled.subList(2, 3), handledInRedelivery);
        assertTrue(testSubject.processingStatus().get(0).isReplaying());
        eventBus.publish(createEvents(1));
        assertWithin(1, TimeUnit.SECONDS, () -> assertFalse(testSubject.processingStatus().get(0).isReplaying()));
    }

    @Test
    public void testResetBeforeStartingPerformsANormalRun() throws Exception {
        when(mockHandler.supportsReset()).thenReturn(true);
        final List<String> handled = new CopyOnWriteArrayList<>();
        final List<String> handledInRedelivery = new CopyOnWriteArrayList<>();
        //noinspection Duplicates
        doAnswer(i -> {
            EventMessage message = i.getArgument(0);
            handled.add(message.getIdentifier());
            if (ReplayToken.isReplay(message)) {
                handledInRedelivery.add(message.getIdentifier());
            }
            return null;
        }).when(mockHandler).handle(any());

        testSubject.start();
        testSubject.shutDown();
        testSubject.resetTokens();
        testSubject.start();
        eventBus.publish(createEvents(4));
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(4, handled.size()));
        assertEquals(0, handledInRedelivery.size());
        assertFalse(testSubject.processingStatus().get(0).isReplaying());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testReplayFlagAvailableWhenReplayInDifferentOrder() throws Exception {
        StreamableMessageSource<TrackedEventMessage<?>> stubSource = mock(StreamableMessageSource.class);
        testSubject = TrackingEventProcessor.builder()
                                            .name("test")
                                            .eventHandlerInvoker(eventHandlerInvoker)
                                            .messageSource(stubSource)
                                            .tokenStore(tokenStore)
                                            .transactionManager(NoTransactionManager.INSTANCE)
                                            .build();

        when(stubSource.openStream(any())).thenReturn(new StubTrackingEventStream(0, 1, 2, 5))
                                          .thenReturn(new StubTrackingEventStream(0, 1, 2, 3, 4, 5, 6, 7));


        when(eventHandlerInvoker.supportsReset()).thenReturn(true);
        doReturn(true).when(eventHandlerInvoker).canHandle(any(), any());
        List<TrackingToken> firstRun = new CopyOnWriteArrayList<>();
        List<TrackingToken> replayRun = new CopyOnWriteArrayList<>();
        doAnswer(i -> {
            firstRun.add(i.<TrackedEventMessage>getArgument(0).trackingToken());
            return null;
        }).when(eventHandlerInvoker).handle(any(), any());

        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(4, firstRun.size()));
        testSubject.shutDown();

        doAnswer(i -> {
            replayRun.add(i.<TrackedEventMessage>getArgument(0).trackingToken());
            return null;
        }).when(eventHandlerInvoker).handle(any(), any());

        testSubject.resetTokens();
        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(8, replayRun.size()));

        TestCase.assertEquals(GapAwareTrackingToken.newInstance(5, asList(3L, 4L)), firstRun.get(3));
        assertTrue(replayRun.get(0) instanceof ReplayToken);
        assertTrue(replayRun.get(5) instanceof ReplayToken);
        assertEquals(GapAwareTrackingToken.newInstance(6, emptySortedSet()), replayRun.get(6));
    }

    @Test(expected = IllegalStateException.class)
    public void testResetRejectedWhileRunning() {
        testSubject.start();
        testSubject.resetTokens();
    }

    @Test
    public void testResetNotSupportedWhenInvokerDoesNotSupportReset() {
        when(mockHandler.supportsReset()).thenReturn(false);
        assertFalse(testSubject.supportsReset());
    }

    @Test(expected = IllegalStateException.class)
    public void testResetRejectedWhenInvokerDoesNotSupportReset() {
        when(mockHandler.supportsReset()).thenReturn(false);
        testSubject.resetTokens();
    }

    @Test
    public void testResetRejectedIfNotAllTokensCanBeClaimed() {
        tokenStore.initializeTokenSegments("test", 4);
        when(tokenStore.fetchToken("test", 3)).thenThrow(new UnableToClaimTokenException("Mock"));

        try {
            testSubject.resetTokens();
            fail("Expected exception");
        } catch (UnableToClaimTokenException e) {
            // expected
        }
        verify(tokenStore, never()).storeToken(isNull(), anyString(), anyInt());
    }

    @Test
    public void testWhenFailureDuringInit() throws InterruptedException {

        when(tokenStore.fetchSegments(anyString()))
                .thenThrow(new RuntimeException("Faking issue during fetchSegments"))
                .thenReturn(new int[]{})
                .thenReturn(new int[]{0});

        doThrow(new RuntimeException("Faking issue during initializeTokenSegments"))
                // and on further calls
                .doNothing()
                .when(tokenStore).initializeTokenSegments(anyString(), anyInt());

        testSubject.start();

        Thread.sleep(2500);

        assertEquals(1, testSubject.activeProcessorThreads());
    }

    @Test
    public void testReleaseSegment() {
        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(1, testSubject.activeProcessorThreads()));
        testSubject.releaseSegment(0);
        assertWithin(2, TimeUnit.SECONDS, () -> assertEquals(0, testSubject.activeProcessorThreads()));
        assertWithin(15, TimeUnit.SECONDS, () -> assertEquals(1, testSubject.activeProcessorThreads()));
    }

    @Test
    public void testHasAvailableSegments() {
        assertEquals(1, testSubject.availableProcessorThreads());
        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(0, testSubject.availableProcessorThreads()));
        testSubject.releaseSegment(0);
        assertWithin(2, TimeUnit.SECONDS, () -> assertEquals(1, testSubject.availableProcessorThreads()));
    }

    private static class StubTrackingEventStream implements TrackingEventStream {

        private final Queue<TrackedEventMessage<?>> eventMessages;

        public StubTrackingEventStream(long... tokens) {
            GapAwareTrackingToken lastToken = GapAwareTrackingToken.newInstance(-1, emptySortedSet());
            eventMessages = new LinkedList<>();
            for (Long seq : tokens) {
                lastToken = lastToken.advanceTo(seq, 1000, true);
                eventMessages.add(new GenericTrackedEventMessage<>(lastToken, createEvent(seq)));
            }
        }

        @Override
        public Optional<TrackedEventMessage<?>> peek() {
            if (eventMessages.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(eventMessages.peek());
        }

        @Override
        public boolean hasNextAvailable(int timeout, TimeUnit unit) {
            return !eventMessages.isEmpty();
        }

        @Override
        public TrackedEventMessage<?> nextAvailable() {
            return eventMessages.poll();
        }

        @Override
        public void close() {

        }
    }
}
