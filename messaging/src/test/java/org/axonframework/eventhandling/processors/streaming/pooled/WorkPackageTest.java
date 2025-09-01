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

package org.axonframework.eventhandling.processors.streaming.pooled;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.eventhandling.processors.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.processors.streaming.token.ReplayToken;
import org.axonframework.eventhandling.processors.streaming.segmenting.Segment;
import org.axonframework.eventhandling.processors.streaming.segmenting.TrackerStatus;
import org.axonframework.eventhandling.processors.streaming.token.TrackingToken;
import org.axonframework.eventhandling.processors.streaming.token.store.TokenStore;
import org.axonframework.eventhandling.processors.streaming.token.store.inmemory.InMemoryTokenStore;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.SimpleEntry;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.UnitOfWorkTestUtils;
import org.axonframework.utils.DelegateScheduledExecutorService;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
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
    private ScheduledExecutorService executorService;
    private TestEventFilter eventFilter;
    private TestBatchProcessor batchProcessor;
    private Segment segment;
    private TrackingToken initialTrackingToken;

    private WorkPackage.Builder testSubjectBuilder;
    private WorkPackage testSubject;

    private TrackerStatus trackerStatus;
    private List<TrackerStatus> trackerStatusUpdates;
    private Predicate<EventMessage> eventFilterPredicate;
    private BiPredicate<List<? extends EventMessage>, TrackingToken> batchProcessorPredicate;

    @BeforeEach
    void setUp() {
        tokenStore = spy(new InMemoryTokenStore());
        executorService = spy(new DelegateScheduledExecutorService(Executors.newScheduledThreadPool(1)));
        eventFilter = new TestEventFilter();
        batchProcessor = new TestBatchProcessor();
        segment = Segment.ROOT_SEGMENT;
        initialTrackingToken = new GlobalSequenceTrackingToken(0L);

        trackerStatus = new TrackerStatus(segment, initialTrackingToken);
        trackerStatusUpdates = new ArrayList<>();
        eventFilterPredicate = event -> true;
        batchProcessorPredicate = (event, token) -> true;

        testSubjectBuilder = WorkPackage.builder()
                                        .name(PROCESSOR_NAME)
                                        .tokenStore(tokenStore)
                                        .unitOfWorkFactory(UnitOfWorkTestUtils.SIMPLE_FACTORY)
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
    void scheduleEventDoesNotScheduleIfTheLastDeliveredTokenCoversTheEventsToken() {
        var testEvent = new SimpleEntry<>(EventTestUtils.asEventMessage("some-event"), globalTrackingTokenContext(1L));

        WorkPackage testSubjectWithCustomInitialToken =
                testSubjectBuilder.initialToken(new GlobalSequenceTrackingToken(2L))
                                  .build();

        testSubjectWithCustomInitialToken.scheduleEvent(testEvent);

        verifyNoInteractions(executorService);
    }

    @Test
    void scheduleEventUpdatesLastDeliveredToken() {
        TrackingToken expectedToken = new GlobalSequenceTrackingToken(1L);
        var testEvent = new SimpleEntry<>(EventTestUtils.asEventMessage("some-event"), globalTrackingTokenContext(1L));


        testSubject.scheduleEvent(testEvent);

        assertEquals(expectedToken, testSubject.lastDeliveredToken());
    }

    @Test
    void scheduleEventFailsOnEventValidator() throws ExecutionException, InterruptedException {
        TrackingToken testToken = new GlobalSequenceTrackingToken(1L);
        var testMessage = EventTestUtils.asEventMessage("some-event");
        var testEvent = new SimpleEntry<>(testMessage, trackingTokenContext(testToken));

        eventFilterPredicate = event -> {
            if (event.equals(testMessage)) {
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
    void scheduleEventFailsOnBatchProcessor() throws ExecutionException, InterruptedException {
        TrackingToken testToken = new GlobalSequenceTrackingToken(1L);
        var testMessage = EventTestUtils.asEventMessage("some-event");
        var testEvent = new SimpleEntry<>(testMessage, trackingTokenContext(testToken));
        batchProcessorPredicate = (event, token) -> {
            if (event.stream().anyMatch(e -> token.equals(testToken))) {
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
    @Disabled("TODO #3432 - Adjust TokenStore API to be async-native")
    @Test
    void scheduleEventRunsSuccessfully() {
        TrackingToken expectedToken = new GlobalSequenceTrackingToken(1L);
        var testMessage = EventTestUtils.asEventMessage("some-event");
        var expectedEvent = new SimpleEntry<>(testMessage, trackingTokenContext(expectedToken));

        testSubject.scheduleEvent(expectedEvent);

        List<EventMessage> validatedEvents = eventFilter.getValidatedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(1, validatedEvents.size()));
        assertEquals(testMessage, validatedEvents.get(0));

        var processedEvents = batchProcessor.getProcessedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(1, processedEvents.size()));
        assertEquals(expectedToken, TrackingToken.fromContext(processedEvents.getFirst().context()).get());

        ArgumentCaptor<TrackingToken> tokenCaptor = ArgumentCaptor.forClass(TrackingToken.class);
        verify(tokenStore).storeToken(tokenCaptor.capture(), eq(PROCESSOR_NAME), eq(segment.getSegmentId()));
        assertEquals(expectedToken, tokenCaptor.getValue());

        assertEquals(1, trackerStatusUpdates.size());
        OptionalLong resultPosition = trackerStatusUpdates.get(0).getCurrentPosition();
        assertTrue(resultPosition.isPresent());
        assertEquals(1L, resultPosition.getAsLong());
    }

    @Test
    void replayTokenIsPropagatedAndAdvancedWithoutCurrent() {
        testSubjectBuilder.initialToken(ReplayToken.createReplayToken(new GlobalSequenceTrackingToken(1L)));
        testSubject = testSubjectBuilder.build();
        TrackingToken expectedToken = new GlobalSequenceTrackingToken(1L);
        var expectedPayload = EventTestUtils.asEventMessage("some-event");
        var expectedEvent = new SimpleEntry<>(expectedPayload, trackingTokenContext(expectedToken));

        testSubject.scheduleEvent(expectedEvent);

        var processedEvents = batchProcessor.getProcessedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(1, processedEvents.size()));

        TrackingToken resultAdvancedToken = TrackingToken.fromContext(processedEvents.getFirst().context()).get();
        assertInstanceOf(ReplayToken.class, resultAdvancedToken);
        assertEquals(expectedToken, ((ReplayToken) resultAdvancedToken).getCurrentToken());
        assertEquals(expectedToken, ((ReplayToken) resultAdvancedToken).getTokenAtReset());
    }


    @Test
    void replayTokenIsPropagatedAndAdvancedWithCurrent() {
        testSubjectBuilder.initialToken(ReplayToken.createReplayToken(new GlobalSequenceTrackingToken(1L),
                                                                      new GlobalSequenceTrackingToken(0L)));
        testSubject = testSubjectBuilder.build();
        TrackingToken expectedToken = new GlobalSequenceTrackingToken(1L);
        var expectedPayload = EventTestUtils.asEventMessage("some-event");
        var expectedEvent = new SimpleEntry<>(expectedPayload, trackingTokenContext(expectedToken));

        testSubject.scheduleEvent(expectedEvent);

        var processedEvents = batchProcessor.getProcessedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(1, processedEvents.size()));

        TrackingToken resultAdvancedToken = TrackingToken.fromContext(processedEvents.getFirst().context()).get();
        assertInstanceOf(ReplayToken.class, resultAdvancedToken);
        assertEquals(expectedToken, ((ReplayToken) resultAdvancedToken).getCurrentToken());
        assertEquals(expectedToken, ((ReplayToken) resultAdvancedToken).getTokenAtReset());
    }

    @Disabled("TODO #3432 - Adjust TokenStore API to be async-native")
    @Test
    void scheduleEventExtendsTokenClaimAfterClaimThresholdExtension() {
        // The short threshold ensures the packages assume the token should be reclaimed.
        int extremelyShortClaimExtensionThreshold = 1;
        WorkPackage testSubjectWithShortThreshold =
                testSubjectBuilder.claimExtensionThreshold(extremelyShortClaimExtensionThreshold)
                                  .build();

        TrackingToken expectedToken = new GlobalSequenceTrackingToken(1L);
        var expectedPayload = EventTestUtils.asEventMessage("some-event");
        var expectedEvent = new SimpleEntry<>(expectedPayload, trackingTokenContext(expectedToken));
        testSubjectWithShortThreshold.scheduleEvent(expectedEvent);

        // Should have handled one event, so a subsequent run of WorkPackage#processEvents will extend the claim.
        var processedEvents = batchProcessor.getProcessedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(1, processedEvents.size()));
        assertEquals(expectedToken, TrackingToken.fromContext(processedEvents.getFirst().context()).get());
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
    void scheduleEventUpdatesTokenAfterClaimThresholdExtension() {
        // The short threshold ensures the packages assume the token should be reclaimed.
        int extremelyShortClaimExtensionThreshold = 1;
        WorkPackage testSubjectWithShortThreshold =
                testSubjectBuilder.claimExtensionThreshold(extremelyShortClaimExtensionThreshold)
                                  .build();
        // Adjust the EventValidator to reject all events
        eventFilterPredicate = event -> false;

        TrackingToken expectedToken = new GlobalSequenceTrackingToken(1L);
        var expectedPayload = EventTestUtils.asEventMessage("some-event");
        var expectedEvent = new SimpleEntry<>(expectedPayload, trackingTokenContext(expectedToken));
        testSubjectWithShortThreshold.scheduleEvent(expectedEvent);

        var validatedEvents = eventFilter.getValidatedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(1, validatedEvents.size()));
        assertEquals(expectedPayload, validatedEvents.getFirst());

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
    void scheduleWorkerForAbortedPackage() throws ExecutionException, InterruptedException {
        CompletableFuture<Exception> result = testSubject.abort(null);

        testSubject.scheduleWorker();

        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertNull(trackerStatus));
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertTrue(result.isDone()));
        assertNull(result.get());
    }

    @Test
    void hasRemainingCapacityReturnsTrueForWorkPackageWithoutScheduledEvents() {
        assertTrue(testSubject.hasRemainingCapacity());
    }

    @Test
    void segment() {
        assertEquals(segment, testSubject.segment());
    }

    @Test
    void lastDeliveredTokenEqualsInitialTokenWhenNoEventsHaveBeenScheduled() {
        assertEquals(initialTrackingToken, testSubject.lastDeliveredToken());
    }

    @Test
    void isAbortTriggeredReturnsFalseInAbsenceOfAbort() {
        assertFalse(testSubject.isAbortTriggered());
    }

    @Test
    void isAbortTriggeredReturnsTrueAfterAbortInvocation() {
        testSubject.abort(null);
        assertTrue(testSubject.isAbortTriggered());
    }

    @Test
    void abortReturnsAbortReason() throws ExecutionException, InterruptedException {
        Exception expectedResult = new IllegalStateException();

        CompletableFuture<Exception> result = testSubject.abort(expectedResult);

        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertTrue(result.isDone()));
        assertEquals(expectedResult, result.get());
    }

    @Test
    void abortReturnsOriginalAbortReason() throws ExecutionException, InterruptedException {
        Exception originalAbortReason = new IllegalStateException();
        Exception otherAbortReason = new IllegalArgumentException();
        testSubject.abort(originalAbortReason);

        CompletableFuture<Exception> result = testSubject.abort(otherAbortReason);

        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertTrue(result.isDone()));
        assertEquals(originalAbortReason, result.get());
    }

    @Test
    void scheduleEventsReturnsFalseForEmptyList() {
        assertFalse(testSubject.scheduleEvents(Collections.emptyList()));
    }

    @Test
    void scheduleEventsThrowsIllegalArgumentExceptionForNoneMatchingTokens() {
        TrackingToken testTokenOne = new GlobalSequenceTrackingToken(1L);
        var testEventOne = new SimpleEntry<>(EventTestUtils.asEventMessage("some-event"),
                                             trackingTokenContext(testTokenOne));
        TrackingToken testTokenTwo = new GlobalSequenceTrackingToken(2L);
        var testEventTwo = new SimpleEntry<>(EventTestUtils.asEventMessage("some-event"),
                                             trackingTokenContext(testTokenTwo));
        List<MessageStream.Entry<? extends EventMessage>> testEvents = new ArrayList<>();
        testEvents.add(testEventOne);
        testEvents.add(testEventTwo);

        assertThrows(IllegalArgumentException.class, () -> testSubject.scheduleEvents(testEvents));
    }

    /**
     * The "last delivered token" is configured as the initialToken for a fresh WorkPackage.
     */
    @Test
    void scheduleEventsDoesNotScheduleIfTheLastDeliveredTokensCoversTheEventsToken() {
        TrackingToken testToken = new GlobalSequenceTrackingToken(1L);
        var testEventOne = new SimpleEntry<>(EventTestUtils.asEventMessage("some-event"),
                                             trackingTokenContext(testToken));
        var testEventTwo = new SimpleEntry<>(EventTestUtils.asEventMessage("some-event"),
                                             trackingTokenContext(testToken));
        List<MessageStream.Entry<? extends EventMessage>> testEvents = new ArrayList<>();
        testEvents.add(testEventOne);
        testEvents.add(testEventTwo);

        WorkPackage testSubjectWithCustomInitialToken =
                testSubjectBuilder.initialToken(new GlobalSequenceTrackingToken(2L))
                                  .build();

        boolean result = testSubjectWithCustomInitialToken.scheduleEvents(testEvents);

        assertFalse(result);
        verifyNoInteractions(executorService);
    }

    @Disabled("TODO #3432 - Adjust TokenStore API to be async-native")
    @Test
    void scheduleEventsReturnsTrueIfOnlyOneEventIsAcceptedByTheEventValidator() {
        TrackingToken expectedToken = new GlobalSequenceTrackingToken(1L);
        var filteredEvent = new SimpleEntry<>(EventTestUtils.asEventMessage("some-event"),
                                              trackingTokenContext(expectedToken));
        var expectedEvent = new SimpleEntry<>(EventTestUtils.asEventMessage("some-event"),
                                              trackingTokenContext(expectedToken));
        List<MessageStream.Entry<? extends EventMessage>> testEvents = new ArrayList<>();
        testEvents.add(filteredEvent);
        testEvents.add(expectedEvent);

        eventFilterPredicate = event -> !event.equals(filteredEvent.message());

        boolean result = testSubject.scheduleEvents(testEvents);

        assertTrue(result);

        List<EventMessage> validatedEvents = eventFilter.getValidatedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(2, validatedEvents.size()));
        assertTrue(validatedEvents.containsAll(testEvents.stream().map(MessageStream.Entry::message).toList()));

        var processedEvents = batchProcessor.getProcessedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(1, processedEvents.size()));
        assertEquals(expectedToken, TrackingToken.fromContext(processedEvents.getFirst().context).get());

        ArgumentCaptor<TrackingToken> tokenCaptor = ArgumentCaptor.forClass(TrackingToken.class);
        verify(tokenStore).storeToken(tokenCaptor.capture(), eq(PROCESSOR_NAME), eq(segment.getSegmentId()));
        assertEquals(expectedToken, tokenCaptor.getValue());

        assertEquals(1, trackerStatusUpdates.size());
        OptionalLong resultPosition = trackerStatusUpdates.get(0).getCurrentPosition();
        assertTrue(resultPosition.isPresent());
        assertEquals(1L, resultPosition.getAsLong());
    }

    @Disabled("TODO #3432 - Adjust TokenStore API to be async-native")
    @Test
    void scheduleEventsHandlesAllEventsInOneTransactionWhenAllEventsCanBeHandled() {
        TrackingToken expectedToken = new GlobalSequenceTrackingToken(1L);
        var expectedEventOne = new SimpleEntry<>(EventTestUtils.asEventMessage("some-event"),
                                                 trackingTokenContext(expectedToken));
        var expectedEventTwo = new SimpleEntry<>(EventTestUtils.asEventMessage("some-event"),
                                                 trackingTokenContext(expectedToken));
        List<MessageStream.Entry<? extends EventMessage>> expectedEvents = new ArrayList<>();
        expectedEvents.add(expectedEventOne);
        expectedEvents.add(expectedEventTwo);

        boolean result = testSubject.scheduleEvents(expectedEvents);

        assertTrue(result);

        List<EventMessage> validatedEvents = eventFilter.getValidatedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(2, validatedEvents.size()));
        assertTrue(validatedEvents.containsAll(expectedEvents.stream().map(MessageStream.Entry::message).toList()));

        var processedEvents = batchProcessor.getProcessedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(2, processedEvents.size()));
        assertEquals(expectedToken,
                     TrackingToken.fromContext(processedEvents.get(0).context).get());
        assertEquals(expectedToken,
                     TrackingToken.fromContext(processedEvents.get(1).context).get());

        ArgumentCaptor<TrackingToken> tokenCaptor = ArgumentCaptor.forClass(TrackingToken.class);
        verify(tokenStore).storeToken(tokenCaptor.capture(), eq(PROCESSOR_NAME), eq(segment.getSegmentId()));
        assertEquals(expectedToken, tokenCaptor.getValue());

        assertFalse(trackerStatusUpdates.isEmpty());
        OptionalLong resultPosition = trackerStatusUpdates.get(0).getCurrentPosition();
        assertTrue(resultPosition.isPresent());
        assertEquals(1L, resultPosition.getAsLong());
    }

    private static Context globalTrackingTokenContext(long globalIndex) {
        return trackingTokenContext(new GlobalSequenceTrackingToken(globalIndex));
    }

    private static Context trackingTokenContext(TrackingToken token) {
        return TrackingToken.addToContext(
                Context.empty(), token);
    }

    private class TestEventFilter implements WorkPackage.EventFilter {

        private final List<EventMessage> validatedEvents = new ArrayList<>();

        @Override
        public boolean canHandle(EventMessage eventMessage, ProcessingContext context, Segment segment)
                throws Exception {
            validatedEvents.add(eventMessage);
            return eventFilterPredicate.test(eventMessage);
        }

        public List<EventMessage> getValidatedEvents() {
            return validatedEvents;
        }
    }

    private class TestBatchProcessor implements WorkPackage.BatchProcessor {

        private final List<ContextMessage> processedEvents = new ArrayList<>();

        @Override
        public MessageStream.Empty<Message> process(@Nonnull List<? extends EventMessage> events, ProcessingContext context) {
            if (batchProcessorPredicate.test(events, TrackingToken.fromContext(context).orElse(null))) {
                processedEvents.addAll(events.stream().map(m -> new ContextMessage(m, context)).toList());
            }
            return MessageStream.empty();
        }

        public List<ContextMessage> getProcessedEvents() {
            return processedEvents;
        }
    }

    record ContextMessage(EventMessage message, Context context) {

    }
}
