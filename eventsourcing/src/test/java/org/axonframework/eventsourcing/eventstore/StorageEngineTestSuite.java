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
import org.axonframework.eventhandling.NoEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine.AppendTransaction;
import org.axonframework.eventstreaming.EventCriteria;
import org.axonframework.eventstreaming.StreamingCondition;
import org.axonframework.eventstreaming.Tag;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageStream.Entry;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.axonframework.utils.AssertUtils;
import org.junit.jupiter.api.*;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite validating the {@link SimpleEventStore} and {@link DefaultEventStoreTransaction} for different
 * implementations of the {@link EventStorageEngine}.
 *
 * @author Steven van Beelen
 */
public abstract class StorageEngineTestSuite<ESE extends EventStorageEngine> {

    protected String TEST_DOMAIN_ID;
    protected String OTHER_DOMAIN_ID;
    protected Set<Tag> TEST_CRITERIA_TAGS;
    protected EventCriteria TEST_CRITERIA;
    protected Set<Tag> OTHER_CRITERIA_TAGS;
    protected EventCriteria OTHER_CRITERIA;

    protected ESE testSubject;

    @BeforeEach
    void setUp() throws Exception {
        TEST_DOMAIN_ID = UUID.randomUUID().toString();
        OTHER_DOMAIN_ID = UUID.randomUUID().toString();

        TEST_CRITERIA_TAGS = Set.of(new Tag("TEST", TEST_DOMAIN_ID));
        TEST_CRITERIA = EventCriteria.havingTags(new Tag("TEST", TEST_DOMAIN_ID));
        OTHER_CRITERIA_TAGS = Set.of(new Tag("OTHER", OTHER_DOMAIN_ID));
        OTHER_CRITERIA = EventCriteria.havingTags(new Tag("OTHER", OTHER_DOMAIN_ID));

        testSubject = buildStorageEngine();
    }

    /**
     * Constructs the {@link EventStorageEngine} used in this test suite.
     *
     * @return The {@link EventStorageEngine} used in this test suite.
     */
    protected abstract ESE buildStorageEngine() throws Exception;

    @Test
    void sourcingEventsReturnsMatchingAggregateEvents() throws Exception {
        int expectedNumberOfEvents = 3;
        int expectedCount = expectedNumberOfEvents + 1; // events and 1 consistency marker message

        testSubject.appendEvents(AppendCondition.none(),
                                 taggedEventMessage("event-0", TEST_CRITERIA_TAGS),
                                 taggedEventMessage("event-1", TEST_CRITERIA_TAGS),
                                 taggedEventMessage("event-2", OTHER_CRITERIA_TAGS),
                                 taggedEventMessage("event-3", OTHER_CRITERIA_TAGS),
                                 taggedEventMessage("event-4", OTHER_CRITERIA_TAGS),
                                 taggedEventMessage("event-5", TEST_CRITERIA_TAGS))
                   .thenCompose(AppendTransaction::commit)
                   .get(5, TimeUnit.SECONDS);

        SourcingCondition testCondition = SourcingCondition.conditionFor(TEST_CRITERIA);

        StepVerifier.create(testSubject.source(testCondition).asFlux())
                    .expectNextCount(expectedCount)
                    .verifyComplete();
    }

    @Test
    void sourcingEventsReturnsConsistencyMarkerWithNoEventMessageAsFinalEntryInTheMessageStream() throws Exception {
        testSubject.appendEvents(AppendCondition.none(),
                                 taggedEventMessage("event-0", TEST_CRITERIA_TAGS),
                                 taggedEventMessage("event-1", TEST_CRITERIA_TAGS),
                                 taggedEventMessage("event-2", OTHER_CRITERIA_TAGS),
                                 taggedEventMessage("event-3", OTHER_CRITERIA_TAGS),
                                 taggedEventMessage("event-4", OTHER_CRITERIA_TAGS),
                                 taggedEventMessage("event-5", OTHER_CRITERIA_TAGS))
                   .thenCompose(AppendTransaction::commit)
                   .get(5, TimeUnit.SECONDS);

        StepVerifier.create(testSubject.source(SourcingCondition.conditionFor(TEST_CRITERIA)).asFlux())
                    .assertNext(entry -> assertNull(entry.getResource(ConsistencyMarker.RESOURCE_KEY)))
                    .assertNext(entry -> assertNull(entry.getResource(ConsistencyMarker.RESOURCE_KEY)))
                    .assertNext(StorageEngineTestSuite::assertMarkerEntry)
                    .verifyComplete();

        StepVerifier.create(testSubject.source(SourcingCondition.conditionFor(OTHER_CRITERIA)).asFlux())
                    .assertNext(entry -> assertNull(entry.getResource(ConsistencyMarker.RESOURCE_KEY)))
                    .assertNext(entry -> assertNull(entry.getResource(ConsistencyMarker.RESOURCE_KEY)))
                    .assertNext(entry -> assertNull(entry.getResource(ConsistencyMarker.RESOURCE_KEY)))
                    .assertNext(entry -> assertNull(entry.getResource(ConsistencyMarker.RESOURCE_KEY)))
                    .assertNext(StorageEngineTestSuite::assertMarkerEntry)
                    .verifyComplete();
    }

    @Test
    void usingConsistencyMarkerFromSourcingEventToAppendAfterAppendWithConditionIsNotAllowed() throws Exception {
        // given ...
        testSubject.appendEvents(AppendCondition.none(),
                                 taggedEventMessage("event-0", TEST_CRITERIA_TAGS),
                                 taggedEventMessage("event-1", TEST_CRITERIA_TAGS),
                                 taggedEventMessage("event-2", OTHER_CRITERIA_TAGS),
                                 taggedEventMessage("event-3", OTHER_CRITERIA_TAGS),
                                 taggedEventMessage("event-4", OTHER_CRITERIA_TAGS),
                                 taggedEventMessage("event-5", OTHER_CRITERIA_TAGS))
                   .thenCompose(AppendTransaction::commit)
                   .get(5, TimeUnit.SECONDS);

        ConsistencyMarker marker = testSubject.source(SourcingCondition.conditionFor(OTHER_CRITERIA))
                                              .asFlux()
                                              .collectList()
                                              .map(List::getLast)
                                              .map(entry -> entry.getResource(ConsistencyMarker.RESOURCE_KEY))
                                              .block();
        testSubject.appendEvents(AppendCondition.none(), taggedEventMessage("event-6", OTHER_CRITERIA_TAGS))
                   .thenCompose(AppendTransaction::commit)
                   .get(5, TimeUnit.SECONDS);
        // when ...
        AppendCondition testAppendCondition = AppendCondition.withCriteria(OTHER_CRITERIA)
                                                             .withMarker(marker);
        CompletableFuture<ConsistencyMarker> result =
                testSubject.appendEvents(testAppendCondition, taggedEventMessage("event-7", OTHER_CRITERIA_TAGS))
                           .thenCompose(AppendTransaction::commit);
        // then ...
        await("Await commit").pollDelay(Duration.ofMillis(50))
                             .atMost(Duration.ofSeconds(5))
                             .untilAsserted(result::isDone);
        assertTrue(result.isCompletedExceptionally());
        assertInstanceOf(AppendEventsTransactionRejectedException.class, result.exceptionNow());
    }

    @Test
    void sourcingEventsReturnsConsistencyMarkerAsSoleMessageWhenNoEventsInTheStoreForFlux() {
        SourcingCondition testCondition = SourcingCondition.conditionFor(TEST_CRITERIA);

        StepVerifier.create(testSubject.source(testCondition).asFlux())
                    .assertNext(StorageEngineTestSuite::assertMarkerEntry)
                    .verifyComplete();
    }

    @Test
    void sourcingEventsReturnsConsistencyMarkerAsSoleMessageAndCompletesWhenNoEventsInTheStore() {
        AtomicBoolean completed = new AtomicBoolean(false);
        SourcingCondition testCondition = SourcingCondition.conditionFor(TEST_CRITERIA);

        MessageStream<EventMessage<?>> sourcingStream = testSubject.source(testCondition)
                                                                   .whenComplete(() -> completed.set(true));
        await("Await first entry availability")
                .pollDelay(Duration.ofMillis(50))
                .atMost(Duration.ofMillis(500))
                .until(sourcingStream::hasNextAvailable);
        Optional<Entry<EventMessage<?>>> entry = sourcingStream.next();
        assertTrue(entry.isPresent());
        assertMarkerEntry(entry.get());
        await("Await end of stream")
                .pollDelay(Duration.ofMillis(50))
                .atMost(Duration.ofMillis(500))
                .until(() -> !sourcingStream.hasNextAvailable());

        await("Awaiting until sourcing completes")
                .pollDelay(Duration.ofMillis(50))
                .atMost(Duration.ofMillis(500))
                .untilTrue(completed);
    }

    private static void assertMarkerEntry(Entry<EventMessage<?>> entry) {
        assertNotNull(entry.getResource(ConsistencyMarker.RESOURCE_KEY));
        assertEquals(NoEventMessage.INSTANCE, entry.message());
    }

    @Test
    void transactionRejectedWithConflictingEventsInStore() throws Exception {
        testSubject.appendEvents(AppendCondition.none(),
                                 taggedEventMessage("event-0", TEST_CRITERIA_TAGS),
                                 taggedEventMessage("event-1", TEST_CRITERIA_TAGS))
                   .thenApply(AppendTransaction::commit)
                   .get(5, TimeUnit.SECONDS);

        AppendCondition testCondition = AppendCondition.withCriteria(TEST_CRITERIA);

        CompletableFuture<ConsistencyMarker> actual =
                testSubject.appendEvents(testCondition, taggedEventMessage("event-2", TEST_CRITERIA_TAGS))
                           .thenCompose(AppendTransaction::commit);

        ExecutionException actualException =
                assertThrows(ExecutionException.class, () -> actual.get(1, TimeUnit.SECONDS));
        assertInstanceOf(AppendEventsTransactionRejectedException.class, actualException.getCause());
    }

    @Test
    void transactionRejectedWhenConcurrentlyCreatedTransactionIsCommittedFirst() {
        AppendCondition appendCondition = AppendCondition.withCriteria(TEST_CRITERIA);

        var firstTx = testSubject.appendEvents(appendCondition, taggedEventMessage("event-0", TEST_CRITERIA_TAGS));
        var secondTx = testSubject.appendEvents(appendCondition, taggedEventMessage("event-1", TEST_CRITERIA_TAGS));

        CompletableFuture<ConsistencyMarker> firstCommit = firstTx.thenCompose(AppendTransaction::commit);
        assertDoesNotThrow(() -> firstCommit.get(1, TimeUnit.SECONDS));

        CompletableFuture<ConsistencyMarker> secondCommit = secondTx.thenCompose(AppendTransaction::commit);
        var actual = assertThrows(ExecutionException.class, () -> secondCommit.get(1, TimeUnit.SECONDS));
        assertInstanceOf(AppendEventsTransactionRejectedException.class, actual.getCause());
    }

    @Test
    void concurrentTransactionsForNonOverlappingTagsBothCommitWithExpectedConsistencyMarkerResponse() throws Exception {
        // given...
        Set<ConsistencyMarker> expected =
                Set.of(new GlobalIndexConsistencyMarker(1), new GlobalIndexConsistencyMarker(2));
        AppendCondition firstCondition = new DefaultAppendCondition(ConsistencyMarker.ORIGIN, TEST_CRITERIA);
        AppendCondition secondCondition = new DefaultAppendCondition(ConsistencyMarker.ORIGIN, OTHER_CRITERIA);

        AppendTransaction firstTx =
                testSubject.appendEvents(firstCondition, taggedEventMessage("event-0", TEST_CRITERIA_TAGS))
                           .get(1, TimeUnit.SECONDS);
        AppendTransaction secondTx =
                testSubject.appendEvents(secondCondition, taggedEventMessage("event-0", OTHER_CRITERIA_TAGS))
                           .get(1, TimeUnit.SECONDS);
        // when...
        CompletableFuture<ConsistencyMarker> firstCommit = firstTx.commit();
        CompletableFuture<ConsistencyMarker> secondCommit = secondTx.commit();
        // then...expecting an unordered set of markers, as the commit order is not consistent for an async system.
        assertDoesNotThrow(() -> firstCommit.get(5, TimeUnit.SECONDS));
        assertDoesNotThrow(() -> secondCommit.get(5, TimeUnit.SECONDS));
        ConsistencyMarker firstMarker = firstCommit.get(50, TimeUnit.MILLISECONDS);
        ConsistencyMarker secondMarker = secondCommit.get(50, TimeUnit.MILLISECONDS);
        assertNotNull(firstMarker);
        assertNotNull(secondMarker);
        Set<ConsistencyMarker> result = Set.of(firstMarker, secondMarker);
        assertEquals(expected, result);
    }

    @Test
    void concurrentTransactionsForOverlappingTagsThrowAnAppendEventsTransactionRejectedException() throws Exception {
        // given...
        int exceptionCounter = 0;
        AppendCondition firstCondition = new DefaultAppendCondition(ConsistencyMarker.ORIGIN, TEST_CRITERIA);
        AppendCondition secondCondition = new DefaultAppendCondition(ConsistencyMarker.ORIGIN, TEST_CRITERIA);
        AppendTransaction firstTx =
                testSubject.appendEvents(firstCondition, taggedEventMessage("event-0", TEST_CRITERIA_TAGS))
                           .get(1, TimeUnit.SECONDS);
        AppendTransaction secondTx =
                testSubject.appendEvents(secondCondition, taggedEventMessage("event-1", TEST_CRITERIA_TAGS))
                           .get(1, TimeUnit.SECONDS);
        // when...
        CompletableFuture<ConsistencyMarker> firstCommit = firstTx.commit();
        CompletableFuture<ConsistencyMarker> secondCommit = secondTx.commit();
        // then... try-catch for both. One should fail, but which differs, because it's an async process.
        try {
            firstCommit.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            exceptionCounter++;
            assertInstanceOf(AppendEventsTransactionRejectedException.class, e.getCause(),
                             () -> "Exception [" + e.getClass() + "] is not expected. Message:" + e.getMessage());
        }
        try {
            secondCommit.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            exceptionCounter++;
            assertInstanceOf(AppendEventsTransactionRejectedException.class, e.getCause(),
                             () -> "Exception [" + e.getClass() + "] is not expected. Message:" + e.getMessage());
        }
        assertEquals(1, exceptionCounter);
    }

    @Test
    void streamingFromStartReturnsSelectedMessages() throws Exception {
        TaggedEventMessage<EventMessage<String>> expectedEventOne = taggedEventMessage("event-0", TEST_CRITERIA_TAGS);
        TaggedEventMessage<EventMessage<String>> expectedEventTwo = taggedEventMessage("event-1", TEST_CRITERIA_TAGS);
        TaggedEventMessage<EventMessage<String>> expectedEventThree = taggedEventMessage("event-4", TEST_CRITERIA_TAGS);
        // Ensure there are "gaps" in the global stream based on events not matching the sourcing condition
        testSubject.appendEvents(AppendCondition.none(),
                                 expectedEventOne,
                                 expectedEventTwo,
                                 taggedEventMessage("event-2", OTHER_CRITERIA_TAGS),
                                 taggedEventMessage("event-3", OTHER_CRITERIA_TAGS),
                                 expectedEventThree,
                                 taggedEventMessage("event-5", OTHER_CRITERIA_TAGS),
                                 taggedEventMessage("event-6", OTHER_CRITERIA_TAGS))
                   .thenCompose(AppendTransaction::commit)
                   .get(5, TimeUnit.SECONDS);

        MessageStream<EventMessage<?>> result =
                testSubject.tailToken()
                           .thenApply(position -> StreamingCondition.conditionFor(position, TEST_CRITERIA))
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
        TaggedEventMessage<EventMessage<String>> expectedEventOne = taggedEventMessage("event-1", TEST_CRITERIA_TAGS);
        TaggedEventMessage<EventMessage<String>> expectedEventTwo = taggedEventMessage("event-4", TEST_CRITERIA_TAGS);
        // Ensure there are "gaps" in the global stream based on events not matching the sourcing condition
        testSubject.appendEvents(AppendCondition.none(),
                                 taggedEventMessage("event-0", TEST_CRITERIA_TAGS),
                                 expectedEventOne,
                                 taggedEventMessage("event-2", OTHER_CRITERIA_TAGS),
                                 taggedEventMessage("event-3", OTHER_CRITERIA_TAGS),
                                 expectedEventTwo,
                                 taggedEventMessage("event-5", OTHER_CRITERIA_TAGS),
                                 taggedEventMessage("event-6", OTHER_CRITERIA_TAGS))
                   .thenCompose(AppendTransaction::commit)
                   .get(5, TimeUnit.SECONDS);

        TrackingToken tokenOfFirstMessage = testSubject.tailToken()
                                                       .thenApply(StreamingCondition::startingFrom)
                                                       .thenApply(testSubject::stream)
                                                       .thenApply(MessageStream::first)
                                                       .thenCompose(MessageStream.Single::asCompletableFuture)
                                                       .thenApply(r -> r.getResource(TrackingToken.RESOURCE_KEY))
                                                       .get(5, TimeUnit.SECONDS);

        StreamingCondition testCondition = StreamingCondition.conditionFor(tokenOfFirstMessage, TEST_CRITERIA);

        StepVerifier.create(testSubject.stream(testCondition).asFlux())
                    // we've skipped the first one by changing the starting point
                    .assertNext(entry -> assertEvent(entry.message(), expectedEventOne.event()))
                    .assertNext(entry -> assertEvent(entry.message(), expectedEventTwo.event()))
                    .thenCancel()
                    .verify();
    }

    @Test
    void streamingAfterLastPositionReturnsEmptyStream() throws Exception {
        testSubject.appendEvents(AppendCondition.none(),
                                 taggedEventMessage("event-0", TEST_CRITERIA_TAGS),
                                 taggedEventMessage("event-1", TEST_CRITERIA_TAGS),
                                 taggedEventMessage("event-2", TEST_CRITERIA_TAGS),
                                 taggedEventMessage("event-3", TEST_CRITERIA_TAGS),
                                 taggedEventMessage("event-4", TEST_CRITERIA_TAGS),
                                 taggedEventMessage("event-5", TEST_CRITERIA_TAGS),
                                 taggedEventMessage("event-6", TEST_CRITERIA_TAGS))
                   .thenCompose(AppendTransaction::commit)
                   .get(5, TimeUnit.SECONDS);

        StreamingCondition testCondition =
                StreamingCondition.conditionFor(new GlobalSequenceTrackingToken(10), TEST_CRITERIA);

        MessageStream<EventMessage<?>> result = testSubject.stream(testCondition);

        try {
            assertTrue(result.next().isEmpty());
        } finally {
            result.close();
        }
    }

    @Test
    void eventsPublishedAreIncludedInOpenStreams() throws Exception {
        testSubject.appendEvents(AppendCondition.none(),
                                 taggedEventMessage("event-0", TEST_CRITERIA_TAGS),
                                 taggedEventMessage("event-1", TEST_CRITERIA_TAGS))
                   .thenCompose(AppendTransaction::commit)
                   .get(5, TimeUnit.SECONDS);

        MessageStream<EventMessage<?>> stream = testSubject.tailToken()
                                                           .thenApply(StreamingCondition::startingFrom)
                                                           .thenApply(testSubject::stream)
                                                           .get(5, TimeUnit.SECONDS);

        AssertUtils.assertWithin(1, TimeUnit.SECONDS, () -> assertTrue(stream.next().isPresent()));
        AssertUtils.assertWithin(1, TimeUnit.SECONDS, () -> assertTrue(stream.next().isPresent()));

        testSubject.appendEvents(AppendCondition.none(), taggedEventMessage("event-3", TEST_CRITERIA_TAGS))
                   .thenCompose(AppendTransaction::commit)
                   .get(5, TimeUnit.SECONDS);

        await(
                "Await until stream contains newly appended events"
        ).atMost(Duration.ofSeconds(1))
         .pollDelay(Duration.ofMillis(100))
         .untilAsserted(() -> assertTrue(stream.hasNextAvailable()));

        assertEquals(
                "event-3",
                stream.next()
                      .map(e -> {
                          if (e.message().getPayload() instanceof String payload) {
                              return payload;
                          } else if (e.message().getPayload() instanceof byte[] payload) {
                              return new String(payload, StandardCharsets.UTF_8);
                          } else {
                              throw new AssertionError(
                                      "Unexpected payload type: " + e.message().getPayload().getClass()
                              );
                          }
                      })
                      .orElse("none")
        );
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
        TaggedEventMessage<EventMessage<String>> firstEvent = taggedEventMessage("event-0", TEST_CRITERIA_TAGS);
        testSubject.appendEvents(AppendCondition.none(),
                                 firstEvent,
                                 taggedEventMessage("event-1", TEST_CRITERIA_TAGS))
                   .thenCompose(AppendTransaction::commit)
                   .get(5, TimeUnit.SECONDS);

        MessageStream<EventMessage<?>> stream = testSubject.tailToken()
                                                           .thenApply(StreamingCondition::startingFrom)
                                                           .thenApply(testSubject::stream)
                                                           .get(5, TimeUnit.SECONDS);

        Entry<EventMessage<?>> actualEntry = stream.first()
                                                   .asCompletableFuture()
                                                   .get(5, TimeUnit.SECONDS);
        assertEvent(actualEntry.message(), firstEvent.event());
    }

    @Test
    void headTokenReturnsTokenBasedOnLastAppendedEvent() throws Exception {
        testSubject.appendEvents(AppendCondition.none(),
                                 taggedEventMessage("event-0", TEST_CRITERIA_TAGS),
                                 taggedEventMessage("event-1", TEST_CRITERIA_TAGS))
                   .thenCompose(AppendTransaction::commit)
                   .get(5, TimeUnit.SECONDS);

        MessageStream<EventMessage<?>> stream = testSubject.headToken()
                                                           .thenApply(StreamingCondition::startingFrom)
                                                           .thenApply(testSubject::stream)
                                                           .get(5, TimeUnit.SECONDS);

        await(
                "Await until the store has caught up"
        ).atLeast(Duration.ofMillis(50))
         .atMost(Duration.ofMillis(500))
         .pollDelay(Duration.ofMillis(100))
         .untilAsserted(() -> assertFalse(stream.hasNextAvailable()));
    }

    @Test
    void tokenAtRetrievesTokenFromStorageEngineThatStreamsEventsSinceThatMoment() throws Exception {
        Instant now = Instant.now(); // assign now to a variable to not be impacted by time passing during test
        testSubject.appendEvents(AppendCondition.none(),
                                 taggedEventMessageAt("event-0", TEST_CRITERIA_TAGS, now.minusSeconds(10)),
                                 taggedEventMessageAt("event-1", TEST_CRITERIA_TAGS, now),
                                 taggedEventMessageAt("event-2", TEST_CRITERIA_TAGS, now.plusSeconds(10)))
                   .thenCompose(AppendTransaction::commit)
                   .get(5, TimeUnit.SECONDS);

        TrackingToken actualToken = testSubject.tokenAt(now.minus(5, ChronoUnit.SECONDS)).get(5, TimeUnit.SECONDS);

        assertNotNull(actualToken);
        StepVerifier.create(testSubject.stream(StreamingCondition.startingFrom(actualToken)).asFlux())
                    .expectNextCount(2)
                    .thenCancel()
                    .verify();
    }

    @Test
    void tokenAtReturnsHeadTokenWhenThereAreNoEventsAfterTheGivenAt() throws Exception {
        testSubject.appendEvents(AppendCondition.none(),
                                 taggedEventMessage("event-0", TEST_CRITERIA_TAGS),
                                 taggedEventMessage("event-1", TEST_CRITERIA_TAGS),
                                 taggedEventMessage("event-2", TEST_CRITERIA_TAGS))
                   .thenCompose(AppendTransaction::commit)
                   .get(5, TimeUnit.SECONDS);

        TrackingToken tokenAt = testSubject.tokenAt(Instant.now().plus(1, ChronoUnit.DAYS))
                                           .get(5, TimeUnit.SECONDS);
        TrackingToken headToken = testSubject.headToken()
                                             .get(5, TimeUnit.SECONDS);

        assertNotNull(tokenAt);
        assertNotNull(headToken);
        assertEquals(headToken, tokenAt);
    }

    private static TaggedEventMessage<EventMessage<String>> taggedEventMessage(String payload, Set<Tag> tags) {
        return taggedEventMessageAt(payload, tags, Instant.now());
    }

    private static TaggedEventMessage<EventMessage<String>> taggedEventMessageAt(String payload,
                                                                                 Set<Tag> tags,
                                                                                 Instant timestamp) {
        return new GenericTaggedEventMessage<>(
                new GenericEventMessage<>(
                        UUID.randomUUID().toString(),
                        new MessageType("event"),
                        payload,
                        MetaData.emptyInstance(),
                        timestamp
                ),
                tags
        );
    }

    private static void assertEvent(EventMessage<?> actual,
                                    EventMessage<String> expected) {
        if (actual.getPayload() instanceof byte[] actualPayload) {
            assertEquals(expected.getPayload(), new String(actualPayload, StandardCharsets.UTF_8));
        } else if (actual.getPayload() instanceof String actualPayload) {
            assertEquals(expected.getPayload(), actualPayload);
        } else {
            throw new AssertionError("Unexpected payload type: " + actual.getPayload().getClass());
        }
        assertEquals(expected.getIdentifier(), actual.getIdentifier());
        assertEquals(expected.getTimestamp().toEpochMilli(), actual.getTimestamp().toEpochMilli());
        assertEquals(expected.getMetaData(), actual.getMetaData());
    }
}