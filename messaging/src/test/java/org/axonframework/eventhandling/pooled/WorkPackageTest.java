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

package org.axonframework.eventhandling.pooled;

import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.GenericTrackedEventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.ReplayToken;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackerStatus;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.axonframework.utils.AssertUtils.assertWithin;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link WorkPackage}.
 *
 * @author Steven van Beelen
 */
class WorkPackageTest {

    private static final String PROCESSOR_NAME = "test";

    private TokenStore tokenStore;
    private ExecutorService executorService;
    private TestEventFilter eventFilter;
    private TestBatchProcessor batchProcessor;
    private Segment segment;
    private TrackingToken initialTrackingToken;

    private WorkPackage.Builder testSubjectBuilder;
    private WorkPackage testSubject;

    private TrackerStatus trackerStatus;
    private List<TrackerStatus> trackerStatusUpdates;
    private Predicate<TrackedEventMessage<?>> eventFilterPredicate;
    private Predicate<List<? extends EventMessage<?>>> batchProcessorPredicate;

    @BeforeEach
    void setUp() {
        tokenStore = spy(new InMemoryTokenStore());
        executorService = spy(Executors.newScheduledThreadPool(1));
        eventFilter = new TestEventFilter();
        batchProcessor = new TestBatchProcessor();
        segment = Segment.ROOT_SEGMENT;
        initialTrackingToken = new GlobalSequenceTrackingToken(0L);

        trackerStatus = new TrackerStatus(segment, initialTrackingToken);
        trackerStatusUpdates = new ArrayList<>();
        eventFilterPredicate = event -> true;
        batchProcessorPredicate = event -> true;

        testSubjectBuilder = WorkPackage.builder()
                                        .name(PROCESSOR_NAME)
                                        .tokenStore(tokenStore)
                                        .transactionManager(NoTransactionManager.instance())
                                        .executorService(executorService)
                                        .eventFilter(eventFilter)
                                        .batchProcessor(batchProcessor)
                                        .segment(segment)
                                        .initialToken(initialTrackingToken)
                                        .batchSize(1)
                                        .claimExtensionThreshold(5000)
                                        .segmentStatusUpdater(op -> {
                                            TrackerStatus update = op.apply(trackerStatus);
                                            trackerStatusUpdates.add(update);
                                            trackerStatus = update;
                                        });
        testSubject = testSubjectBuilder.build();
    }

    @AfterEach
    void tearDown() {
        executorService.shutdown();
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    /**
     * The "last delivered token" is configured as the initialToken for a fresh WorkPackage.
     */
    @Test
    void testScheduleEventDoesNotScheduleIfTheLastDeliveredTokenCoversTheEventsToken() {
        TrackedEventMessage<String> testEvent = new GenericTrackedEventMessage<>(
                new GlobalSequenceTrackingToken(1L), GenericEventMessage.asEventMessage("some-event")
        );

        WorkPackage testSubjectWithCustomInitialToken =
                testSubjectBuilder.initialToken(new GlobalSequenceTrackingToken(2L))
                                  .build();

        testSubjectWithCustomInitialToken.scheduleEvent(testEvent);

        verifyNoInteractions(executorService);
    }

    @Test
    void testScheduleEventUpdatesLastDeliveredToken() {
        TrackingToken expectedToken = new GlobalSequenceTrackingToken(1L);
        TrackedEventMessage<String> testEvent =
                new GenericTrackedEventMessage<>(expectedToken, GenericEventMessage.asEventMessage("some-event"));

        testSubject.scheduleEvent(testEvent);

        assertEquals(expectedToken, testSubject.lastDeliveredToken());
    }

    @Test
    void testScheduleEventFailsOnEventValidator() throws ExecutionException, InterruptedException {
        TrackingToken testToken = new GlobalSequenceTrackingToken(1L);
        TrackedEventMessage<String> testEvent =
                new GenericTrackedEventMessage<>(testToken, GenericEventMessage.asEventMessage("some-event"));
        eventFilterPredicate = event -> {
            if (event.equals(testEvent)) {
                throw new IllegalStateException("Some exception");
            }
            return true;
        };

        testSubject.scheduleEvent(testEvent);

        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertNull(trackerStatus));
        assertEquals(2, trackerStatusUpdates.size());
        assertTrue(trackerStatusUpdates.get(0).isErrorState());
        assertNull(trackerStatusUpdates.get(1));

        CompletableFuture<Exception> abortResult = testSubject.abort(null);
        assertTrue(abortResult.isDone());
        assertTrue(abortResult.get().getClass().isAssignableFrom(IllegalStateException.class));
    }

    @Test
    void testScheduleEventFailsOnBatchProcessor() throws ExecutionException, InterruptedException {
        TrackingToken testToken = new GlobalSequenceTrackingToken(1L);
        TrackedEventMessage<String> testEvent =
                new GenericTrackedEventMessage<>(testToken, GenericEventMessage.asEventMessage("some-event"));
        batchProcessorPredicate = event -> {
            if (event.stream().anyMatch(e -> ((TrackedEventMessage<?>) e).trackingToken().equals(testToken))) {
                throw new IllegalStateException("Some exception");
            }
            return true;
        };

        testSubject.scheduleEvent(testEvent);

        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertNull(trackerStatus));
        assertEquals(2, trackerStatusUpdates.size());
        assertTrue(trackerStatusUpdates.get(0).isErrorState());
        assertNull(trackerStatusUpdates.get(1));

        CompletableFuture<Exception> abortResult = testSubject.abort(null);
        assertTrue(abortResult.isDone());
        assertTrue(abortResult.get().getClass().isAssignableFrom(IllegalStateException.class));
    }

    /**
     * This means an event was scheduled, was validated to be handled by the EventValidator, processed by the
     * BatchProcessor and the updated token stored.
     */
    @Test
    void testScheduleEventRunsSuccessfully() {
        TrackingToken expectedToken = new GlobalSequenceTrackingToken(1L);
        TrackedEventMessage<String> expectedEvent =
                new GenericTrackedEventMessage<>(expectedToken, GenericEventMessage.asEventMessage("some-event"));

        testSubject.scheduleEvent(expectedEvent);

        List<EventMessage<?>> validatedEvents = eventFilter.getValidatedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(1, validatedEvents.size()));
        assertEquals(expectedEvent, validatedEvents.get(0));

        List<EventMessage<?>> processedEvents = batchProcessor.getProcessedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(1, processedEvents.size()));
        assertEquals(expectedEvent.trackingToken(), ((TrackedEventMessage<?>) processedEvents.get(0)).trackingToken());

        ArgumentCaptor<TrackingToken> tokenCaptor = ArgumentCaptor.forClass(TrackingToken.class);
        verify(tokenStore).storeToken(tokenCaptor.capture(), eq(PROCESSOR_NAME), eq(segment.getSegmentId()));
        assertEquals(expectedToken, tokenCaptor.getValue());

        assertEquals(1, trackerStatusUpdates.size());
        OptionalLong resultPosition = trackerStatusUpdates.get(0).getCurrentPosition();
        assertTrue(resultPosition.isPresent());
        assertEquals(1L, resultPosition.getAsLong());
    }

    @Test
    void testReplayTokenIsPropagatedAndAdvancedWithoutCurrent() {
        testSubjectBuilder.initialToken(new ReplayToken(new GlobalSequenceTrackingToken(1L)));
        testSubject = testSubjectBuilder.build();
        TrackingToken expectedToken = new GlobalSequenceTrackingToken(1L);
        TrackedEventMessage<String> expectedEvent =
                new GenericTrackedEventMessage<>(expectedToken, GenericEventMessage.asEventMessage("some-event"));

        testSubject.scheduleEvent(expectedEvent);

        List<EventMessage<?>> processedEvents = batchProcessor.getProcessedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(1, processedEvents.size()));

        ReplayToken expectedAdvancedToken = new ReplayToken(
                new GlobalSequenceTrackingToken(1L),
                new GlobalSequenceTrackingToken(1L)
        );
        assertEquals(expectedAdvancedToken, ((TrackedEventMessage<?>) processedEvents.get(0)).trackingToken());
    }


    @Test
    void testReplayTokenIsPropagatedAndAdvancedWithCurrent() {
        testSubjectBuilder.initialToken(new ReplayToken(new GlobalSequenceTrackingToken(1L),
                                                        new GlobalSequenceTrackingToken(0L)));
        testSubject = testSubjectBuilder.build();
        TrackingToken expectedToken = new GlobalSequenceTrackingToken(1L);
        TrackedEventMessage<String> expectedEvent =
                new GenericTrackedEventMessage<>(expectedToken, GenericEventMessage.asEventMessage("some-event"));

        testSubject.scheduleEvent(expectedEvent);

        List<EventMessage<?>> processedEvents = batchProcessor.getProcessedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(1, processedEvents.size()));

        ReplayToken expectedAdvancedToken = new ReplayToken(
                new GlobalSequenceTrackingToken(1L),
                new GlobalSequenceTrackingToken(1L)
        );
        assertEquals(expectedAdvancedToken, ((TrackedEventMessage<?>) processedEvents.get(0)).trackingToken());
    }

    @Test
    void testScheduleEventExtendsTokenClaimAfterClaimThresholdExtension() {
        // The short threshold ensures the packages assume the token should be reclaimed.
        int extremelyShortClaimExtensionThreshold = 1;
        WorkPackage testSubjectWithShortThreshold =
                testSubjectBuilder.claimExtensionThreshold(extremelyShortClaimExtensionThreshold)
                                  .build();

        TrackingToken expectedToken = new GlobalSequenceTrackingToken(1L);
        TrackedEventMessage<String> expectedEvent =
                new GenericTrackedEventMessage<>(expectedToken, GenericEventMessage.asEventMessage("some-event"));
        testSubjectWithShortThreshold.scheduleEvent(expectedEvent);

        // Should have handled one event, so a subsequent run of WorkPackage#processEvents will extend the claim.
        List<EventMessage<?>> processedEvents = batchProcessor.getProcessedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(1, processedEvents.size()));
        assertEquals(expectedEvent.trackingToken(), ((TrackedEventMessage<?>) processedEvents.get(0)).trackingToken());
        // We  need to verify the TokenStore#storeToken operation, otherwise the extendClaim verify will not succeed.
        ArgumentCaptor<TrackingToken> tokenCaptor = ArgumentCaptor.forClass(TrackingToken.class);
        verify(tokenStore).storeToken(tokenCaptor.capture(), eq(PROCESSOR_NAME), eq(segment.getSegmentId()));
        assertEquals(expectedToken, tokenCaptor.getValue());

        assertWithin(
                500, TimeUnit.MILLISECONDS,
                () -> {
                    // Consciously trigger the WorkPackage again, to force it through WorkPackage#processEvents.
                    // This should be done inside the assertWithin, as the WorkPackage does not re-trigger itself.
                    // Furthermore, the test could be to fast to incorporate the extremelyShortClaimExtensionThreshold as a reason to extend the claim too.
                    testSubjectWithShortThreshold.scheduleWorker();
                    verify(tokenStore, atLeastOnce()).extendClaim(PROCESSOR_NAME, segment.getSegmentId());
                }
        );
    }

    /**
     * This requires the WorkPackage to have received events which it should not handle.
     */
    @Test
    void testScheduleEventUpdatesTokenAfterClaimThresholdExtension() {
        // The short threshold ensures the packages assume the token should be reclaimed.
        int extremelyShortClaimExtensionThreshold = 1;
        WorkPackage testSubjectWithShortThreshold =
                testSubjectBuilder.claimExtensionThreshold(extremelyShortClaimExtensionThreshold)
                                  .build();
        // Adjust the EventValidator to reject all events
        eventFilterPredicate = event -> false;

        TrackingToken expectedToken = new GlobalSequenceTrackingToken(1L);
        TrackedEventMessage<String> expectedEvent =
                new GenericTrackedEventMessage<>(expectedToken, GenericEventMessage.asEventMessage("some-event"));
        testSubjectWithShortThreshold.scheduleEvent(expectedEvent);

        List<EventMessage<?>> validatedEvents = eventFilter.getValidatedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(1, validatedEvents.size()));
        assertEquals(expectedEvent, validatedEvents.get(0));

        ArgumentCaptor<TrackingToken> tokenCaptor = ArgumentCaptor.forClass(TrackingToken.class);

        assertWithin(
                500, TimeUnit.MILLISECONDS,
                () -> {
                    // Consciously trigger the WorkPackage again, to force it through WorkPackage#processEvents.
                    // This should be done inside the assertWithin, as the WorkPackage does not re-trigger itself.
                    // Furthermore, the test could be to fast to incorporate the extremelyShortClaimExtensionThreshold as a reason to extend the claim too.
                    testSubjectWithShortThreshold.scheduleWorker();
                    verify(tokenStore, atLeastOnce())
                            .storeToken(tokenCaptor.capture(), eq(PROCESSOR_NAME), eq(segment.getSegmentId()));
                }
        );
        assertEquals(expectedToken, tokenCaptor.getValue());
    }

    @Test
    void testScheduleWorkerForAbortedPackage() throws ExecutionException, InterruptedException {
        CompletableFuture<Exception> result = testSubject.abort(null);

        testSubject.scheduleWorker();

        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertNull(trackerStatus));
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertTrue(result.isDone()));
        assertNull(result.get());
    }

    @Test
    void testHasRemainingCapacityReturnsTrueForWorkPackageWithoutScheduledEvents() {
        assertTrue(testSubject.hasRemainingCapacity());
    }

    @Test
    void testSegment() {
        assertEquals(segment, testSubject.segment());
    }

    @Test
    void testLastDeliveredTokenEqualsInitialTokenWhenNoEventsHaveBeenScheduled() {
        assertEquals(initialTrackingToken, testSubject.lastDeliveredToken());
    }

    @Test
    void testIsAbortTriggeredReturnsFalseInAbsenceOfAbort() {
        assertFalse(testSubject.isAbortTriggered());
    }

    @Test
    void testIsAbortTriggeredReturnsTrueAfterAbortInvocation() {
        testSubject.abort(null);
        assertTrue(testSubject.isAbortTriggered());
    }

    @Test
    void testAbortReturnsAbortReason() throws ExecutionException, InterruptedException {
        Exception expectedResult = new IllegalStateException();

        CompletableFuture<Exception> result = testSubject.abort(expectedResult);

        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertTrue(result.isDone()));
        assertEquals(expectedResult, result.get());
    }

    @Test
    void testAbortReturnsOriginalAbortReason() throws ExecutionException, InterruptedException {
        Exception originalAbortReason = new IllegalStateException();
        Exception otherAbortReason = new IllegalArgumentException();
        testSubject.abort(originalAbortReason);

        CompletableFuture<Exception> result = testSubject.abort(otherAbortReason);

        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertTrue(result.isDone()));
        assertEquals(originalAbortReason, result.get());
    }

    @Test
    void testScheduleEventsReturnsFalseForEmptyList() {
        assertFalse(testSubject.scheduleEvents(Collections.emptyList()));
    }

    @Test
    void testScheduleEventsThrowsIllegalArgumentExceptionForNoneMatchingTokens() {
        TrackingToken testTokenOne = new GlobalSequenceTrackingToken(1L);
        TrackedEventMessage<String> testEventOne =
                new GenericTrackedEventMessage<>(testTokenOne, GenericEventMessage.asEventMessage("this-event"));
        TrackingToken testTokenTwo = new GlobalSequenceTrackingToken(2L);
        TrackedEventMessage<String> testEventTwo =
                new GenericTrackedEventMessage<>(testTokenTwo, GenericEventMessage.asEventMessage("that-event"));
        List<TrackedEventMessage<?>> testEvents = new ArrayList<>();
        testEvents.add(testEventOne);
        testEvents.add(testEventTwo);

        assertThrows(IllegalArgumentException.class, () -> testSubject.scheduleEvents(testEvents));
    }

    /**
     * The "last delivered token" is configured as the initialToken for a fresh WorkPackage.
     */
    @Test
    void testScheduleEventsDoesNotScheduleIfTheLastDeliveredTokensCoversTheEventsToken() {
        TrackingToken testToken = new GlobalSequenceTrackingToken(1L);
        TrackedEventMessage<String> testEventOne =
                new GenericTrackedEventMessage<>(testToken, GenericEventMessage.asEventMessage("this-event"));
        TrackedEventMessage<String> testEventTwo =
                new GenericTrackedEventMessage<>(testToken, GenericEventMessage.asEventMessage("that-event"));
        List<TrackedEventMessage<?>> testEvents = new ArrayList<>();
        testEvents.add(testEventOne);
        testEvents.add(testEventTwo);

        WorkPackage testSubjectWithCustomInitialToken =
                testSubjectBuilder.initialToken(new GlobalSequenceTrackingToken(2L))
                                  .build();

        boolean result = testSubjectWithCustomInitialToken.scheduleEvents(testEvents);

        assertFalse(result);
        verifyNoInteractions(executorService);
    }

    @Test
    void testScheduleEventsReturnsTrueIfOnlyOneEventIsAcceptedByTheEventValidator() {
        TrackingToken expectedToken = new GlobalSequenceTrackingToken(1L);
        TrackedEventMessage<String> filteredEvent =
                new GenericTrackedEventMessage<>(expectedToken, GenericEventMessage.asEventMessage("this-event"));
        TrackedEventMessage<String> expectedEvent =
                new GenericTrackedEventMessage<>(expectedToken, GenericEventMessage.asEventMessage("that-event"));
        List<TrackedEventMessage<?>> testEvents = new ArrayList<>();
        testEvents.add(filteredEvent);
        testEvents.add(expectedEvent);

        eventFilterPredicate = event -> !event.equals(filteredEvent);

        boolean result = testSubject.scheduleEvents(testEvents);

        assertTrue(result);

        List<EventMessage<?>> validatedEvents = eventFilter.getValidatedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(2, validatedEvents.size()));
        assertTrue(validatedEvents.containsAll(testEvents));

        List<EventMessage<?>> processedEvents = batchProcessor.getProcessedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(1, processedEvents.size()));
        assertEquals(expectedEvent.trackingToken(), ((TrackedEventMessage<?>) processedEvents.get(0)).trackingToken());

        ArgumentCaptor<TrackingToken> tokenCaptor = ArgumentCaptor.forClass(TrackingToken.class);
        verify(tokenStore).storeToken(tokenCaptor.capture(), eq(PROCESSOR_NAME), eq(segment.getSegmentId()));
        assertEquals(expectedToken, tokenCaptor.getValue());

        assertEquals(1, trackerStatusUpdates.size());
        OptionalLong resultPosition = trackerStatusUpdates.get(0).getCurrentPosition();
        assertTrue(resultPosition.isPresent());
        assertEquals(1L, resultPosition.getAsLong());
    }

    @Test
    void testScheduleEventsHandlesAllEventsInOneTransactionWhenAllEventsCanBeHandled() {
        TrackingToken expectedToken = new GlobalSequenceTrackingToken(1L);
        TrackedEventMessage<String> expectedEventOne =
                new GenericTrackedEventMessage<>(expectedToken, GenericEventMessage.asEventMessage("this-event"));
        TrackedEventMessage<String> expectedEventTwo =
                new GenericTrackedEventMessage<>(expectedToken, GenericEventMessage.asEventMessage("that-event"));
        List<TrackedEventMessage<?>> expectedEvents = new ArrayList<>();
        expectedEvents.add(expectedEventOne);
        expectedEvents.add(expectedEventTwo);

        boolean result = testSubject.scheduleEvents(expectedEvents);

        assertTrue(result);

        List<EventMessage<?>> validatedEvents = eventFilter.getValidatedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(2, validatedEvents.size()));
        assertTrue(validatedEvents.containsAll(expectedEvents));

        List<EventMessage<?>> processedEvents = batchProcessor.getProcessedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(2, processedEvents.size()));
        assertEquals(expectedEventOne.trackingToken(),
                     ((TrackedEventMessage<?>) processedEvents.get(0)).trackingToken());
        assertEquals(expectedEventTwo.trackingToken(),
                     ((TrackedEventMessage<?>) processedEvents.get(1)).trackingToken());

        ArgumentCaptor<TrackingToken> tokenCaptor = ArgumentCaptor.forClass(TrackingToken.class);
        verify(tokenStore).storeToken(tokenCaptor.capture(), eq(PROCESSOR_NAME), eq(segment.getSegmentId()));
        assertEquals(expectedToken, tokenCaptor.getValue());

        assertTrue(trackerStatusUpdates.size() >= 1);
        OptionalLong resultPosition = trackerStatusUpdates.get(0).getCurrentPosition();
        assertTrue(resultPosition.isPresent());
        assertEquals(1L, resultPosition.getAsLong());
    }

    private class TestEventFilter implements WorkPackage.EventFilter {

        private final List<EventMessage<?>> validatedEvents = new ArrayList<>();

        @Override
        public boolean canHandle(TrackedEventMessage<?> eventMessage, Segment segment) {
            validatedEvents.add(eventMessage);
            return eventFilterPredicate.test(eventMessage);
        }

        public List<EventMessage<?>> getValidatedEvents() {
            return validatedEvents;
        }
    }

    private class TestBatchProcessor implements WorkPackage.BatchProcessor {

        private final List<EventMessage<?>> processedEvents = new ArrayList<>();

        @Override
        public void processBatch(List<? extends EventMessage<?>> eventMessages,
                                 UnitOfWork<? extends EventMessage<?>> unitOfWork,
                                 Collection<Segment> processingSegments) {
            if (batchProcessorPredicate.test(eventMessages)) {
                unitOfWork.executeWithResult(() -> {
                    unitOfWork.commit();
                    processedEvents.addAll(eventMessages);
                    // We don't care about the result to perform our tests. Just return null.
                    return null;
                });
            }
        }

        public List<EventMessage<?>> getProcessedEvents() {
            return processedEvents;
        }
    }
}
