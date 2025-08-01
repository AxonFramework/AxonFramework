/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.eventhandling.pooled;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.eventhandling.DefaultEventProcessorSpanFactory;
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.PropagatingErrorHandler;
import org.axonframework.eventhandling.ReplayToken;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.eventstreaming.EventCriteria;
import org.axonframework.eventstreaming.StreamableEventSource;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.tracing.TestSpanFactory;
import org.axonframework.utils.AsyncInMemoryStreamableEventSource;
import org.axonframework.utils.DelegateScheduledExecutorService;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.axonframework.eventhandling.EventTestUtils.createEvents;
import static org.axonframework.utils.AssertUtils.assertWithin;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link PooledStreamingEventProcessor}.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 */
class PooledStreamingEventProcessorTest {

    private static final Logger logger = LoggerFactory.getLogger(PooledStreamingEventProcessorTest.class);

    private static final String PROCESSOR_NAME = "test";

    private PooledStreamingEventProcessor testSubject;
    private EventHandlerInvoker stubEventHandler; // TODO: remove in TODO #3304 - Integrate event replay logic into Event Handling Component
    private EventHandlingComponent stubEventHandlingComponent;
    private AsyncInMemoryStreamableEventSource stubMessageSource;
    private InMemoryTokenStore tokenStore;
    private ScheduledExecutorService coordinatorExecutor;
    private ScheduledExecutorService workerExecutor;
    private TestSpanFactory spanFactory;

    @BeforeEach
    void setUp() {
        stubMessageSource = new AsyncInMemoryStreamableEventSource();
        stubEventHandler = mock(EventHandlerInvoker.class);
        stubEventHandlingComponent = mock(EventHandlingComponent.class);
        tokenStore = spy(new InMemoryTokenStore());
        coordinatorExecutor = new DelegateScheduledExecutorService(Executors.newScheduledThreadPool(2));
        workerExecutor = new DelegateScheduledExecutorService(Executors.newScheduledThreadPool(8));
        spanFactory = new TestSpanFactory();

        setTestSubject(createTestSubject());

        when(stubEventHandlingComponent.supports(any())).thenReturn(true);
        when(stubEventHandlingComponent.handle(any(), any())).thenReturn(MessageStream.empty());
        when(stubEventHandlingComponent.sequenceIdentifierFor(any())).thenAnswer(
                e -> e.getArgument(0, EventMessage.class).identifier()
        );
    }

    private void setTestSubject(PooledStreamingEventProcessor testSubject) {
        this.testSubject = testSubject;
    }

    private PooledStreamingEventProcessor createTestSubject() {
        return createTestSubject(builder -> builder);
    }

    private PooledStreamingEventProcessor createTestSubject(
            UnaryOperator<PooledStreamingEventProcessor.Builder> customization
    ) {
        PooledStreamingEventProcessor.Builder processorBuilder =
                PooledStreamingEventProcessor.builder()
                                             .name(PROCESSOR_NAME)
                                             .eventHandlingComponent(stubEventHandlingComponent)
                                             .errorHandler(PropagatingErrorHandler.instance())
                                             .eventSource(stubMessageSource)
                                             .tokenStore(tokenStore)
                                             .transactionManager(NoTransactionManager.instance())
                                             .coordinatorExecutor(coordinatorExecutor)
                                             .workerExecutor(workerExecutor)
                                             .initialSegmentCount(8)
                                             .claimExtensionThreshold(500)
                                             .spanFactory(DefaultEventProcessorSpanFactory.builder()
                                                                                          .spanFactory(spanFactory)
                                                                                          .build());
        return customization.apply(processorBuilder).build();
    }

    @AfterEach
    void tearDown() {
        testSubject.shutDown();
        coordinatorExecutor.shutdown();
        workerExecutor.shutdown();
    }

    @Test
    void retriesWhenTokenInitializationInitiallyFails() {
        InMemoryTokenStore spy = tokenStore;
        setTestSubject(createTestSubject(b -> b.tokenStore(spy)));

        doThrow(new RuntimeException("Simulated failure")).doCallRealMethod()
                                                          .when(spy)
                                                          .initializeTokenSegments(any(), anyInt(), any());

        List<EventMessage<Integer>> events =
                createEvents(100);
        events.forEach(stubMessageSource::publishMessage);
        testSubject.start();

        assertTrue(testSubject.isRunning());

        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(8, testSubject.processingStatus().size()));
        assertWithin(2, TimeUnit.SECONDS, () -> {
            long nonNullTokens = IntStream.range(0, 8)
                                          .mapToObj(i -> tokenStore.fetchToken(PROCESSOR_NAME, i))
                                          .filter(Objects::nonNull)
                                          .count();
            assertEquals(8, nonNullTokens);
        });
        assertEquals(8, testSubject.processingStatus().size());
    }

    @Test
    void startShutsDownImmediatelyIfCoordinatorExecutorThrowsAnException() {
        ScheduledExecutorService spiedCoordinatorExecutor = spy(coordinatorExecutor);
        doThrow(new IllegalArgumentException("Some exception")).when(spiedCoordinatorExecutor)
                                                               .submit(any(Runnable.class));

        setTestSubject(createTestSubject(builder -> builder.coordinatorExecutor(spiedCoordinatorExecutor)));

        assertThrows(IllegalArgumentException.class, testSubject::start);
        assertFalse(testSubject.isRunning());
    }

    @Test
    void secondStartInvocationIsIgnored() {
        ScheduledExecutorService spiedCoordinatorExecutor = spy(coordinatorExecutor);

        setTestSubject(createTestSubject(builder -> builder.coordinatorExecutor(spiedCoordinatorExecutor)));

        testSubject.start();
        // The second invocation does not cause the Coordinator to schedule another CoordinationTask.
        testSubject.start();
        verify(spiedCoordinatorExecutor, times(1)).submit(any(Runnable.class));
    }

    @Test
    void startingProcessorClaimsAllAvailableTokens() {
        startAndAssertProcessorClaimsAllTokens();
    }

    private void startAndAssertProcessorClaimsAllTokens() {
        List<EventMessage<Integer>> events =
                createEvents(100);
        events.forEach(stubMessageSource::publishMessage);

        testSubject.start();

        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(8, testSubject.processingStatus().size()));
        assertWithin(2, TimeUnit.SECONDS, () -> {
            long nonNullTokens = IntStream.range(0, 8)
                                          .mapToObj(i -> tokenStore.fetchToken(PROCESSOR_NAME, i))
                                          .filter(Objects::nonNull)
                                          .count();
            assertEquals(8, nonNullTokens);
        });
        assertEquals(8, testSubject.processingStatus().size());
    }

    @Test
    void handlingEventsAreCorrectlyTraced() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(8);
        List<Message<?>> invokedMessages = new CopyOnWriteArrayList<>();
        doAnswer(
                answer -> {
                    EventMessage<?> message = answer.getArgument(0, EventMessage.class);
                    invokedMessages.add(message);
                    spanFactory.verifySpanActive("StreamingEventProcessor.batch");
                    spanFactory.verifySpanActive("StreamingEventProcessor.process", message);
                    countDownLatch.countDown();
                    return MessageStream.empty();
                }
        ).when(stubEventHandlingComponent).handle(any(), any());

        List<EventMessage<Integer>> events = createEvents(8);
        events.forEach(stubMessageSource::publishMessage);
        testSubject.start();
        assertTrue(countDownLatch.await(5, TimeUnit.SECONDS));
        invokedMessages.forEach(
                e -> assertWithin(
                        1, TimeUnit.SECONDS,
                        () -> spanFactory.verifySpanCompleted("StreamingEventProcessor.process", e)
                )
        );
        spanFactory.verifySpanCompleted("StreamingEventProcessor.batch");
    }

    @Test
    void handlingEventsHaveSegmentAndTokenInUnitOfWork() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(8);
        doAnswer(
                answer -> {
                    var processingContext = answer.getArgument(1, ProcessingContext.class);
                    boolean containsSegment = Segment.fromContext(processingContext).isPresent();
                    boolean containsToken = TrackingToken.fromContext(processingContext).isPresent();
                    if (!containsSegment) {
                        logger.error("UoW didn't contain the segment!");
                        return null;
                    }
                    if (!containsToken) {
                        logger.error("UoW didn't contain the token!");
                        return null;
                    }
                    countDownLatch.countDown();
                    return MessageStream.empty();
                }
        ).when(stubEventHandlingComponent).handle(any(), any());

        List<EventMessage<Integer>> events = createEvents(8);
        events.forEach(stubMessageSource::publishMessage);
        testSubject.start();
        assertTrue(countDownLatch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void processorOnlyTriesToClaimAvailableSegments() {
        tokenStore.storeToken(new GlobalSequenceTrackingToken(1L), "test", 0);
        tokenStore.storeToken(new GlobalSequenceTrackingToken(2L), "test", 1);
        tokenStore.storeToken(new GlobalSequenceTrackingToken(1L), "test", 2);
        tokenStore.storeToken(new GlobalSequenceTrackingToken(1L), "test", 3);
        when(tokenStore.fetchAvailableSegments(testSubject.getName()))
                .thenReturn(Collections.singletonList(Segment.computeSegment(2, 0, 1, 2, 3)));

        testSubject.start();

        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(1, testSubject.processingStatus().size()));
        assertWithin(1, TimeUnit.SECONDS, () -> assertTrue(testSubject.processingStatus().containsKey(2)));
        verify(tokenStore, never())
                .fetchToken(eq(testSubject.getName()), intThat(i -> Arrays.asList(0, 1, 3).contains(i)));
    }

    @Disabled("TODO #3304 - Integrate event replay logic into Event Handling Component")
    @Test
    void startingAfterShutdownLetsProcessorProceed() {
        when(stubEventHandler.supportsReset()).thenReturn(true);

        testSubject.start();
        testSubject.shutDown();

        List<EventMessage<Integer>> events = createEvents(100);
        events.forEach(stubMessageSource::publishMessage);

        testSubject.start();

        assertWithin(
                1, TimeUnit.SECONDS,
                () -> assertEquals(8, testSubject.processingStatus().size())
        );
        assertWithin(2, TimeUnit.SECONDS, () -> {
            long nonNullTokens = IntStream.range(0, 8)
                                          .mapToObj(i -> tokenStore.fetchToken(PROCESSOR_NAME, i))
                                          .filter(Objects::nonNull)
                                          .count();
            assertEquals(8, nonNullTokens);
        });
        assertEquals(8, testSubject.processingStatus().size());
    }

    @Test
    void allTokensUpdatedToLatestValue() {
        List<EventMessage<Integer>> events = createEvents(100);
        events.forEach(stubMessageSource::publishMessage);

        testSubject.start();

        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(8, testSubject.processingStatus().size()));
        assertWithin(6, TimeUnit.SECONDS, () -> {
            long lowestToken = IntStream.range(0, 8)
                                        .mapToObj(i -> tokenStore.fetchToken(testSubject.getName(), i))
                                        .mapToLong(this::tokenPosition)
                                        .min()
                                        .orElse(-1);
            assertEquals(100, lowestToken);
        });
    }

    private long tokenPosition(TrackingToken token) {
        return token == null ? 0 : token.position().orElse(0);
    }

    @Test
    void exceptionWhileHandlingEventAbortsWorker() throws Exception {
        MessageType testName = new MessageType("event");
        List<EventMessage<Integer>> events = Stream.of(1, 2, 2, 4, 5)
                                                   .map(i -> new GenericEventMessage<>(testName, i))
                                                   .collect(Collectors.toList());
        doReturn(MessageStream.failed(new RuntimeException("Simulating worker failure")))
                .doReturn(MessageStream.empty())
                .when(stubEventHandlingComponent)
                .handle(argThat(em -> em.identifier().equals(events.get(2).identifier())), any());

        testSubject.start();

        assertWithin(1, TimeUnit.SECONDS, () -> assertThat(testSubject.processingStatus()).hasSize(8));
        assertEquals(8, tokenStore.fetchSegments(PROCESSOR_NAME).length);

        verify(stubEventHandler, never()).canHandle(any(), any(), any());

        events.forEach(e -> stubMessageSource.publishMessage(e));

        assertWithin(1, TimeUnit.SECONDS, () -> {
            try {
                verify(stubEventHandlingComponent).handle(
                        argThat(em -> em.identifier().equals(events.get(2).identifier())),
                        any()
                );
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertWithin(1, TimeUnit.SECONDS, () -> {
            assertThat(testSubject.processingStatus()).hasSize(7);
            assertThat(testSubject.processingStatus()).containsKey(2);
        });
    }

    @Test
    void workPackageIsAbortedWhenExtendingClaimFails() {
        InMemoryTokenStore spy = tokenStore;
        setTestSubject(createTestSubject(b -> b.tokenStore(spy)
                                               .eventSource(new AsyncInMemoryStreamableEventSource(true))
                                               .claimExtensionThreshold(10)));

        doThrow(new MockException("Simulated failure")).when(spy)
                                                       .extendClaim(any(), anyInt());

        testSubject.start();
        assertWithin(
                250, TimeUnit.MILLISECONDS,
                () -> verify(spy, atLeastOnce()).extendClaim(testSubject.getName(), 0)
        );
        assertWithin(100, TimeUnit.MILLISECONDS, () -> assertTrue(testSubject.processingStatus().isEmpty()));
    }

    @Test
    void handlingMessageTypeNotSupportedByEventHandlingComponentWillAdvanceToken() {
        // given - Let all events through EventCriteria but configure an EventHandlingComponent to not support Integer events
        setTestSubject(createTestSubject(builder -> builder.initialSegmentCount(1)));
        QualifiedName integerTypeName = new QualifiedName(Integer.class.getName());
        when(stubEventHandlingComponent.supports(integerTypeName)).thenReturn(false);

        // when - Publish an Integer event that will reach the processor but won't be handled
        EventMessage<Integer> eventToIgnore = EventTestUtils.asEventMessage(1337);
        stubMessageSource.publishMessage(eventToIgnore);
        testSubject.start();

        // then - Verify processor status and token advancement
        await().atMost(1, TimeUnit.SECONDS)
               .untilAsserted(() -> assertThat(testSubject.processingStatus()).hasSize(1));
        await().atMost(200, TimeUnit.MILLISECONDS)
               .untilAsserted(() -> {
                   long currentPosition = testSubject.processingStatus().get(0).getCurrentPosition().orElse(0);
                   assertThat(currentPosition).isEqualTo(1);
               });

        // then - Verify no events were handled
        verify(stubEventHandlingComponent, never()).handle(any(), any());
    }

    @Test
    void handlingMessageTypeSupportedByEventHandlingComponentWillAdvanceToken() {
        // given
        QualifiedName integerTypeName = new QualifiedName(Integer.class.getName());
        QualifiedName stringTypeName = new QualifiedName(String.class);
        when(stubEventHandlingComponent.supportedEvents()).thenReturn(Set.of(stringTypeName));
        when(stubEventHandlingComponent.supports(stringTypeName)).thenReturn(true);
        when(stubEventHandlingComponent.supports(integerTypeName)).thenReturn(false);
        setTestSubject(
                createTestSubject(builder -> builder
                        .initialSegmentCount(1)
                        .eventCriteria(supportedEvents ->
                                               EventCriteria.havingAnyTag().andBeingOneOfTypes(supportedEvents)
                        )
                )
        );

        // when
        EventMessage<Integer> supportedEvent = EventTestUtils.asEventMessage("Payload");
        stubMessageSource.publishMessage(supportedEvent);
        testSubject.start();

        // then
        await().atMost(1, TimeUnit.SECONDS)
               .untilAsserted(() -> assertThat(testSubject.processingStatus()).hasSize(1));
        await().atMost(200, TimeUnit.MILLISECONDS)
               .untilAsserted(() -> {
                   long currentPosition = testSubject.processingStatus().get(0).getCurrentPosition().orElse(0);
                   assertThat(currentPosition).isEqualTo(1);
               });

        // then
        verify(stubEventHandlingComponent, times(1)).handle(any(), any());
    }

    @Test
    void eventCriteriaFiltersEventsOnSourceLevelSoEventIsNotHandledAndTokenNotAdvanced() {
        // given - Configure EventCriteria to filter out Integer events at stream level
        EventCriteria stringOnlyCriteria = EventCriteria.havingAnyTag()
                                                        .andBeingOneOfTypes(new QualifiedName(String.class.getName()));
        setTestSubject(createTestSubject(builder -> builder.initialSegmentCount(1)
                                                           .eventCriteria((__) -> stringOnlyCriteria)));

        // when - Publish an Integer event that will be filtered out by EventCriteria before reaching processor
        EventMessage<Integer> eventToFilter = EventTestUtils.asEventMessage(1337);
        stubMessageSource.publishMessage(eventToFilter);
        testSubject.start();

        // then - Verify processor status, but token should NOT advance (stays at 0)
        await().atMost(1, TimeUnit.SECONDS)
               .untilAsserted(() -> assertThat(testSubject.processingStatus()).hasSize(1));
        await().atMost(200, TimeUnit.MILLISECONDS)
               .untilAsserted(() -> {
                   long currentPosition = testSubject.processingStatus().get(0).getCurrentPosition().orElse(0);
                   assertThat(currentPosition).isEqualTo(0); // Token should not advance - event was filtered at stream level
               });

        // then - Verify no events were handled (filtered out by EventCriteria)
        verify(stubEventHandlingComponent, never()).handle(any(), any());

        // then - Verify the event was tracked as ignored (even though filtered at stream level)
        assertThat(stubMessageSource.getIgnoredEvents()).hasSize(1);
        assertThat(stubMessageSource.getIgnoredEvents().getFirst().payload()).isEqualTo(1337);
    }

    @Test
    void tokenStoreReturningSingleNullToken() {
        when(stubEventHandlingComponent.supports(any())).thenReturn(false);

        tokenStore.initializeTokenSegments(testSubject.getName(), 2);
        tokenStore.storeToken(new GlobalSequenceTrackingToken(0), testSubject.getName(), 1);

        testSubject.start();

        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(2, testSubject.processingStatus().size()));
    }

    @Test
    void eventsWhichMustBeIgnoredAreNotHandled() {
        // given
        EventCriteria stringOnlyCriteria = EventCriteria.havingAnyTag()
                                                        .andBeingOneOfTypes(new QualifiedName(String.class.getName()));

        setTestSubject(createTestSubject(builder -> builder.initialSegmentCount(1)
                                                           .eventCriteria((__) -> stringOnlyCriteria)));

        EventMessage<Integer> eventToIgnoreOne = EventTestUtils.asEventMessage(1337);
        EventMessage<Integer> eventToIgnoreTwo = EventTestUtils.asEventMessage(42);
        EventMessage<Integer> eventToIgnoreThree = EventTestUtils.asEventMessage(9001);
        List<Integer> eventsToIgnore = new ArrayList<>();
        eventsToIgnore.add(eventToIgnoreOne.payload());
        eventsToIgnore.add(eventToIgnoreTwo.payload());
        eventsToIgnore.add(eventToIgnoreThree.payload());

        EventMessage<String> eventToHandleOne = EventTestUtils.asEventMessage("some-text");
        EventMessage<String> eventToHandleTwo = EventTestUtils.asEventMessage("some-other-text");
        List<String> eventsToHandle = new ArrayList<>();
        eventsToHandle.add(eventToHandleOne.payload());
        eventsToHandle.add(eventToHandleTwo.payload());

        List<Object> eventsToValidate = new ArrayList<>();
        eventsToValidate.add(eventToHandleOne.payload());
        eventsToValidate.add(eventToHandleTwo.payload());

        // when
        stubMessageSource.publishMessage(eventToIgnoreOne);
        stubMessageSource.publishMessage(eventToIgnoreTwo);
        stubMessageSource.publishMessage(eventToIgnoreThree);
        stubMessageSource.publishMessage(eventToHandleOne);
        stubMessageSource.publishMessage(eventToHandleTwo);

        testSubject.start();

        await().atMost(1, TimeUnit.SECONDS)
              .untilAsserted(() -> assertThat(testSubject.processingStatus()).hasSize(1));

        // then - Verify that only String events are handled (Integer events are filtered out by EventCriteria).
        ArgumentCaptor<EventMessage<?>> handledEventsCaptor = ArgumentCaptor.forClass(EventMessage.class);
        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(stubEventHandlingComponent, times(2)).handle(handledEventsCaptor.capture(), any()));

        // then - Validate that the correct String events were handled.
        List<EventMessage<?>> handledEvents = handledEventsCaptor.getAllValues();
        assertThat(handledEvents).hasSize(2);

        List<Object> handledPayloads = handledEvents.stream()
                                                    .map(EventMessage::payload)
                                                    .collect(Collectors.toList());
        assertThat(handledPayloads).containsExactlyInAnyOrderElementsOf(eventsToHandle);

        // then - Verify that ignored events are tracked correctly
        List<EventMessage<?>> ignoredEvents = stubMessageSource.getIgnoredEvents();
        assertThat(ignoredEvents).hasSize(3);

        List<Object> ignoredPayloads = ignoredEvents.stream()
                                                    .map(EventMessage::payload)
                                                    .collect(Collectors.toList());
        assertThat(ignoredPayloads).containsExactlyInAnyOrderElementsOf(eventsToIgnore);
    }

    @Test
    void coordinationIsTriggeredThroughEventAvailabilityCallback() {
        boolean streamCallbackSupported = true;
        AsyncInMemoryStreamableEventSource testMessageSource = new AsyncInMemoryStreamableEventSource(
                streamCallbackSupported);
        setTestSubject(createTestSubject(builder -> builder.eventSource(testMessageSource)));

        Stream.of(0, 1, 2, 3)
              .map(i -> new GenericEventMessage<>(new MessageType("event"), i))
              .forEach(testMessageSource::publishMessage);

        testSubject.start();

        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(8, testSubject.processingStatus().size()));
        assertWithin(1, TimeUnit.SECONDS, () -> {
            long lowestToken = testSubject.processingStatus().values().stream()
                                          .map(status -> status.getCurrentPosition().orElse(-1))
                                          .min(Long::compareTo)
                                          .orElse(-1L);

            assertEquals(4, lowestToken);
        });

        Stream.of(4, 5, 6, 7)
              .map(i -> new GenericEventMessage<>(new MessageType("event"), i))
              .forEach(testMessageSource::publishMessage);
        testMessageSource.runOnAvailableCallback();

        assertWithin(1, TimeUnit.SECONDS, () -> {
            long lowestToken = testSubject.processingStatus().values().stream()
                                          .map(status -> status.getCurrentPosition().orElse(-1))
                                          .min(Long::compareTo)
                                          .orElse(-1L);

            assertEquals(8, lowestToken);
        });
    }

    @Test
    void shutdownCompletesAfterAbortingWorkPackages()
            throws InterruptedException, ExecutionException, TimeoutException {
        testSubject.start();
        Stream.of(1, 2, 2, 4, 5)
              .map(i -> new GenericEventMessage<>(new MessageType("event"), i))
              .forEach(stubMessageSource::publishMessage);

        assertWithin(1, TimeUnit.SECONDS, () -> assertFalse(testSubject.processingStatus().isEmpty()));

        testSubject.shutdownAsync().get(1, TimeUnit.SECONDS);
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(0, testSubject.processingStatus().size()));

        assertFalse(coordinatorExecutor.isShutdown());
        assertFalse(workerExecutor.isShutdown());
    }

    @Test
    void shutdownProcessorWhichHasNotStartedYetReturnsCompletedFuture() {
        assertTrue(testSubject.shutdownAsync().isDone());
    }

    @Test
    void shutdownProcessorAsyncTwiceReturnsSameFuture() {
        testSubject.start();

        CompletableFuture<Void> resultOne = testSubject.shutdownAsync();
        CompletableFuture<Void> resultTwo = testSubject.shutdownAsync();

        assertSame(resultOne, resultTwo);
    }

    @Test
    void startFailsWhenShutdownIsInProgress() throws Exception {
        when(stubEventHandlingComponent.supports(any())).thenReturn(true);
        // Use CountDownLatch to block worker threads from actually doing work, and thus shutting down successfully.
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(i -> latch.await(10, TimeUnit.MILLISECONDS)).when(stubEventHandlingComponent)
                                                             .handle(any(), any());

        testSubject.start();

        Stream.of(1, 2, 2, 4, 5)
              .map(i -> new GenericEventMessage<>(new MessageType("event"), i))
              .forEach(stubMessageSource::publishMessage);

        assertWithin(1, TimeUnit.SECONDS, () -> assertFalse(testSubject.processingStatus().isEmpty()));

        CompletableFuture<Void> shutdownComplete = testSubject.shutdownAsync();
        assertThrows(IllegalStateException.class, () -> testSubject.start());
        // Unblock the Worker threads
        latch.countDown();
        shutdownComplete.get(1, TimeUnit.SECONDS);

        // This is allowed
        assertDoesNotThrow(() -> testSubject.start());
    }

    @Test
    void isRunningOnlyReturnsTrueForStartedProcessor() {
        assertFalse(testSubject.isRunning());

        testSubject.start();

        assertTrue(testSubject.isRunning());
    }

    @Test
    void isErrorForFailingMessageSourceOperation() {
        assertFalse(testSubject.isError());

        testSubject.start();

        assertFalse(testSubject.isError());

        stubMessageSource.publishMessage(AsyncInMemoryStreamableEventSource.FAIL_EVENT);

        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertTrue(testSubject.isError()));

        // After one exception the Coordinator#errorWaitBackOff is 1 second. After this, the Coordinator should proceed.
        Stream.of(1, 2, 2, 4, 5)
              .map(i -> new GenericEventMessage<>(new MessageType("event"), i))
              .forEach(stubMessageSource::publishMessage);
        assertWithin(1500, TimeUnit.MILLISECONDS, () -> assertFalse(testSubject.isError()));
    }

    @Test
    void isErrorWhenOpeningTheStreamFails() {
        StreamableEventSource<EventMessage<?>> spiedMessageSource = spy(new AsyncInMemoryStreamableEventSource());
        when(spiedMessageSource.open(any())).thenThrow(new IllegalStateException("Failed to open the stream"))
                                            .thenCallRealMethod();
        setTestSubject(createTestSubject(builder -> builder.eventSource(spiedMessageSource)));

        assertFalse(testSubject.isError());

        testSubject.start();

        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertTrue(testSubject.isError()));

        // After one exception the Coordinator#errorWaitBackOff is 1 second. After this, the Coordinator should proceed.
        Stream.of(1, 2, 2, 4, 5)
              .map(i -> new GenericEventMessage<>(new MessageType("event"), i))
              .forEach(stubMessageSource::publishMessage);
        assertWithin(1500, TimeUnit.MILLISECONDS, () -> assertFalse(testSubject.isError()));
    }

    @Test
    void getTokenStoreIdentifier() {
        String expectedIdentifier = "some-identifier";

        TokenStore tokenStore = mock(TokenStore.class);
        when(tokenStore.retrieveStorageIdentifier()).thenReturn(Optional.of(expectedIdentifier));
        setTestSubject(createTestSubject(builder -> builder.tokenStore(tokenStore)));

        assertEquals(expectedIdentifier, testSubject.getTokenStoreIdentifier());
    }

    @Test
    void releaseSegmentMakesTheTokenUnclaimedForTwiceTheTokenClaimInterval() {
        // Given...
        int testSegmentId = 0;
        int testTokenClaimInterval = 500;

        setTestSubject(createTestSubject(builder -> builder.initialSegmentCount(1)
                                                           .tokenClaimInterval(testTokenClaimInterval)));
        testSubject.start();
        // Assert the single WorkPackage is in progress prior to invoking the release.
        assertWithin(
                testTokenClaimInterval, TimeUnit.MILLISECONDS,
                () -> assertNotNull(testSubject.processingStatus().get(testSegmentId))
        );

        // When...
        testSubject.releaseSegment(testSegmentId);

        await().atMost(testTokenClaimInterval + 200, TimeUnit.MILLISECONDS)
                       .untilAsserted(() -> assertNull(testSubject.processingStatus().get(testSegmentId)));

        // Assert that within twice the tokenClaimInterval, the WorkPackage is in progress again.
        await().atMost((testTokenClaimInterval * 2) + 200, TimeUnit.MILLISECONDS)
               .untilAsserted(() -> assertNotNull(testSubject.processingStatus().get(testSegmentId)));
    }

    @Test
    void splitSegmentIsNotSupported() {
        TokenStore tokenStoreWhichCannotSplitSegments = mock(TokenStore.class);
        when(tokenStoreWhichCannotSplitSegments.requiresExplicitSegmentInitialization()).thenReturn(false);
        setTestSubject(createTestSubject(builder -> builder.tokenStore(tokenStoreWhichCannotSplitSegments)));

        CompletableFuture<Boolean> result = testSubject.splitSegment(0);

        assertTrue(result.isDone());
        assertTrue(result.isCompletedExceptionally());
        result.exceptionally(exception -> {
            assertTrue(exception.getClass().isAssignableFrom(UnsupportedOperationException.class));
            return null;
        });
    }

    @Test
    void splitSegment() {
        // Given...
        int testSegmentId = 0;
        int testTokenClaimInterval = 500;

        setTestSubject(createTestSubject(builder -> builder.initialSegmentCount(1)
                                                           .tokenClaimInterval(testTokenClaimInterval)));
        testSubject.start();
        // Assert the single WorkPackage is in progress prior to invoking the split.
        assertWithin(
                500, TimeUnit.MILLISECONDS,
                () -> assertNotNull(testSubject.processingStatus().get(testSegmentId))
        );

        // When...
        CompletableFuture<Boolean> result = testSubject.splitSegment(testSegmentId);

        // Assert the SplitTask is done and completed successfully.
        assertWithin(testTokenClaimInterval * 2, TimeUnit.MILLISECONDS, () -> assertTrue(result.isDone()));
        assertFalse(result.isCompletedExceptionally());
        // Assert the Coordinator has set two WorkPackages on the segments.
        assertWithin(
                testTokenClaimInterval, TimeUnit.MILLISECONDS,
                () -> assertNotNull(testSubject.processingStatus().get(testSegmentId))
        );
        assertWithin(
                testTokenClaimInterval, TimeUnit.MILLISECONDS,
                () -> assertNotNull(testSubject.processingStatus().get(1))
        );
    }

    @Test
    void mergeSegmentIsNotSupported() {
        TokenStore tokenStoreWhichCannotMergeSegments = mock(TokenStore.class);
        when(tokenStoreWhichCannotMergeSegments.requiresExplicitSegmentInitialization()).thenReturn(false);
        setTestSubject(createTestSubject(builder -> builder.tokenStore(tokenStoreWhichCannotMergeSegments)));

        CompletableFuture<Boolean> result = testSubject.mergeSegment(0);

        assertTrue(result.isDone());
        assertTrue(result.isCompletedExceptionally());
        result.exceptionally(exception -> {
            assertTrue(exception.getClass().isAssignableFrom(UnsupportedOperationException.class));
            return null;
        });
    }

    @Test
    void mergeSegment() {
        // Given...
        int testSegmentId = 0;
        int testSegmentIdToMerge = 1;
        int testTokenClaimInterval = 500;

        setTestSubject(createTestSubject(builder -> builder.initialSegmentCount(2)
                                                           .tokenClaimInterval(testTokenClaimInterval)));
        testSubject.start();
        // Assert the single WorkPackage is in progress prior to invoking the merge.
        assertWithin(
                testTokenClaimInterval, TimeUnit.MILLISECONDS,
                () -> {
                    assertNotNull(testSubject.processingStatus().get(testSegmentId));
                    assertNotNull(testSubject.processingStatus().get(testSegmentIdToMerge));
                }
        );

        // When...
        CompletableFuture<Boolean> result = testSubject.mergeSegment(testSegmentId);

        // Assert the MergeTask is done and completed successfully.
        assertWithin(testTokenClaimInterval * 2, TimeUnit.MILLISECONDS, () -> assertTrue(result.isDone()));
        assertFalse(result.isCompletedExceptionally());
        // Assert the Coordinator has only one WorkPackage at work now.
        assertWithin(
                testTokenClaimInterval, TimeUnit.MILLISECONDS,
                () -> assertNotNull(testSubject.processingStatus().get(testSegmentId))
        );
        assertWithin(
                testTokenClaimInterval, TimeUnit.MILLISECONDS,
                () -> assertNull(testSubject.processingStatus().get(testSegmentIdToMerge))
        );
    }

    @Test
    void releaseAndClaimSegment() {
        int testSegmentId = 0;
        int testTokenClaimInterval = 5000;

        setTestSubject(createTestSubject(builder -> builder.initialSegmentCount(2)
                                                           .tokenClaimInterval(testTokenClaimInterval)));
        testSubject.start();
        // Assert the single WorkPackage is in progress prior to invoking the merge.
        assertWithin(
                testTokenClaimInterval, TimeUnit.MILLISECONDS,
                () -> assertNotNull(testSubject.processingStatus().get(testSegmentId))
        );

        // When...
        testSubject.releaseSegment(testSegmentId, 180, TimeUnit.SECONDS);

        // Assert the MergeTask is done and completed successfully.
        assertWithin(testTokenClaimInterval,
                     TimeUnit.MILLISECONDS,
                     () -> assertEquals(1, testSubject.processingStatus().size()));

        testSubject.claimSegment(testSegmentId);

        // Assert the Coordinator has only one WorkPackage at work now.
        assertWithin(testTokenClaimInterval,
                     TimeUnit.MILLISECONDS,
                     () -> assertEquals(2, testSubject.processingStatus().size()));
    }

    @Disabled("TODO #3304 - Integrate event replay logic into Event Handling Component")
    @Test
    void supportReset() {
        when(stubEventHandler.supportsReset()).thenReturn(true);

        assertTrue(testSubject.supportsReset());

        when(stubEventHandler.supportsReset()).thenReturn(false);

        assertFalse(testSubject.supportsReset());
    }

    @Disabled("TODO #3304 - Integrate event replay logic into Event Handling Component")
    @Test
    void resetTokensFailsIfTheProcessorIsStillRunning() {
        testSubject.start();

        assertThrows(IllegalStateException.class, () -> testSubject.resetTokens());
    }

    @Disabled("TODO #3304 - Integrate event replay logic into Event Handling Component")
    @Test
    void resetTokens() {
        int expectedSegmentCount = 2;
        TrackingToken expectedToken = new GlobalSequenceTrackingToken(42);

        when(stubEventHandler.supportsReset()).thenReturn(true);
        setTestSubject(createTestSubject(builder -> builder.initialSegmentCount(expectedSegmentCount)
                                                           .initialToken(source -> CompletableFuture.completedFuture(
                                                                   expectedToken))));

        // Start and stop the processor to initialize the tracking tokens
        testSubject.start();
        assertWithin(2,
                     TimeUnit.SECONDS,
                     () -> assertEquals(expectedSegmentCount, tokenStore.fetchSegments(PROCESSOR_NAME).length));
        testSubject.shutDown();

        testSubject.resetTokens();

        verify(stubEventHandler).performReset(null, null);

        int[] segments = tokenStore.fetchSegments(PROCESSOR_NAME);
        // The token stays the same, as the original and token after reset are identical.
        assertEquals(expectedToken, tokenStore.fetchToken(PROCESSOR_NAME, segments[0]));
        assertEquals(expectedToken, tokenStore.fetchToken(PROCESSOR_NAME, segments[1]));
    }

    @Disabled("TODO #3304 - Integrate event replay logic into Event Handling Component")
    @Test
    void resetTokensWithContext() {
        int expectedSegmentCount = 2;
        TrackingToken expectedToken = new GlobalSequenceTrackingToken(42);
        String expectedContext = "my-context";

        when(stubEventHandler.supportsReset()).thenReturn(true);
        setTestSubject(createTestSubject(builder -> builder.initialSegmentCount(expectedSegmentCount)
                                                           .initialToken(source -> CompletableFuture.completedFuture(
                                                                   expectedToken))));

        // Start and stop the processor to initialize the tracking tokens
        testSubject.start();
        await().atMost(Duration.ofSeconds(2L)).untilAsserted(
                () -> assertEquals(expectedSegmentCount, tokenStore.fetchSegments(PROCESSOR_NAME).length)
        );
        testSubject.shutDown();

        testSubject.resetTokens(expectedContext);

        verify(stubEventHandler).performReset(expectedContext, null);

        int[] segments = tokenStore.fetchSegments(PROCESSOR_NAME);
        // The token stays the same, as the original and token after reset are identical.
        assertEquals(expectedToken, tokenStore.fetchToken(PROCESSOR_NAME, segments[0]));
        assertEquals(expectedToken, tokenStore.fetchToken(PROCESSOR_NAME, segments[1]));
        await().atMost(Duration.ofSeconds(2L)).untilAsserted(
                () -> verify(stubEventHandler, times(2)).segmentReleased(any(Segment.class))
        );
    }

    @Disabled("TODO #3304 - Integrate event replay logic into Event Handling Component")
    @Test
    void resetTokensFromDefinedPosition() {
        TrackingToken testToken = new GlobalSequenceTrackingToken(42);

        int expectedSegmentCount = 2;
        TrackingToken expectedToken = ReplayToken.createReplayToken(testToken, null);

        when(stubEventHandler.supportsReset()).thenReturn(true);
        setTestSubject(createTestSubject(builder -> builder.initialSegmentCount(expectedSegmentCount)
                                                           .initialToken(source -> CompletableFuture.completedFuture(
                                                                   testToken))));

        // Start and stop the processor to initialize the tracking tokens
        testSubject.start();
        assertWithin(2,
                     TimeUnit.SECONDS,
                     () -> assertEquals(expectedSegmentCount, tokenStore.fetchSegments(PROCESSOR_NAME).length));
        testSubject.shutDown();

        testSubject.resetTokens(source -> source.latestToken());

        verify(stubEventHandler).performReset(isNull(), any());

        int[] segments = tokenStore.fetchSegments(PROCESSOR_NAME);
        assertEquals(expectedToken, tokenStore.fetchToken(PROCESSOR_NAME, segments[0]));
        assertEquals(expectedToken, tokenStore.fetchToken(PROCESSOR_NAME, segments[1]));
    }

    @Disabled("TODO #3304 - Integrate event replay logic into Event Handling Component")
    @Test
    void resetTokensFromDefinedPositionAndWithResetContext() {
        TrackingToken testToken = new GlobalSequenceTrackingToken(42);

        int expectedSegmentCount = 2;
        String expectedContext = "my-context";
        TrackingToken expectedToken = ReplayToken.createReplayToken(testToken, null, expectedContext);

        when(stubEventHandler.supportsReset()).thenReturn(true);
        setTestSubject(createTestSubject(builder -> builder.initialSegmentCount(expectedSegmentCount)
                                                           .initialToken(source -> CompletableFuture.completedFuture(
                                                                   testToken))));

        // Start and stop the processor to initialize the tracking tokens
        testSubject.start();
        assertWithin(2,
                     TimeUnit.SECONDS,
                     () -> assertEquals(expectedSegmentCount, tokenStore.fetchSegments(PROCESSOR_NAME).length));
        testSubject.shutDown();

        testSubject.resetTokens(source -> source.latestToken(), expectedContext);

        verify(stubEventHandler).performReset(eq(expectedContext), any());

        int[] segments = tokenStore.fetchSegments(PROCESSOR_NAME);
        assertEquals(expectedToken, tokenStore.fetchToken(PROCESSOR_NAME, segments[0]));
        assertEquals(expectedToken, tokenStore.fetchToken(PROCESSOR_NAME, segments[1]));
    }

    @Test
    void maxCapacityDefaultsToShortMax() {
        assertEquals(Short.MAX_VALUE, testSubject.maxCapacity());
    }

    @Test
    void maxCapacityReturnsConfiguredCapacity() {
        int expectedMaxCapacity = 500;
        setTestSubject(createTestSubject(builder -> builder.maxSegmentProvider(p -> expectedMaxCapacity)));

        assertEquals(expectedMaxCapacity, testSubject.maxCapacity());
    }

    @Test
    void processingStatusIsUpdatedWithTrackingToken() {
        testSubject.start();

        Stream.of(1, 2, 2, 4, 5)
              .map(i -> new GenericEventMessage<>(new MessageType("event"), i))
              .forEach(stubMessageSource::publishMessage);

        assertWithin(
                1, TimeUnit.SECONDS,
                () -> testSubject.processingStatus().values().forEach(
                        status -> assertEquals(5, status.getCurrentPosition().orElse(0))
                )
        );
    }


    @Test
    void buildWithNullMessageSourceThrowsAxonConfigurationException() {
        PooledStreamingEventProcessor.Builder builderTestSubject = PooledStreamingEventProcessor.builder();

        //noinspection ConstantConditions
        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.eventSource(null));
    }

    @Test
    void buildWithoutMessageSourceThrowsAxonConfigurationException() {
        PooledStreamingEventProcessor.Builder builderTestSubject =
                PooledStreamingEventProcessor.builder()
                                             .tokenStore(new InMemoryTokenStore())
                                             .transactionManager(NoTransactionManager.INSTANCE);

        assertThrows(AxonConfigurationException.class, builderTestSubject::build);
    }

    @Test
    void buildWithNullTokenStoreThrowsAxonConfigurationException() {
        PooledStreamingEventProcessor.Builder builderTestSubject = PooledStreamingEventProcessor.builder();

        //noinspection ConstantConditions
        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.tokenStore(null));
    }

    @Test
    void buildWithoutTokenStoreThrowsAxonConfigurationException() {
        PooledStreamingEventProcessor.Builder builderTestSubject =
                PooledStreamingEventProcessor.builder()
                                             .name(PROCESSOR_NAME)
                                             .eventHandlingComponent(stubEventHandlingComponent)
                                             .eventSource(stubMessageSource)
                                             .transactionManager(NoTransactionManager.INSTANCE);

        assertThrows(AxonConfigurationException.class, builderTestSubject::build);
    }

    @Test
    void buildWithNullTransactionManagerThrowsAxonConfigurationException() {
        PooledStreamingEventProcessor.Builder builderTestSubject = PooledStreamingEventProcessor.builder();

        //noinspection ConstantConditions
        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.transactionManager(null));
    }

    @Test
    void buildWithoutTransactionManagerThrowsAxonConfigurationException() {
        PooledStreamingEventProcessor.Builder builderTestSubject =
                PooledStreamingEventProcessor.builder()
                                             .name(PROCESSOR_NAME)
                                             .eventHandlingComponent(stubEventHandlingComponent)
                                             .eventSource(stubMessageSource)
                                             .tokenStore(new InMemoryTokenStore())
                                             .transactionManager(NoTransactionManager.instance());

        assertThrows(AxonConfigurationException.class, builderTestSubject::build);
    }

    @Test
    void buildWithNullCoordinatorExecutorThrowsAxonConfigurationException() {
        PooledStreamingEventProcessor.Builder builderTestSubject = PooledStreamingEventProcessor.builder();

        //noinspection ConstantConditions
        assertThrows(
                AxonConfigurationException.class,
                () -> builderTestSubject.coordinatorExecutor((ScheduledExecutorService) null)
        );
    }

    @Test
    void buildWithNullCoordinatorExecutorBuilderThrowsAxonConfigurationException() {
        PooledStreamingEventProcessor.Builder builderTestSubject = PooledStreamingEventProcessor.builder();

        //noinspection ConstantConditions
        assertThrows(
                AxonConfigurationException.class,
                () -> builderTestSubject.coordinatorExecutor((Function<String, ScheduledExecutorService>) null)
        );
    }

    @Test
    void buildWithoutCoordinatorExecutorThrowsAxonConfigurationException() {
        PooledStreamingEventProcessor.Builder builderTestSubject =
                PooledStreamingEventProcessor.builder()
                                             .name(PROCESSOR_NAME)
                                             .eventHandlingComponent(stubEventHandlingComponent)
                                             .eventSource(stubMessageSource)
                                             .tokenStore(new InMemoryTokenStore())
                                             .transactionManager(NoTransactionManager.instance())
                                             .coordinatorExecutor(coordinatorExecutor);

        assertThrows(AxonConfigurationException.class, builderTestSubject::build);
    }

    @Test
    void buildWithNullWorkerExecutorThrowsAxonConfigurationException() {
        PooledStreamingEventProcessor.Builder builderTestSubject = PooledStreamingEventProcessor.builder();

        //noinspection ConstantConditions
        assertThrows(
                AxonConfigurationException.class,
                () -> builderTestSubject.workerExecutor((ScheduledExecutorService) null)
        );
    }

    @Test
    void buildWithNullWorkerExecutorBuilderThrowsAxonConfigurationException() {
        PooledStreamingEventProcessor.Builder builderTestSubject = PooledStreamingEventProcessor.builder();

        //noinspection ConstantConditions
        assertThrows(
                AxonConfigurationException.class,
                () -> builderTestSubject.workerExecutor((Function<String, ScheduledExecutorService>) null)
        );
    }

    @Test
    void buildWithoutWorkerExecutorThrowsAxonConfigurationException() {
        PooledStreamingEventProcessor.Builder builderTestSubject =
                PooledStreamingEventProcessor.builder()
                                             .name(PROCESSOR_NAME)
                                             .eventHandlingComponent(stubEventHandlingComponent)
                                             .eventSource(stubMessageSource)
                                             .tokenStore(new InMemoryTokenStore())
                                             .transactionManager(NoTransactionManager.instance())
                                             .coordinatorExecutor(coordinatorExecutor);

        assertThrows(AxonConfigurationException.class, builderTestSubject::build);
    }

    @Test
    void buildWithZeroOrNegativeInitialSegmentCountThrowsAxonConfigurationException() {
        PooledStreamingEventProcessor.Builder builderTestSubject = PooledStreamingEventProcessor.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.initialSegmentCount(0));
        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.initialSegmentCount(-1));
    }

    @Test
    void buildWithNullInitialTokenThrowsAxonConfigurationException() {
        PooledStreamingEventProcessor.Builder builderTestSubject = PooledStreamingEventProcessor.builder();

        //noinspection ConstantConditions
        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.initialToken(null));
    }

    @Test
    void buildWithZeroOrNegativeTokenClaimIntervalThrowsAxonConfigurationException() {
        PooledStreamingEventProcessor.Builder builderTestSubject = PooledStreamingEventProcessor.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.tokenClaimInterval(0));
        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.tokenClaimInterval(-1));
    }

    @Test
    void buildWithZeroOrNegativeMaxCapacityThrowsAxonConfigurationException() {
        PooledStreamingEventProcessor.Builder builderTestSubject = PooledStreamingEventProcessor.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.maxSegmentProvider(e -> 0));
        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.maxSegmentProvider(e -> -1));
    }

    @Test
    void buildWithZeroOrNegativeClaimExtensionThresholdThrowsAxonConfigurationException() {
        PooledStreamingEventProcessor.Builder builderTestSubject = PooledStreamingEventProcessor.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.claimExtensionThreshold(0));
        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.claimExtensionThreshold(-1));
    }

    @Test
    void buildWithZeroOrNegativeBatchSizeThrowsAxonConfigurationException() {
        PooledStreamingEventProcessor.Builder builderTestSubject = PooledStreamingEventProcessor.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.batchSize(0));
        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.batchSize(-1));
    }

    @Disabled("TODO #3304 - Integrate event replay logic into Event Handling Component")
    @Test
    void isReplaying() {
        when(stubEventHandler.supportsReset()).thenReturn(true);

        setTestSubject(createTestSubject(builder -> builder.initialSegmentCount(1)));

        List<EventMessage<Integer>> events = createEvents(100);
        testSubject.start();

        events.forEach(stubMessageSource::publishMessage);

        assertWithin(
                1, TimeUnit.SECONDS,
                () -> {
                    assertEquals(1, testSubject.processingStatus().size());
                    assertTrue(testSubject.processingStatus().get(0).isCaughtUp());
                    assertFalse(testSubject.processingStatus().get(0).isReplaying());
                    assertFalse(testSubject.isReplaying());
                }
        );

        testSubject.shutDown();
        testSubject.resetTokens(source -> source.latestToken());
        testSubject.start();

        assertWithin(
                1, TimeUnit.SECONDS, () -> {
                    assertEquals(1, testSubject.processingStatus().size());
                    assertTrue(testSubject.processingStatus().get(0).isCaughtUp());
                    assertTrue(testSubject.processingStatus().get(0).isReplaying());
                    assertFalse(testSubject.isReplaying());
                }
        );
    }

    @Test
    void isCaughtUpWhenDoneProcessing() throws Exception {
        mockSlowEventHandler();
        setTestSubject(createTestSubject(builder -> builder.initialSegmentCount(1)));
        List<EventMessage<Integer>> events = createEvents(3);
        events.forEach(stubMessageSource::publishMessage);

        testSubject.start();

        AtomicReference<Instant> startedProcessing = new AtomicReference<>(null);
        assertWithin(
                5, TimeUnit.SECONDS,
                () -> {
                    assertEquals(1, testSubject.processingStatus().size());
                    startedProcessing.compareAndSet(null, Instant.now());
                }
        );
        assertWithin(
                5, TimeUnit.SECONDS,
                () -> assertTrue(testSubject.processingStatus().get(0).isCaughtUp())
        );
        Instant now = Instant.now();
        //It should have taken 2 seconds (rounded down) or more this will fail, want changed to normal mock, then it goes faster
        assertTrue(Duration.between(startedProcessing.get(), now).getSeconds() >= 2);
    }

    @Test
    void existingEventsBeforeProcessorStartAreConsideredReplayed() throws Exception {
        setTestSubject(createTestSubject(b -> b.initialSegmentCount(1)));

        CountDownLatch countDownLatch = new CountDownLatch(3);
        testSubject.registerHandlerInterceptor(new MessageHandlerInterceptor<EventMessage<?>>() {
            @Override
            public Object handle(@Nonnull LegacyUnitOfWork<? extends EventMessage<?>> unitOfWork,
                                 @Nonnull ProcessingContext context,
                                 @Nonnull InterceptorChain interceptorChain) throws Exception {
                unitOfWork.onCleanup(uow -> countDownLatch.countDown());
                return interceptorChain.proceedSync(context);
            }

            @Override
            public <M extends EventMessage<?>, R extends Message<?>> MessageStream<R> interceptOnHandle(
                    @Nonnull M message,
                    @Nonnull ProcessingContext context,
                    @Nonnull InterceptorChain<M, R> interceptorChain) {
                context.doFinally(uow -> countDownLatch.countDown());
                return interceptorChain.proceed(message, context);
            }
        });
        createEvents(3).forEach(stubMessageSource::publishMessage);

        testSubject.start();

        assertTrue(countDownLatch.await(5, TimeUnit.SECONDS), "Expected Unit of Work to have reached clean up phase");
        TrackingToken trackingToken = tokenStore.fetchToken(testSubject.getName(), 0);
        assertTrue(ReplayToken.isReplay(trackingToken),
                   "Not a replay token: " + trackingToken);
    }

    @Test
    void eventsPublishedAfterProcessorStartAreNotConsideredReplayed() throws Exception {
        setTestSubject(createTestSubject(b -> b.initialSegmentCount(1)));

        CountDownLatch countDownLatch = new CountDownLatch(3);
        testSubject.registerHandlerInterceptor(new MessageHandlerInterceptor<>() {
            @Override
            public Object handle(@Nonnull LegacyUnitOfWork<? extends EventMessage<?>> unitOfWork,
                                 @Nonnull ProcessingContext context,
                                 @Nonnull InterceptorChain interceptorChain) throws Exception {
                unitOfWork.onCleanup(uow -> countDownLatch.countDown());
                return interceptorChain.proceedSync(context);
            }

            @Override
            public <M extends EventMessage<?>, R extends Message<?>> MessageStream<R> interceptOnHandle(
                    @Nonnull M message, @Nonnull ProcessingContext context,
                    @Nonnull InterceptorChain<M, R> interceptorChain) {
                context.doFinally(uow -> countDownLatch.countDown());
                return interceptorChain.proceed(message, context);
            }
        });
        stubMessageSource.publishMessage(EventTestUtils.asEventMessage(0));
        stubMessageSource.publishMessage(EventTestUtils.asEventMessage(1));

        testSubject.start();

        stubMessageSource.publishMessage(EventTestUtils.asEventMessage(2));

        assertTrue(countDownLatch.await(5, TimeUnit.SECONDS), "Expected Unit of Work to have reached clean up phase");
        TrackingToken trackingToken = tokenStore.fetchToken(testSubject.getName(), 0);
        assertFalse(ReplayToken.isReplay(trackingToken),
                    "Not a replay token: " + trackingToken);
    }

    private void mockSlowEventHandler() throws Exception {
        doAnswer(invocation -> {
            Thread.sleep(1000);
            return MessageStream.empty();
        }).when(stubEventHandlingComponent).handle(any(), any());
    }

    @Test
    void coordinatorExtendsClaimsEarlierForBusyWorkPackages() throws Exception {
        setTestSubject(createTestSubject(builder -> builder.initialSegmentCount(1)
                                                           .enableCoordinatorClaimExtension()));

        AtomicBoolean isWaiting = new AtomicBoolean(false);
        CountDownLatch handleLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            // Waiting for the latch to simulate a slow/busy WorkPackage.
            isWaiting.set(true);
            handleLatch.await(5, TimeUnit.SECONDS);
            return MessageStream.empty();
        }).when(stubEventHandlingComponent)
          .handle(any(), any());

        List<EventMessage<Integer>> events = createEvents(42);
        events.forEach(stubMessageSource::publishMessage);

        testSubject.start();

        // Wait until we've reached the blocking WorkPackage before validating if the token is extended.
        // Otherwise, the WorkPackage may extend the token itself.
        await().pollDelay(Duration.ofMillis(50))
               .atMost(Duration.ofSeconds(5))
               .until(isWaiting::get);

        // As the WorkPackage is blocked, we can verify if the claim is extended, but not stored.
        verify(tokenStore, timeout(5000)).extendClaim(PROCESSOR_NAME, 0);
        verify(tokenStore, never()).storeToken(any(), eq(PROCESSOR_NAME), eq(0));

        // Unblock the WorkPackage after successful validation
        handleLatch.countDown();

        // Processing finished...
        await().pollDelay(Duration.ofMillis(50))
               .atMost(Duration.ofSeconds(5))
               .until(() -> testSubject.processingStatus().get(0).isCaughtUp());
        // Validate the token is stored
        verify(tokenStore, timeout(5000).atLeastOnce()).storeToken(any(), eq(PROCESSOR_NAME), eq(0));
    }

    @Test
    void coordinatorExtendingClaimFailsAndAbortsWorkPackage() throws Exception {
        setTestSubject(createTestSubject(builder -> builder.initialSegmentCount(1)
                                                           .enableCoordinatorClaimExtension()));
        String expectedExceptionMessage = "bummer";
        doThrow(new RuntimeException(expectedExceptionMessage))
                .when(tokenStore)
                .extendClaim(PROCESSOR_NAME, 0);

        AtomicBoolean isWaiting = new AtomicBoolean(false);
        CountDownLatch handleLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            // Waiting for the latch to simulate a slow/busy WorkPackage.
            isWaiting.set(true);
            handleLatch.await(5, TimeUnit.SECONDS);
            return MessageStream.empty();
        }).when(stubEventHandlingComponent)
          .handle(any(), any());

        List<EventMessage<Integer>> events = createEvents(42);
        events.forEach(stubMessageSource::publishMessage);

        testSubject.start();

        // Wait until we've reached the blocking WorkPackage before validating if the token is extended.
        // Otherwise, the WorkPackage may extend the token itself.
        await().pollDelay(Duration.ofMillis(50))
               .atMost(Duration.ofSeconds(5))
               .until(isWaiting::get);

        // As the WorkPackage is blocked, we can verify if the claim is extended, but not stored.
        verify(tokenStore, timeout(5000)).extendClaim(PROCESSOR_NAME, 0);
        verify(tokenStore, never()).storeToken(any(), eq(PROCESSOR_NAME), eq(0));

        // Although the WorkPackage is waiting, the Coordinator should in the meantime fail with extending the claim.
        // This updates the processing status of the WorkPackage.
        await().pollDelay(Duration.ofMillis(50))
               .atMost(Duration.ofSeconds(5))
               .until(() -> testSubject.processingStatus().get(0)
                                       .getError()
                                       .getMessage().equals(expectedExceptionMessage));

        // Unblock the WorkPackage after successful validation
        handleLatch.countDown();
    }
}