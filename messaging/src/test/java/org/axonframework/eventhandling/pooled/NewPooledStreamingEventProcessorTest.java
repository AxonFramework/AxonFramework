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
import org.axonframework.eventhandling.ErrorContext;
import org.axonframework.eventhandling.ErrorHandler;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.RecordingEventHandlingComponent;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.eventstreaming.EventCriteria;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.axonframework.eventhandling.EventTestUtils.createEvents;
import static org.axonframework.utils.AssertUtils.assertWithin;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link PooledStreamingEventProcessor}.
 *
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @author Steven van Beelen
 */
// TODO #3098 - Rename to PooledStreamingEventProcessorTest
class NewPooledStreamingEventProcessorTest {

    private static final Logger logger = LoggerFactory.getLogger(
            NewPooledStreamingEventProcessorTest.class);

    private static final String PROCESSOR_NAME = "test";

    private PooledStreamingEventProcessor testSubject;
    private AsyncInMemoryStreamableEventSource stubMessageSource;
    private InMemoryTokenStore tokenStore;
    private ScheduledExecutorService coordinatorExecutor;
    private ScheduledExecutorService workerExecutor;
    private RecordingEventHandlingComponent defaultEventHandlingComponent;

    @BeforeEach
    void setUp() {
        stubMessageSource = spy(new AsyncInMemoryStreamableEventSource());
        tokenStore = spy(new InMemoryTokenStore());
        coordinatorExecutor = spy(new DelegateScheduledExecutorService(Executors.newScheduledThreadPool(2)));
        workerExecutor = new DelegateScheduledExecutorService(Executors.newScheduledThreadPool(8));
        defaultEventHandlingComponent = spy(new RecordingEventHandlingComponent(new SimpleEventHandlingComponent()));
        defaultEventHandlingComponent.subscribe(new QualifiedName(Integer.class),
                                                (event, ctx) -> MessageStream.empty());
        withTestSubject(List.of()); // default always applied
    }

    @AfterEach
    void tearDown() {
        testSubject.shutDown();
        coordinatorExecutor.shutdown();
        workerExecutor.shutdown();
    }

    private PooledStreamingEventProcessor withTestSubject(List<EventHandlingComponent> eventHandlingComponents) {
        return withTestSubject(eventHandlingComponents, UnaryOperator.identity());
    }

    private PooledStreamingEventProcessor withTestSubject(
            List<EventHandlingComponent> eventHandlingComponents,
            UnaryOperator<PooledStreamingEventProcessorConfiguration> configOverride
    ) {
        var componentsWithDefault = new ArrayList<>(eventHandlingComponents);
        componentsWithDefault.add(defaultEventHandlingComponent);

        var testDefaultConfiguration = new PooledStreamingEventProcessorConfiguration()
                .eventSource(stubMessageSource)
                .eventHandlingComponents(componentsWithDefault)
                .unitOfWorkFactory(new SimpleUnitOfWorkFactory())
                .tokenStore(tokenStore)
                .coordinatorExecutor(ignored -> coordinatorExecutor)
                .workerExecutor(ignored -> workerExecutor)
                .initialSegmentCount(8)
                .claimExtensionThreshold(500);
        var customizedConfiguration = configOverride.apply(testDefaultConfiguration);

        var processor = new PooledStreamingEventProcessor(
                PROCESSOR_NAME,
                customizedConfiguration
        );
        this.testSubject = processor;
        return processor;
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


    @Nested
    class LifecycleTest {

        @Test
        void startShutsDownImmediatelyIfCoordinatorExecutorThrowsAnException() {
            // given
            doThrow(new IllegalArgumentException("Some exception")).when(coordinatorExecutor)
                                                                   .submit(any(Runnable.class));

            // when
            assertThrows(IllegalArgumentException.class, testSubject::start);

            // then
            assertFalse(testSubject.isRunning());
        }

        @Test
        void secondStartInvocationIsIgnored() {
            // given
            testSubject.start();

            // when - The second invocation does not cause the Coordinator to schedule another CoordinationTask.
            testSubject.start();

            // then
            verify(coordinatorExecutor, times(1)).submit(any(Runnable.class));
        }

        @Test
        void startingProcessorClaimsAllAvailableTokens() {
            // given
            List<EventMessage<Integer>> events =
                    createEvents(100);
            events.forEach(stubMessageSource::publishMessage);

            // when
            testSubject.start();

            // then
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
            // Use CountDownLatch to block worker threads from actually doing work, and thus shutting down successfully.
            CountDownLatch latch = new CountDownLatch(1);
            doAnswer(i -> latch.await(10, TimeUnit.MILLISECONDS)).when(defaultEventHandlingComponent)
                                                                 .handle(any(), any());

            testSubject.start();

            List<EventMessage<Integer>> events = createEvents(5);
            events.forEach(stubMessageSource::publishMessage);

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
            List<EventMessage<Integer>> events = createEvents(5);
            events.forEach(stubMessageSource::publishMessage);
            assertWithin(1500, TimeUnit.MILLISECONDS, () -> assertFalse(testSubject.isError()));
        }

        @Test
        void isErrorWhenOpeningTheStreamFails() {
            when(stubMessageSource.open(any())).thenThrow(new IllegalStateException("Failed to open the stream"))
                                               .thenCallRealMethod();
            withTestSubject(List.of());

            assertFalse(testSubject.isError());

            testSubject.start();

            assertWithin(500, TimeUnit.MILLISECONDS, () -> assertTrue(testSubject.isError()));

            // After one exception the Coordinator#errorWaitBackOff is 1 second. After this, the Coordinator should proceed.
            List<EventMessage<Integer>> events = createEvents(5);
            events.forEach(stubMessageSource::publishMessage);
            assertWithin(1500, TimeUnit.MILLISECONDS, () -> assertFalse(testSubject.isError()));
        }

        @Test
        void isCaughtUpWhenDoneProcessing() {
            mockSlowEventHandler();
            withTestSubject(List.of(), (c -> c.initialSegmentCount(1)));
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

        private void mockSlowEventHandler() {
            doAnswer(invocation -> {
                Thread.sleep(1000);
                return MessageStream.empty();
            }).when(defaultEventHandlingComponent).handle(any(), any());
        }
    }

    @Disabled("Tracing another task!")
    @Nested
    class TracingTest {

//        @Test
//        void handlingEventsAreCorrectlyTraced() throws Exception {
//            CountDownLatch countDownLatch = new CountDownLatch(8);
//            List<Message<?>> invokedMessages = new CopyOnWriteArrayList<>();
//            doAnswer(
//                    answer -> {
//                        EventMessage<?> message = answer.getArgument(0, EventMessage.class);
//                        invokedMessages.add(message);
//                        spanFactory.verifySpanActive("StreamingEventProcessor.batch");
//                        spanFactory.verifySpanActive("StreamingEventProcessor.process", message);
//                        countDownLatch.countDown();
//                        return MessageStream.empty();
//                    }
//            ).when(stubEventHandlingComponent).handle(any(), any());
//
//            List<EventMessage<Integer>> events = createEvents(8);
//            events.forEach(stubMessageSource::publishMessage);
//            testSubject.start();
//            assertTrue(countDownLatch.await(5, TimeUnit.SECONDS));
//            invokedMessages.forEach(
//                    e -> assertWithin(
//                            1, TimeUnit.SECONDS,
//                            () -> spanFactory.verifySpanCompleted("StreamingEventProcessor.process", e)
//                    )
//            );
//            spanFactory.verifySpanCompleted("StreamingEventProcessor.batch");
//        }
    }

    @Nested
    class ProcessingContextResourcesTest {

        @Test
        void handlingEventsHaveSegmentAndTokenInProcessingContext() throws Exception {
            // given
            CountDownLatch countDownLatch = new CountDownLatch(8);
            var eventHandlingComponent = new SimpleEventHandlingComponent();
            eventHandlingComponent.subscribe(new QualifiedName(Integer.class), (event, context) -> {
                boolean containsSegment = Segment.fromContext(context).isPresent();
                boolean containsToken = TrackingToken.fromContext(context).isPresent();
                if (!containsSegment) {
                    logger.error("UoW didn't contain the segment!");
                    return MessageStream.empty();
                }
                if (!containsToken) {
                    logger.error("UoW didn't contain the token!");
                    return MessageStream.empty();
                }
                countDownLatch.countDown();
                return MessageStream.empty();
            });
            withTestSubject(List.of(eventHandlingComponent));

            // when
            List<EventMessage<Integer>> events = createEvents(8);
            events.forEach(stubMessageSource::publishMessage);
            testSubject.start();

            // then
            assertTrue(countDownLatch.await(5, TimeUnit.SECONDS));
        }
    }

    @Nested
    class TokenManagementTest {

        @Test
        void retriesWhenTokenInitializationInitiallyFails() {
            // given
            doThrow(new RuntimeException("Simulated failure")).doCallRealMethod()
                                                              .when(tokenStore)
                                                              .initializeTokenSegments(any(), anyInt(), any());

            // when
            List<EventMessage<Integer>> events =
                    createEvents(100);
            events.forEach(stubMessageSource::publishMessage);
            testSubject.start();

            // then
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
        void processingStatusIsUpdatedWithTrackingToken() {
            testSubject.start();

            List<EventMessage<Integer>> events =
                    createEvents(5);
            events.forEach(stubMessageSource::publishMessage);

            assertWithin(
                    1, TimeUnit.SECONDS,
                    () -> testSubject.processingStatus().values().forEach(
                            status -> assertEquals(5, status.getCurrentPosition().orElse(0))
                    )
            );
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
                                            .mapToLong(token -> token == null ? 0 : token.position().orElse(0))
                                            .min()
                                            .orElse(-1);
                assertEquals(100, lowestToken);
            });
        }

        @Test
        void tokenStoreReturningSingleNullToken() {
            tokenStore.initializeTokenSegments(testSubject.getName(), 2);
            tokenStore.storeToken(new GlobalSequenceTrackingToken(0), testSubject.getName(), 1);

            testSubject.start();

            assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(2, testSubject.processingStatus().size()));
        }

        @Test
        void getTokenStoreIdentifier() {
            String expectedIdentifier = "some-identifier";

            when(tokenStore.retrieveStorageIdentifier()).thenReturn(Optional.of(expectedIdentifier));

            assertEquals(expectedIdentifier, testSubject.getTokenStoreIdentifier());
        }

        @Test
        void releaseSegmentMakesTheTokenUnclaimedForTwiceTheTokenClaimInterval() {
            // Given...
            int testSegmentId = 0;
            int testTokenClaimInterval = 500;

            withTestSubject(List.of(), c -> c.initialSegmentCount(1).tokenClaimInterval(testTokenClaimInterval));

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
    }

    @Nested
    class Coordinating {

        @Test
        void coordinationIsTriggeredThroughEventAvailabilityCallback() {
            boolean streamCallbackSupported = true;
            AsyncInMemoryStreamableEventSource testMessageSource = new AsyncInMemoryStreamableEventSource(
                    streamCallbackSupported);
            stubMessageSource = testMessageSource;
            withTestSubject(List.of());

            List<EventMessage<Integer>> events1 = createEvents(4);
            events1.forEach(testMessageSource::publishMessage);

            testSubject.start();

            assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(8, testSubject.processingStatus().size()));
            assertWithin(1, TimeUnit.SECONDS, () -> {
                long lowestToken = testSubject.processingStatus().values().stream()
                                              .map(status -> status.getCurrentPosition().orElse(-1))
                                              .min(Long::compareTo)
                                              .orElse(-1L);

                assertEquals(4, lowestToken);
            });

            List<EventMessage<Integer>> events2 = createEvents(4);
            events2.forEach(testMessageSource::publishMessage);
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
        void coordinatorExtendsClaimsEarlierForBusyWorkPackages() throws Exception {
            withTestSubject(
                    List.of(),
                    c -> c.initialSegmentCount(1).enableCoordinatorClaimExtension()
            );

            AtomicBoolean isWaiting = new AtomicBoolean(false);
            CountDownLatch handleLatch = new CountDownLatch(1);
            doAnswer(invocation -> {
                // Waiting for the latch to simulate a slow/busy WorkPackage.
                isWaiting.set(true);
                handleLatch.await(5, TimeUnit.SECONDS);
                return MessageStream.empty();
            }).when(defaultEventHandlingComponent)
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
            withTestSubject(
                    List.of(),
                    c -> c.initialSegmentCount(1).enableCoordinatorClaimExtension()
            );

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
            }).when(defaultEventHandlingComponent)
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

    @Nested
    class WorkPackageAbortingTest {

        @Test
        void exceptionWhileHandlingEventAbortsWorker() {
            List<EventMessage<Integer>> events = createEvents(5);
            doReturn(MessageStream.failed(new RuntimeException("Simulating worker failure")))
                    .doReturn(MessageStream.empty())
                    .when(defaultEventHandlingComponent)
                    .handle(argThat(em -> em.getIdentifier().equals(events.get(2).getIdentifier())), any());

            testSubject.start();

            assertWithin(1, TimeUnit.SECONDS, () -> assertThat(testSubject.processingStatus()).hasSize(8));
            assertEquals(8, tokenStore.fetchSegments(PROCESSOR_NAME).length);

            events.forEach(e -> stubMessageSource.publishMessage(e));

            assertWithin(1, TimeUnit.SECONDS, () -> {
                try {
                    verify(defaultEventHandlingComponent).handle(
                            argThat(em -> em.getIdentifier().equals(events.get(2).getIdentifier())),
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
            withTestSubject(List.of(), c -> c.claimExtensionThreshold(10));

            doThrow(new MockException("Simulated failure")).when(tokenStore)
                                                           .extendClaim(any(), anyInt());
            //  from legacy? .eventSource(new AsyncInMemoryStreamableEventSource(true))
            testSubject.start();
            assertWithin(
                    250, TimeUnit.MILLISECONDS,
                    () -> verify(tokenStore, atLeastOnce()).extendClaim(testSubject.getName(), 0)
            );
            assertWithin(100, TimeUnit.MILLISECONDS, () -> assertTrue(testSubject.processingStatus().isEmpty()));
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
    }

    @Nested
    class EventFilteringTest {

        @Test
        void handlingMessageTypeNotSupportedByEventHandlingComponentWillAdvanceToken() {
            // given - Let all events through EventCriteria but configure an EventHandlingComponent to not support Integer events
            withTestSubject(List.of(), c -> c.initialSegmentCount(1));
            QualifiedName integerTypeName = new QualifiedName(Integer.class.getName());
            when(defaultEventHandlingComponent.supports(integerTypeName)).thenReturn(false);

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
            verify(defaultEventHandlingComponent, never()).handle(any(), any());
        }

        @Test
        void handlingMessageTypeSupportedByEventHandlingComponentWillAdvanceToken() {
            // given
            withTestSubject(
                    List.of(),
                    c -> c.initialSegmentCount(1)
            );

            // when
            EventMessage<Integer> supportedEvent = EventTestUtils.asEventMessage(123);
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
            verify(defaultEventHandlingComponent, times(1)).handle(any(), any());
        }

        @Test
        void eventCriteriaFiltersEventsOnSourceLevelSoEventIsNotHandledAndTokenNotAdvanced() {
            // given - Configure EventCriteria to filter out Integer events at stream level
            EventCriteria stringOnlyCriteria = EventCriteria.havingAnyTag()
                                                            .andBeingOneOfTypes(new QualifiedName(String.class.getName()));
            withTestSubject(
                    List.of(),
                    c -> c.initialSegmentCount(1)
                          .eventCriteria(ignored -> stringOnlyCriteria)
            );

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
            verify(defaultEventHandlingComponent, never()).handle(any(), any());

            // then - Verify the event was tracked as ignored (even though filtered at stream level)
            assertThat(stubMessageSource.getIgnoredEvents()).hasSize(1);
            assertThat(stubMessageSource.getIgnoredEvents().getFirst().getPayload()).isEqualTo(1337);
        }

        @Test
        void eventsWhichMustBeIgnoredAreNotHandled() {
            // given
            EventCriteria stringOnlyCriteria = EventCriteria.havingAnyTag()
                                                            .andBeingOneOfTypes(new QualifiedName(String.class.getName()));

            var stringEventHandlingComponent = new RecordingEventHandlingComponent(new SimpleEventHandlingComponent());
            stringEventHandlingComponent.subscribe(new QualifiedName(String.class),
                                                   (event, ctx) -> MessageStream.empty());
            withTestSubject(
                    List.of(stringEventHandlingComponent),
                    c -> c.initialSegmentCount(1)
                          .eventCriteria(ignored -> stringOnlyCriteria)
            );

            EventMessage<Integer> eventToIgnoreOne = EventTestUtils.asEventMessage(1337);
            EventMessage<Integer> eventToIgnoreTwo = EventTestUtils.asEventMessage(42);
            EventMessage<Integer> eventToIgnoreThree = EventTestUtils.asEventMessage(9001);
            List<Integer> eventsToIgnore = new ArrayList<>();
            eventsToIgnore.add(eventToIgnoreOne.getPayload());
            eventsToIgnore.add(eventToIgnoreTwo.getPayload());
            eventsToIgnore.add(eventToIgnoreThree.getPayload());

            EventMessage<String> eventToHandleOne = EventTestUtils.asEventMessage("some-text");
            EventMessage<String> eventToHandleTwo = EventTestUtils.asEventMessage("some-other-text");
            List<String> eventsToHandle = new ArrayList<>();
            eventsToHandle.add(eventToHandleOne.getPayload());
            eventsToHandle.add(eventToHandleTwo.getPayload());

            List<Object> eventsToValidate = new ArrayList<>();
            eventsToValidate.add(eventToHandleOne.getPayload());
            eventsToValidate.add(eventToHandleTwo.getPayload());

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
            await().atMost(1, TimeUnit.SECONDS)
                   .untilAsserted(() -> assertThat(stringEventHandlingComponent.recorded()).hasSameSizeAs(
                           eventsToValidate));

            // then - Validate that the correct String events were handled.
            List<EventMessage<?>> handledEvents = stringEventHandlingComponent.recorded();
            assertThat(handledEvents).hasSize(2);

            List<Object> handledPayloads = handledEvents.stream()
                                                        .map(EventMessage::getPayload)
                                                        .collect(Collectors.toList());
            assertThat(handledPayloads).containsExactlyInAnyOrderElementsOf(eventsToHandle);

            // then - Verify that ignored events are tracked correctly
            List<EventMessage<?>> ignoredEvents = stubMessageSource.getIgnoredEvents();
            assertThat(ignoredEvents).hasSize(3);

            List<Object> ignoredPayloads = ignoredEvents.stream()
                                                        .map(EventMessage::getPayload)
                                                        .collect(Collectors.toList());
            assertThat(ignoredPayloads).containsExactlyInAnyOrderElementsOf(eventsToIgnore);
        }
    }

    @Test
    void handlingEventsByMultipleEventHandlingComponents() {
        // given
        var eventHandlingComponent1 = new RecordingEventHandlingComponent(new SimpleEventHandlingComponent());
        eventHandlingComponent1.subscribe(new QualifiedName(String.class), (event, ctx) -> MessageStream.empty());
        var eventHandlingComponent2 = new RecordingEventHandlingComponent(new SimpleEventHandlingComponent());
        eventHandlingComponent2.subscribe(new QualifiedName(String.class), (event, ctx) -> MessageStream.empty());

        List<EventHandlingComponent> components = List.of(eventHandlingComponent1, eventHandlingComponent2);
        withTestSubject(components, customization -> customization.initialSegmentCount(1));

        // when
        EventMessage<Integer> supportedEvent1 = EventTestUtils.asEventMessage("Payload");
        EventMessage<Integer> supportedEvent2 = EventTestUtils.asEventMessage("Payload");
        stubMessageSource.publishMessage(supportedEvent1);
        stubMessageSource.publishMessage(supportedEvent2);
        testSubject.start();

        // then
        await().atMost(200, TimeUnit.MILLISECONDS)
               .untilAsserted(() -> assertThat(testSubject.processingStatus()).hasSizeGreaterThan(0));

        // then
        assertThat(eventHandlingComponent1.recorded()).containsExactly(supportedEvent1, supportedEvent2);
        assertThat(eventHandlingComponent2.recorded()).containsExactly(supportedEvent1, supportedEvent2);

        // then
        await().atMost(200, TimeUnit.MILLISECONDS)
               .untilAsserted(() -> {
                   long currentPosition = testSubject.processingStatus().get(0).getCurrentPosition().orElse(0);
                   assertThat(currentPosition).isEqualTo(2);
               });
    }

    @Nested
    class SegmentClaimingAndReleasingTest {

        @Test
        void releaseAndClaimSegment() {
            // given
            int testSegmentId = 0;
            int testTokenClaimInterval = 5000;

            withTestSubject(
                    List.of(),
                    c -> c.initialSegmentCount(2).tokenClaimInterval(testTokenClaimInterval)
            );

            // when
            testSubject.start();

            // then - Assert the single WorkPackage is in progress prior to invoking the merge.
            assertWithin(
                    testTokenClaimInterval, TimeUnit.MILLISECONDS,
                    () -> assertNotNull(testSubject.processingStatus().get(testSegmentId))
            );

            // when
            testSubject.releaseSegment(testSegmentId, 180, TimeUnit.SECONDS);

            // then - Assert the MergeTask is done and completed successfully.
            assertWithin(testTokenClaimInterval,
                         TimeUnit.MILLISECONDS,
                         () -> assertEquals(1, testSubject.processingStatus().size()));

            testSubject.claimSegment(testSegmentId);

            // then - Assert the Coordinator has only one WorkPackage at work now.
            assertWithin(testTokenClaimInterval,
                         TimeUnit.MILLISECONDS,
                         () -> assertEquals(2, testSubject.processingStatus().size()));
        }
    }

    @Nested
    class SegmentChangeTest {

        @Test
        void releaseSegmentMakesTheTokenUnclaimedForTwiceTheTokenClaimInterval() {
            // given
            int testSegmentId = 0;
            int testTokenClaimInterval = 500;

            withTestSubject(
                    List.of(),
                    c -> c.initialSegmentCount(1).tokenClaimInterval(testTokenClaimInterval)
            );


            // when
            testSubject.start();

            // then - Assert the single WorkPackage is in progress prior to invoking the release.
            assertWithin(
                    testTokenClaimInterval, TimeUnit.MILLISECONDS,
                    () -> assertNotNull(testSubject.processingStatus().get(testSegmentId))
            );

            // when
            testSubject.releaseSegment(testSegmentId);

            await().atMost(testTokenClaimInterval + 200, TimeUnit.MILLISECONDS)
                   .untilAsserted(() -> assertNull(testSubject.processingStatus().get(testSegmentId)));

            // then - Assert that within twice the tokenClaimInterval, the WorkPackage is in progress again.
            await().atMost((testTokenClaimInterval * 2) + 200, TimeUnit.MILLISECONDS)
                   .untilAsserted(() -> assertNotNull(testSubject.processingStatus().get(testSegmentId)));
        }

        @Test
        void splitSegmentIsNotSupported() {
            when(tokenStore.requiresExplicitSegmentInitialization()).thenReturn(false);

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
            // given
            int testSegmentId = 0;
            int testTokenClaimInterval = 500;

            withTestSubject(
                    List.of(),
                    c -> c.initialSegmentCount(1).tokenClaimInterval(testTokenClaimInterval)
            );

            // when
            testSubject.start();

            // then - Assert the single WorkPackage is in progress prior to invoking the split.
            assertWithin(
                    500, TimeUnit.MILLISECONDS,
                    () -> assertNotNull(testSubject.processingStatus().get(testSegmentId))
            );

            // when
            CompletableFuture<Boolean> result = testSubject.splitSegment(testSegmentId);

            // then - Assert the SplitTask is done and completed successfully.
            assertWithin(testTokenClaimInterval * 2, TimeUnit.MILLISECONDS, () -> assertTrue(result.isDone()));
            assertFalse(result.isCompletedExceptionally());
            // then - Assert the Coordinator has set two WorkPackages on the segments.
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
            when(tokenStore.requiresExplicitSegmentInitialization()).thenReturn(false);

            CompletableFuture<Boolean> result = testSubject.mergeSegment(0);

            assertTrue(result.isDone());
            assertTrue(result.isCompletedExceptionally());
            result.exceptionally(exception -> {
                assertTrue(exception.getClass().isAssignableFrom(UnsupportedOperationException.class));
                return null;
            });
        }
    }


    @Nested
    class ErrorHandlerTest {

        @Test
        void errorHandlerIsInvokedWhenEventHandlingComponentHandleFails() {
            // given
            var mockErrorHandler = mock(ErrorHandler.class);
            var expectedError = new RuntimeException("Simulated handling error");
            var failingEventHandlingComponent = new SimpleEventHandlingComponent();
            failingEventHandlingComponent.subscribe(new QualifiedName(String.class), (event, context) -> MessageStream.failed(expectedError));
            withTestSubject(List.of(failingEventHandlingComponent), c -> c.errorHandler(mockErrorHandler));

            // when
            EventMessage<String> testEvent = EventTestUtils.asEventMessage("Payload");
            stubMessageSource.publishMessage(testEvent);
            testSubject.start();

            // then
            await().atMost(1, TimeUnit.SECONDS)
                   .untilAsserted(() -> {
                       var errorContextCaptor = ArgumentCaptor.forClass(ErrorContext.class);
                       verify(mockErrorHandler).handleError(errorContextCaptor.capture());

                       var capturedContext = errorContextCaptor.getValue();
                       assertThat(capturedContext.error()).isEqualTo(expectedError);
                       assertThat(capturedContext.eventProcessor()).isEqualTo(PROCESSOR_NAME);

                       var eventMessages = capturedContext.failedEvents();
                       assertThat(eventMessages).hasSize(1);
                       assertThat(eventMessages.getFirst()).isEqualTo(testEvent);
                   });
        }

        @Test
        void errorHandlerIsInvokedWhenEventHandlingComponentSupportsFails() {
            // given
            var mockErrorHandler = mock(ErrorHandler.class);
            var expectedError = new RuntimeException("Simulated handling error");
            when(defaultEventHandlingComponent.supports(new QualifiedName(Integer.class))).thenThrow(expectedError);
            withTestSubject(List.of(), c -> c.errorHandler(mockErrorHandler)
                                             .initialSegmentCount(1)
                                             .eventCriteria(ignored -> EventCriteria.havingAnyTag())
            );

            // when
            EventMessage<Integer> testEvent = EventTestUtils.asEventMessage(42);
            stubMessageSource.publishMessage(testEvent);
            testSubject.start();

            // then
            await().atMost(1, TimeUnit.SECONDS)
                   .untilAsserted(() -> {
                       var errorContextCaptor = ArgumentCaptor.forClass(ErrorContext.class);
                       verify(mockErrorHandler).handleError(errorContextCaptor.capture());

                       var capturedContext = errorContextCaptor.getValue();
                       assertThat(capturedContext.error()).isEqualTo(expectedError);
                       assertThat(capturedContext.eventProcessor()).isEqualTo(PROCESSOR_NAME);

                       var eventMessages = capturedContext.failedEvents();
                       assertThat(eventMessages).hasSize(1);
                       assertThat(eventMessages.getFirst()).isEqualTo(testEvent);
                   });
        }

    }

    @Disabled("TODO #3304 - Integrate event replay logic into Event Handling Component")
    @Nested
    class ResetSupportTest {

        @Test
        void startingAfterShutdownLetsProcessorProceed() {
//            when(stubEventHandler.supportsReset()).thenReturn(true);

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
        void supportReset() {
//            when(stubEventHandler.supportsReset()).thenReturn(true);

            assertTrue(testSubject.supportsReset());

//            when(stubEventHandler.supportsReset()).thenReturn(false);

            assertFalse(testSubject.supportsReset());
        }

        @Test
        void resetTokensFailsIfTheProcessorIsStillRunning() {
            testSubject.start();

            assertThrows(IllegalStateException.class, () -> testSubject.resetTokens());
        }

        @Test
        void resetTokens() {
//            int expectedSegmentCount = 2;
//            TrackingToken expectedToken = new GlobalSequenceTrackingToken(42);
//
//            when(stubEventHandler.supportsReset()).thenReturn(true);
//            setTestSubject(createTestSubject(builder -> builder.initialSegmentCount(expectedSegmentCount)
//                                                               .initialToken(source -> CompletableFuture.completedFuture(
//                                                                       expectedToken))));
//
//            // Start and stop the processor to initialize the tracking tokens
//            testSubject.start();
//            assertWithin(2,
//                         TimeUnit.SECONDS,
//                         () -> assertEquals(expectedSegmentCount, tokenStore.fetchSegments(PROCESSOR_NAME).length));
//            testSubject.shutDown();
//
//            testSubject.resetTokens();
//
//            verify(stubEventHandler).performReset(null, null);
//
//            int[] segments = tokenStore.fetchSegments(PROCESSOR_NAME);
//            // The token stays the same, as the original and token after reset are identical.
//            assertEquals(expectedToken, tokenStore.fetchToken(PROCESSOR_NAME, segments[0]));
//            assertEquals(expectedToken, tokenStore.fetchToken(PROCESSOR_NAME, segments[1]));
        }

        @Test
        void resetTokensWithContext() {
//            int expectedSegmentCount = 2;
//            TrackingToken expectedToken = new GlobalSequenceTrackingToken(42);
//            String expectedContext = "my-context";
//
//            when(stubEventHandler.supportsReset()).thenReturn(true);
//            setTestSubject(createTestSubject(builder -> builder.initialSegmentCount(expectedSegmentCount)
//                                                               .initialToken(source -> CompletableFuture.completedFuture(
//                                                                       expectedToken))));
//
//            // Start and stop the processor to initialize the tracking tokens
//            testSubject.start();
//            await().atMost(Duration.ofSeconds(2L)).untilAsserted(
//                    () -> assertEquals(expectedSegmentCount, tokenStore.fetchSegments(PROCESSOR_NAME).length)
//            );
//            testSubject.shutDown();
//
//            testSubject.resetTokens(expectedContext);
//
//            verify(stubEventHandler).performReset(expectedContext, null);
//
//            int[] segments = tokenStore.fetchSegments(PROCESSOR_NAME);
//            // The token stays the same, as the original and token after reset are identical.
//            assertEquals(expectedToken, tokenStore.fetchToken(PROCESSOR_NAME, segments[0]));
//            assertEquals(expectedToken, tokenStore.fetchToken(PROCESSOR_NAME, segments[1]));
//            await().atMost(Duration.ofSeconds(2L)).untilAsserted(
//                    () -> verify(stubEventHandler, times(2)).segmentReleased(any(Segment.class))
//            );
        }

        @Test
        void isReplaying() {
//                when(stubEventHandler.supportsReset()).thenReturn(true);

            withTestSubject(List.of(), c -> c.initialSegmentCount(1));

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
    }

    @Test
    void resetTokensFromDefinedPosition() {
//            TrackingToken testToken = new GlobalSequenceTrackingToken(42);
//
//            int expectedSegmentCount = 2;
//            TrackingToken expectedToken = ReplayToken.createReplayToken(testToken, null);
//
//            when(stubEventHandler.supportsReset()).thenReturn(true);
//            setTestSubject(createTestSubject(builder -> builder.initialSegmentCount(expectedSegmentCount)
//                                                               .initialToken(source -> CompletableFuture.completedFuture(
//                                                                       testToken))));
//
//            // Start and stop the processor to initialize the tracking tokens
//            testSubject.start();
//            assertWithin(2,
//                         TimeUnit.SECONDS,
//                         () -> assertEquals(expectedSegmentCount, tokenStore.fetchSegments(PROCESSOR_NAME).length));
//            testSubject.shutDown();
//
//            testSubject.resetTokens(source -> source.latestToken());
//
//            verify(stubEventHandler).performReset(isNull(), any());
//
//            int[] segments = tokenStore.fetchSegments(PROCESSOR_NAME);
//            assertEquals(expectedToken, tokenStore.fetchToken(PROCESSOR_NAME, segments[0]));
//            assertEquals(expectedToken, tokenStore.fetchToken(PROCESSOR_NAME, segments[1]));
    }

    @Test
    void resetTokensFromDefinedPositionAndWithResetContext() {
//            TrackingToken testToken = new GlobalSequenceTrackingToken(42);
//
//            int expectedSegmentCount = 2;
//            String expectedContext = "my-context";
//            TrackingToken expectedToken = ReplayToken.createReplayToken(testToken, null, expectedContext);
//
//            when(stubEventHandler.supportsReset()).thenReturn(true);
//            setTestSubject(createTestSubject(builder -> builder.initialSegmentCount(expectedSegmentCount)
//                                                               .initialToken(source -> CompletableFuture.completedFuture(
//                                                                       testToken))));
//
//            // Start and stop the processor to initialize the tracking tokens
//            testSubject.start();
//            assertWithin(2,
//                         TimeUnit.SECONDS,
//                         () -> assertEquals(expectedSegmentCount, tokenStore.fetchSegments(PROCESSOR_NAME).length));
//            testSubject.shutDown();
//
//            testSubject.resetTokens(source -> source.latestToken(), expectedContext);
//
//            verify(stubEventHandler).performReset(eq(expectedContext), any());
//
//            int[] segments = tokenStore.fetchSegments(PROCESSOR_NAME);
//            assertEquals(expectedToken, tokenStore.fetchToken(PROCESSOR_NAME, segments[0]));
//            assertEquals(expectedToken, tokenStore.fetchToken(PROCESSOR_NAME, segments[1]));
    }

    @Nested
    class ConfigurationTest {

        @Test
        void maxCapacityDefaultsToShortMax() {
            assertEquals(Short.MAX_VALUE, testSubject.maxCapacity());
        }

        @Test
        void maxCapacityReturnsConfiguredCapacity() {
            int expectedMaxCapacity = 500;
            withTestSubject(List.of(), (c -> c.maxClaimedSegments(expectedMaxCapacity)));

            assertEquals(expectedMaxCapacity, testSubject.maxCapacity());
        }

        @Test
        void zeroOrNegativeInitialSegmentCountThrowsAxonConfigurationException() {
            assertThrows(AxonConfigurationException.class,
                         () -> withTestSubject(List.of(), c -> c.initialSegmentCount(0)));
            assertThrows(AxonConfigurationException.class,
                         () -> withTestSubject(List.of(), c -> c.initialSegmentCount(-1)));
        }
    }
}

