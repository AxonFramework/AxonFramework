/*
 * Copyright (c) 2010-2022. Axon Framework
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
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventMessageHandler;
import org.axonframework.eventhandling.EventTrackerStatus;
import org.axonframework.eventhandling.EventTrackerStatusChangeListener;
import org.axonframework.eventhandling.GapAwareTrackingToken;
import org.axonframework.eventhandling.GenericTrackedEventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.MultiEventHandlerInvoker;
import org.axonframework.eventhandling.PropagatingErrorHandler;
import org.axonframework.eventhandling.ReplayToken;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.SimpleEventHandlerInvoker;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.axonframework.eventhandling.TrackingEventProcessorConfiguration;
import org.axonframework.eventhandling.TrackingEventStream;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.SequenceEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.integrationtests.utils.MockException;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.serialization.SerializationException;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySortedSet;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;
import static org.axonframework.eventhandling.EventUtils.asTrackedEventMessage;
import static org.axonframework.integrationtests.utils.AssertUtils.assertUntil;
import static org.axonframework.integrationtests.utils.AssertUtils.assertWithin;
import static org.axonframework.integrationtests.utils.EventTestUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link TrackingEventProcessor}. This test class is part of the {@code integrationtests}
 * module as it relies on both the {@code messaging} (where the {@code TrackingEventProcessor} resides) and
 * {@code eventsourcing} modules.
 *
 * @author Rene de Waele
 */
class TrackingEventProcessorTest {

    private static final Object NO_RESET_PAYLOAD = null;

    private TrackingEventProcessor testSubject;
    private EmbeddedEventStore eventBus;
    private TokenStore tokenStore;
    private EventHandlerInvoker eventHandlerInvoker;
    private EventMessageHandler mockHandler;
    private List<Long> sleepInstructions;
    private TransactionManager mockTransactionManager;
    private Transaction mockTransaction;

    private static TrackingEventStream trackingEventStreamOf(Iterator<TrackedEventMessage<?>> iterator) {
        return trackingEventStreamOf(iterator, c -> {
        });
    }

    private static TrackingEventStream trackingEventStreamOf(Iterator<TrackedEventMessage<?>> iterator,
                                                             Consumer<Class<?>> skippedPayloadListener) {
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
                if (timeout > 0) {
                    // To keep tests speedy, we don't wait, but we do give other threads a chance
                    Thread.yield();
                }
                return hasPeeked || iterator.hasNext();
            }

            @Override
            public TrackedEventMessage<?> nextAvailable() {
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

            @Override
            public void skipMessagesWithPayloadTypeOf(TrackedEventMessage<?> ignoredMessage) {
                skippedPayloadListener.accept(ignoredMessage.getPayloadType());
            }
        };
    }

    @BeforeEach
    void setUp() {
        tokenStore = spy(new InMemoryTokenStore());
        mockHandler = mock(EventMessageHandler.class);
        when(mockHandler.canHandle(any())).thenReturn(true);
        when(mockHandler.supportsReset()).thenReturn(true);
        eventHandlerInvoker = spy(
                SimpleEventHandlerInvoker.builder()
                                         .eventHandlers(mockHandler)
                                         .listenerInvocationErrorHandler(PropagatingErrorHandler.instance())
                                         .build()
        );
        mockTransaction = mock(Transaction.class);
        mockTransactionManager = mock(TransactionManager.class);
        when(mockTransactionManager.startTransaction()).thenReturn(mockTransaction);

        //noinspection unchecked
        when(mockTransactionManager.fetchInTransaction(any(Supplier.class))).thenAnswer(i -> {
            Supplier<?> s = i.getArgument(0);
            return s.get();
        });
        doAnswer(i -> {
            Runnable r = i.getArgument(0);
            r.run();
            return null;
        }).when(mockTransactionManager).executeInTransaction(any(Runnable.class));
        eventBus = EmbeddedEventStore.builder().storageEngine(new InMemoryEventStorageEngine()).build();
        sleepInstructions = new CopyOnWriteArrayList<>();

        initProcessor(TrackingEventProcessorConfiguration.forSingleThreadedProcessing()
                                                         .andEventAvailabilityTimeout(100, TimeUnit.MILLISECONDS));
    }

    private void initProcessor(TrackingEventProcessorConfiguration config) {
        initProcessor(config, UnaryOperator.identity());
    }

    private void initProcessor(TrackingEventProcessorConfiguration config,
                               UnaryOperator<TrackingEventProcessor.Builder> customization) {
        TrackingEventProcessor.Builder eventProcessorBuilder =
                TrackingEventProcessor.builder()
                                      .name("test")
                                      .eventHandlerInvoker(eventHandlerInvoker)
                                      .messageSource(eventBus)
                                      .trackingEventProcessorConfiguration(config)
                                      .tokenStore(tokenStore)
                                      .transactionManager(mockTransactionManager);
        testSubject = new TrackingEventProcessor(customization.apply(eventProcessorBuilder)) {
            @Override
            protected void doSleepFor(long millisToSleep) {
                if (isRunning()) {
                    sleepInstructions.add(millisToSleep);
                    Thread.yield();
                }
            }
        };
    }

    @AfterEach
    void tearDown() {
        testSubject.shutDown();
        eventBus.shutDown();
    }

    @Test
    void testSequenceEventStorageReceivesEachEventOnlyOnce() throws Exception {
        InMemoryEventStorageEngine historic = new InMemoryEventStorageEngine();
        InMemoryEventStorageEngine active = new InMemoryEventStorageEngine(2);
        SequenceEventStorageEngine sequenceEventStorageEngine = new SequenceEventStorageEngine(historic, active);

        EmbeddedEventStore sequenceEventBus = EmbeddedEventStore.builder()
                                                                .storageEngine(sequenceEventStorageEngine)
                                                                .build();

        initProcessor(TrackingEventProcessorConfiguration.forSingleThreadedProcessing()
                                                         .andEventAvailabilityTimeout(100, TimeUnit.MILLISECONDS),
                      b -> {
                          b.messageSource(sequenceEventBus);
                          return b;
                      });

        historic.appendEvents(createEvent(AGGREGATE, 1L, "message1"), createEvent(AGGREGATE, 2L, "message2"));
        // to make sure tracking tokens match, we need to offset the InMemoryEventStorageEngine
        active.appendEvents(createEvent(AGGREGATE, 3L, "message3"), createEvent(AGGREGATE, 4L, "message4"),
                            createEvent(AGGREGATE, 5L, "message5"));

        int expectedEventCount = 5;

        CountDownLatch countDownLatch = new CountDownLatch(expectedEventCount);
        AtomicInteger counter = new AtomicInteger(0);
        doAnswer(invocation -> {
            int cnt = counter.incrementAndGet();
            countDownLatch.countDown();
            assertTrue(cnt <= expectedEventCount);
            return null;
        }).when(mockHandler).handle(any());

        testSubject.start();

        assertTrue(countDownLatch.await(5, TimeUnit.SECONDS), "Expected Handler to have received 4 published events");
        assertEquals(expectedEventCount, counter.get(), "Handler should only receive each event once");
    }

    @Test
    void testSequenceMaxCapacity() {
        testSubject.start();
        assertEquals(1, testSubject.maxCapacity());
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(0, testSubject.availableProcessorThreads()));
        assertEquals(1, testSubject.activeProcessorThreads());
    }

    @Test
    void testPublishedEventsGetPassedToHandler() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        doAnswer(invocation -> {
            countDownLatch.countDown();
            return null;
        }).when(mockHandler).handle(any());
        testSubject.start();
        // Give it a bit of time to start
        Thread.sleep(200);
        eventBus.publish(createEvents(2));
        assertTrue(countDownLatch.await(5, TimeUnit.SECONDS), "Expected Handler to have received 2 published events");
    }

    @Test
    void testBlacklist() {
        when(mockHandler.canHandle(any())).thenReturn(false);
        when(mockHandler.canHandleType(String.class)).thenReturn(false);
        Set<Class<?>> skipped = new HashSet<>();

        EmbeddedEventStore mockEventBus = mock(EmbeddedEventStore.class);
        TrackingToken trackingToken = new GlobalSequenceTrackingToken(0);
        List<TrackedEventMessage<?>> events =
                createEvents(2).stream().map(event -> asTrackedEventMessage(event, trackingToken)).collect(toList());
        when(mockEventBus.openStream(null)).thenReturn(trackingEventStreamOf(events.iterator(), skipped::add));
        testSubject = TrackingEventProcessor.builder()
                                            .name("test")
                                            .eventHandlerInvoker(eventHandlerInvoker)
                                            .messageSource(mockEventBus)
                                            .tokenStore(tokenStore)
                                            .transactionManager(NoTransactionManager.INSTANCE)
                                            .build();
        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> {
            assertEquals(1, skipped.size());
            assertTrue(skipped.contains(String.class));
        });
    }

    @Test
    void testProcessorExposesErrorStateOnHandlerException() throws Exception {
        doReturn(Object.class).when(mockHandler).getTargetType();
        AtomicBoolean errorFlag = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (errorFlag.get()) {
                throw new MockException("Simulating issues");
            }
            return null;
        }).when(mockHandler).handle(any());
        int segmentId = 0;

        testSubject.start();
        eventBus.publish(createEvents(2));

        assertWithin(2, TimeUnit.SECONDS, () -> {
            EventTrackerStatus status = testSubject.processingStatus().get(segmentId);
            assertNotNull(status);
            assertTrue(status.isErrorState());
            assertEquals(MockException.class, status.getError().getClass());
        });

        errorFlag.set(false);

        assertWithin(5, TimeUnit.SECONDS, () -> {
            EventTrackerStatus status = testSubject.processingStatus().get(segmentId);
            assertNotNull(status);
            assertFalse(status.isErrorState());
            assertNull(status.getError());
        });
    }

    @Test
    void testHandlerIsInvokedInTransactionScope() throws Exception {
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
        // Give it a bit of time to start
        Thread.sleep(200);
        eventBus.publish(createEvents(2));
        assertTrue(countDownLatch.await(5, TimeUnit.SECONDS), "Expected Handler to have received 2 published events");
        assertEquals(1, counterAtHandle.get());
    }

    @Test
    void testProcessorStopsOnNonTransientExceptionWhenLoadingToken() {
        doThrow(new SerializationException("Faking a serialization issue")).when(tokenStore).fetchToken("test", 0);

        testSubject.start();

        assertWithin(
                1, TimeUnit.SECONDS, () -> assertFalse(testSubject.isRunning(), "Expected processor to have stopped")
        );
        assertWithin(
                1, TimeUnit.SECONDS, () -> assertTrue(testSubject.isError(), "Expected processor to set the error flag")
        );
        assertEquals(Collections.emptyList(), sleepInstructions);
    }

    @Test
    void testProcessorRetriesOnTransientExceptionWhenLoadingToken() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            countDownLatch.countDown();
            return null;
        }).when(mockHandler).handle(any());

        doThrow(new RuntimeException("Faking a recoverable issue"))
                .doCallRealMethod()
                .when(tokenStore).fetchToken("test", 0);

        testSubject.start();

        eventBus.publish(createEvent());

        assertTrue(countDownLatch.await(5, TimeUnit.SECONDS), "Expected Handler to have received published event");
        assertTrue(testSubject.isRunning());
        assertFalse(testSubject.isError());
        assertEquals(Collections.singletonList(5000L), sleepInstructions);
    }

    @Test
    void testTokenIsStoredWhenEventIsRead() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceed();
        }));
        eventBus.publish(createEvent());
        testSubject.start();
        assertTrue(countDownLatch.await(5, TimeUnit.SECONDS), "Expected Unit of Work to have reached clean up phase");
        verify(tokenStore).storeToken(any(), eq(testSubject.getName()), eq(0));
        assertNotNull(tokenStore.fetchToken(testSubject.getName(), 0));
    }

    @Test
    void testTokenIsExtendedAtStartAndStoredAtEndOfEventBatch_WithStoringTokensAfterProcessingSetting()
            throws Exception {
        initProcessor(
                TrackingEventProcessorConfiguration.forSingleThreadedProcessing().andBatchSize(100),
                TrackingEventProcessor.Builder::storingTokensAfterProcessing
        );
        CountDownLatch countDownLatch = new CountDownLatch(2);
        AtomicInteger invocationsInUnitOfWork = new AtomicInteger();
        doAnswer(i -> {
            if (CurrentUnitOfWork.isStarted()) {
                invocationsInUnitOfWork.incrementAndGet();
            }
            return i.callRealMethod();
        }).when(tokenStore).extendClaim(anyString(), anyInt());
        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceed();
        }));
        testSubject.start();
        eventBus.publish(createEvents(2));
        assertTrue(
                countDownLatch.await(5, TimeUnit.SECONDS),
                "Expected Unit of Work to have reached clean up phase for 2 messages"
        );
        InOrder inOrder = inOrder(tokenStore);
        inOrder.verify(tokenStore, times(1)).extendClaim(eq(testSubject.getName()), anyInt());
        inOrder.verify(tokenStore, atLeastOnce()).storeToken(any(), any(), anyInt());

        assertNotNull(tokenStore.fetchToken(testSubject.getName(), 0));
        assertEquals(
                1, invocationsInUnitOfWork.get(),
                "Unexpected number of invocations of token extension in unit of work"
        );
    }

    @Test
    void testTokenStoredAtEndOfEventBatchAndNotExtendedWhenUsingANoTransactionManager() throws Exception {
        TrackingEventProcessorConfiguration tepConfig =
                TrackingEventProcessorConfiguration.forSingleThreadedProcessing().andBatchSize(100);
        testSubject = TrackingEventProcessor.builder()
                                            .name("test")
                                            .eventHandlerInvoker(eventHandlerInvoker)
                                            .trackingEventProcessorConfiguration(tepConfig)
                                            .messageSource(eventBus)
                                            .tokenStore(tokenStore)
                                            .transactionManager(NoTransactionManager.INSTANCE)
                                            .build();

        CountDownLatch countDownLatch = new CountDownLatch(2);
        AtomicInteger invocationsInUnitOfWork = new AtomicInteger();
        doAnswer(i -> {
            if (CurrentUnitOfWork.isStarted()) {
                invocationsInUnitOfWork.incrementAndGet();
            }
            return i.callRealMethod();
        }).when(tokenStore).extendClaim(anyString(), anyInt());

        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceed();
        }));
        testSubject.start();
        eventBus.publish(createEvents(2));
        assertTrue(
                countDownLatch.await(5, TimeUnit.SECONDS),
                "Expected Unit of Work to have reached clean up phase for 2 messages"
        );

        verify(tokenStore, times(1)).storeToken(any(), any(), anyInt());
        assertNotNull(tokenStore.fetchToken(testSubject.getName(), 0));

        assertEquals(
                1, invocationsInUnitOfWork.get(),
                "Unexpected number of invocations of token extension in unit of work"
        );
    }

    @Test
    void testTokenStoredAtEndOfEventBatchAndNotExtendedWhenTransactionManagerIsConfigured() throws Exception {
        TrackingEventProcessorConfiguration tepConfig =
                TrackingEventProcessorConfiguration.forSingleThreadedProcessing().andBatchSize(100);
        testSubject = TrackingEventProcessor.builder()
                                            .name("test")
                                            .eventHandlerInvoker(eventHandlerInvoker)
                                            .trackingEventProcessorConfiguration(tepConfig)
                                            .messageSource(eventBus)
                                            .tokenStore(tokenStore)
                                            .transactionManager(() -> mock(Transaction.class))
                                            .build();

        CountDownLatch countDownLatch = new CountDownLatch(2);
        AtomicInteger invocationsInUnitOfWork = new AtomicInteger();
        doAnswer(i -> {
            if (CurrentUnitOfWork.isStarted()) {
                invocationsInUnitOfWork.incrementAndGet();
                fail("Did not expect an invocation in a Unit of Work");
            }
            return i.callRealMethod();
        }).when(tokenStore).extendClaim(anyString(), anyInt());

        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceed();
        }));
        testSubject.start();
        eventBus.publish(createEvents(2));
        assertTrue(
                countDownLatch.await(5, TimeUnit.SECONDS),
                "Expected Unit of Work to have reached clean up phase for 2 messages"
        );

        verify(tokenStore, times(1)).storeToken(any(), any(), anyInt());
        assertNotNull(tokenStore.fetchToken(testSubject.getName(), 0));

        assertEquals(
                0, invocationsInUnitOfWork.get(),
                "Unexpected number of invocations of token extension in unit of work"
        );
    }

    @Test
    void testTokenStoredAtEndOfEventBatchAndExtendedWhenTokenClaimIntervalExceeded() throws Exception {
        TrackingEventProcessorConfiguration tepConfig =
                TrackingEventProcessorConfiguration.forSingleThreadedProcessing()
                                                   .andEventAvailabilityTimeout(10, TimeUnit.MILLISECONDS);
        testSubject = TrackingEventProcessor.builder()
                                            .name("test")
                                            .trackingEventProcessorConfiguration(tepConfig)
                                            .eventHandlerInvoker(eventHandlerInvoker)
                                            .messageSource(eventBus)
                                            .tokenStore(tokenStore)
                                            .transactionManager(NoTransactionManager.INSTANCE)
                                            .build();
        CountDownLatch countDownLatch = new CountDownLatch(2);
        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            Thread.sleep(50);
            return interceptorChain.proceed();
        }));
        testSubject.start();

        eventBus.publish(createEvents(2));
        assertTrue(
                countDownLatch.await(5, TimeUnit.SECONDS),
                "Expected Unit of Work to have reached clean up phase for 2 messages"
        );

        InOrder inOrder = inOrder(tokenStore);
        inOrder.verify(tokenStore, times(1)).storeToken(any(), any(), anyInt());
        inOrder.verify(tokenStore, times(1)).extendClaim(eq(testSubject.getName()), anyInt());

        assertNotNull(tokenStore.fetchToken(testSubject.getName(), 0));
    }

    @Test
    void testTokenIsNotStoredWhenUnitOfWorkIsRolledBack() throws Exception {
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

        eventBus.publish(createEvent());
        assertTrue(countDownLatch.await(5, TimeUnit.SECONDS), "Expected Unit of Work to have reached clean up phase");
        assertThat(
                tokenStore.fetchToken(testSubject.getName(), 0),
                CoreMatchers.anyOf(CoreMatchers.nullValue(), CoreMatchers.equalTo(eventBus.createTailToken()))
        );
    }

    @Test
    void testContinueFromPreviousToken() throws Exception {
        tokenStore = new InMemoryTokenStore();
        eventBus.publish(createEvents(10));
        TrackedEventMessage<?> firstEvent = eventBus.openStream(null).nextAvailable();
        tokenStore.storeToken(firstEvent.trackingToken(), testSubject.getName(), 0);
        assertEquals(firstEvent.trackingToken(), tokenStore.fetchToken(testSubject.getName(), 0));

        List<EventMessage<?>> acknowledgedEvents = new CopyOnWriteArrayList<>();
        CountDownLatch countDownLatch = new CountDownLatch(9);
        doAnswer(invocation -> {
            acknowledgedEvents.add((EventMessage<?>) invocation.getArguments()[0]);
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

        assertTrue(countDownLatch.await(5, TimeUnit.SECONDS), "Expected 9 invocations on Event Handler by now");
        assertEquals(9, acknowledgedEvents.size());
    }

    @Test
    @Timeout(value = 10)
    @DirtiesContext
    void testContinueAfterPause() throws Exception {
        List<EventMessage<?>> acknowledgedEvents = new CopyOnWriteArrayList<>();
        CountDownLatch countDownLatch = new CountDownLatch(2);
        doAnswer(invocation -> {
            acknowledgedEvents.add((EventMessage<?>) invocation.getArguments()[0]);
            countDownLatch.countDown();
            return null;
        }).when(mockHandler).handle(any());
        testSubject.start();

        eventBus.publish(createEvents(2));

        assertTrue(countDownLatch.await(5, TimeUnit.SECONDS), "Expected 2 invocations on Event Handler by now");
        assertEquals(2, acknowledgedEvents.size());

        testSubject.shutDown();
        // The thread may block for 1 second waiting for a next event to pop up
        while (testSubject.activeProcessorThreads() > 0) {
            Thread.sleep(1);
            // Wait...
        }

        CountDownLatch countDownLatch2 = new CountDownLatch(2);
        doAnswer(invocation -> {
            acknowledgedEvents.add((EventMessage<?>) invocation.getArguments()[0]);
            countDownLatch2.countDown();
            return null;
        }).when(mockHandler).handle(any());

        eventBus.publish(createEvents(2));

        assertEquals(2, countDownLatch2.getCount());

        testSubject.start();

        assertTrue(countDownLatch2.await(5, TimeUnit.SECONDS), "Expected 4 invocations on Event Handler by now");
        assertEquals(4, acknowledgedEvents.size());
    }

    @Test
    @DirtiesContext
    void testProcessorGoesToRetryModeWhenOpenStreamFails() throws Exception {
        eventBus = spy(eventBus);

        tokenStore = new InMemoryTokenStore();
        eventBus.publish(createEvents(5));
        when(eventBus.openStream(any())).thenThrow(new MockException()).thenCallRealMethod();

        List<EventMessage<?>> acknowledgedEvents = new ArrayList<>();
        CountDownLatch countDownLatch = new CountDownLatch(5);
        doAnswer(invocation -> {
            acknowledgedEvents.add((EventMessage<?>) invocation.getArguments()[0]);
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
        // Give it a bit of time to start
        Thread.sleep(200);
        assertTrue(countDownLatch.await(10, TimeUnit.SECONDS), "Expected 5 invocations on Event Handler by now");
        assertEquals(5, acknowledgedEvents.size());
        verify(eventBus, times(2)).openStream(any());
    }

    @Test
    @DirtiesContext
    void testProcessorSetsAndUnsetsErrorState() throws Exception {
        eventBus = spy(eventBus);

        tokenStore = new InMemoryTokenStore();
        eventBus.publish(createEvents(5));
        AtomicBoolean fail = new AtomicBoolean(true);
        when(eventBus.openStream(any())).then(invocationOnMock -> {
            if (fail.get()) {
                throw new MockException();
            }
            return invocationOnMock.callRealMethod();
        });

        List<EventMessage<?>> acknowledgedEvents = new ArrayList<>();
        CountDownLatch countDownLatch = new CountDownLatch(5);
        doAnswer(invocation -> {
            acknowledgedEvents.add((EventMessage<?>) invocation.getArguments()[0]);
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
        // Give it a bit of time to start
        Thread.sleep(200);
        Optional<EventTrackerStatus> healthy = testSubject.processingStatus().values().stream().filter(s -> !s
                .isErrorState()).findFirst();
        assertThat("no healthy processor when open stream fails", !healthy.isPresent());
        fail.set(false);
        assertTrue(countDownLatch.await(10, TimeUnit.SECONDS), "Expected 5 invocations on Event Handler by now");
        assertEquals(5, acknowledgedEvents.size());
        Optional<EventTrackerStatus> inErrorState = testSubject.processingStatus()
                                                               .values()
                                                               .stream()
                                                               .filter(EventTrackerStatus::isErrorState)
                                                               .findFirst();
        assertThat("no processor in error state when open stream succeeds again", !inErrorState.isPresent());
    }

    @Test
    void testFirstTokenIsStoredWhenUnitOfWorkIsRolledBackOnSecondEvent() throws Exception {
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
        // Give it a bit of time to start
        Thread.sleep(200);

        eventBus.publish(events);
        assertTrue(countDownLatch.await(5, TimeUnit.SECONDS), "Expected Unit of Work to have reached clean up phase");
        verify(tokenStore, atLeastOnce()).storeToken(any(), any(), anyInt());
        assertNotNull(tokenStore.fetchToken(testSubject.getName(), 0));
    }

    @Test
    @DirtiesContext
    void testEventsWithTheSameTokenAreProcessedInTheSameBatch() throws Exception {
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

        assertTrue(countDownLatch.await(5, TimeUnit.SECONDS), "Expected Unit of Work to have reached clean up phase");
        verify(tokenStore, atLeastOnce()).storeToken(any(), any(), anyInt());
        assertNull(tokenStore.fetchToken(testSubject.getName(), 0));
    }

    @Test
    void testResetCausesEventsToBeReplayedWithCorrectContext() throws Exception {
        when(mockHandler.supportsReset()).thenReturn(true);
        final List<String> handled = new CopyOnWriteArrayList<>();
        final List<String> handledInRedelivery = new CopyOnWriteArrayList<>();
        final List<Object> contextInRedelivery = new CopyOnWriteArrayList<>();
        int segmentId = 0;

        //noinspection Duplicates
        doAnswer(i -> {
            EventMessage<?> message = i.getArgument(0);
            if (ReplayToken.isReplay(message)) {
                handledInRedelivery.add(message.getIdentifier());
                contextInRedelivery.add(ReplayToken.replayContext(message, MyResetContext.class).orElse(null));
            }
            handled.add(message.getIdentifier());
            return null;
        }).when(mockHandler).handle(any());

        eventBus.publish(createEvents(4));
        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(4, handled.size()));
        testSubject.shutDown();
        MyResetContext one = new MyResetContext("one");
        testSubject.resetTokens(one);
        // Resetting twice caused problems (see issue #559)
        MyResetContext two = new MyResetContext("two");
        testSubject.resetTokens(two);
        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(8, handled.size()));
        assertEquals(handled.subList(0, 4), handled.subList(4, 8));
        assertEquals(handled.subList(4, 8), handledInRedelivery);
        assertEquals(4, contextInRedelivery.size());
        assertTrue(contextInRedelivery.stream().allMatch(two::equals));
        assertTrue(testSubject.processingStatus().get(segmentId).isReplaying());
        assertTrue(testSubject.processingStatus().get(segmentId).getCurrentPosition().isPresent());
        assertTrue(testSubject.processingStatus().get(segmentId).getResetPosition().isPresent());

        long resetPositionAtReplay = testSubject.processingStatus().get(segmentId).getCurrentPosition().getAsLong();
        eventBus.publish(createEvents(1));

        assertWithin(1, TimeUnit.SECONDS, () -> assertFalse(
                testSubject.processingStatus().get(segmentId).isReplaying()
        ));
        assertWithin(1, TimeUnit.SECONDS, () -> assertFalse(
                testSubject.processingStatus().get(segmentId).getResetPosition().isPresent()
        ));
        assertWithin(1, TimeUnit.SECONDS, () -> assertTrue(
                testSubject.processingStatus().get(segmentId).getCurrentPosition().isPresent()
        ));
        //noinspection OptionalGetWithoutIsPresent
        assertWithin(1, TimeUnit.SECONDS, () -> assertTrue(
                testSubject.processingStatus().get(segmentId).getCurrentPosition().getAsLong() > resetPositionAtReplay
        ));

        verify(eventHandlerInvoker, times(1)).performReset(one);
        verify(eventHandlerInvoker, times(1)).performReset(two);
    }

    @Test
    void testResetTokensPassesOnResetContextToResetHandlers() throws Exception {
        String resetContext = "reset-context";
        final List<String> handled = new CopyOnWriteArrayList<>();

        when(mockHandler.supportsReset()).thenReturn(true);

        //noinspection Duplicates
        doAnswer(i -> {
            EventMessage<?> message = i.getArgument(0);
            handled.add(message.getIdentifier());
            return null;
        }).when(mockHandler).handle(any());

        eventBus.publish(createEvents(4));
        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(4, handled.size()));

        testSubject.shutDown();
        testSubject.resetTokens(resetContext);
        testSubject.start();

        verify(eventHandlerInvoker).performReset(resetContext);
    }

    @Test
    void testResetToPositionCausesCertainEventsToBeReplayed() throws Exception {
        when(mockHandler.supportsReset()).thenReturn(true);
        final List<String> handled = new CopyOnWriteArrayList<>();
        final List<String> handledInRedelivery = new CopyOnWriteArrayList<>();
        int segmentId = 0;

        //noinspection Duplicates
        doAnswer(i -> {
            EventMessage<?> message = i.getArgument(0);
            if (ReplayToken.isReplay(message)) {
                handledInRedelivery.add(message.getIdentifier());
            }
            handled.add(message.getIdentifier());
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
        assertEquals(handled.subList(2, 4), handled.subList(4, 6));
        assertEquals(handled.subList(4, 6), handledInRedelivery);
        assertTrue(testSubject.processingStatus().get(segmentId).isReplaying());
        assertTrue(testSubject.processingStatus().get(segmentId).getCurrentPosition().isPresent());
        assertTrue(testSubject.processingStatus().get(segmentId).getResetPosition().isPresent());

        long resetPositionAtReplay = testSubject.processingStatus().get(segmentId).getResetPosition().getAsLong();
        eventBus.publish(createEvents(1));

        assertWithin(1, TimeUnit.SECONDS, () -> assertFalse(
                testSubject.processingStatus().get(segmentId).isReplaying()
        ));
        assertWithin(1, TimeUnit.SECONDS, () -> assertFalse(
                testSubject.processingStatus().get(segmentId).getResetPosition().isPresent()
        ));
        assertWithin(1, TimeUnit.SECONDS, () -> assertTrue(
                testSubject.processingStatus().get(segmentId).getCurrentPosition().isPresent()
        ));
        //noinspection OptionalGetWithoutIsPresent
        assertWithin(1, TimeUnit.SECONDS, () -> assertTrue(
                testSubject.processingStatus().get(segmentId).getCurrentPosition().getAsLong() > resetPositionAtReplay
        ));

        verify(eventHandlerInvoker).performReset(NO_RESET_PAYLOAD);
    }

    @Test
    void testResetOnInitializeWithTokenResetToThatToken() throws Exception {
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
        int segmentId = 0;

        //noinspection Duplicates
        doAnswer(i -> {
            EventMessage<?> message = i.getArgument(0);
            if (ReplayToken.isReplay(message)) {
                handledInRedelivery.add(message.getIdentifier());
            }
            handled.add(message.getIdentifier());
            return null;
        }).when(mockHandler).handle(any());

        eventBus.publish(createEvents(4));
        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(2, handled.size()));
        testSubject.shutDown();
        testSubject.resetTokens();
        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(4, handled.size()));
        assertEquals(handled.subList(0, 2), handled.subList(2, 4));
        assertEquals(handled.subList(2, 4), handledInRedelivery);
        assertTrue(testSubject.processingStatus().get(segmentId).isReplaying());
        assertTrue(testSubject.processingStatus().get(segmentId).getCurrentPosition().isPresent());
        assertTrue(testSubject.processingStatus().get(segmentId).getResetPosition().isPresent());

        long resetPositionAtReplay = testSubject.processingStatus().get(segmentId).getResetPosition().getAsLong();
        eventBus.publish(createEvents(1));

        assertWithin(1, TimeUnit.SECONDS, () -> assertFalse(
                testSubject.processingStatus().get(segmentId).isReplaying()
        ));
        assertWithin(1, TimeUnit.SECONDS, () -> assertFalse(
                testSubject.processingStatus().get(segmentId).getResetPosition().isPresent()));
        assertWithin(1, TimeUnit.SECONDS, () -> assertTrue(
                testSubject.processingStatus().get(segmentId).getCurrentPosition().isPresent()
        ));
        //noinspection OptionalGetWithoutIsPresent
        assertWithin(1, TimeUnit.SECONDS, () -> assertTrue(
                testSubject.processingStatus().get(segmentId).getCurrentPosition().getAsLong() > resetPositionAtReplay
        ));

        verify(eventHandlerInvoker).performReset(NO_RESET_PAYLOAD);
    }

    @Test
    void testResetBeforeStartingPerformsANormalRun() throws Exception {
        when(mockHandler.supportsReset()).thenReturn(true);
        final List<String> handled = new CopyOnWriteArrayList<>();
        final List<String> handledInRedelivery = new CopyOnWriteArrayList<>();
        int segmentId = 0;
        //noinspection Duplicates
        doAnswer(i -> {
            EventMessage<?> message = i.getArgument(0);
            if (ReplayToken.isReplay(message)) {
                handledInRedelivery.add(message.getIdentifier());
            }
            handled.add(message.getIdentifier());
            return null;
        }).when(mockHandler).handle(any());

        testSubject.resetTokens();

        testSubject.start();
        eventBus.publish(createEvents(4));
        assertWithin(2, TimeUnit.SECONDS, () -> assertEquals(4, handled.size()));
        assertEquals(0, handledInRedelivery.size());
        assertFalse(testSubject.processingStatus().get(segmentId).isReplaying());
        assertFalse(testSubject.processingStatus().get(segmentId).getResetPosition().isPresent());
        assertTrue(testSubject.processingStatus().get(segmentId).getCurrentPosition().isPresent());
        assertTrue(testSubject.processingStatus().get(segmentId).getCurrentPosition().getAsLong() > 0);

        verify(eventHandlerInvoker).performReset(NO_RESET_PAYLOAD);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testReplayFlagAvailableWhenReplayInDifferentOrder() throws Exception {
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
            firstRun.add(i.<TrackedEventMessage<?>>getArgument(0).trackingToken());
            return null;
        }).when(eventHandlerInvoker).handle(any(), any());

        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(4, firstRun.size()));
        testSubject.shutDown();

        doAnswer(i -> {
            replayRun.add(i.<TrackedEventMessage<?>>getArgument(0).trackingToken());
            return null;
        }).when(eventHandlerInvoker).handle(any(), any());

        testSubject.resetTokens();
        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(8, replayRun.size()));

        assertEquals(GapAwareTrackingToken.newInstance(5, asList(3L, 4L)), firstRun.get(3));
        assertTrue(replayRun.get(0) instanceof ReplayToken);
        assertTrue(replayRun.get(5) instanceof ReplayToken);
        assertEquals(GapAwareTrackingToken.newInstance(6, emptySortedSet()), replayRun.get(6));

        verify(eventHandlerInvoker).performReset(NO_RESET_PAYLOAD);
    }

    @Test
    void testResetRejectedWhileRunning() {
        testSubject.start();

        assertThrows(IllegalStateException.class, testSubject::resetTokens);
    }

    @Test
    void testResetNotSupportedWhenInvokerDoesNotSupportReset() {
        when(mockHandler.supportsReset()).thenReturn(false);
        assertFalse(testSubject.supportsReset());
    }

    @Test
    void testResetRejectedWhenInvokerDoesNotSupportReset() {
        when(mockHandler.supportsReset()).thenReturn(false);

        assertThrows(IllegalStateException.class, testSubject::resetTokens);
    }

    @Test
    void testResetRejectedIfNotAllTokensCanBeClaimed() {
        tokenStore.initializeTokenSegments("test", 4);
        when(tokenStore.fetchToken("test", 3)).thenThrow(new UnableToClaimTokenException("Mock"));

        assertThrows(UnableToClaimTokenException.class, testSubject::resetTokens);
        verify(tokenStore, never()).storeToken(isNull(), anyString(), anyInt());
    }

    @Test
    void testResetTokensPassesOnResetContextToTrackingToken() throws Exception {
        String resetContext = "reset-context";
        final AtomicInteger count = new AtomicInteger();

        when(mockHandler.supportsReset()).thenReturn(true);

        //noinspection Duplicates
        doAnswer(i -> {
            count.incrementAndGet();
            return null;
        }).when(mockHandler).handle(any());

        eventBus.publish(createEvents(4));
        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(4, count.get()));
        testSubject.shutDown();
        testSubject.resetTokens(resetContext);
        testSubject.start();

        TrackingToken test = tokenStore.fetchToken("test", 0);
        assertEquals(resetContext, ReplayToken.replayContext(test, String.class).orElse(null));
    }

    private static class MyResetContext {

        private String property;

        private MyResetContext(String property) {
            this.property = property;
        }

        public String getProperty() {
            return property;
        }

        public void setProperty(String property) {
            this.property = property;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            MyResetContext that = (MyResetContext) o;

            return Objects.equals(property, that.property);
        }

        @Override
        public int hashCode() {
            return property != null ? property.hashCode() : 0;
        }
    }

    @Test
    void testWhenFailureDuringInit() throws InterruptedException {
        doThrow(new RuntimeException("Faking issue during fetchSegments"))
                .doCallRealMethod()
                .when(tokenStore).fetchSegments(anyString());

        doThrow(new RuntimeException("Faking issue during initializeTokenSegments"))
                // And on further calls
                .doNothing()
                .when(tokenStore).initializeTokenSegments(anyString(), anyInt());

        testSubject.start();

        for (int i = 0; i < 250 && testSubject.activeProcessorThreads() < 1; i++) {
            Thread.sleep(10);
        }

        assertEquals(1, testSubject.activeProcessorThreads());
    }

    @Test
    void testUpdateActiveSegmentsWhenBatchIsEmpty() throws Exception {
        int segmentId = 0;
        //noinspection unchecked
        StreamableMessageSource<TrackedEventMessage<?>> stubSource = mock(StreamableMessageSource.class);
        testSubject = TrackingEventProcessor.builder()
                                            .name("test")
                                            .eventHandlerInvoker(eventHandlerInvoker)
                                            .messageSource(stubSource)
                                            .tokenStore(tokenStore)
                                            .transactionManager(NoTransactionManager.INSTANCE).build();

        when(stubSource.openStream(any())).thenReturn(new StubTrackingEventStream(0, 1, 2, 5));
        doReturn(true, false).when(eventHandlerInvoker).canHandle(any(), any());

        testSubject.start();
        // Give it a bit of time to start
        waitForStatus("processor thread started", 200, TimeUnit.MILLISECONDS, status -> status.containsKey(0));

        waitForStatus("Segment 0 caught up", 5, TimeUnit.SECONDS, status -> status.get(0).isCaughtUp());

        EventTrackerStatus eventTrackerStatus = testSubject.processingStatus().get(segmentId);
        GapAwareTrackingToken expectedToken = GapAwareTrackingToken.newInstance(5, asList(3L, 4L));
        TrackingToken lastToken = eventTrackerStatus.getTrackingToken();
        assertTrue(lastToken.covers(expectedToken));
    }

    @Test
    void testReleaseSegment() {
        testSubject.start();
        assertWithin(5, TimeUnit.SECONDS, () -> assertEquals(1, testSubject.activeProcessorThreads()));
        testSubject.releaseSegment(0, 2, TimeUnit.SECONDS);
        assertWithin(2, TimeUnit.SECONDS, () -> assertEquals(0, testSubject.activeProcessorThreads()));
        assertWithin(5, TimeUnit.SECONDS, () -> assertEquals(1, testSubject.activeProcessorThreads()));
    }

    @Test
    void testHasAvailableSegments() {
        assertEquals(1, testSubject.availableProcessorThreads());
        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(0, testSubject.availableProcessorThreads()));
        testSubject.releaseSegment(0);
        assertWithin(2, TimeUnit.SECONDS, () -> assertEquals(1, testSubject.availableProcessorThreads()));
    }

    @Test
    @Timeout(value = 10)
    void testSplitSegments() throws InterruptedException {
        tokenStore.initializeTokenSegments(testSubject.getName(), 1);
        testSubject.start();
        waitForSegmentStart(0);
        assertTrue(testSubject.splitSegment(0).join(), "Expected split to succeed");
        assertArrayEquals(new int[]{0, 1}, tokenStore.fetchSegments(testSubject.getName()));
        waitForSegmentStart(0);
    }

    @Test
    @Timeout(value = 10)
    void testMergeSegments() throws InterruptedException {
        tokenStore.initializeTokenSegments(testSubject.getName(), 2);
        testSubject.start();
        while (testSubject.processingStatus().isEmpty()) {
            Thread.sleep(10);
        }
        int segmentId = 0;

        assertTrue(testSubject.mergeSegment(segmentId).join(), "Expected merge to succeed");
        waitForProcessingStatus(segmentId, EventTrackerStatus::isMerging);

        waitForSegmentStart(segmentId);

        assertArrayEquals(new int[]{0}, tokenStore.fetchSegments(testSubject.getName()));

        publishEvents(1);
        waitForProcessingStatus(segmentId, s -> !s.isMerging());
    }

    @Test
    @Timeout(value = 10)
    void testMergeSegments_BothClaimedByProcessor() throws Exception {
        initProcessor(TrackingEventProcessorConfiguration.forParallelProcessing(2)
                                                         .andEventAvailabilityTimeout(10, TimeUnit.MILLISECONDS)
                                                         .andBatchSize(100));
        tokenStore.initializeTokenSegments(testSubject.getName(), 2);
        List<EventMessage<?>> handledEvents = new CopyOnWriteArrayList<>();
        int segmentId = 0;

        when(mockHandler.handle(any())).thenAnswer(i -> handledEvents.add(i.getArgument(0)));

        publishEvents(10);

        testSubject.start();
        waitForActiveThreads(2);

        assertWithin(
                5, TimeUnit.SECONDS,
                () -> assertEquals(
                        10, handledEvents.stream().map(EventMessage::getIdentifier).distinct().count(),
                        "Expected message to be handled"
                )
        );

        assertFalse(testSubject.processingStatus().get(segmentId).isMerging());
        assertFalse(testSubject.processingStatus().get(segmentId).mergeCompletedPosition().isPresent());

        assertWithin(
                50, TimeUnit.MILLISECONDS,
                () -> assertTrue(testSubject.mergeSegment(segmentId).join(), "Expected merge to succeed")
        );

        EventTrackerStatus status = waitForProcessingStatus(segmentId, EventTrackerStatus::isMerging);
        assertTrue(status.mergeCompletedPosition().isPresent());
        long mergeCompletedPosition = status.mergeCompletedPosition().getAsLong();

        assertArrayEquals(new int[]{0}, tokenStore.fetchSegments(testSubject.getName()));

        publishEvents(1);
        status = waitForProcessingStatus(segmentId, s -> !s.isMerging());
        assertFalse(status.mergeCompletedPosition().isPresent());
        assertTrue(status.getCurrentPosition().isPresent());
        assertTrue(status.getCurrentPosition().getAsLong() > mergeCompletedPosition);
    }

    @Test
    @Timeout(value = 10)
    void testMergeSegments_WithExplicitReleaseOther() throws Exception {
        initProcessor(TrackingEventProcessorConfiguration.forParallelProcessing(2));
        tokenStore.initializeTokenSegments(testSubject.getName(), 2);
        List<EventMessage<?>> handledEvents = new CopyOnWriteArrayList<>();
        List<EventMessage<?>> events = new ArrayList<>();
        int segmentId = 0;
        for (int i = 0; i < 10; i++) {
            events.add(createEvent(UUID.randomUUID().toString(), 0));
        }
        when(mockHandler.handle(any())).thenAnswer(i -> handledEvents.add(i.getArgument(0)));
        eventBus.publish(events);
        testSubject.start();
        waitForActiveThreads(2);

        testSubject.releaseSegment(1);
        waitForSegmentRelease(1);

        assertWithin(
                50, TimeUnit.MILLISECONDS,
                () -> assertTrue(testSubject.mergeSegment(segmentId).join(), "Expected merge to succeed")
        );
        assertArrayEquals(new int[]{0}, tokenStore.fetchSegments(testSubject.getName()));
        waitForSegmentStart(segmentId);

        while (!Optional.ofNullable(testSubject.processingStatus().get(segmentId))
                        .map(EventTrackerStatus::isCaughtUp)
                        .orElse(false)) {
            Thread.sleep(10);
        }

        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(10, handledEvents.size()));
    }

    @Test
    @Timeout(value = 10)
    void testDoubleSplitAndMerge() throws Exception {
        tokenStore.initializeTokenSegments(testSubject.getName(), 1);
        List<EventMessage<?>> handledEvents = new CopyOnWriteArrayList<>();
        int segmentId = 0;
        when(mockHandler.handle(any())).thenAnswer(i -> handledEvents.add(i.getArgument(0)));

        publishEvents(10);

        testSubject.start();
        waitForActiveThreads(1);

        assertWithin(
                50, TimeUnit.MILLISECONDS,
                () -> assertTrue(testSubject.splitSegment(segmentId).join(), "Expected split to succeed")
        );
        waitForActiveThreads(1);

        assertWithin(
                50, TimeUnit.MILLISECONDS,
                () -> assertTrue(testSubject.splitSegment(segmentId).join(), "Expected split to succeed")
        );
        waitForActiveThreads(1);

        assertArrayEquals(new int[]{0, 1, 2}, tokenStore.fetchSegments(testSubject.getName()));

        publishEvents(20);

        waitForProcessingStatus(segmentId, EventTrackerStatus::isCaughtUp);

        assertFalse(testSubject.processingStatus().get(segmentId).isMerging());

        assertTrue(testSubject.mergeSegment(segmentId).join(), "Expected merge to succeed");
        assertArrayEquals(new int[]{0, 1}, tokenStore.fetchSegments(testSubject.getName()));

        publishEvents(10);

        waitForSegmentStart(segmentId);
        waitForProcessingStatus(segmentId, est -> est.getSegment().getMask() == 1 && est.isCaughtUp());

        assertTrue(testSubject.mergeSegment(segmentId).join(), "Expected merge to succeed");
        assertArrayEquals(new int[]{0}, tokenStore.fetchSegments(testSubject.getName()));

        assertWithin(3, TimeUnit.SECONDS, () -> assertEquals(40, handledEvents.size()));
    }

    @Test
    @Timeout(value = 10)
    void testMergeSegmentWithDifferentProcessingGroupsAndSequencingPolicies() throws Exception {
        EventMessageHandler otherHandler = mock(EventMessageHandler.class);
        when(otherHandler.canHandle(any())).thenReturn(true);
        when(otherHandler.supportsReset()).thenReturn(true);
        int segmentId = 0;
        EventHandlerInvoker mockInvoker = SimpleEventHandlerInvoker.builder()
                                                                   .eventHandlers(singleton(otherHandler))
                                                                   .sequencingPolicy(m -> 0)
                                                                   .build();
        initProcessor(
                TrackingEventProcessorConfiguration.forParallelProcessing(2).andBatchSize(5),
                builder -> builder.eventHandlerInvoker(new MultiEventHandlerInvoker(eventHandlerInvoker, mockInvoker))
        );

        List<EventMessage<?>> handledEvents = new CopyOnWriteArrayList<>();
        when(mockHandler.handle(any())).thenAnswer(i -> {
            TrackedEventMessage<?> message = i.getArgument(0);
            return handledEvents.add(message);
        });

        publishEvents(10);
        testSubject.start();

        while (testSubject.processingStatus().size() < 2
                || !testSubject.processingStatus().values().stream().allMatch(EventTrackerStatus::isCaughtUp)) {
            Thread.sleep(10);
        }

        testSubject.releaseSegment(1);

        while (testSubject.processingStatus().size() != 1
                || !testSubject.processingStatus().values().stream().allMatch(EventTrackerStatus::isCaughtUp)) {
            Thread.sleep(10);
        }

        publishEvents(10);

        testSubject.mergeSegment(segmentId);

        publishEvents(10);

        while (testSubject.processingStatus().size() != 1
                || !testSubject.processingStatus().values().stream().allMatch(EventTrackerStatus::isCaughtUp)) {
            Thread.sleep(10);
        }

        assertWithin(5, TimeUnit.SECONDS, () -> assertEquals(30, handledEvents.size()));

        Thread.sleep(100);
        assertEquals(30, handledEvents.size());
    }

    @Test
    @Timeout(value = 10)
    void testMergeSegmentsDuringReplay() throws Exception {
        initProcessor(TrackingEventProcessorConfiguration.forParallelProcessing(2));
        tokenStore.initializeTokenSegments(testSubject.getName(), 2);
        List<EventMessage<?>> handledEvents = new CopyOnWriteArrayList<>();
        List<EventMessage<?>> replayedEvents = new CopyOnWriteArrayList<>();
        int segmentId = 0;
        when(mockHandler.handle(any())).thenAnswer(i -> {
            TrackedEventMessage<?> message = i.getArgument(0);
            if (ReplayToken.isReplay(message)) {
                replayedEvents.add(message);
            } else {
                handledEvents.add(message);
            }
            return null;
        });
        for (int i = 0; i < 10; i++) {
            eventBus.publish(createEvent(UUID.randomUUID().toString(), 0));
        }
        testSubject.start();
        while (testSubject.processingStatus().size() < 2
                || !testSubject.processingStatus().values().stream().allMatch(EventTrackerStatus::isCaughtUp)) {
            Thread.sleep(10);
        }

        testSubject.shutDown();
        testSubject.resetTokens();

        testSubject.releaseSegment(1);
        testSubject.start();
        waitForActiveThreads(1);
        Thread.yield();

        CompletableFuture<Boolean> mergeResult = testSubject.mergeSegment(segmentId);

        publishEvents(20);

        assertTrue(mergeResult.join(), "Expected merge to succeed");
        assertArrayEquals(new int[]{0}, tokenStore.fetchSegments(testSubject.getName()));
        waitForSegmentStart(segmentId);

        assertWithin(10, TimeUnit.SECONDS, () -> assertEquals(30, handledEvents.size()));
        Thread.sleep(100);
        assertEquals(30, handledEvents.size());

        // Make sure replay events are only delivered once.
        assertEquals(
                replayedEvents.stream().map(EventMessage::getIdentifier).distinct().count(), replayedEvents.size()
        );
    }

    @Test
    @Timeout(value = 10)
    void testReplayDuringIncompleteMerge() throws Exception {
        int segmentIdZero = 0;
        int segmentIdOne = 1;
        List<EventMessage<?>> handledEvents = new CopyOnWriteArrayList<>();
        List<EventMessage<?>> initialEvents = IntStream.range(0, 10)
                                                       .mapToObj(i -> createEvent(UUID.randomUUID().toString(), 0))
                                                       .collect(toList());

        tokenStore.initializeTokenSegments(testSubject.getName(), 2);
        initProcessor(TrackingEventProcessorConfiguration.forParallelProcessing(2));
        when(mockHandler.handle(any())).thenAnswer(i -> {
            TrackedEventMessage<?> message = i.getArgument(0);
            if (ReplayToken.isReplay(message)) {
                // Ignore replays
                return null;
            }
            return handledEvents.add(message);
        });

        eventBus.publish(initialEvents);
        testSubject.start();

        while (testSubject.processingStatus().size() < 2
                || !testSubject.processingStatus().values().stream().allMatch(EventTrackerStatus::isCaughtUp)) {
            Thread.sleep(10);
        }

        testSubject.releaseSegment(segmentIdOne);
        while (testSubject.processingStatus().containsKey(segmentIdOne)) {
            Thread.yield();
        }

        publishEvents(10);

        CompletableFuture<Boolean> mergeResult = testSubject.mergeSegment(segmentIdZero);
        assertTrue(mergeResult.join(), "Expected merge to succeed");
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(1, testSubject.processingStatus().size()));

        testSubject.shutDown();
        testSubject.resetTokens();
        publishEvents(10);
        testSubject.start();

        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(1, testSubject.processingStatus().size()));

        assertArrayEquals(new int[]{0}, tokenStore.fetchSegments(testSubject.getName()));
        waitForSegmentStart(segmentIdZero);

        assertWithin(
                1, TimeUnit.SECONDS,
                () -> assertTrue(testSubject.processingStatus().get(segmentIdZero).isCaughtUp())
        );

        // Replayed messages aren't counted
        assertEquals(30, handledEvents.size());
    }

    @Test
    @Timeout(value = 10)
    void testMergeWithIncompatibleSegmentRejected() throws InterruptedException {
        initProcessor(TrackingEventProcessorConfiguration.forParallelProcessing(3));

        testSubject.start();
        waitForActiveThreads(3);
        assertTrue(testSubject.processingStatus().containsKey(0));
        assertTrue(testSubject.processingStatus().containsKey(1));
        assertTrue(testSubject.processingStatus().containsKey(2));

        // 1 is not "mergeable" with 0, because 0 itself was already split
        testSubject.releaseSegment(0);
        testSubject.releaseSegment(2);

        testSubject.processingStatus().values().forEach(status -> assertFalse(status::isMerging));

        while (testSubject.processingStatus().size() > 1) {
            Thread.sleep(10);
        }

        CompletableFuture<Boolean> actual = testSubject.mergeSegment(1);

        assertFalse(actual.join(), "Expected merge to be rejected");
    }

    @Test
    @Timeout(value = 10)
    void testMergeWithSingleSegmentRejected() throws InterruptedException {
        int numberOfSegments = 1;
        initProcessor(TrackingEventProcessorConfiguration.forParallelProcessing(numberOfSegments));
        int segmentId = 0;

        testSubject.start();
        waitForActiveThreads(1);

        CompletableFuture<Boolean> actual = testSubject.mergeSegment(segmentId);

        assertFalse(actual.join(), "Expected merge to be rejected");
        assertFalse(testSubject.processingStatus().get(segmentId).isMerging());
    }

    /**
     * This test is a follow-up from issue https://github.com/AxonIQ/axon-server-se/issues/135
     */
    @Test
    @Timeout(value = 10)
    void testMergeInvertedSegmentOrder() throws InterruptedException {
        int numberOfSegments = 4;
        initProcessor(TrackingEventProcessorConfiguration.forParallelProcessing(numberOfSegments));
        testSubject.start();
        waitForActiveThreads(4);
        int segmentId = 3;
        CompletableFuture<Boolean> mergeResult = testSubject.mergeSegment(segmentId);
        assertTrue(mergeResult.join(), "Expected merge to succeed");
        verify(tokenStore).deleteToken("test", 3);
        verify(tokenStore).storeToken(any(), eq("test"), eq(1));
    }

    /**
     * This test is a follow-up from issue https://github.com/AxonFramework/AxonFramework/issues/1212
     */
    @Test
    void testThrownErrorBubblesUp() {
        AtomicReference<Throwable> thrownException = new AtomicReference<>();

        EventHandlerInvoker eventHandlerInvoker = mock(EventHandlerInvoker.class);
        when(eventHandlerInvoker.canHandle(any(), any())).thenThrow(new TestError());

        initProcessor(
                TrackingEventProcessorConfiguration.forSingleThreadedProcessing()
                                                   .andThreadFactory(name -> runnableForThread -> new Thread(() -> {
                                                       try {
                                                           runnableForThread.run();
                                                       } catch (Throwable t) {
                                                           thrownException.set(t);
                                                       }
                                                   })),
                builder -> builder.eventHandlerInvoker(eventHandlerInvoker)
        );

        eventBus.publish(createEvents(1));
        testSubject.start();

        assertWithin(2, TimeUnit.SECONDS, () -> assertTrue(testSubject.isError()));
        assertWithin(
                15, TimeUnit.SECONDS,
                () -> assertTrue(thrownException.get() instanceof TestError)
        );
    }

    @Test
    void retrievingStorageIdentifierWillCacheResults() {
        String id = testSubject.getTokenStoreIdentifier();
        InOrder inOrder = inOrder(mockTransactionManager, tokenStore);
        inOrder.verify(mockTransactionManager).fetchInTransaction(any());
        inOrder.verify(tokenStore, times(1)).retrieveStorageIdentifier();

        String id2 = testSubject.getTokenStoreIdentifier();
        // expect no extra invocations
        verify(tokenStore, times(1)).retrieveStorageIdentifier();

        assertEquals(id, id2);
    }

    /**
     * This test can spot three invocations of the {@link EventTrackerStatusChangeListener}, but asserts two:
     * <ol>
     *     <li> First call is when the single active {@link org.axonframework.eventhandling.Segment} is added.</li>
     *     <li> Second call is when the status transitions to {@link EventTrackerStatus#isCaughtUp()}.</li>
     *     <li>
     *         The last not asserted call is when the {@link TrackingEventProcessor} is shutting down, which removes the
     *         status. This isn't taking into account as it is part of the {@link #tearDown()}.
     *     </li>
     * </ol>
     * <p>
     * More changes occur on the {@link EventTrackerStatus}, but by default only complete additions, removals and {@code
     * boolean} field updates are included as changes.
     */
    @Test
    void testPublishedEventsUpdateStatusAndHitChangeListener() throws Exception {
        CountDownLatch eventHandlingLatch = new CountDownLatch(2);
        doAnswer(invocation -> {
            eventHandlingLatch.countDown();
            return null;
        }).when(mockHandler).handle(any());

        CountDownLatch statusChangeLatch = new CountDownLatch(2);
        AtomicInteger addedStatusCounter = new AtomicInteger(0);
        AtomicInteger updatedStatusCounter = new AtomicInteger(0);
        AtomicInteger removedStatusCounter = new AtomicInteger(0);
        EventTrackerStatusChangeListener statusChangeListener = updatedTrackerStatus -> {
            assertEquals(1, updatedTrackerStatus.size());
            EventTrackerStatus eventTrackerStatus = updatedTrackerStatus.get(0);
            if (eventTrackerStatus.trackerAdded()) {
                addedStatusCounter.getAndIncrement();
            } else if (eventTrackerStatus.trackerRemoved()) {
                removedStatusCounter.getAndIncrement();
            } else {
                updatedStatusCounter.getAndIncrement();
            }
            statusChangeLatch.countDown();
        };

        TrackingEventProcessorConfiguration tepConfiguration =
                TrackingEventProcessorConfiguration.forSingleThreadedProcessing()
                                                   .andEventTrackerStatusChangeListener(statusChangeListener);
        initProcessor(tepConfiguration);

        testSubject.start();
        // Give it a bit of time to start
        Thread.sleep(200);
        publishEvents(2);

        assertTrue(eventHandlingLatch.await(5, TimeUnit.SECONDS));
        assertTrue(statusChangeLatch.await(5, TimeUnit.SECONDS));
        assertEquals(1, addedStatusCounter.get());
        assertEquals(1, updatedStatusCounter.get());
        assertEquals(0, removedStatusCounter.get());
    }

    /**
     * This test can spot five invocations of the {@link EventTrackerStatusChangeListener}, but asserts four:
     * <ol>
     *     <li> First call is when the single active {@link org.axonframework.eventhandling.Segment} is added.</li>
     *     <li> Second call is when the status transitions to {@link EventTrackerStatus#isCaughtUp()}.</li>
     *     <li> Third call is when the {@link EventTrackerStatus#getCurrentPosition()} moves to 0.</li>
     *     <li> Fourth call is when the {@link EventTrackerStatus#getCurrentPosition()} moves to 1.</li>
     *     <li>
     *         The last not asserted call is when the {@link TrackingEventProcessor} is shutting down, which removes the
     *         status. This isn't taking into account as it is part of the {@link #tearDown()}.
     *     </li>
     * </ol>
     */
    @Test
    void testPublishedEventsUpdateStatusAndHitChangeListenerIncludingPositions() throws Exception {
        CountDownLatch eventHandlingLatch = new CountDownLatch(2);
        doAnswer(invocation -> {
            eventHandlingLatch.countDown();
            return null;
        }).when(mockHandler).handle(any());

        CountDownLatch statusChangeLatch = new CountDownLatch(4);
        AtomicInteger addedStatusCounter = new AtomicInteger(0);
        AtomicInteger updatedStatusCounter = new AtomicInteger(0);
        AtomicInteger removedStatusCounter = new AtomicInteger(0);
        EventTrackerStatusChangeListener statusChangeListener = new EventTrackerStatusChangeListener() {
            @Override
            public void onEventTrackerStatusChange(Map<Integer, EventTrackerStatus> updatedTrackerStatus) {
                assertEquals(1, updatedTrackerStatus.size());
                EventTrackerStatus eventTrackerStatus = updatedTrackerStatus.get(0);
                if (eventTrackerStatus.trackerAdded()) {
                    addedStatusCounter.getAndIncrement();
                } else if (eventTrackerStatus.trackerRemoved()) {
                    removedStatusCounter.getAndIncrement();
                } else {
                    updatedStatusCounter.getAndIncrement();
                }
                statusChangeLatch.countDown();
            }

            @Override
            public boolean validatePositions() {
                return true;
            }
        };

        TrackingEventProcessorConfiguration tepConfiguration =
                TrackingEventProcessorConfiguration.forSingleThreadedProcessing()
                                                   .andEventTrackerStatusChangeListener(statusChangeListener);
        initProcessor(tepConfiguration);

        testSubject.start();
        // Give it a bit of time to start
        Thread.sleep(200);
        publishEvents(2);

        assertTrue(eventHandlingLatch.await(5, TimeUnit.SECONDS));
        assertTrue(statusChangeLatch.await(5, TimeUnit.SECONDS));
        assertEquals(1, addedStatusCounter.get());
        assertEquals(3, updatedStatusCounter.get());
        assertEquals(0, removedStatusCounter.get());
    }

    @Test
    @Timeout(value = 15)
    void testSplitAndMergeInfluenceOnChangeListenerInvocations() throws InterruptedException {
        int firstSegment = 0;
        int secondSegment = 1;

        CountDownLatch addedStatusLatch = new CountDownLatch(4);
        CountDownLatch updatedStatusLatch = new CountDownLatch(1);
        CountDownLatch removedStatusLatch = new CountDownLatch(3);
        EventTrackerStatusChangeListener statusChangeListener = updatedTrackerStatus -> {
            assertEquals(1, updatedTrackerStatus.size());
            EventTrackerStatus eventTrackerStatus = updatedTrackerStatus.values().iterator().next();
            if (eventTrackerStatus.trackerAdded()) {
                addedStatusLatch.countDown();
            } else if (eventTrackerStatus.trackerRemoved()) {
                removedStatusLatch.countDown();
            } else {
                updatedStatusLatch.countDown();
            }
        };

        // Provide some spare threads so that assertions don't block the processor.
        // Added, lower the event availability timeout to have the TEP threads pick up the segments faster.
        TrackingEventProcessorConfiguration tepConfiguration =
                TrackingEventProcessorConfiguration.forParallelProcessing(4)
                                                   .andInitialSegmentsCount(2)
                                                   .andEventAvailabilityTimeout(50, TimeUnit.MILLISECONDS)
                                                   .andEventTrackerStatusChangeListener(statusChangeListener);
        initProcessor(tepConfiguration);
        tokenStore.initializeTokenSegments(testSubject.getName(), 1);

        publishEvents(2);
        testSubject.start();
        assertWithin(5, TimeUnit.SECONDS, () -> assertTrue(testSubject.processingStatus().containsKey(firstSegment)));

        assertTrue(testSubject.splitSegment(firstSegment).join(), "Expected split to succeed");
        assertArrayEquals(new int[]{firstSegment, secondSegment}, tokenStore.fetchSegments(testSubject.getName()));
        assertWithin(5, TimeUnit.SECONDS, () -> assertTrue(testSubject.processingStatus().containsKey(secondSegment)));

        assertWithin(
                500, TimeUnit.MILLISECONDS,
                () -> assertTrue(testSubject.mergeSegment(firstSegment).join(), "Expected merge to succeed")
        );
        assertArrayEquals(new int[]{firstSegment}, tokenStore.fetchSegments(testSubject.getName()));
        assertWithin(5, TimeUnit.SECONDS, () -> assertTrue(testSubject.processingStatus().containsKey(firstSegment)));

        assertTrue(addedStatusLatch.await(5, TimeUnit.SECONDS));
        assertTrue(updatedStatusLatch.await(5, TimeUnit.SECONDS));
        assertTrue(removedStatusLatch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testCaughtUpSetToTrueAfterWaitingForEventAvailabilityTimeout() {
        AtomicBoolean hasNextInvoked = new AtomicBoolean(false);
        AtomicBoolean hasNextAvailableTimedOut = new AtomicBoolean(true);
        // Set up two threads and one segment, to provide more "space" for assertions.
        TrackingEventProcessorConfiguration tepConfiguration =
                TrackingEventProcessorConfiguration.forParallelProcessing(2)
                                                   .andInitialSegmentsCount(1)
                                                   .andEventAvailabilityTimeout(100, TimeUnit.MILLISECONDS);
        EventStore enhancedEventStore = new EmbeddedEventStore(
                EmbeddedEventStore.builder().storageEngine(new InMemoryEventStorageEngine())
        ) {
            @Override
            public TrackingEventStream openStream(TrackingToken trackingToken) {
                //noinspection resource
                TrackingEventStream trackingEventStream = super.openStream(trackingToken);
                return new TrackingEventStream() {
                    @Override
                    public Optional<TrackedEventMessage<?>> peek() {
                        return trackingEventStream.peek();
                    }

                    @Override
                    public boolean hasNextAvailable(int timeout, TimeUnit unit) throws InterruptedException {
                        hasNextInvoked.set(true);
                        boolean result = trackingEventStream.hasNextAvailable(timeout, unit);
                        hasNextAvailableTimedOut.set(false);
                        // Add sleep to ensure switching hasNextAvailableTimedOut and it's assertion has precedence
                        // over regular execution of the TrackingEventProcessor.
                        Thread.sleep(5);
                        return result;
                    }

                    @Override
                    public TrackedEventMessage<?> nextAvailable() throws InterruptedException {
                        return trackingEventStream.nextAvailable();
                    }

                    @Override
                    public void close() {
                        trackingEventStream.close();
                    }
                };
            }
        };
        initProcessor(tepConfiguration, builder -> builder.messageSource(enhancedEventStore));
        testSubject.start();

        assertWithin(100, TimeUnit.MILLISECONDS, () -> assertTrue(hasNextInvoked.get()));

        assertUntil(hasNextAvailableTimedOut::get, Duration.ofMillis(10), () -> {
            EventTrackerStatus trackerOneStatus = testSubject.processingStatus().get(0);
            assertNotNull(trackerOneStatus);
            assertFalse(trackerOneStatus.isCaughtUp());
        });

        assertWithin(1, TimeUnit.SECONDS, () -> assertTrue(testSubject.processingStatus().get(0).isCaughtUp()));
    }

    @Test
    void testRefuseStartDuringShutdown() throws Exception {
        initProcessor(TrackingEventProcessorConfiguration.forSingleThreadedProcessing()
                                                         .andEventAvailabilityTimeout(1000, TimeUnit.MILLISECONDS));
        CountDownLatch cdl = new CountDownLatch(1);
        publishEvents(10);
        doAnswer(i -> {
            cdl.countDown();
            Thread.sleep(100);
            return i.callRealMethod();
        }).when(eventHandlerInvoker).handle(any(), any());
        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertFalse(testSubject.processingStatus().isEmpty()));

        cdl.await();
        testSubject.shutdownAsync();

        IllegalStateException result = assertThrows(IllegalStateException.class, () -> testSubject.start());
        assertTrue(result.getMessage().contains("pending shutdown"));
    }

    @Test
    @Timeout(value = 1000)
    void testIsReplayingWhenNotCaughtUp() throws Exception {
        when(mockHandler.supportsReset()).thenReturn(true);

        final List<String> handled = new CopyOnWriteArrayList<>();
        final List<String> handledInRedelivery = new CopyOnWriteArrayList<>();
        int segmentId = 0;
        int numberOfEvents = 750;

        AtomicInteger delay = new AtomicInteger(0);

        //noinspection Duplicates
        doAnswer(i -> {
            EventMessage<?> message = i.getArgument(0);
            if (ReplayToken.isReplay(message)) {
                handledInRedelivery.add(message.getIdentifier());
            }
            handled.add(message.getIdentifier());
            Thread.sleep(delay.get());
            return null;
        }).when(mockHandler).handle(any());

        // ensure some events have been handled by the TEP
        eventBus.publish(createEvents(numberOfEvents));
        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(numberOfEvents, handled.size()));
        assertEquals(0, handledInRedelivery.size());

        // initiate reset to toggle replay status
        testSubject.shutDown();
        testSubject.resetTokens();
        assertWithin(1, TimeUnit.SECONDS, () -> assertTrue(testSubject.processingStatus().isEmpty()));

        // set a processing delay to ensure we see some of the redelivery events first
        delay.set(10);

        testSubject.start();

        assertWithin(
                100, TimeUnit.MILLISECONDS,
                () -> {
                    assertFalse(testSubject.processingStatus().isEmpty());
                    assertFalse(testSubject.processingStatus().get(segmentId).isCaughtUp());
                    assertTrue(testSubject.processingStatus().get(segmentId).isReplaying());
                    assertTrue(testSubject.isReplaying());
                }
        );

        // remove the processing delay to rush to the end
        delay.set(0);

        assertWithin(
                1, TimeUnit.SECONDS,
                () -> {
                    assertTrue(testSubject.processingStatus().get(segmentId).isCaughtUp());
                    assertTrue(testSubject.processingStatus().get(segmentId).isReplaying());
                    assertFalse(testSubject.isReplaying());
                }
        );
    }

    @Test
    void testProcessorOnlyTriesToClaimAvailableSegments() {
        tokenStore.storeToken(new GlobalSequenceTrackingToken(1L), "test", 0);
        tokenStore.storeToken(new GlobalSequenceTrackingToken(2L), "test", 1);
        tokenStore.storeToken(new GlobalSequenceTrackingToken(1L), "test", 2);
        tokenStore.storeToken(new GlobalSequenceTrackingToken(1L), "test", 3);
        when(tokenStore.fetchAvailableSegments(testSubject.getName()))
                .thenReturn(Collections.singletonList(Segment.computeSegment(2, 0, 1, 2, 3)));

        testSubject.start();

        eventBus.publish(createEvents(1));

        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(1, testSubject.processingStatus().size()));
        assertWithin(1, TimeUnit.SECONDS, () -> assertTrue(testSubject.processingStatus().containsKey(2)));
        verify(tokenStore, never())
                .fetchToken(eq(testSubject.getName()), intThat(i -> Arrays.asList(0, 1, 3).contains(i)));
    }

    @Test
    void testShutdownTerminatesWorkersAfterConfiguredWorkerTerminationTimeout() throws Exception {
        int testWorkerTerminationTimeout = 50;
        List<Thread> createdThreads = new CopyOnWriteArrayList<>();
        // A higher event availability timeout will block a worker thread that should shut down
        initProcessor(TrackingEventProcessorConfiguration.forParallelProcessing(2)
                                                         .andInitialSegmentsCount(2)
                                                         .andBatchSize(100)
                                                         .andThreadFactory(n -> r -> {
                                                             Thread thread = new Thread(r, n);
                                                             createdThreads.add(thread);
                                                             return thread;
                                                         })
                                                         .andWorkerTerminationTimeout(testWorkerTerminationTimeout)
                                                         .andEventAvailabilityTimeout(20, TimeUnit.SECONDS));

        testSubject.start();

        // sleep a little to reach the EventAvailabilityTimeout usage on the Event Stream
        Thread.sleep(500);

        assertEquals(2, createdThreads.size());

        CompletableFuture<Void> result = testSubject.shutdownAsync();
        assertWithin(testWorkerTerminationTimeout * 2, TimeUnit.MILLISECONDS, () -> assertTrue(result.isDone()));
        assertFalse(createdThreads.get(0).isAlive());
        assertFalse(createdThreads.get(1).isAlive());
    }

    private void waitForStatus(String description,
                               long time,
                               TimeUnit unit,
                               Predicate<Map<Integer, EventTrackerStatus>> status) throws InterruptedException {
        long deadline = System.currentTimeMillis() + unit.toMillis(time);
        while (!status.test(testSubject.processingStatus())) {
            if (deadline < System.currentTimeMillis()) {
                fail("Expected state '" + description + "'' within " + time + " " + unit.name());
            }
            Thread.sleep(10);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void waitForSegmentStart(int segmentId) throws InterruptedException {
        while (!testSubject.processingStatus().containsKey(segmentId)) {
            Thread.sleep(10);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void waitForSegmentRelease(int segmentId) throws InterruptedException {
        while (testSubject.processingStatus().containsKey(segmentId)) {
            Thread.sleep(10);
        }
    }

    private void waitForActiveThreads(int minimalThreadCount) throws InterruptedException {
        while (testSubject.processingStatus().size() < minimalThreadCount) {
            Thread.sleep(10);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private EventTrackerStatus waitForProcessingStatus(int segmentId,
                                                       Predicate<EventTrackerStatus> expectedStatus)
            throws InterruptedException {
        EventTrackerStatus status = testSubject.processingStatus().get(segmentId);
        while (!Optional.ofNullable(status)
                        .map(expectedStatus::test)
                        .orElse(false)) {
            Thread.sleep(1);
            status = testSubject.processingStatus().get(segmentId);
        }
        return status;
    }

    private void publishEvents(int nrOfEvents) {
        for (int i = 0; i < nrOfEvents; i++) {
            eventBus.publish(createEvent(UUID.randomUUID().toString(), 0));
        }
    }

    private static class StubTrackingEventStream implements TrackingEventStream {

        private final Queue<TrackedEventMessage<?>> eventMessages;

        public StubTrackingEventStream(long... tokens) {
            GapAwareTrackingToken lastToken = GapAwareTrackingToken.newInstance(-1, emptySortedSet());
            eventMessages = new LinkedList<>();
            for (Long seq : tokens) {
                lastToken = lastToken.advanceTo(seq, 1000);
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

    private static class TestError extends Error {

        private static final long serialVersionUID = -5579826202840099704L;
    }
}
