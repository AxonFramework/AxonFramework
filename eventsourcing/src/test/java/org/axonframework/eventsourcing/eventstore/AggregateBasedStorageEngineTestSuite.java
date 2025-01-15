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

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.AsyncEventStorageEngine.AppendTransaction;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageStream.Entry;
import org.axonframework.messaging.MessageType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import reactor.test.StepVerifier;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import static java.util.Collections.emptySet;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite validating implementations of {@link AsyncEventStorageEngine} implementations that are aggregate-based.
 *
 * @author Allard Buijze
 * @since 5.0.0
 */
public abstract class AggregateBasedStorageEngineTestSuite<ESE extends AsyncEventStorageEngine> {

    private static final String TEST_AGGREGATE_TYPE = "TEST_AGGREGATE";
    protected String TEST_AGGREGATE_ID;
    protected String OTHER_AGGREGATE_ID;
    protected EventCriteria TEST_AGGREGATE_CRITERIA;
    protected EventCriteria OTHER_AGGREGATE_CRITERIA;

    protected ESE testSubject;

    @BeforeEach
    void setUp() throws Exception {
        TEST_AGGREGATE_ID = UUID.randomUUID().toString();
        OTHER_AGGREGATE_ID = UUID.randomUUID().toString();

        TEST_AGGREGATE_CRITERIA = EventCriteria.hasTag(new Tag(TEST_AGGREGATE_TYPE, TEST_AGGREGATE_ID));
        OTHER_AGGREGATE_CRITERIA = EventCriteria.hasTag(new Tag("OTHER_AGGREGATE", OTHER_AGGREGATE_ID));

        testSubject = buildStorageEngine();
    }

    /**
     * Constructs the {@link AsyncEventStorageEngine} used in this test suite.
     *
     * @return The {@link AsyncEventStorageEngine} used in this test suite.
     */
    protected abstract ESE buildStorageEngine() throws Exception;

    @Test
    void streamingFromStartReturnsSelectedMessages() {
        TaggedEventMessage<?> expectedEventOne = taggedEventMessage("event-0", TEST_AGGREGATE_CRITERIA.tags());
        TaggedEventMessage<?> expectedEventTwo = taggedEventMessage("event-1", TEST_AGGREGATE_CRITERIA.tags());
        TaggedEventMessage<?> expectedEventThree = taggedEventMessage("event-4", TEST_AGGREGATE_CRITERIA.tags());
        // Ensure there are "gaps" in the global stream based on events not matching the sourcing condition
        long newMarker = testSubject.appendEvents(AppendCondition.withCriteria(TEST_AGGREGATE_CRITERIA).withMarker(-1),
                                                  expectedEventOne,
                                                  expectedEventTwo).thenCompose(AppendTransaction::commit).join();
        testSubject.appendEvents(AppendCondition.none(),
                                 taggedEventMessage("event-2", emptySet()),
                                 taggedEventMessage("event-3", emptySet())).thenCompose(AppendTransaction::commit)
                   .join();
        testSubject.appendEvents(new DefaultAppendCondition(newMarker, TEST_AGGREGATE_CRITERIA), expectedEventThree)
                   .thenCompose(AppendTransaction::commit).join();
        testSubject.appendEvents(AppendCondition.none(),
                                 taggedEventMessage("event-5", emptySet()),
                                 taggedEventMessage("event-6", emptySet())).thenCompose(AppendTransaction::commit)
                   .join();

        MessageStream<EventMessage<?>> result =
                testSubject.stream(StreamingCondition.startingFrom(new GlobalSequenceTrackingToken(-1)));

        StepVerifier.create(result.asFlux())
                    .assertNext(entry -> assertTrackedEntry(entry, expectedEventOne.event(), 0))
                    .assertNext(entry -> assertTrackedEntry(entry, expectedEventTwo.event(), 1))
                    .expectNextCount(2)
                    .assertNext(entry -> assertTrackedEntry(entry, expectedEventThree.event(), 4))
                    .expectNextCount(2)
                    .thenCancel()
                    .verify();
    }

    @Test
    void streamingFromSpecificPositionSkipsMessages() {
        TaggedEventMessage<?> expectedEventOne = taggedEventMessage("event-0", TEST_AGGREGATE_CRITERIA.tags());
        TaggedEventMessage<?> expectedEventTwo = taggedEventMessage("event-1", TEST_AGGREGATE_CRITERIA.tags());
        TaggedEventMessage<?> expectedEventThree = taggedEventMessage("event-4", TEST_AGGREGATE_CRITERIA.tags());

        // Ensure there are "gaps" in the global stream based on events not matching the sourcing condition
        testSubject.appendEvents(AppendCondition.withCriteria(TEST_AGGREGATE_CRITERIA).withMarker(-1),
                                 expectedEventOne,
                                 expectedEventTwo,
                                 taggedEventMessage("event-2", Set.of()),
                                 taggedEventMessage("event-3", TEST_AGGREGATE_CRITERIA.tags()),
                                 expectedEventThree,
                                 taggedEventMessage("event-5", TEST_AGGREGATE_CRITERIA.tags()),
                                 taggedEventMessage("event-6", TEST_AGGREGATE_CRITERIA.tags())).thenCompose(
                AppendTransaction::commit).join();

        MessageStream<EventMessage<?>> result =
                testSubject.stream(StreamingCondition.startingFrom(new GlobalSequenceTrackingToken(1)));

        StepVerifier.create(result.asFlux())
                    // we've skipped the first two
                    .expectNextCount(2).assertNext(entry -> assertTrackedEntry(entry, expectedEventThree.event(), 4))
                    .expectNextCount(2).thenCancel().verify();
    }

    @Test
    void streamingAfterLastPositionReturnsEmptyStream() {
        EventCriteria expectedCriteria = TEST_AGGREGATE_CRITERIA;
        TaggedEventMessage<?> expectedEventOne = taggedEventMessage("event-0", TEST_AGGREGATE_CRITERIA.tags());
        TaggedEventMessage<?> expectedEventTwo = taggedEventMessage("event-1", TEST_AGGREGATE_CRITERIA.tags());
        TaggedEventMessage<?> expectedEventThree = taggedEventMessage("event-4", TEST_AGGREGATE_CRITERIA.tags());
        // Ensure there are "gaps" in the global stream based on events not matching the sourcing condition
        Long marker1 = testSubject.appendEvents(AppendCondition.withCriteria(expectedCriteria),
                                                expectedEventOne,
                                                expectedEventTwo).thenCompose(AppendTransaction::commit).join();
        testSubject.appendEvents(AppendCondition.none(),
                                 taggedEventMessage("event-2", Set.of()),
                                 taggedEventMessage("event-3", Set.of())).thenCompose(
                AppendTransaction::commit).join();
        testSubject.appendEvents(new DefaultAppendCondition(marker1, expectedCriteria), expectedEventThree)
                   .thenCompose(AppendTransaction::commit).join();
        testSubject.appendEvents(AppendCondition.none(),
                                 taggedEventMessage("event-5", Set.of()),
                                 taggedEventMessage("event-6", Set.of())).thenCompose(
                AppendTransaction::commit).join();

        MessageStream<EventMessage<?>> result = testSubject.stream(StreamingCondition.startingFrom(new GlobalSequenceTrackingToken(
                10)).with(expectedCriteria));

        try {
            assertTrue(result.next().isEmpty());
        } finally {
            result.close();
        }
    }

    @Test
    void sourcingEventsReturnsMatchingAggregateEvents() {
        AppendCondition appendCondition = AppendCondition.withCriteria(TEST_AGGREGATE_CRITERIA);
        AppendCondition appendCondition2 = AppendCondition.withCriteria(OTHER_AGGREGATE_CRITERIA);
        testSubject.appendEvents(appendCondition,
                                 taggedEventMessage("event-0", TEST_AGGREGATE_CRITERIA.tags()),
                                 taggedEventMessage("event-1", TEST_AGGREGATE_CRITERIA.tags()),
                                 taggedEventMessage("event-2", TEST_AGGREGATE_CRITERIA.tags()))
                   .thenCompose(AppendTransaction::commit).join();
        testSubject.appendEvents(appendCondition2,
                                 taggedEventMessage("event-4", OTHER_AGGREGATE_CRITERIA.tags()),
                                 taggedEventMessage("event-5", OTHER_AGGREGATE_CRITERIA.tags()),
                                 taggedEventMessage("event-6", OTHER_AGGREGATE_CRITERIA.tags()))
                   .thenCompose(AppendTransaction::commit).join();

        StepVerifier.create(testSubject.source(SourcingCondition.conditionFor(TEST_AGGREGATE_CRITERIA)).asFlux())
                    .expectNextMatches(entryWithAggregateEvent("event-0", 0))
                    .expectNextMatches(entryWithAggregateEvent("event-1", 1))
                    .expectNextMatches(entryWithAggregateEvent("event-2", 2))
                    .verifyComplete();
    }

    @Test
    void eventsWithoutTagsAreNotSourcedAsAggregatedEvents() {
        testSubject.appendEvents(AppendCondition.withCriteria(TEST_AGGREGATE_CRITERIA),
                                 taggedEventMessage("event-0", Set.of()),
                                 taggedEventMessage("event-1", TEST_AGGREGATE_CRITERIA.tags()),
                                 taggedEventMessage("event-2", Set.of()),
                                 taggedEventMessage("event-3", TEST_AGGREGATE_CRITERIA.tags()),
                                 taggedEventMessage("event-4", Set.of()))
                   .thenCompose(AppendTransaction::commit).join();

        StepVerifier.create(testSubject.source(SourcingCondition.conditionFor(TEST_AGGREGATE_CRITERIA)).asFlux())
                    .expectNextMatches(entryWithAggregateEvent("event-1", 0))
                    .expectNextMatches(entryWithAggregateEvent("event-3", 1))
                    .verifyComplete();
    }

    @Test
    void tagsNotMatchingCriteriaAreRejected() {
        var result = testSubject.appendEvents(AppendCondition.withCriteria(TEST_AGGREGATE_CRITERIA),
                                              taggedEventMessage("event-4", OTHER_AGGREGATE_CRITERIA.tags()),
                                              taggedEventMessage("event-5", OTHER_AGGREGATE_CRITERIA.tags()),
                                              taggedEventMessage("event-6", OTHER_AGGREGATE_CRITERIA.tags()))
                                .thenCompose(AppendTransaction::commit);

        var actualException = assertThrows(ExecutionException.class, () -> result.get(1, TimeUnit.SECONDS));
        assertInstanceOf(IllegalArgumentException.class, actualException.getCause());
    }

    @Test
    void transactionRejectedWithConflictingEventsInStore() {
        testSubject.appendEvents(AppendCondition.withCriteria(TEST_AGGREGATE_CRITERIA),
                                 taggedEventMessage("event-0", TEST_AGGREGATE_CRITERIA.tags()),
                                 taggedEventMessage("event-1", TEST_AGGREGATE_CRITERIA.tags())).thenApply(
                AppendTransaction::commit).join();


        CompletableFuture<Long> actual = testSubject.appendEvents(
                new DefaultAppendCondition(0, TEST_AGGREGATE_CRITERIA),
                taggedEventMessage("event-2", TEST_AGGREGATE_CRITERIA.tags())
        ).thenCompose(AppendTransaction::commit);

        ExecutionException actualException = assertThrows(ExecutionException.class,
                                                          () -> actual.get(1, TimeUnit.SECONDS));
        assertInstanceOf(AppendConditionAssertionException.class, actualException.getCause());
    }

    @Test
    void transactionRejectedWhenConcurrentlyCreatedTransactionIsCommittedFirst() {
        var firstTx = testSubject.appendEvents(AppendCondition.withCriteria(TEST_AGGREGATE_CRITERIA),
                                               taggedEventMessage("event-10", TEST_AGGREGATE_CRITERIA.tags()));
        var secondTx = testSubject.appendEvents(AppendCondition.withCriteria(TEST_AGGREGATE_CRITERIA),
                                                taggedEventMessage("event-11", TEST_AGGREGATE_CRITERIA.tags()));

        CompletableFuture<Long> firstCommit = firstTx.thenCompose(AppendTransaction::commit);
        assertDoesNotThrow(() -> firstCommit.get(1, TimeUnit.SECONDS));

        CompletableFuture<Long> secondCommit = secondTx.thenCompose(AppendTransaction::commit);
        var actual = assertThrows(ExecutionException.class, () -> secondCommit.get(1, TimeUnit.SECONDS));
        assertInstanceOf(AppendConditionAssertionException.class, actual.getCause());
    }

    @Test
    void concurrentTransactionsForNonOverlappingTagsBothCommit()
            throws ExecutionException, InterruptedException, TimeoutException {

        AppendTransaction firstTx =
                testSubject.appendEvents(AppendCondition.withCriteria(TEST_AGGREGATE_CRITERIA),
                                         taggedEventMessage("event-0", TEST_AGGREGATE_CRITERIA.tags()))
                           .get(1, TimeUnit.SECONDS);
        AppendTransaction secondTx =
                testSubject.appendEvents(AppendCondition.withCriteria(OTHER_AGGREGATE_CRITERIA),
                                         taggedEventMessage("event-0", OTHER_AGGREGATE_CRITERIA.tags()))
                           .get(1, TimeUnit.SECONDS);

        CompletableFuture<Long> firstCommit = firstTx.commit();
        CompletableFuture<Long> secondCommit = secondTx.commit();

        assertDoesNotThrow(() -> firstCommit.get(1, TimeUnit.SECONDS));
        assertDoesNotThrow(() -> secondCommit.get(1, TimeUnit.SECONDS));

        assertEquals(0, firstCommit.join());
        assertEquals(0, secondCommit.join());
    }

    @Test
    public void shouldRejectAppendCriteriaWithMoreThanOneIndex() {
        CompletableFuture<Long> actual = testSubject.appendEvents(AppendCondition.withCriteria(
                TEST_AGGREGATE_CRITERIA.combine(OTHER_AGGREGATE_CRITERIA))).thenCompose(AppendTransaction::commit);

        ExecutionException actualException = assertThrows(ExecutionException.class,
                                                          () -> actual.get(1, TimeUnit.SECONDS));
        assertInstanceOf(AppendConditionAssertionException.class, actualException.getCause());
    }

    private void assertTrackedEntry(Entry<EventMessage<?>> actual, EventMessage<?> expected, long expectedPosition) {
        Optional<TrackingToken> actualToken = TrackingToken.fromContext(actual);
        assertTrue(actualToken.isPresent());
        OptionalLong actualPosition = actualToken.get().position();
        assertTrue(actualPosition.isPresent());
        assertEquals(expectedPosition, actualPosition.getAsLong());
        assertEvent(actual.message(), expected);
    }

    private void assertEvent(EventMessage<?> actual, EventMessage<?> expected) {
        assertEquals(expected.getPayload(), convertPayload(actual).getPayload());
        assertEquals(expected.getIdentifier(), actual.getIdentifier());
        assertEquals(expected.getTimestamp().toEpochMilli(), actual.getTimestamp().toEpochMilli());
        assertEquals(expected.getMetaData(), actual.getMetaData());
    }

    protected abstract EventMessage<String> convertPayload(EventMessage<?> original);

    protected static TaggedEventMessage<?> taggedEventMessage(String payload, Set<Tag> tags) {
        return new GenericTaggedEventMessage<>(
                new GenericEventMessage<>(new MessageType("event"), payload),
                tags
        );
    }

    private @NotNull Predicate<Entry<EventMessage<?>>> entryWithAggregateEvent(String expectedPayload,
                                                                               int expectedSequence) {
        return e -> expectedPayload.equals(convertPayload(e.message()).getPayload())
                && TEST_AGGREGATE_ID.equals(e.getResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY))
                && e.getResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY) == expectedSequence
                && TEST_AGGREGATE_TYPE.equals(e.getResource(LegacyResources.AGGREGATE_TYPE_KEY));
    }
}