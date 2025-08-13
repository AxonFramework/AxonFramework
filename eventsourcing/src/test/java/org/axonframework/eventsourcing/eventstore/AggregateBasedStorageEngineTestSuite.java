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

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.TerminalEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine.AppendTransaction;
import org.axonframework.eventstreaming.EventCriteria;
import org.axonframework.eventstreaming.StreamingCondition;
import org.axonframework.eventstreaming.Tag;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageStream.Entry;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.Converter;
import org.axonframework.serialization.json.JacksonConverter;
import org.junit.jupiter.api.*;
import org.opentest4j.TestAbortedException;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.util.Collections.emptySet;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite validating implementations of {@link EventStorageEngine} implementations that are aggregate-based.
 *
 * @author Allard Buijze
 * @since 5.0.0
 */
public abstract class AggregateBasedStorageEngineTestSuite<ESE extends EventStorageEngine> {

    private static final String TEST_AGGREGATE_TYPE = "TEST_AGGREGATE";
    private static ExecutorService executor;

    protected String TEST_AGGREGATE_ID;
    protected String OTHER_AGGREGATE_ID;
    protected Set<Tag> TEST_AGGREGATE_TAGS;
    protected EventCriteria TEST_AGGREGATE_CRITERIA;
    protected Set<Tag> OTHER_AGGREGATE_TAGS;
    protected EventCriteria OTHER_AGGREGATE_CRITERIA;

    protected Converter converter;

    protected ESE testSubject;

    @BeforeEach
    void setUp() throws Exception {
        TEST_AGGREGATE_ID = UUID.randomUUID().toString();
        OTHER_AGGREGATE_ID = UUID.randomUUID().toString();

        TEST_AGGREGATE_TAGS = Set.of(new Tag(TEST_AGGREGATE_TYPE, TEST_AGGREGATE_ID));
        TEST_AGGREGATE_CRITERIA = EventCriteria.havingTags(TEST_AGGREGATE_TYPE, TEST_AGGREGATE_ID);
        OTHER_AGGREGATE_TAGS = Set.of(new Tag("OTHER_AGGREGATE", OTHER_AGGREGATE_ID));
        OTHER_AGGREGATE_CRITERIA = EventCriteria.havingTags("OTHER_AGGREGATE", OTHER_AGGREGATE_ID);

        converter = new JacksonConverter();

        testSubject = buildStorageEngine();
    }

    @BeforeAll
    static void beforeAll() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @AfterAll
    static void afterAll() {
        executor.close();
    }

    /**
     * Constructs the {@link EventStorageEngine} used in this test suite.
     *
     * @return The {@link EventStorageEngine} used in this test suite.
     */
    protected abstract ESE buildStorageEngine() throws Exception;

    /**
     * Will translate position to global sequence index. It differs among the EventStore implementations. For example:
     * AxonServer starts the global stream from 0, whereas JPA implementations starts from 1.
     *
     * @param position the event order to translate, first is 1
     * @return the global sequence index for given event storage engine
     */
    protected abstract long globalSequenceOfEvent(long position);

    protected abstract TrackingToken trackingTokenAt(long position);

    @Test
    void streamingFromStartReturnsSelectedMessages() {
        TaggedEventMessage<?> expectedEventOne = taggedEventMessage("event-0", TEST_AGGREGATE_TAGS);
        TaggedEventMessage<?> expectedEventTwo = taggedEventMessage("event-1", TEST_AGGREGATE_TAGS);
        TaggedEventMessage<?> expectedEventThree = taggedEventMessage("event-4", TEST_AGGREGATE_TAGS);
        // Ensure there are "gaps" in the global stream based on events not matching the condition.
        ConsistencyMarker newMarker = testSubject.appendEvents(AppendCondition.withCriteria(TEST_AGGREGATE_CRITERIA),
                                                               expectedEventOne,
                                                               expectedEventTwo).thenCompose(AppendTransaction::commit)
                                                 .join();
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
                testSubject.stream(StreamingCondition.startingFrom(trackingTokenAt(0)));

        StepVerifier.create(result.asFlux())
                    .assertNext(entry -> assertTrackedEntry(entry, expectedEventOne.event(), 1))
                    .assertNext(entry -> assertTrackedEntry(entry, expectedEventTwo.event(), 2))
                    .expectNextCount(2)
                    .assertNext(entry -> assertTrackedEntry(entry, expectedEventThree.event(), 5))
                    .expectNextCount(2)
                    .thenCancel()
                    .verify();
    }

    @Test
    void streamingFromSpecificPositionSkipsMessages() {
        TaggedEventMessage<?> expectedEventOne = taggedEventMessage("event-0", TEST_AGGREGATE_TAGS);
        TaggedEventMessage<?> expectedEventTwo = taggedEventMessage("event-1", TEST_AGGREGATE_TAGS);
        TaggedEventMessage<?> expectedEventThree = taggedEventMessage("event-4", TEST_AGGREGATE_TAGS);
        // Ensure there are "gaps" in the global stream based on events not matching the condition.
        testSubject.appendEvents(AppendCondition.withCriteria(TEST_AGGREGATE_CRITERIA),
                                 expectedEventOne,
                                 expectedEventTwo,
                                 taggedEventMessage("event-2", Set.of()),
                                 taggedEventMessage("event-3", TEST_AGGREGATE_TAGS),
                                 expectedEventThree,
                                 taggedEventMessage("event-5", TEST_AGGREGATE_TAGS),
                                 taggedEventMessage("event-6", TEST_AGGREGATE_TAGS)).thenCompose(
                AppendTransaction::commit).join();

        MessageStream<EventMessage<?>> result =
                testSubject.stream(StreamingCondition.startingFrom(trackingTokenAt(2)));

        StepVerifier.create(result.asFlux())
                    // we've skipped the first two
                    .expectNextCount(2).assertNext(entry -> assertTrackedEntry(entry, expectedEventThree.event(), 5))
                    .expectNextCount(2).thenCancel().verify();
    }

    @Test
    void streamingAfterLastPositionReturnsEmptyStream() {
        EventCriteria expectedCriteria = TEST_AGGREGATE_CRITERIA;
        TaggedEventMessage<?> expectedEventOne = taggedEventMessage("event-0", TEST_AGGREGATE_TAGS);
        TaggedEventMessage<?> expectedEventTwo = taggedEventMessage("event-1", TEST_AGGREGATE_TAGS);
        TaggedEventMessage<?> expectedEventThree = taggedEventMessage("event-4", TEST_AGGREGATE_TAGS);
        // Ensure there are "gaps" in the global stream based on events not matching the condition.
        ConsistencyMarker marker1 = testSubject.appendEvents(AppendCondition.withCriteria(expectedCriteria),
                                                             expectedEventOne,
                                                             expectedEventTwo).thenCompose(AppendTransaction::commit)
                                               .join();
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

        MessageStream<EventMessage<?>> result = testSubject.stream(StreamingCondition.startingFrom(
                trackingTokenAt(10)).or(expectedCriteria));

        try {
            assertTrue(result.next().isEmpty());
        } finally {
            result.close();
        }
    }

    @Test
    void sourcingEventsReturnsMatchingAggregateEventAndConsistencyMarkerEntry() {
        AppendCondition appendCondition = AppendCondition.withCriteria(TEST_AGGREGATE_CRITERIA);
        testSubject.appendEvents(appendCondition, taggedEventMessage("event-0", TEST_AGGREGATE_TAGS))
                   .thenCompose(AppendTransaction::commit)
                   .join();

        StepVerifier.create(testSubject.source(SourcingCondition.conditionFor(TEST_AGGREGATE_CRITERIA)).asFlux())
                    .expectNextMatches(entryWithAggregateEvent("event-0", 0))
                    .expectNextMatches(AggregateBasedStorageEngineTestSuite::assertMarkerEntry)
                    .verifyComplete();
    }

    @Test
    void sourcingEventsWithMetadata() {
        AppendCondition appendCondition = AppendCondition.withCriteria(TEST_AGGREGATE_CRITERIA);
        testSubject.appendEvents(appendCondition,
                                 taggedEventMessage(
                                         "event-0",
                                         TEST_AGGREGATE_TAGS,
                                         MetaData.with("key1", "value1")
                                                 .and("key2", "true")
                                                 .and("key3", "44")
                                 )
                   )
                   .thenCompose(AppendTransaction::commit)
                   .join();

        StepVerifier.create(testSubject.source(SourcingCondition.conditionFor(TEST_AGGREGATE_CRITERIA)).asFlux())
                    .expectNextMatches(entryWithAggregateEvent("event-0", 0))
                    .expectNextMatches(AggregateBasedStorageEngineTestSuite::assertMarkerEntry)
                    .verifyComplete();
    }

    @Test
    void sourcingEventsReturnsMatchingAggregateEvents() {
        AppendCondition appendCondition = AppendCondition.withCriteria(TEST_AGGREGATE_CRITERIA);
        AppendCondition appendCondition2 = AppendCondition.withCriteria(OTHER_AGGREGATE_CRITERIA);
        testSubject.appendEvents(appendCondition,
                                 taggedEventMessage("event-0", TEST_AGGREGATE_TAGS),
                                 taggedEventMessage("event-1", TEST_AGGREGATE_TAGS),
                                 taggedEventMessage("event-2", TEST_AGGREGATE_TAGS))
                   .thenCompose(AppendTransaction::commit)
                   .join();
        testSubject.appendEvents(appendCondition2,
                                 taggedEventMessage("event-4", OTHER_AGGREGATE_TAGS),
                                 taggedEventMessage("event-5", OTHER_AGGREGATE_TAGS),
                                 taggedEventMessage("event-6", OTHER_AGGREGATE_TAGS))
                   .thenCompose(AppendTransaction::commit)
                   .join();

        StepVerifier.create(testSubject.source(SourcingCondition.conditionFor(TEST_AGGREGATE_CRITERIA)).asFlux())
                    .expectNextMatches(entryWithAggregateEvent("event-0", 0))
                    .expectNextMatches(entryWithAggregateEvent("event-1", 1))
                    .expectNextMatches(entryWithAggregateEvent("event-2", 2))
                    .expectNextMatches(AggregateBasedStorageEngineTestSuite::assertMarkerEntry)
                    .verifyComplete();
    }

    @Test
    void eventsWithoutTagsAreNotSourcedAsAggregatedEvents() {
        testSubject.appendEvents(AppendCondition.withCriteria(TEST_AGGREGATE_CRITERIA),
                                 taggedEventMessage("event-0", Set.of()),
                                 taggedEventMessage("event-1", TEST_AGGREGATE_TAGS),
                                 taggedEventMessage("event-2", Set.of()),
                                 taggedEventMessage("event-3", TEST_AGGREGATE_TAGS),
                                 taggedEventMessage("event-4", Set.of()))
                   .thenCompose(AppendTransaction::commit)
                   .join();

        StepVerifier.create(testSubject.source(SourcingCondition.conditionFor(TEST_AGGREGATE_CRITERIA)).asFlux())
                    .expectNextMatches(entryWithAggregateEvent("event-1", 0))
                    .expectNextMatches(entryWithAggregateEvent("event-3", 1))
                    .expectNextMatches(AggregateBasedStorageEngineTestSuite::assertMarkerEntry)
                    .verifyComplete();
    }

    @Test
    void eventsWithTagsNotMatchingCriteriaAreInsertedAtSequenceZero() {
        testSubject.appendEvents(AppendCondition.withCriteria(OTHER_AGGREGATE_CRITERIA),
                                 taggedEventMessage("event-4", TEST_AGGREGATE_TAGS),
                                 taggedEventMessage("event-5", TEST_AGGREGATE_TAGS),
                                 taggedEventMessage("event-6", TEST_AGGREGATE_TAGS))
                   .thenCompose(AppendTransaction::commit)
                   .join();

        StepVerifier.create(testSubject.source(SourcingCondition.conditionFor(TEST_AGGREGATE_CRITERIA)).asFlux())
                    .expectNextMatches(entryWithAggregateEvent("event-4", 0))
                    .expectNextMatches(entryWithAggregateEvent("event-5", 1))
                    .expectNextMatches(entryWithAggregateEvent("event-6", 2))
                    .expectNextMatches(AggregateBasedStorageEngineTestSuite::assertMarkerEntry)
                    .verifyComplete();
    }

    @Test
    void sourcingFromTwoAggregateStreamsReturnsACombinedStream() {
        var appendMarker = testSubject.appendEvents(AppendCondition.none(),
                                                    taggedEventMessage("event-0", TEST_AGGREGATE_TAGS),
                                                    taggedEventMessage("event-1", OTHER_AGGREGATE_TAGS),
                                                    taggedEventMessage("event-2", TEST_AGGREGATE_TAGS),
                                                    taggedEventMessage("event-3", OTHER_AGGREGATE_TAGS),
                                                    taggedEventMessage("event-4", TEST_AGGREGATE_TAGS),
                                                    taggedEventMessage("event-5", OTHER_AGGREGATE_TAGS))
                                      .thenCompose(AppendTransaction::commit)
                                      .join();

        SourcingCondition testCondition =
                SourcingCondition.conditionFor(TEST_AGGREGATE_CRITERIA.or(OTHER_AGGREGATE_CRITERIA));

        StepVerifier.create(testSubject.source(testCondition).asFlux())
                    .expectNextCount(6)
                    .assertNext(entry -> assertEquals(appendMarker, entry.getResource(ConsistencyMarker.RESOURCE_KEY)))
                    .verifyComplete();
    }

    @Test
    void sourcingBeginsEventStreamFromTheSpecifiedPosition() {
        // given...
        Set<String> expected = Set.of("event-2", "event-4");
        Set<String> actual = new HashSet<>();
        testSubject.appendEvents(AppendCondition.none(),
                                 taggedEventMessage("event-0", TEST_AGGREGATE_TAGS),
                                 taggedEventMessage("event-1", OTHER_AGGREGATE_TAGS),
                                 taggedEventMessage("event-2", TEST_AGGREGATE_TAGS),
                                 taggedEventMessage("event-3", OTHER_AGGREGATE_TAGS),
                                 taggedEventMessage("event-4", TEST_AGGREGATE_TAGS),
                                 taggedEventMessage("event-5", OTHER_AGGREGATE_TAGS))
                   .thenCompose(AppendTransaction::commit)
                   .join();

        // when...
        SourcingCondition testCondition = SourcingCondition.conditionFor(1, TEST_AGGREGATE_CRITERIA);
        MessageStream<EventMessage<?>> result = testSubject.source(testCondition);
        // then...
        StepVerifier.create(result.asFlux())
                    .consumeNextWith(entry -> actual.add(entry.map(this::convertPayload).message().payload()))
                    .consumeNextWith(entry -> actual.add(entry.map(this::convertPayload).message().payload()))
                    .assertNext(AggregateBasedStorageEngineTestSuite::assertMarkerEntry)
                    .verifyComplete();
        assertEquals(expected, actual);
    }

    @Test
    void sourcingFromTwoAggregatesBeginsEventStreamsFromTheSpecifiedPositions() {
        // given...
        Set<String> expected = Set.of("event-2", "event-3", "event-4", "event-5");
        Set<String> actual = new HashSet<>();
        testSubject.appendEvents(AppendCondition.none(),
                                 taggedEventMessage("event-0", TEST_AGGREGATE_TAGS),
                                 taggedEventMessage("event-1", OTHER_AGGREGATE_TAGS),
                                 taggedEventMessage("event-2", TEST_AGGREGATE_TAGS),
                                 taggedEventMessage("event-3", OTHER_AGGREGATE_TAGS),
                                 taggedEventMessage("event-4", TEST_AGGREGATE_TAGS),
                                 taggedEventMessage("event-5", OTHER_AGGREGATE_TAGS))
                   .thenCompose(AppendTransaction::commit).join();

        // when...
        MessageStream<EventMessage<?>> source;
        SourcingCondition testCondition =
                SourcingCondition.conditionFor(1, TEST_AGGREGATE_CRITERIA.or(OTHER_AGGREGATE_CRITERIA));
        try {
            source = testSubject.source(testCondition);
        } catch (IllegalArgumentException e) {
            throw new TestAbortedException("Multi-aggregate streams not supported", e);
        }

        // then...
        StepVerifier.create(source.asFlux())
                    .consumeNextWith(entry -> actual.add(entry.map(this::convertPayload).message().payload()))
                    .consumeNextWith(entry -> actual.add(entry.map(this::convertPayload).message().payload()))
                    .consumeNextWith(entry -> actual.add(entry.map(this::convertPayload).message().payload()))
                    .consumeNextWith(entry -> actual.add(entry.map(this::convertPayload).message().payload()))
                    .assertNext(AggregateBasedStorageEngineTestSuite::assertMarkerEntry)
                    .verifyComplete();
        assertEquals(expected, actual);
    }

    @Test
    void sourcingAnEmptyEventStoreReturnsAnExpectedConsistencyMarker() {
        // given...
        ConsistencyMarker testAggregateMarker = new AggregateBasedConsistencyMarker(TEST_AGGREGATE_ID, 0);
        ConsistencyMarker otherAggregateMarker = new AggregateBasedConsistencyMarker(OTHER_AGGREGATE_ID, 0);
        ConsistencyMarker expectedMarker = testAggregateMarker.upperBound(otherAggregateMarker);
        // when...
        SourcingCondition testCondition =
                SourcingCondition.conditionFor(0, TEST_AGGREGATE_CRITERIA.or(OTHER_AGGREGATE_CRITERIA));
        MessageStream<EventMessage<?>> result = testSubject.source(testCondition);
        // then...
        StepVerifier.create(result.asFlux())
                    .assertNext(entry -> assertEquals(
                            expectedMarker, entry.getResource(ConsistencyMarker.RESOURCE_KEY)
                    ))
                    .verifyComplete();
    }

    @Test
    void transactionRejectedWithConflictingEventsInStore() {
        ConsistencyMarker consistencyMarker =
                testSubject.appendEvents(AppendCondition.withCriteria(TEST_AGGREGATE_CRITERIA),
                                         taggedEventMessage("event-0", TEST_AGGREGATE_TAGS))
                           .thenCompose(AppendTransaction::commit)
                           .join();
        testSubject.appendEvents(AppendCondition.withCriteria(TEST_AGGREGATE_CRITERIA)
                                                .withMarker(consistencyMarker),
                                 taggedEventMessage("event-1", TEST_AGGREGATE_TAGS))
                   .thenCompose(AppendTransaction::commit)
                   .join();

        CompletableFuture<ConsistencyMarker> actual =
                testSubject.appendEvents(AppendCondition.withCriteria(TEST_AGGREGATE_CRITERIA)
                                                        .withMarker(consistencyMarker),
                                         taggedEventMessage("event-2", TEST_AGGREGATE_TAGS))
                           .thenCompose(AppendTransaction::commit);

        ExecutionException actualException = assertThrows(ExecutionException.class,
                                                          () -> actual.get(1, TimeUnit.SECONDS));
        assertInstanceOf(AppendEventsTransactionRejectedException.class, actualException.getCause());
    }

    @Test
    void transactionRejectedWhenConcurrentlyCreatedTransactionIsCommittedFirst() {
        var firstTx = testSubject.appendEvents(AppendCondition.withCriteria(TEST_AGGREGATE_CRITERIA),
                                               taggedEventMessage("event-10", TEST_AGGREGATE_TAGS));
        var secondTx = testSubject.appendEvents(AppendCondition.withCriteria(TEST_AGGREGATE_CRITERIA),
                                                taggedEventMessage("event-11", TEST_AGGREGATE_TAGS));

        CompletableFuture<ConsistencyMarker> firstCommit = firstTx.thenCompose(AppendTransaction::commit);
        assertDoesNotThrow(() -> firstCommit.get(1, TimeUnit.SECONDS));

        CompletableFuture<ConsistencyMarker> secondCommit = secondTx.thenCompose(AppendTransaction::commit);
        var thrown = assertThrows(ExecutionException.class, () -> secondCommit.get(1, TimeUnit.SECONDS));
        assertInstanceOf(AppendEventsTransactionRejectedException.class, thrown.getCause());
    }

    @Test
    void whenConflictingTransactionsRunOnDifferentThreadsConcurrentlyThenOnlyOneOfThemIsCommited() {
        var transactions = List.of(
                runAsync(() -> testSubject.appendEvents(
                        AppendCondition.withCriteria(TEST_AGGREGATE_CRITERIA),
                        taggedEventMessage("event-10", TEST_AGGREGATE_TAGS)
                )),
                runAsync(() -> testSubject.appendEvents(
                        AppendCondition.withCriteria(TEST_AGGREGATE_CRITERIA),
                        taggedEventMessage("event-11", TEST_AGGREGATE_TAGS)
                )),
                runAsync(() -> testSubject.appendEvents(
                        AppendCondition.withCriteria(TEST_AGGREGATE_CRITERIA),
                        taggedEventMessage("event-12", TEST_AGGREGATE_TAGS)
                )),
                runAsync(() -> testSubject.appendEvents(
                        AppendCondition.withCriteria(TEST_AGGREGATE_CRITERIA),
                        taggedEventMessage("event-13", TEST_AGGREGATE_TAGS)
                ))
        );

        var commits = transactions.stream()
                                  .map(tx -> tx.thenCompose(AppendTransaction::commit))
                                  .toList();

        var result = CompletableFuture.allOf(commits.toArray(new CompletableFuture[4]));
        var thrown = assertThrows(Exception.class, result::join);
        assertInstanceOf(AppendEventsTransactionRejectedException.class, thrown.getCause());

        var commitedTransaction = commits.stream().filter(tx -> !tx.isCompletedExceptionally()).count();
        assertEquals(1, commitedTransaction);
        var rejectedTransactions = commits.stream().filter(CompletableFuture::isCompletedExceptionally).count();
        assertEquals(transactions.size() - 1, rejectedTransactions);
    }

    private static <T> CompletableFuture<T> runAsync(Supplier<CompletableFuture<T>> task) {
        return CompletableFuture.supplyAsync(task, executor).thenCompose(future -> future);
    }

    @Test
    void concurrentTransactionsForNonOverlappingTagsBothCommit()
            throws ExecutionException, InterruptedException, TimeoutException {

        AppendTransaction firstTx =
                testSubject.appendEvents(AppendCondition.withCriteria(TEST_AGGREGATE_CRITERIA),
                                         taggedEventMessage("event-0", TEST_AGGREGATE_TAGS))
                           .get(1, TimeUnit.SECONDS);
        AppendTransaction secondTx =
                testSubject.appendEvents(AppendCondition.withCriteria(OTHER_AGGREGATE_CRITERIA),
                                         taggedEventMessage("event-0", OTHER_AGGREGATE_TAGS))
                           .get(1, TimeUnit.SECONDS);

        CompletableFuture<ConsistencyMarker> firstCommit = firstTx.commit();
        CompletableFuture<ConsistencyMarker> secondCommit = secondTx.commit();

        assertDoesNotThrow(() -> firstCommit.get(1, TimeUnit.SECONDS));
        assertDoesNotThrow(() -> secondCommit.get(1, TimeUnit.SECONDS));

        assertTrue(validConsistencyMarker(firstCommit.join(), TEST_AGGREGATE_ID, 0));
        assertTrue(validConsistencyMarker(secondCommit.join(), OTHER_AGGREGATE_ID, 0));
    }

    @Test
    void transactionCanBeCommitedOnlyOnce() {
        var tx =
                testSubject.appendEvents(AppendCondition.withCriteria(TEST_AGGREGATE_CRITERIA),
                                         taggedEventMessage("event-0", TEST_AGGREGATE_TAGS)).join();

        assertDoesNotThrow(() -> tx.commit().get(1, TimeUnit.SECONDS));
        assertThrows(Exception.class, () -> tx.commit().get(1, TimeUnit.SECONDS));
    }

    @Test
    void emptyTransactionAlwaysCommitSuccessfullyAndReturnsOriginConsistencyMarker() {
        var appendCondition = AppendCondition.withCriteria(TEST_AGGREGATE_CRITERIA);
        var commit = testSubject.appendEvents(appendCondition, Collections.emptyList())
                                .thenCompose(AppendTransaction::commit);

        var afterCommitConsistencyMarker = assertDoesNotThrow(commit::join);
        assertEquals(ConsistencyMarker.ORIGIN, afterCommitConsistencyMarker);
    }

    @Test
    void eventWithMultipleTagsIsReportedAsPartOfException() {
        TaggedEventMessage<?> violatingEntry = taggedEventMessage("event2",
                                                                  Set.of(new Tag("key1", "value1"),
                                                                         new Tag("key2", "value2")));
        CompletableFuture<EventStorageEngine.AppendTransaction> actual = testSubject.appendEvents(
                AppendCondition.none(),
                taggedEventMessage("event1", Set.of(new Tag("key1", "value1"))),
                violatingEntry,
                taggedEventMessage("event3", Set.of(new Tag("key1", "value1")))
        );

        assertTrue(actual.isDone());
        assertTrue(actual.isCompletedExceptionally());

        ExecutionException actualException = assertThrows(ExecutionException.class, actual::get);
        if (actualException.getCause() instanceof TooManyTagsOnEventMessageException e) {
            assertEquals(violatingEntry.tags(), e.tags());
            assertEquals(violatingEntry.event(), e.eventMessage());
        } else {
            fail("Unexpected exception", actualException);
        }
    }

    private void assertTrackedEntry(Entry<EventMessage<?>> actual, EventMessage<?> expected, long eventNumber) {
        Optional<TrackingToken> actualToken = TrackingToken.fromContext(actual);
        assertTrue(actualToken.isPresent());
        OptionalLong actualPosition = actualToken.get().position();
        assertTrue(actualPosition.isPresent());
        assertEquals(globalSequenceOfEvent(eventNumber), actualPosition.getAsLong());
        assertEvent(actual.message(), expected);
    }

    private void assertEvent(EventMessage<?> actual, EventMessage<?> expected) {
        assertEquals(expected.payload(), convertPayload(actual).payload());
        assertEquals(expected.identifier(), actual.identifier());
        assertEquals(expected.timestamp().toEpochMilli(), actual.timestamp().toEpochMilli());
        assertEquals(expected.metaData(), actual.metaData());
    }

    protected abstract EventMessage<String> convertPayload(EventMessage<?> original);

    private static boolean assertMarkerEntry(Entry<EventMessage<?>> entry) {
        return entry.getResource(ConsistencyMarker.RESOURCE_KEY) instanceof AggregateBasedConsistencyMarker
                && entry.message().equals(TerminalEventMessage.INSTANCE);
    }

    protected static TaggedEventMessage<?> taggedEventMessage(String payload, Set<Tag> tags) {
        return taggedEventMessage(payload, tags, MetaData.emptyInstance());
    }

    protected static TaggedEventMessage<?> taggedEventMessage(String payload, Set<Tag> tags, MetaData metaData) {
        return new GenericTaggedEventMessage<>(
                new GenericEventMessage<>(new MessageType("event"), payload, metaData),
                tags
        );
    }

    private @Nonnull Predicate<Entry<EventMessage<?>>> entryWithAggregateEvent(String expectedPayload,
                                                                               int expectedSequence) {
        return e -> expectedPayload.equals(convertPayload(e.message()).payload())
                && TEST_AGGREGATE_ID.equals(e.getResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY))
                && e.getResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY) == expectedSequence
                && TEST_AGGREGATE_TYPE.equals(e.getResource(LegacyResources.AGGREGATE_TYPE_KEY));
    }

    protected boolean validConsistencyMarker(ConsistencyMarker consistencyMarker,
                                             String aggregateIdentifier,
                                             int aggregateSequence) {
        return consistencyMarker instanceof AggregateBasedConsistencyMarker cm
                && cm.positionOf(aggregateIdentifier) == aggregateSequence;
    }

    public record ComplexObject(String value1, boolean value2, int value3) {

    }
}