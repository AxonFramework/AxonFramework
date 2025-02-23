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
import org.axonframework.messaging.MessageType;
import org.axonframework.utils.AssertUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Test suite validating the {@link SimpleEventStore} and {@link DefaultEventStoreTransaction} for different
 * implementations of the {@link AsyncEventStorageEngine}.
 *
 * @author Steven van Beelen
 */
public abstract class StorageEngineTestSuite<ESE extends AsyncEventStorageEngine> {

    protected String TEST_DOMAIN_ID;
    protected String OTHER_DOMAIN_ID;
    protected EventCriteria TEST_CRITERIA;
    protected EventCriteria OTHER_CRITERIA;

    protected ESE testSubject;

    @BeforeEach
    void setUp() throws Exception {
        TEST_DOMAIN_ID = UUID.randomUUID().toString();
        OTHER_DOMAIN_ID = UUID.randomUUID().toString();

        TEST_CRITERIA = EventCriteria.forAnyEventType().withTags(new Tag("TEST", TEST_DOMAIN_ID));
        OTHER_CRITERIA = EventCriteria.forAnyEventType().withTags(new Tag("OTHER", OTHER_DOMAIN_ID));

        testSubject = buildStorageEngine();
    }

    /**
     * Constructs the {@link AsyncEventStorageEngine} used in this test suite.
     *
     * @return The {@link AsyncEventStorageEngine} used in this test suite.
     */
    protected abstract ESE buildStorageEngine() throws Exception;

    @Test
    void streamingFromStartReturnsSelectedMessages() throws Exception {
        TaggedEventMessage<?> expectedEventOne = taggedEventMessage("event-0", TEST_CRITERIA.tags());
        TaggedEventMessage<?> expectedEventTwo = taggedEventMessage("event-1", TEST_CRITERIA.tags());
        TaggedEventMessage<?> expectedEventThree = taggedEventMessage("event-4", TEST_CRITERIA.tags());
        // Ensure there are "gaps" in the global stream based on events not matching the sourcing condition
        testSubject.appendEvents(AppendCondition.none(),
                                 expectedEventOne,
                                 expectedEventTwo,
                                 taggedEventMessage("event-2", OTHER_CRITERIA.tags()),
                                 taggedEventMessage("event-3", OTHER_CRITERIA.tags()),
                                 expectedEventThree,
                                 taggedEventMessage("event-5", OTHER_CRITERIA.tags()),
                                 taggedEventMessage("event-6", OTHER_CRITERIA.tags()))
                   .thenCompose(AppendTransaction::commit).get(5, TimeUnit.SECONDS);

        MessageStream<EventMessage<?>> result = testSubject.tailToken()
                                                           .thenApply(StreamingCondition::startingFrom)
                                                           .thenApply(sc -> sc.or(TEST_CRITERIA))
                                                           .thenApply(testSubject::stream)
                                                           .get(5, TimeUnit.SECONDS);

        StepVerifier.create(result.asFlux())
                    .assertNext(entry -> assertEvent(entry.message(), expectedEventOne.event()))
                    .assertNext(entry -> assertEvent(entry.message(), expectedEventTwo.event()))
                    .assertNext(entry -> assertEvent(entry.message(), expectedEventThree.event()))
                    .thenCancel()
                    .verify();
    }

    @Test
    void streamingFromSpecificPositionReturnsSelectedMessages() throws Exception {
        TaggedEventMessage<?> expectedEventOne = taggedEventMessage("event-0", TEST_CRITERIA.tags());
        TaggedEventMessage<?> expectedEventTwo = taggedEventMessage("event-1", TEST_CRITERIA.tags());
        TaggedEventMessage<?> expectedEventThree = taggedEventMessage("event-4", TEST_CRITERIA.tags());
        // Ensure there are "gaps" in the global stream based on events not matching the sourcing condition
        testSubject.appendEvents(AppendCondition.none(),
                                 expectedEventOne,
                                 expectedEventTwo,
                                 taggedEventMessage("event-2", OTHER_CRITERIA.tags()),
                                 taggedEventMessage("event-3", OTHER_CRITERIA.tags()),
                                 expectedEventThree,
                                 taggedEventMessage("event-5", OTHER_CRITERIA.tags()),
                                 taggedEventMessage("event-6", OTHER_CRITERIA.tags()))
                   .thenCompose(AppendTransaction::commit).get(5, TimeUnit.SECONDS);

        TrackingToken tokenOfFirstMessage = testSubject.tailToken()
                                                       .thenApply(StreamingCondition::startingFrom)
                                                       .thenApply(testSubject::stream)
                                                       .thenApply(MessageStream::first)
                                                       .thenCompose(MessageStream.Single::asCompletableFuture)
                                                       .thenApply(r -> r.getResource(TrackingToken.RESOURCE_KEY)).get(5,
                                                                                                                      TimeUnit.SECONDS);

        StepVerifier.create(testSubject.stream(StreamingCondition.startingFrom(tokenOfFirstMessage).or(TEST_CRITERIA))
                                       .asFlux())
                    // we've skipped the first one
                    .assertNext(entry -> assertEvent(entry.message(), expectedEventTwo.event()))
                    .assertNext(entry -> assertEvent(entry.message(), expectedEventThree.event()))
                    .thenCancel()
                    .verify();
    }

    @Test
    void streamingAfterLastPositionReturnsEmptyStream() throws Exception {
        testSubject.appendEvents(AppendCondition.none(),
                                 taggedEventMessage("event-0", TEST_CRITERIA.tags()),
                                 taggedEventMessage("event-1", TEST_CRITERIA.tags()),
                                 taggedEventMessage("event-2", TEST_CRITERIA.tags()),
                                 taggedEventMessage("event-3", TEST_CRITERIA.tags()),
                                 taggedEventMessage("event-4", TEST_CRITERIA.tags()),
                                 taggedEventMessage("event-5", TEST_CRITERIA.tags()),
                                 taggedEventMessage("event-6", TEST_CRITERIA.tags()))
                   .thenCompose(AppendTransaction::commit).get(5, TimeUnit.SECONDS);

        MessageStream<EventMessage<?>> result = testSubject.stream(StreamingCondition.startingFrom(new GlobalSequenceTrackingToken(
                                                                                             10))
                                                                                     .or(TEST_CRITERIA));

        try {
            assertTrue(result.next().isEmpty());
        } finally {
            result.close();
        }
    }

    @Test
    void sourcingEventsReturnsMatchingAggregateEvents() throws Exception {
        testSubject.appendEvents(AppendCondition.none(),
                                 taggedEventMessage("event-0", TEST_CRITERIA.tags()),
                                 taggedEventMessage("event-1", TEST_CRITERIA.tags()),
                                 taggedEventMessage("event-2", OTHER_CRITERIA.tags()),
                                 taggedEventMessage("event-3", OTHER_CRITERIA.tags()),
                                 taggedEventMessage("event-4", OTHER_CRITERIA.tags()),
                                 taggedEventMessage("event-5", TEST_CRITERIA.tags()))
                   .thenCompose(AppendTransaction::commit).get(5, TimeUnit.SECONDS);

        StepVerifier.create(
                            testSubject.source(SourcingCondition.conditionFor(TEST_CRITERIA)).asFlux())
                    .expectNextCount(3)
                    .verifyComplete();
    }


    @Test
    void sourcingEventsReturnsTheHeadGlobalIndexAsConsistencyMarker() throws Exception {
        testSubject.appendEvents(AppendCondition.none(),
                                 taggedEventMessage("event-0", TEST_CRITERIA.tags()),
                                 taggedEventMessage("event-1", TEST_CRITERIA.tags()),
                                 taggedEventMessage("event-2", OTHER_CRITERIA.tags()),
                                 taggedEventMessage("event-3", OTHER_CRITERIA.tags()),
                                 taggedEventMessage("event-4", OTHER_CRITERIA.tags()),
                                 taggedEventMessage("event-5", OTHER_CRITERIA.tags()))
                   .thenCompose(AppendTransaction::commit).get(5, TimeUnit.SECONDS);

        StepVerifier.create(
                            testSubject.source(SourcingCondition.conditionFor(TEST_CRITERIA)).asFlux())
                    .expectNextMatches(assertHasConsistencyMarker(5))
                    .expectNextMatches(assertHasConsistencyMarker(5))
                    .verifyComplete();
    }

    @Test
    void sourcingEventsReturnsEmptyStreamIfNoEventsInTheStoreForFlux() {
        StepVerifier.create(testSubject.source(SourcingCondition.conditionFor(TEST_CRITERIA)).asFlux())
                    .expectNextCount(0)
                    .verifyComplete();
    }


    @Test
    void sourcingEventsReturnsEmptyStreamThatCompletesIfNoEventsInTheStore() {
        AtomicBoolean completed = new AtomicBoolean(false);
        testSubject.source(SourcingCondition.conditionFor(TEST_CRITERIA)).whenComplete(
                () -> completed.set(true));

        await().untilTrue(completed);
    }

    @Test
    void transactionRejectedWithConflictingEventsInStore() throws Exception {
        testSubject.appendEvents(AppendCondition.none(),
                                 taggedEventMessage("event-0", TEST_CRITERIA.tags()),
                                 taggedEventMessage("event-1", TEST_CRITERIA.tags()))
                   .thenApply(AppendTransaction::commit).get(5, TimeUnit.SECONDS);

        CompletableFuture<ConsistencyMarker> actual = testSubject.appendEvents(AppendCondition.withCriteria(
                                                                                       TEST_CRITERIA),
                                                                               taggedEventMessage("event-2",
                                                                                                  TEST_CRITERIA.tags()))
                                                                 .thenCompose(AppendTransaction::commit);

        ExecutionException actualException = assertThrows(ExecutionException.class,
                                                          () -> actual.get(1, TimeUnit.SECONDS));
        assertInstanceOf(AppendEventsTransactionRejectedException.class, actualException.getCause());
    }

    @Test
    void transactionRejectedWhenConcurrentlyCreatedTransactionIsCommittedFirst() {
        AppendCondition appendCondition = AppendCondition.withCriteria(TEST_CRITERIA);

        var firstTx = testSubject.appendEvents(appendCondition,
                                               taggedEventMessage("event-0", TEST_CRITERIA.tags()));
        var secondTx = testSubject.appendEvents(appendCondition,
                                                taggedEventMessage("event-1", TEST_CRITERIA.tags()));

        CompletableFuture<ConsistencyMarker> firstCommit = firstTx.thenCompose(AppendTransaction::commit);
        assertDoesNotThrow(() -> firstCommit.get(1, TimeUnit.SECONDS));

        CompletableFuture<ConsistencyMarker> secondCommit = secondTx.thenCompose(AppendTransaction::commit);
        var actual = assertThrows(ExecutionException.class, () -> secondCommit.get(1, TimeUnit.SECONDS));
        assertInstanceOf(AppendEventsTransactionRejectedException.class, actual.getCause());
    }

    @Test
    void concurrentTransactionsForNonOverlappingIndicesBothCommit() throws Exception {
        AppendCondition appendCondition1 = new DefaultAppendCondition(ConsistencyMarker.ORIGIN, TEST_CRITERIA);
        AppendCondition appendCondition2 = new DefaultAppendCondition(ConsistencyMarker.ORIGIN, OTHER_CRITERIA);

        AppendTransaction firstTx = testSubject.appendEvents(appendCondition1,
                                                             taggedEventMessage("event-0", TEST_CRITERIA.tags()))
                                               .get(1, TimeUnit.SECONDS);
        AppendTransaction secondTx = testSubject.appendEvents(appendCondition2,
                                                              taggedEventMessage("event-0",
                                                                                 TEST_CRITERIA.tags()))
                                                .get(1, TimeUnit.SECONDS);

        CompletableFuture<ConsistencyMarker> firstCommit = firstTx.commit();
        CompletableFuture<ConsistencyMarker> secondCommit = secondTx.commit();

        assertDoesNotThrow(() -> firstCommit.get(1, TimeUnit.SECONDS));
        assertDoesNotThrow(() -> secondCommit.get(1, TimeUnit.SECONDS));

        ConsistencyMarker actualIndex = firstCommit.get(5, TimeUnit.SECONDS);
        assertNotNull(actualIndex);
        assertEquals(GlobalIndexConsistencyMarker.position(actualIndex) + 1,
                     GlobalIndexConsistencyMarker.position(secondCommit.get(5, TimeUnit.SECONDS)));
    }

    @Test
    void eventsPublishedAreIncludedInOpenStreams() throws Exception {
        testSubject.appendEvents(AppendCondition.none(),
                                 taggedEventMessage("event-0", TEST_CRITERIA.tags()),
                                 taggedEventMessage("event-1", TEST_CRITERIA.tags()))
                   .thenCompose(AppendTransaction::commit).get(5, TimeUnit.SECONDS);

        MessageStream<EventMessage<?>> stream = testSubject.tailToken()
                                                           .thenApply(StreamingCondition::startingFrom).thenApply(
                        testSubject::stream).get(5, TimeUnit.SECONDS);

        AssertUtils.assertWithin(1, TimeUnit.SECONDS, () -> assertTrue(stream.next().isPresent()));
        AssertUtils.assertWithin(1, TimeUnit.SECONDS, () -> assertTrue(stream.next().isPresent()));

        testSubject.appendEvents(AppendCondition.none(),
                                 taggedEventMessage("event-3", TEST_CRITERIA.tags()))
                   .thenCompose(AppendTransaction::commit).get(5, TimeUnit.SECONDS);

        AssertUtils.assertWithin(1,
                                 TimeUnit.SECONDS,
                                 () -> assertTrue(stream.hasNextAvailable()));

        assertEquals("event-3",
                     stream.next().map(e -> (String) e.message().getPayload())
                           .orElse("none"));
    }


    @Test
    void tailTokenReturnsHeadTokenForEmptyStore() throws Exception {
        TrackingToken actualTailToken = testSubject.tailToken().get(5, TimeUnit.SECONDS);
        TrackingToken actualHeadToken = testSubject.headToken().get(5, TimeUnit.SECONDS);

        assertTrue(actualHeadToken.covers(actualTailToken));
        assertTrue(actualTailToken.covers(actualHeadToken));
    }

    @Test
    void tailTokenReturnsFirstAppendedEvent() throws Exception {
        TaggedEventMessage<?> firstEvent = taggedEventMessage("event-0", TEST_CRITERIA.tags());
        testSubject.appendEvents(AppendCondition.none(),
                                 firstEvent,
                                 taggedEventMessage("event-1", TEST_CRITERIA.tags()))
                   .thenCompose(AppendTransaction::commit).get(5, TimeUnit.SECONDS);

        MessageStream<EventMessage<?>> stream = testSubject.tailToken().thenApply(StreamingCondition::startingFrom)
                                                           .thenApply(testSubject::stream).get(5, TimeUnit.SECONDS);

        MessageStream.Entry<EventMessage<?>> actualEntry = stream.first().asCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEvent(actualEntry.message(), firstEvent.event());
    }

    @Test
    void headTokenReturnsTokenBasedOnLastAppendedEvent() throws Exception {
        TaggedEventMessage<?> firstEvent = taggedEventMessage("event-0", TEST_CRITERIA.tags());
        testSubject.appendEvents(AppendCondition.none(),
                                 firstEvent,
                                 taggedEventMessage("event-1", TEST_CRITERIA.tags()))
                   .thenCompose(AppendTransaction::commit).get(5, TimeUnit.SECONDS);

        MessageStream<EventMessage<?>> stream = testSubject.headToken().thenApply(StreamingCondition::startingFrom)
                                                           .thenApply(testSubject::stream).get(5, TimeUnit.SECONDS);

        // we need to give the store a chance to wire things up.
        Thread.sleep(500);
        assertFalse(stream.hasNextAvailable());
    }

    @Test
    void tokenAtRetrievesTokenFromStorageEngineThatStreamsEventsSinceThatMoment() throws Exception {
        Instant now = Instant.now(); // assign now to a variable to not be impacted by time passing during test
        testSubject.appendEvents(AppendCondition.none(),
                                 new GenericTaggedEventMessage<>(new GenericEventMessage<>(UUID.randomUUID().toString(),
                                                                                           new MessageType("event"),
                                                                                           "event-0",
                                                                                           Map.of(),
                                                                                           now.minusSeconds(10)),
                                                                 TEST_CRITERIA.tags()),
                                 new GenericTaggedEventMessage<>(new GenericEventMessage<>(UUID.randomUUID().toString(),
                                                                                           new MessageType("event"),
                                                                                           "event-1",
                                                                                           Map.of(),
                                                                                           now),
                                                                 TEST_CRITERIA.tags()),
                                 new GenericTaggedEventMessage<>(new GenericEventMessage<>(UUID.randomUUID().toString(),
                                                                                           new MessageType("event"),
                                                                                           "event-2",
                                                                                           Map.of(),
                                                                                           now.plusSeconds(10)),
                                                                 TEST_CRITERIA.tags()))

                   .thenCompose(AppendTransaction::commit).get(5, TimeUnit.SECONDS);

        TrackingToken actualToken = testSubject.tokenAt(now.minus(5, ChronoUnit.SECONDS)).get(5, TimeUnit.SECONDS);

        assertNotNull(actualToken);
        StepVerifier.create(testSubject.stream(StreamingCondition.startingFrom(actualToken)).asFlux())
                    .expectNextCount(2).thenCancel().verify();
    }

    @Test
    void tokenAtReturnsHeadTokenWhenThereAreNoEventsAfterTheGivenAt() throws Exception {
        testSubject.appendEvents(AppendCondition.none(),
                                 taggedEventMessage("event-0", TEST_CRITERIA.tags()),
                                 taggedEventMessage("event-1", TEST_CRITERIA.tags()),
                                 taggedEventMessage("event-2", TEST_CRITERIA.tags()))

                   .thenCompose(AppendTransaction::commit).get(5, TimeUnit.SECONDS);

        TrackingToken actualTokenAtToken = testSubject.tokenAt(Instant.now().plus(1, ChronoUnit.DAYS)).get(5,
                                                                                                           TimeUnit.SECONDS);
        TrackingToken actualHeadToken = testSubject.headToken().get(5, TimeUnit.SECONDS);

        assertNotNull(actualTokenAtToken);
        assertEquals(actualHeadToken, actualTokenAtToken);
    }

    private static void assertEvent(EventMessage<?> actual,
                                    EventMessage<?> expected) {
        assertEquals(expected.getPayload(), actual.getPayload());
        assertEquals(expected.getIdentifier(), actual.getIdentifier());
        assertEquals(expected.getTimestamp().toEpochMilli(), actual.getTimestamp().toEpochMilli());
        assertEquals(expected.getMetaData(), actual.getMetaData());
    }

    protected static TaggedEventMessage<?> taggedEventMessage(String payload, Set<Tag> tags) {
        return new GenericTaggedEventMessage<>(
                new GenericEventMessage<>(new MessageType("event"), payload),
                tags
        );
    }


    private static Predicate<MessageStream.Entry<EventMessage<?>>> assertHasConsistencyMarker(int position) {
        return em -> {
            assertEquals(new GlobalIndexConsistencyMarker(position),
                         em.getResource(ConsistencyMarker.RESOURCE_KEY));
            return true;
        };
    }
}