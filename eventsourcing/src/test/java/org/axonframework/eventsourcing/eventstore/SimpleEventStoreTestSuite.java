/*
 * Copyright (c) 2010-2024. Axon Framework
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

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.*;
import org.axonframework.eventsourcing.StubProcessingContext;
import org.axonframework.eventsourcing.eventstore.StreamableEventSource.TrackedEntry;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.AsyncUnitOfWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Test suite validating the {@link SimpleEventStore} and {@link DefaultEventStoreTransaction} for different
 * implementations of the {@link AsyncEventStorageEngine}.
 *
 * @author Steven van Beelen
 */
abstract class SimpleEventStoreTestSuite<ESE extends AsyncEventStorageEngine> {

    protected static final String TEST_CONTEXT = "some-context";
    protected static final String NOT_MATCHING_CONTEXT = "some-other-context";
    protected static final String TEST_AGGREGATE_ID = "someId";
    protected static final EventCriteria TEST_AGGREGATE_CRITERIA =
            EventCriteria.hasIndex(new Index("aggregateIdentifier", TEST_AGGREGATE_ID));

    protected ESE storageEngine;

    @Captor
    protected ArgumentCaptor<List<EventMessage<?>>> eventsCaptor;

    protected SimpleEventStore testSubject;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        storageEngine = spy(buildStorageEngine());

        testSubject = new SimpleEventStore(storageEngine, TEST_CONTEXT);
    }

    /**
     * Constructs the {@link AsyncEventStorageEngine} used in this test suite.
     *
     * @return The {@link AsyncEventStorageEngine} used in this test suite.
     */
    protected abstract ESE buildStorageEngine();

    @Test
    void sourcingReturnsExpectedMessageStream() {
        EventCriteria expectedCriteria = TEST_AGGREGATE_CRITERIA;
        EventMessage<?> expectedEventOne = eventMessage(0);
        EventMessage<?> expectedEventTwo = eventMessage(1);
        EventMessage<?> expectedEventThree = eventMessage(4);
        SourcingCondition testSourcingCondition = SourcingCondition.conditionFor(expectedCriteria);
        // Ensure there are "gaps" in the global stream based on events not matching the sourcing condition
        CompletableFuture.allOf(
                storageEngine.appendEvents(AppendCondition.from(testSourcingCondition),
                                           expectedEventOne, expectedEventTwo),
                storageEngine.appendEvents(AppendCondition.none(),
                                           eventMessage(2), eventMessage(3)),
                storageEngine.appendEvents(AppendCondition.from(testSourcingCondition).withMarker(3),
                                           expectedEventThree),
                storageEngine.appendEvents(AppendCondition.none(),
                                           eventMessage(5), eventMessage(6))
        ).join();

        AsyncUnitOfWork uow = new AsyncUnitOfWork();
        MessageStream<EventMessage<?>> result = awaitCompletion(uow.executeWithResult(context -> {
            EventStoreTransaction transaction = testSubject.transaction(context, TEST_CONTEXT);

            return CompletableFuture.completedFuture(transaction.source(testSourcingCondition, context));
        }));

        StepVerifier.create(result.asFlux())
                    .assertNext(event -> assertTrackedAndTagged(event, expectedEventOne, 0, expectedCriteria))
                    .assertNext(event -> assertTrackedAndTagged(event, expectedEventTwo, 1, expectedCriteria))
                    .assertNext(event -> assertTrackedAndTagged(event, expectedEventThree, 4, expectedCriteria))
                    .verifyComplete();
    }

    @Test
    void sourcingConditionIsMappedToAppendConditionByEventStoreTransaction() {
        EventCriteria expectedCriteria = TEST_AGGREGATE_CRITERIA;
        EventMessage<?> expectedEventOne = eventMessage(0);
        EventMessage<?> expectedEventTwo = eventMessage(1);
        EventMessage<?> expectedEventThree = eventMessage(2);
        SourcingCondition testSourcingCondition = SourcingCondition.conditionFor(expectedCriteria);

        AtomicReference<MessageStream<EventMessage<?>>> initialStreamReference = new AtomicReference<>();
        AtomicReference<MessageStream<EventMessage<?>>> finalStreamReference = new AtomicReference<>();

        AsyncUnitOfWork uow = new AsyncUnitOfWork();
        uow.runOnPreInvocation(context -> {
               EventStoreTransaction transaction = testSubject.transaction(context, TEST_CONTEXT);
               initialStreamReference.set(transaction.source(testSourcingCondition, context));
           })
           .runOnPostInvocation(context -> {
               EventStoreTransaction transaction = testSubject.transaction(context, TEST_CONTEXT);
               transaction.appendEvent(expectedEventOne);
               transaction.appendEvent(expectedEventTwo);
               transaction.appendEvent(expectedEventThree);
           })
           // Event are given to the store in the PREPARE_COMMIT phase.
           // Hence, we retrieve the sourced set after that.
           .runOnAfterCommit(context -> {
               EventStoreTransaction transaction = testSubject.transaction(context, TEST_CONTEXT);
               finalStreamReference.set(transaction.source(testSourcingCondition, context));
           });
        awaitCompletion(uow.execute());

        assertNull(initialStreamReference.get().asCompletableFuture().join());

        StepVerifier.create(finalStreamReference.get().asFlux())
                    .assertNext(event -> assertTrackedAndTagged(event, expectedEventOne, 0, expectedCriteria))
                    .assertNext(event -> assertTrackedAndTagged(event, expectedEventTwo, 1, expectedCriteria))
                    .assertNext(event -> assertTrackedAndTagged(event, expectedEventThree, 2, expectedCriteria))
                    .verifyComplete();
    }

    private static void assertTrackedAndTagged(EventMessage<?> actual,
                                               EventMessage<?> expected,
                                               int expectedPosition,
                                               EventCriteria expectedCriteria) {
        assertInstanceOf(GenericTrackedAndIndexedEventMessage.class, actual);
        GenericTrackedAndIndexedEventMessage<?> trackedAndTagged = (GenericTrackedAndIndexedEventMessage<?>) actual;
        assertTrue(trackedAndTagged.indices().containsAll(expectedCriteria.indices()));
        assertTracked(trackedAndTagged, expected, expectedPosition);
    }

    @Test
    void appendedEventsAreGivenCombinedToTheStorageEngine() {
        EventMessage<?> testEventOne = eventMessage(0);
        EventMessage<?> testEventTwo = eventMessage(1);
        EventMessage<?> testEventThree = eventMessage(2);

        AppendCondition expectedCondition = AppendCondition.none();

        AsyncUnitOfWork uow = new AsyncUnitOfWork();
        uow.runOnPreInvocation(context -> {
            EventStoreTransaction transaction = testSubject.transaction(context, TEST_CONTEXT);

            transaction.appendEvent(testEventOne);
            transaction.appendEvent(testEventTwo);
            transaction.appendEvent(testEventThree);
        });
        awaitCompletion(uow.execute());

        verify(storageEngine).appendEvents(eq(expectedCondition), eventsCaptor.capture());
        List<EventMessage<?>> resultEvents = eventsCaptor.getValue();
        assertTrue(resultEvents.contains(testEventOne));
        assertTrue(resultEvents.contains(testEventTwo));
        assertTrue(resultEvents.contains(testEventThree));
    }

    @Test
    void appendEventsThrowsAppendConditionAssertionExceptionWhenTheConsistencyMarkerIsSurpassedByMatchingEvents() {
        SourcingCondition testCondition = SourcingCondition.conditionFor(TEST_AGGREGATE_CRITERIA);
        AtomicReference<MessageStream<EventMessage<?>>> fastStreamReference = new AtomicReference<>();
        AtomicReference<MessageStream<EventMessage<?>>> slowStreamReference = new AtomicReference<>();

        EventMessage<?> testEvent = eventMessage(0);

        AsyncUnitOfWork fastUow = new AsyncUnitOfWork();
        fastUow.runOnPreInvocation(context -> {
                   EventStoreTransaction transaction = testSubject.transaction(context, TEST_CONTEXT);
                   fastStreamReference.set(transaction.source(testCondition, context));
               })
               .runOnPostInvocation(context -> {
                   EventStoreTransaction transaction = testSubject.transaction(context, TEST_CONTEXT);
                   transaction.appendEvent(testEvent);
               });

        AsyncUnitOfWork slowUow = new AsyncUnitOfWork();
        slowUow.runOnPreInvocation(context -> {
                   EventStoreTransaction transaction = testSubject.transaction(context, TEST_CONTEXT);
                   slowStreamReference.set(transaction.source(testCondition, context));
               })
               // By completing the fast UnitOfWork after sourcing in the slow UnitOfWork, we:
               // 1. Ensure the AppendCondition will have a consistency marker set to zero, as the stream is empty, and
               // 2. Ascertain the consistency marker is surpassed by appending an event in the fast UnitOfWork
               .runOnPreInvocation(context -> awaitCompletion(fastUow.execute()))
               .runOnPostInvocation(context -> {
                   EventStoreTransaction transaction = testSubject.transaction(context, TEST_CONTEXT);
                   transaction.appendEvent(testEvent);
               });

        CompletableFuture<Void> result = slowUow.execute();

        await().atMost(Duration.ofMillis(500))
               .pollDelay(Duration.ofMillis(25))
               .untilAsserted(() -> {
                   assertTrue(result.isCompletedExceptionally());
                   assertInstanceOf(AppendConditionAssertionException.class, result.exceptionNow());
               });

        // The streams should both be entirely empty, as non-existing models are sourced.
        assertNotNull(fastStreamReference.get());
        StepVerifier.create(fastStreamReference.get().asFlux())
                    .verifyComplete();
        assertNotNull(slowStreamReference.get());
        StepVerifier.create(slowStreamReference.get().asFlux())
                    .verifyComplete();
    }

    @Test
    void appendEventInvocationsInvokeAllOnAppendCallbacks() {
        EventMessage<?> testEvent = eventMessage(0);
        AtomicReference<EventMessage<?>> callbackOneReference = new AtomicReference<>();
        AtomicReference<EventMessage<?>> callbackTwoReference = new AtomicReference<>();
        AtomicReference<EventMessage<?>> callbackThreeReference = new AtomicReference<>();

        AsyncUnitOfWork uow = new AsyncUnitOfWork();
        uow.runOnPreInvocation(context -> {
            EventStoreTransaction transaction = testSubject.transaction(context, TEST_CONTEXT);

            transaction.onAppend(callbackOneReference::set);
            transaction.onAppend(callbackTwoReference::set);
            transaction.onAppend(callbackThreeReference::set);
            transaction.appendEvent(testEvent);
        });
        awaitCompletion(uow.execute());

        assertEquals(testEvent, callbackOneReference.get());
        assertEquals(testEvent, callbackTwoReference.get());
        assertEquals(testEvent, callbackThreeReference.get());
    }

    @Test
    void startingTransactionsForAnotherContextThrowIllegalArgumentExceptions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> testSubject.transaction(StubProcessingContext.NONE, NOT_MATCHING_CONTEXT)
        );
    }

    @Test
    void streamForEmptyStoreReturnsEmptyMessageStream() {
        MessageStream<TrackedEntry<EventMessage<?>>> result =
                testSubject.open(TEST_CONTEXT, StreamingCondition.startingFrom(new GlobalSequenceTrackingToken(0)));

        StepVerifier.create(result.asFlux())
                    .verifyComplete();
    }

    @Test
    void streamForReturnsEventsStartingFromStreamingConditionPosition() {
        EventMessage<?> expectedEventOne = eventMessage(4);
        EventMessage<?> expectedEventTwo = eventMessage(5);
        EventMessage<?> expectedEventThree = eventMessage(6);
        StreamingCondition testStreamingCondition = StreamingCondition.startingFrom(new GlobalSequenceTrackingToken(4));

        CompletableFuture.allOf(
                storageEngine.appendEvents(AppendCondition.from(SourcingCondition.conditionFor(TEST_AGGREGATE_CRITERIA)),
                                           eventMessage(0), eventMessage(1)),
                storageEngine.appendEvents(AppendCondition.none(),
                                           eventMessage(2), eventMessage(3)),
                storageEngine.appendEvents(AppendCondition.from(SourcingCondition.conditionFor(TEST_AGGREGATE_CRITERIA))
                                                          .withMarker(3),
                                           expectedEventOne),
                storageEngine.appendEvents(AppendCondition.none(),
                                           expectedEventTwo, expectedEventThree)
        ).join();

        MessageStream<TrackedEntry<EventMessage<?>>> result = testSubject.open(TEST_CONTEXT, testStreamingCondition);

        StepVerifier.create(result.asFlux())
                    .assertNext(trackedEvent -> assertTrackedEvent(trackedEvent, expectedEventOne, 4))
                    .assertNext(trackedEvent -> assertTrackedEvent(trackedEvent, expectedEventTwo, 5))
                    .assertNext(trackedEvent -> assertTrackedEvent(trackedEvent, expectedEventThree, 6))
                    .verifyComplete();
    }

    @Test
    void streamForReturnsEventsMatchingTheStreamingCondition() {
        EventMessage<?> expectedEventOne = eventMessage(0);
        EventMessage<?> expectedEventTwo = eventMessage(1);
        EventMessage<?> expectedEventThree = eventMessage(4);
        SourcingCondition testSourcingCondition = SourcingCondition.conditionFor(TEST_AGGREGATE_CRITERIA);
        StreamingCondition testStreamingCondition = StreamingCondition.startingFrom(new GlobalSequenceTrackingToken(0))
                                                                      .with(testSourcingCondition.criteria());

        CompletableFuture.allOf(
                storageEngine.appendEvents(AppendCondition.from(testSourcingCondition),
                                           expectedEventOne, expectedEventTwo),
                storageEngine.appendEvents(AppendCondition.none(),
                                           eventMessage(2), eventMessage(3)),
                storageEngine.appendEvents(AppendCondition.from(testSourcingCondition)
                                                          .withMarker(3),
                                           expectedEventThree),
                storageEngine.appendEvents(AppendCondition.none(),
                                           eventMessage(5), eventMessage(6))
        ).join();

        MessageStream<TrackedEntry<EventMessage<?>>> result = testSubject.open(TEST_CONTEXT, testStreamingCondition);

        StepVerifier.create(result.asFlux())
                    .assertNext(trackedEvent -> assertTrackedEvent(trackedEvent, expectedEventOne, 0))
                    .assertNext(trackedEvent -> assertTrackedEvent(trackedEvent, expectedEventTwo, 1))
                    .assertNext(trackedEvent -> assertTrackedEvent(trackedEvent, expectedEventThree, 4))
                    .verifyComplete();
    }

    private static void assertTrackedEvent(TrackedEntry<EventMessage<?>> actual,
                                           EventMessage<?> expected,
                                           int expectedPosition) {
        assertEquals(expectedPosition, actual.token().position().orElse(-1));
        assertEvent(actual.event(), expected);
    }

    private static void assertEvent(EventMessage<?> actual,
                                    EventMessage<?> expected) {
        assertEquals(expected.getIdentifier(), actual.getIdentifier());
        assertEquals(expected.getPayload(), actual.getPayload());
        assertEquals(expected.getTimestamp(), actual.getTimestamp());
        assertEquals(expected.getMetaData(), actual.getMetaData());
    }

    @Test
    void openingStreamsForAnotherContextThrowIllegalArgumentExceptions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> testSubject.open(NOT_MATCHING_CONTEXT, StreamingCondition.startingFrom(null))
        );
    }

    @Test
    void tailTokenReturnsTailTokenForEmptyStore() {
        TrackingToken result = awaitCompletion(testSubject.tailToken(TEST_CONTEXT));

        OptionalLong resultPosition = result.position();
        assertTrue(resultPosition.isPresent());
        assertEquals(-1, resultPosition.getAsLong());

        verify(storageEngine).tailToken();
    }

    @Test
    void tailTokenReturnsFirstAppendedEvent() {
        EventMessage<?> testEventOne = eventMessage(0);
        EventMessage<?> testEventTwo = eventMessage(1);
        EventMessage<?> testEventThree = eventMessage(2);

        awaitCompletion(storageEngine.appendEvents(AppendCondition.none(), testEventOne, testEventTwo, testEventThree));

        TrackingToken result = awaitCompletion(testSubject.tailToken(TEST_CONTEXT));

        OptionalLong resultPosition = result.position();
        assertTrue(resultPosition.isPresent());
        assertEquals(-1, resultPosition.getAsLong());

        verify(storageEngine).tailToken();
    }

    @Test
    void creatingTailTokensForAnotherContextThrowIllegalArgumentExceptions() {
        assertThrows(IllegalArgumentException.class, () -> testSubject.tailToken(NOT_MATCHING_CONTEXT));
    }

    @Test
    void headTokenReturnsTokenBasedOnLastAppendedEvent() {
        EventMessage<?> testEventOne = eventMessage(0);
        EventMessage<?> testEventTwo = eventMessage(1);
        EventMessage<?> testEventThree = eventMessage(2);
        int expectedPosition = 2;

        awaitCompletion(storageEngine.appendEvents(AppendCondition.none(), testEventOne, testEventTwo, testEventThree));

        TrackingToken result = awaitCompletion(testSubject.headToken(TEST_CONTEXT));

        OptionalLong resultPosition = result.position();
        assertTrue(resultPosition.isPresent());
        assertEquals(expectedPosition, resultPosition.getAsLong());

        verify(storageEngine).headToken();
    }

    @Test
    void creatingHeadTokensForAnotherContextThrowIllegalArgumentExceptions() {
        assertThrows(IllegalArgumentException.class, () -> testSubject.headToken(NOT_MATCHING_CONTEXT));
    }

    @Test
    void tokenAtReturnsHeadTokenWhenThereAreNoEventsBeforeTheGivenAt() {
        Instant testAt = Instant.now();

        TrackingToken result = awaitCompletion(testSubject.tokenAt(TEST_CONTEXT, testAt));

        OptionalLong resultPosition = result.position();
        assertTrue(resultPosition.isPresent());
        assertEquals(-1, resultPosition.getAsLong());

        verify(storageEngine).tokenAt(testAt);
    }

    @Test
    void tokenAtRetrievesTokenFromStorageEngine() {
        EventMessage<?> testEventOne = eventMessage(0);
        EventMessage<?> testEventTwo = eventMessage(1);
        EventMessage<?> testEventThree = eventMessage(2);
        int expectedPosition = 2;

        awaitCompletion(storageEngine.appendEvents(AppendCondition.none(), testEventOne, testEventTwo, testEventThree));

        // Instant is past event's timestamp
        Instant testAt = testEventThree.getTimestamp().plusMillis(500);

        TrackingToken result = awaitCompletion(testSubject.tokenAt(TEST_CONTEXT, testAt));

        OptionalLong resultPosition = result.position();
        assertTrue(resultPosition.isPresent());
        assertEquals(expectedPosition, resultPosition.getAsLong());

        verify(storageEngine).tokenAt(testAt);
    }

    @Test
    void creatingTokensAtGivenInstantForAnotherContextThrowIllegalArgumentExceptions() {
        assertThrows(IllegalArgumentException.class, () -> testSubject.tokenAt(NOT_MATCHING_CONTEXT, Instant.now()));
    }

    @Test
    void tokenSinceReturnsHeadTokenWhenThereAreNoEventsBeforeTheGivenSince() {
        Duration testSince = Duration.ofSeconds(50);

        TrackingToken result = awaitCompletion(testSubject.tokenSince(TEST_CONTEXT, testSince));

        OptionalLong resultPosition = result.position();
        assertTrue(resultPosition.isPresent());
        assertEquals(-1, resultPosition.getAsLong());

        verify(storageEngine).tokenSince(testSince);
    }

    @Test
    void tokenSinceRetrievesHeadTokenFromStorageEngine() {
        EventMessage<?> testEventOne = eventMessage(0);
        EventMessage<?> testEventTwo = eventMessage(1);
        EventMessage<?> testEventThree = eventMessage(2);
        int expectedPosition = 2;

        awaitCompletion(storageEngine.appendEvents(AppendCondition.none(), testEventOne, testEventTwo, testEventThree));

        Duration testSince = Duration.ofMillis(50);

        // TODO #3083
        // Ideally we would be able to adjust the Instants generated by the Clock of the storage engine.
        // Since we use a Clock instead of something like a Supplier<Instant>, this doesn't work for the time being.
        await().pollDelay(testSince)
               .atMost(Duration.ofMillis(500))
               .untilAsserted(() -> {
                   TrackingToken result = awaitCompletion(testSubject.tokenSince(TEST_CONTEXT, testSince));

                   OptionalLong resultPosition = result.position();
                   assertTrue(resultPosition.isPresent());
                   assertEquals(expectedPosition, resultPosition.getAsLong());
               });

        verify(storageEngine).tokenSince(testSince);
    }

    @Test
    void creatingTokensSinceGivenDurationForAnotherContextThrowIllegalArgumentExceptions() {
        assertThrows(IllegalArgumentException.class, () -> testSubject.tokenSince(NOT_MATCHING_CONTEXT, Duration.ZERO));
    }

    // TODO - Discuss: Perfect candidate to move to a commons test utils module?
    protected static <R> R awaitCompletion(CompletableFuture<R> completion) {
        await().atMost(Duration.ofMillis(500))
               .pollDelay(Duration.ofMillis(25))
               .untilAsserted(() -> assertFalse(completion.isCompletedExceptionally(),
                                                () -> completion.exceptionNow().toString()));
        return completion.join();
    }

    // TODO - Discuss: Perfect candidate to move to a commons test utils module?
    protected static EventMessage<?> eventMessage(int seq) {
        return GenericEventMessage.asEventMessage("Event[" + seq + "]");
    }
}