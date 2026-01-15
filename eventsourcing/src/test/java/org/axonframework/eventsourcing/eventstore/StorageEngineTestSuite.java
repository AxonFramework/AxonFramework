/*
 * Copyright (c) 2010-2026. Axon Framework
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

import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.TerminalEventMessage;
import org.axonframework.messaging.eventhandling.conversion.DelegatingEventConverter;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.conversion.json.JacksonConverter;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine.AppendTransaction;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.messaging.eventstreaming.StreamingCondition;
import org.axonframework.messaging.eventstreaming.Tag;
import org.axonframework.messaging.core.FluxUtils;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageStream.Entry;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for validating a {@link EventStorageEngine} and the {@link AppendTransaction}.
 * <p>
 * Note that methods and classes in this test suite are generally {@code protected} to allow
 * them to be overridden or used in the subtypes which may live in a different package.
 * This allows disabling specific tests in the subtype, or writing new tests using the
 * existing helper methods.
 *
 * @author Steven van Beelen
 * @author John Hendrikx
 */
@TestInstance(Lifecycle.PER_CLASS)
public abstract class StorageEngineTestSuite<ESE extends EventStorageEngine> {
    protected static final EventConverter CONVERTER = new DelegatingEventConverter(new JacksonConverter());

    protected String TEST_DOMAIN_ID;
    protected String OTHER_DOMAIN_ID;
    protected Set<Tag> TEST_CRITERIA_TAGS;
    protected EventCriteria TEST_CRITERIA;
    protected Set<Tag> OTHER_CRITERIA_TAGS;
    protected EventCriteria OTHER_CRITERIA;

    protected ESE testSubject;

    @BeforeAll
    void beforeAll() throws Exception {
        testSubject = buildStorageEngine();

        // At this time the store is empty, verify first and latest token are the same:
        TrackingToken actualTailToken = testSubject.firstToken(processingContext()).get(5, TimeUnit.SECONDS);
        TrackingToken actualHeadToken = testSubject.latestToken(processingContext()).get(5, TimeUnit.SECONDS);

        assertTrue(actualHeadToken.covers(actualTailToken));
        assertTrue(actualTailToken.covers(actualHeadToken));
    }

    @BeforeEach
    void setUp() throws Exception {
        TEST_DOMAIN_ID = UUID.randomUUID().toString();
        OTHER_DOMAIN_ID = UUID.randomUUID().toString();

        TEST_CRITERIA_TAGS = Set.of(new Tag("TEST", TEST_DOMAIN_ID));
        TEST_CRITERIA = EventCriteria.havingTags(new Tag("TEST", TEST_DOMAIN_ID));
        OTHER_CRITERIA_TAGS = Set.of(new Tag("OTHER", OTHER_DOMAIN_ID));
        OTHER_CRITERIA = EventCriteria.havingTags(new Tag("OTHER", OTHER_DOMAIN_ID));
    }

    /**
     * Constructs the {@link EventStorageEngine} used in this test suite.
     *
     * @return The {@link EventStorageEngine} used in this test suite.
     */
    protected abstract ESE buildStorageEngine() throws Exception;

    /**
     * Returns the processing context to use for event storage engine calls.
     *
     * @return The {@link ProcessingContext}.
     */
    protected abstract ProcessingContext processingContext();

    @Test
    protected void sourcingEventsReturnsMatchingAggregateEvents() throws Exception {
        int expectedNumberOfEvents = 3;
        int expectedCount = expectedNumberOfEvents + 1; // events and 1 consistency marker message

        appendEvents(
                AppendCondition.none(),
                taggedEventMessage("event-0", TEST_CRITERIA_TAGS),
                taggedEventMessage("event-1", TEST_CRITERIA_TAGS),
                taggedEventMessage("event-2", OTHER_CRITERIA_TAGS),
                taggedEventMessage("event-3", OTHER_CRITERIA_TAGS),
                taggedEventMessage("event-4", OTHER_CRITERIA_TAGS),
                taggedEventMessage("event-5", TEST_CRITERIA_TAGS)
        );

        SourcingCondition testCondition = SourcingCondition.conditionFor(TEST_CRITERIA);

        StepVerifier.create(FluxUtils.of(testSubject.source(testCondition, processingContext())))
                    .expectNextCount(expectedCount)
                    .verifyComplete();
    }

    @Test
    protected void sourcingEventsReturnsConsistencyMarkerWithNoEventMessageAsFinalEntryInTheMessageStream() throws Exception {
        appendEvents(
                AppendCondition.none(),
                taggedEventMessage("event-0", TEST_CRITERIA_TAGS),
                taggedEventMessage("event-1", TEST_CRITERIA_TAGS),
                taggedEventMessage("event-2", OTHER_CRITERIA_TAGS),
                taggedEventMessage("event-3", OTHER_CRITERIA_TAGS),
                taggedEventMessage("event-4", OTHER_CRITERIA_TAGS),
                taggedEventMessage("event-5", OTHER_CRITERIA_TAGS)
        );

        StepVerifier.create(FluxUtils.of(testSubject.source(SourcingCondition.conditionFor(TEST_CRITERIA), processingContext())))
                    .assertNext(entry -> assertNull(entry.getResource(ConsistencyMarker.RESOURCE_KEY)))
                    .assertNext(entry -> assertNull(entry.getResource(ConsistencyMarker.RESOURCE_KEY)))
                    .assertNext(StorageEngineTestSuite::assertMarkerEntry)
                    .verifyComplete();

        StepVerifier.create(FluxUtils.of(testSubject.source(SourcingCondition.conditionFor(OTHER_CRITERIA), processingContext())))
                    .assertNext(entry -> assertNull(entry.getResource(ConsistencyMarker.RESOURCE_KEY)))
                    .assertNext(entry -> assertNull(entry.getResource(ConsistencyMarker.RESOURCE_KEY)))
                    .assertNext(entry -> assertNull(entry.getResource(ConsistencyMarker.RESOURCE_KEY)))
                    .assertNext(entry -> assertNull(entry.getResource(ConsistencyMarker.RESOURCE_KEY)))
                    .assertNext(StorageEngineTestSuite::assertMarkerEntry)
                    .verifyComplete();
    }

    @Test
    protected void usingConsistencyMarkerFromSourcingEventToAppendAfterAppendWithConditionIsNotAllowed() throws Exception {
        // given ...
        appendEvents(
                AppendCondition.none(),
                taggedEventMessage("event-0", TEST_CRITERIA_TAGS),
                taggedEventMessage("event-1", TEST_CRITERIA_TAGS),
                taggedEventMessage("event-2", OTHER_CRITERIA_TAGS),
                taggedEventMessage("event-3", OTHER_CRITERIA_TAGS),
                taggedEventMessage("event-4", OTHER_CRITERIA_TAGS),
                taggedEventMessage("event-5", OTHER_CRITERIA_TAGS)
        );

        ConsistencyMarker marker = FluxUtils
            .of(testSubject.source(SourcingCondition.conditionFor(OTHER_CRITERIA), processingContext()))
            .collectList()
            .map(List::getLast)
            .map(entry -> entry.getResource(ConsistencyMarker.RESOURCE_KEY))
            .block();

        appendEvents(AppendCondition.none(), taggedEventMessage("event-6", OTHER_CRITERIA_TAGS));

        // when ...
        AppendCondition testAppendCondition = AppendCondition.withCriteria(OTHER_CRITERIA)
                                                             .withMarker(marker);
        CompletableFuture<ConsistencyMarker> result =
                asyncAppendEvents(testAppendCondition, taggedEventMessage("event-7", OTHER_CRITERIA_TAGS));

        // then ...
        await("Await commit").pollDelay(Duration.ofMillis(50))
                             .atMost(Duration.ofSeconds(5))
                             .untilAsserted(result::isDone);
        assertTrue(result.isCompletedExceptionally());
        assertInstanceOf(AppendEventsTransactionRejectedException.class, result.exceptionNow());
    }

    @Test
    protected void sourcingEventsReturnsConsistencyMarkerAsSoleMessageWhenNoEventsInTheStoreForFlux() {
        SourcingCondition testCondition = SourcingCondition.conditionFor(TEST_CRITERIA);

        StepVerifier.create(FluxUtils.of(testSubject.source(testCondition, processingContext())))
                    .assertNext(StorageEngineTestSuite::assertMarkerEntry)
                    .verifyComplete();
    }

    @Test
    protected void sourcingEmptyStreamReturnsOnlyConsistencyMarker() {
        // given
        // no events appended to the store

        // when
        SourcingCondition testCondition = SourcingCondition.conditionFor(TEST_CRITERIA);
        MessageStream<EventMessage> sourcingStream = testSubject.source(testCondition, processingContext());

        // then
        StepVerifier.create(FluxUtils.of(sourcingStream))
                    .assertNext(StorageEngineTestSuite::assertMarkerEntry)
                    .verifyComplete();
    }

    @Test
    protected void sourcingEventsReturnsConsistencyMarkerAsSoleMessageAndCompletesWhenNoEventsInTheStore() {
        AtomicBoolean completed = new AtomicBoolean(false);
        SourcingCondition testCondition = SourcingCondition.conditionFor(TEST_CRITERIA);

        MessageStream<EventMessage> sourcingStream = testSubject.source(testCondition, processingContext())
                                                                .onComplete(() -> completed.set(true));
        await("Await first entry availability")
                .pollDelay(Duration.ofMillis(50))
                .atMost(Duration.ofMillis(500))
                .until(sourcingStream::hasNextAvailable);
        Optional<Entry<EventMessage>> entry = sourcingStream.next();
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

    @Test
    protected void sourcingEventsShouldReturnLatestConsistencyMarker() throws Exception {
        appendEvents(
            AppendCondition.none(),
            taggedEventMessage("event-0", TEST_CRITERIA_TAGS),
            taggedEventMessage("event-1", OTHER_CRITERIA_TAGS)
        );

        ConsistencyMarker marker1 = FluxUtils.of(testSubject.source(SourcingCondition.conditionFor(TEST_CRITERIA), processingContext()))
            .collectList()
            .map(List::getLast)
            .map(entry -> entry.getResource(ConsistencyMarker.RESOURCE_KEY))
            .block();

        ConsistencyMarker marker2 = FluxUtils.of(testSubject.source(SourcingCondition.conditionFor(OTHER_CRITERIA), processingContext()))
            .collectList()
            .map(List::getLast)
            .map(entry -> entry.getResource(ConsistencyMarker.RESOURCE_KEY))
            .block();

        assertThat(marker1).isEqualTo(marker2);
    }

    @Test
    protected void sourcingEventsShouldReturnLatestConsistencyMarkerEvenWhenStoreIsEmpty() {
        ConsistencyMarker marker1 = FluxUtils.of(testSubject.source(SourcingCondition.conditionFor(TEST_CRITERIA), processingContext()))
            .collectList()
            .map(List::getLast)
            .map(entry -> entry.getResource(ConsistencyMarker.RESOURCE_KEY))
            .block();

        ConsistencyMarker marker2 = FluxUtils.of(testSubject.source(SourcingCondition.conditionFor(OTHER_CRITERIA), processingContext()))
            .collectList()
            .map(List::getLast)
            .map(entry -> entry.getResource(ConsistencyMarker.RESOURCE_KEY))
            .block();

        assertThat(marker1).isEqualTo(marker2);
    }

    @Test
    protected void sourcingEventsWithOffsetReturnsMatchingAggregateEvents() throws Exception {
        int expectedNumberOfEvents = 2;  // first match is skipped
        int expectedCount = expectedNumberOfEvents + 1; // events and 1 consistency marker message

        appendEvents(
                AppendCondition.none(),
                taggedEventMessage("event-0", TEST_CRITERIA_TAGS),
                taggedEventMessage("event-1", TEST_CRITERIA_TAGS),
                taggedEventMessage("event-2", OTHER_CRITERIA_TAGS),
                taggedEventMessage("event-3", OTHER_CRITERIA_TAGS),
                taggedEventMessage("event-4", OTHER_CRITERIA_TAGS),
                taggedEventMessage("event-5", TEST_CRITERIA_TAGS)
        );

        SourcingCondition testCondition = SourcingCondition.conditionFor(TEST_CRITERIA);

        TrackingToken tokenOfFirstMessage = testSubject.source(testCondition, processingContext())
            .first()
            .asCompletableFuture()
            .thenApply(r -> r.getResource(TrackingToken.RESOURCE_KEY))
            .get(5, TimeUnit.SECONDS);

        // Dirty hack, having to convert a token to a position, hoping the tested engine accepts this...
        Position position = new GlobalIndexPosition(tokenOfFirstMessage.position().getAsLong());

        SourcingCondition offsetTestCondition = SourcingCondition.conditionFor(position, TEST_CRITERIA);

        StepVerifier.create(FluxUtils.of(testSubject.source(offsetTestCondition, processingContext())))
                    .expectNextCount(expectedCount)
                    .verifyComplete();
    }

    protected static void assertMarkerEntry(Entry<EventMessage> entry) {
        assertNotNull(entry.getResource(ConsistencyMarker.RESOURCE_KEY));
        assertEquals(TerminalEventMessage.INSTANCE, entry.message());
    }

    @Test
    protected void transactionRejectedWithConflictingEventsInStore() throws Exception {
        appendEvents(
                AppendCondition.none(),
                taggedEventMessage("event-0", TEST_CRITERIA_TAGS),
                taggedEventMessage("event-1", TEST_CRITERIA_TAGS)
        );

        AppendCondition testCondition = AppendCondition.withCriteria(TEST_CRITERIA);

        CompletableFuture<ConsistencyMarker> actual =
                asyncAppendEvents(testCondition, taggedEventMessage("event-2", TEST_CRITERIA_TAGS));

        ExecutionException actualException =
                assertThrows(ExecutionException.class, () -> actual.get(1, TimeUnit.SECONDS));
        assertInstanceOf(AppendEventsTransactionRejectedException.class, actualException.getCause());
    }

    @Test
    protected void transactionRejectedWhenConcurrentlyCreatedTransactionIsCommittedFirst() {
        AppendCondition appendCondition = AppendCondition.withCriteria(TEST_CRITERIA);

        var firstTx = testSubject.appendEvents(appendCondition,
                                               processingContext(),
                                               taggedEventMessage("event-0", TEST_CRITERIA_TAGS));
        var secondTx = testSubject.appendEvents(appendCondition,
                                                processingContext(),
                                                taggedEventMessage("event-1", TEST_CRITERIA_TAGS));

        CompletableFuture<ConsistencyMarker> firstCommit = finishTx(firstTx);
        assertDoesNotThrow(() -> firstCommit.get(1, TimeUnit.SECONDS));

        CompletableFuture<ConsistencyMarker> secondCommit = finishTx(secondTx);
        var actual = assertThrows(ExecutionException.class, () -> secondCommit.get(1, TimeUnit.SECONDS));
        assertInstanceOf(AppendEventsTransactionRejectedException.class, actual.getCause());
    }

    @RepeatedTest(5)  // repeat a few times to detect concurrency bugs earlier
    protected void concurrentTransactionsForNonOverlappingTagsBothCommitWithExpectedConsistencyMarkerResponse() throws Exception {
        TrackingToken startToken = testSubject.latestToken(processingContext()).join();
        long position = startToken.position().getAsLong();
        GlobalIndexConsistencyMarker marker1 = new GlobalIndexConsistencyMarker(position + 1);
        GlobalIndexConsistencyMarker marker2 = new GlobalIndexConsistencyMarker(position + 2);

        AppendCondition firstCondition = AppendCondition.withCriteria(TEST_CRITERIA)
                                                        .withMarker(ConsistencyMarker.ORIGIN);
        AppendCondition secondCondition = AppendCondition.withCriteria(OTHER_CRITERIA)
                                                         .withMarker(ConsistencyMarker.ORIGIN);
        CompletableFuture<AppendTransaction<?>> firstTx =
                testSubject.appendEvents(firstCondition,
                                         processingContext(),
                                         taggedEventMessage("event-0", TEST_CRITERIA_TAGS));
        CompletableFuture<AppendTransaction<?>> secondTx =
                testSubject.appendEvents(secondCondition,
                                         processingContext(),
                                         taggedEventMessage("event-0", OTHER_CRITERIA_TAGS));

        firstTx.get(1, TimeUnit.SECONDS);
        secondTx.get(1, TimeUnit.SECONDS);

        // when...
        CompletableFuture<ConsistencyMarker> firstCommit = finishTx(firstTx);
        CompletableFuture<ConsistencyMarker> secondCommit = finishTx(secondTx);
        // then...expecting an unordered set of markers, as the commit order is not consistent for an async system.
        assertDoesNotThrow(() -> firstCommit.get(5, TimeUnit.SECONDS));
        assertDoesNotThrow(() -> secondCommit.get(5, TimeUnit.SECONDS));
        ConsistencyMarker firstMarker = firstCommit.get(50, TimeUnit.MILLISECONDS);
        ConsistencyMarker secondMarker = secondCommit.get(50, TimeUnit.MILLISECONDS);
        assertNotNull(firstMarker);
        assertNotNull(secondMarker);
        List<ConsistencyMarker> result = List.of(firstMarker, secondMarker);

        // Valid results here are: [1, 2], [2, 1], [2, 2], but not [1, 1]
        assertThat(result).isIn(List.of(
            List.of(marker1, marker2),
            List.of(marker2, marker1),
            List.of(marker2, marker2)
        ));
    }

    @Test
    protected void concurrentTransactionsForOverlappingTagsThrowAnAppendEventsTransactionRejectedException() throws Exception {
        // given...
        int exceptionCounter = 0;
        AppendCondition firstCondition = new DefaultAppendCondition(ConsistencyMarker.ORIGIN, TEST_CRITERIA);
        AppendCondition secondCondition = new DefaultAppendCondition(ConsistencyMarker.ORIGIN, TEST_CRITERIA);
        CompletableFuture<AppendTransaction<?>> firstTx =
                testSubject.appendEvents(firstCondition,
                                         processingContext(),
                                         taggedEventMessage("event-0", TEST_CRITERIA_TAGS));
        CompletableFuture<AppendTransaction<?>> secondTx =
                testSubject.appendEvents(secondCondition,
                                         processingContext(),
                                         taggedEventMessage("event-1", TEST_CRITERIA_TAGS));

        firstTx.get(1, TimeUnit.SECONDS);
        secondTx.get(1, TimeUnit.SECONDS);

        // when...
        CompletableFuture<ConsistencyMarker> firstCommit = finishTx(firstTx);
        CompletableFuture<ConsistencyMarker> secondCommit = finishTx(secondTx);
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
    protected void streamingFromStartReturnsSelectedMessages() throws Exception {
        TaggedEventMessage<EventMessage> expectedEventOne = taggedEventMessage("event-0", TEST_CRITERIA_TAGS);
        TaggedEventMessage<EventMessage> expectedEventTwo = taggedEventMessage("event-1", TEST_CRITERIA_TAGS);
        TaggedEventMessage<EventMessage> expectedEventThree = taggedEventMessage("event-4", TEST_CRITERIA_TAGS);
        // Ensure there are "gaps" in the global stream based on events not matching the sourcing condition
        appendEvents(
                AppendCondition.none(),
                expectedEventOne,
                expectedEventTwo,
                taggedEventMessage("event-2", OTHER_CRITERIA_TAGS),
                taggedEventMessage("event-3", OTHER_CRITERIA_TAGS),
                expectedEventThree,
                taggedEventMessage("event-5", OTHER_CRITERIA_TAGS),
                taggedEventMessage("event-6", OTHER_CRITERIA_TAGS)
        );

        MessageStream<EventMessage> result =
                testSubject.firstToken(processingContext())
                           .thenApply(position -> StreamingCondition.conditionFor(position, TEST_CRITERIA))
                           .thenApply(c -> testSubject.stream(c, processingContext()))
                           .get(5, TimeUnit.SECONDS);

        StepVerifier.create(FluxUtils.of(result))
                    .assertNext(entry -> assertEvent(entry.message(), expectedEventOne.event()))
                    .assertNext(entry -> assertEvent(entry.message(), expectedEventTwo.event()))
                    .assertNext(entry -> assertEvent(entry.message(), expectedEventThree.event()))
                    .thenCancel()
                    .verify();
    }

    @Test
    protected void streamingFromSpecificPositionReturnsSelectedMessages() throws Exception {
        TaggedEventMessage<EventMessage> expectedEventOne = taggedEventMessage("event-1", TEST_CRITERIA_TAGS);
        TaggedEventMessage<EventMessage> expectedEventTwo = taggedEventMessage("event-4", TEST_CRITERIA_TAGS);
        TrackingToken startToken = testSubject.latestToken(processingContext()).join();

        // Ensure there are "gaps" in the global stream based on events not matching the streaming condition
        appendEvents(
                AppendCondition.none(),
                taggedEventMessage("event-0", TEST_CRITERIA_TAGS),
                expectedEventOne,
                taggedEventMessage("event-2", OTHER_CRITERIA_TAGS),
                taggedEventMessage("event-3", OTHER_CRITERIA_TAGS),
                expectedEventTwo,
                taggedEventMessage("event-5", OTHER_CRITERIA_TAGS),
                taggedEventMessage("event-6", OTHER_CRITERIA_TAGS)
        );

        TrackingToken tokenOfFirstMessage = testSubject.stream(StreamingCondition.startingFrom(startToken), processingContext())
            .first()
            .asCompletableFuture()
            .thenApply(r -> r.getResource(TrackingToken.RESOURCE_KEY))
            .get(5, TimeUnit.SECONDS);

        StreamingCondition testCondition = StreamingCondition.conditionFor(tokenOfFirstMessage, TEST_CRITERIA);

        StepVerifier.create(FluxUtils.of(testSubject.stream(testCondition, processingContext())))
                    // we've skipped the first one by changing the starting point
                    .assertNext(entry -> assertEvent(entry.message(), expectedEventOne.event()))
                    .assertNext(entry -> assertEvent(entry.message(), expectedEventTwo.event()))
                    .thenCancel()
                    .verify();
    }

    @Test
    protected void streamingAfterLastPositionReturnsEmptyStream() throws Exception {
        TrackingToken startToken = testSubject.latestToken(processingContext()).join();

        appendEvents(
                AppendCondition.none(),
                taggedEventMessage("event-0", TEST_CRITERIA_TAGS),
                taggedEventMessage("event-1", TEST_CRITERIA_TAGS),
                taggedEventMessage("event-2", TEST_CRITERIA_TAGS),
                taggedEventMessage("event-3", TEST_CRITERIA_TAGS),
                taggedEventMessage("event-4", TEST_CRITERIA_TAGS),
                taggedEventMessage("event-5", TEST_CRITERIA_TAGS),
                taggedEventMessage("event-6", TEST_CRITERIA_TAGS)
        );

        StreamingCondition testCondition =
                StreamingCondition.conditionFor(new GlobalSequenceTrackingToken(startToken.position().getAsLong() + 10), TEST_CRITERIA);

        MessageStream<EventMessage> result = testSubject.stream(testCondition, processingContext());

        try {
            assertTrue(result.next().isEmpty());
        } finally {
            result.close();
        }
    }

    @Test
    protected void eventsPublishedAreIncludedInOpenStreams() throws Exception {
        TrackingToken startToken = testSubject.latestToken(processingContext()).join();

        appendEvents(
                AppendCondition.none(),
                taggedEventMessage("event-0", TEST_CRITERIA_TAGS),
                taggedEventMessage("event-1", TEST_CRITERIA_TAGS)
        );

        MessageStream<EventMessage> stream = testSubject.stream(StreamingCondition.startingFrom(startToken), processingContext());

        // Wait for first event...
        await().pollDelay(Duration.ofMillis(250))
               .untilAsserted(() -> assertTrue(stream.next().isPresent()));
        // And then for second event...
        await().pollDelay(Duration.ofMillis(250))
               .untilAsserted(() -> assertTrue(stream.next().isPresent()));

        appendEvents(AppendCondition.none(), taggedEventMessage("event-3", TEST_CRITERIA_TAGS));

        await(
                "Await until stream contains newly appended events"
        ).atMost(Duration.ofSeconds(1))
         .pollDelay(Duration.ofMillis(100))
         .untilAsserted(() -> assertTrue(stream.hasNextAvailable()));

        assertEquals(
                "event-3",
                stream.next()
                      .map(e -> {
                          if (e.message().payload() instanceof String payload) {
                              return payload;
                          } else if (e.message().payload() instanceof byte[] payload) {
                              return new String(payload, StandardCharsets.UTF_8);
                          } else {
                              throw new AssertionError(
                                      "Unexpected payload type: " + e.message().payload().getClass()
                              );
                          }
                      })
                      .orElse("none")
        );
    }

    @Test
    protected void streamReceivesEventsAppendedAfterStreamOpened() throws Exception {
        TrackingToken startToken = testSubject.latestToken(processingContext()).join();

        // given
        MessageStream<EventMessage> stream = testSubject.stream(StreamingCondition.startingFrom(startToken), processingContext());

        // when
        TaggedEventMessage<EventMessage> expectedEvent1 = taggedEventMessage("event-1", TEST_CRITERIA_TAGS);
        TaggedEventMessage<EventMessage> expectedEvent2 = taggedEventMessage("event-2", TEST_CRITERIA_TAGS);

        appendEvents(AppendCondition.none(), expectedEvent1, expectedEvent2);

        // then
        waitUntilHasNextAvailable(stream);

        Optional<Entry<EventMessage>> firstEvent = stream.next();
        assertTrue(firstEvent.isPresent());
        assertEvent(firstEvent.get().message(), expectedEvent1.event());

        waitUntilHasNextAvailable(stream);

        Optional<Entry<EventMessage>> secondEvent = stream.next();
        assertTrue(secondEvent.isPresent());
        assertEvent(secondEvent.get().message(), expectedEvent2.event());
    }

    @Test
    protected void tailTokenReturnsFirstAppendedEvent() throws Exception {
        TaggedEventMessage<EventMessage> firstEvent = taggedEventMessage("event-0", TEST_CRITERIA_TAGS);
        TrackingToken startToken = testSubject.latestToken(processingContext()).join();

        appendEvents(
                AppendCondition.none(),
                firstEvent,
                taggedEventMessage("event-1", TEST_CRITERIA_TAGS)
        );

        MessageStream<EventMessage> stream = testSubject.stream(StreamingCondition.startingFrom(startToken), processingContext());

        Entry<EventMessage> actualEntry = stream.first()
                                                .asCompletableFuture()
                                                .get(5, TimeUnit.SECONDS);
        assertEvent(actualEntry.message(), firstEvent.event());
    }

    @Test
    protected void headTokenReturnsTokenBasedOnLastAppendedEvent() throws Exception {
        appendEvents(
                AppendCondition.none(),
                taggedEventMessage("event-0", TEST_CRITERIA_TAGS),
                taggedEventMessage("event-1", TEST_CRITERIA_TAGS)
        );

        MessageStream<EventMessage> stream = testSubject.latestToken(processingContext())
                                                        .thenApply(StreamingCondition::startingFrom)
                                                        .thenApply(c -> testSubject.stream(c, processingContext()))
                                                        .get(5, TimeUnit.SECONDS);

        await(
                "Await until the store has caught up"
        ).atLeast(Duration.ofMillis(50))
         .atMost(Duration.ofMillis(500))
         .pollDelay(Duration.ofMillis(100))
         .untilAsserted(() -> assertFalse(stream.hasNextAvailable()));
    }

    @Test
    protected void tokenAtRetrievesTokenFromStorageEngineThatStreamsEventsSinceThatMoment() throws Exception {
        Instant now = Instant.now(); // assign now to a variable to not be impacted by time passing during test
        appendEvents(
                AppendCondition.none(),
                taggedEventMessageAt("event-0", TEST_CRITERIA_TAGS, now.minusSeconds(10)),
                taggedEventMessageAt("event-1", TEST_CRITERIA_TAGS, now),
                taggedEventMessageAt("event-2", TEST_CRITERIA_TAGS, now.plusSeconds(10))
        );

        TrackingToken actualToken = testSubject.tokenAt(now.minus(5, ChronoUnit.SECONDS), processingContext()).get(5,
                                                                                                                   TimeUnit.SECONDS);

        assertNotNull(actualToken);
        StepVerifier.create(FluxUtils.of(testSubject.stream(StreamingCondition.startingFrom(actualToken), processingContext())))
                    .expectNextCount(2)
                    .thenCancel()
                    .verify();
    }

    @Test
    protected void tokenAtReturnsHeadTokenWhenThereAreNoEventsAfterTheGivenAt() throws Exception {
        appendEvents(
                AppendCondition.none(),
                taggedEventMessage("event-0", TEST_CRITERIA_TAGS),
                taggedEventMessage("event-1", TEST_CRITERIA_TAGS),
                taggedEventMessage("event-2", TEST_CRITERIA_TAGS)
        );

        TrackingToken tokenAt = testSubject.tokenAt(Instant.now().plus(1, ChronoUnit.DAYS), processingContext())
                                           .get(5, TimeUnit.SECONDS);
        TrackingToken headToken = testSubject.latestToken(processingContext())
                                             .get(5, TimeUnit.SECONDS);

        assertNotNull(tokenAt);
        assertNotNull(headToken);
        assertEquals(headToken, tokenAt);
    }

    @Test
    protected void streamShouldBeNotifiedOfAppend() {
        TrackingToken latest = testSubject.latestToken(null).join();

        // Create a stream to see what if it is notified of a new event:
        MessageStream<EventMessage> stream = testSubject.stream(StreamingCondition.startingFrom(latest), null);

        finishTx(testSubject.appendEvents(AppendCondition.none(), null, List.of(
            taggedEventMessage("Hello World", Set.of())
        )));

        // Assert that the event has become available:
        await().untilAsserted(() -> assertThat(stream.hasNextAvailable()).isTrue());

        assertThat(stream.next())
            .map(Entry::message)
            .map(em -> em.payloadAs(String.class, CONVERTER))
            .contains("Hello World");
    }

    @Test
    protected void twoIndependentStorageEnginesShouldSeeEachOthersAppends() throws Exception {
        EventStorageEngine engine1 = testSubject;
        EventStorageEngine engine2 = buildStorageEngine();

        TrackingToken latest = engine1.latestToken(null).join();

        assertThat(latest).isEqualTo(engine2.latestToken(null).join());

        // Create a stream on both engines, to see what events are being appended:
        MessageStream<EventMessage> stream1 = engine1.stream(StreamingCondition.startingFrom(latest), null);
        MessageStream<EventMessage> stream2 = engine1.stream(StreamingCondition.startingFrom(latest), null);

        // Append an event via engine 1:
        finishTx(engine1.appendEvents(AppendCondition.none(), null, List.of(
            taggedEventMessage("Hello From Engine 1", Set.of())
        )));

        // Assert that both engines see the event:
        await().untilAsserted(() -> {
            assertThat(stream1.hasNextAvailable()).isTrue();
            assertThat(stream2.hasNextAvailable()).isTrue();
        });

        assertThat(stream1.next())
            .map(Entry::message)
            .map(em -> em.payloadAs(String.class, CONVERTER))
            .contains("Hello From Engine 1");

        assertThat(stream2.next())
            .map(Entry::message)
            .map(em -> em.payloadAs(String.class, CONVERTER))
            .contains("Hello From Engine 1");

        // Append an event via engine 2:
        finishTx(engine1.appendEvents(AppendCondition.none(), null, List.of(
            taggedEventMessage("Hello From Engine 2", Set.of())
        )));

        // Assert that both engines see the event:
        await().untilAsserted(() -> {
            assertThat(stream1.hasNextAvailable()).isTrue();
            assertThat(stream2.hasNextAvailable()).isTrue();
        });

        assertThat(stream1.next())
            .map(Entry::message)
            .map(em -> em.payloadAs(String.class, CONVERTER))
            .contains("Hello From Engine 2");

        assertThat(stream2.next())
            .map(Entry::message)
            .map(em -> em.payloadAs(String.class, CONVERTER))
            .contains("Hello From Engine 2");
    }

    @Nested
    protected class Peek {

        @Test
        protected void returnsMarkerWhenNoEventsMatchCriteria() {
            SourcingCondition condition = SourcingCondition.conditionFor(TEST_CRITERIA);
            MessageStream<EventMessage> stream = testSubject.source(condition, processingContext());

            waitUntilHasNextAvailable(stream);
            Optional<Entry<EventMessage>> peeked = stream.peek();

            assertTrue(peeked.isPresent());
            assertMarkerEntry(peeked.get());
        }

        @Test
        protected void returnsFirstEventWithoutAdvancing() throws Exception {
            TaggedEventMessage<EventMessage> expectedEvent1 = taggedEventMessage("event-1", TEST_CRITERIA_TAGS);

            appendEvents(
                    AppendCondition.none(),
                    expectedEvent1,
                    taggedEventMessage("event-2", TEST_CRITERIA_TAGS)
            );

            SourcingCondition condition = SourcingCondition.conditionFor(TEST_CRITERIA);
            MessageStream<EventMessage> stream = testSubject.source(condition, processingContext());

            waitUntilHasNextAvailable(stream);
            Optional<Entry<EventMessage>> peeked = stream.peek();
            Optional<Entry<EventMessage>> peekedAgain = stream.peek();

            assertTrue(peeked.isPresent());
            assertTrue(peekedAgain.isPresent());
            assertEvent(peeked.get().message(), expectedEvent1.event());
        }

        @Test
        protected void doesNotAdvanceStream() throws Exception {
            TaggedEventMessage<EventMessage> expectedEvent1 = taggedEventMessage("event-1", TEST_CRITERIA_TAGS);

            appendEvents(AppendCondition.none(), expectedEvent1);

            SourcingCondition condition = SourcingCondition.conditionFor(TEST_CRITERIA);
            MessageStream<EventMessage> stream = testSubject.source(condition, processingContext());

            waitUntilHasNextAvailable(stream);
            Optional<Entry<EventMessage>> peeked = stream.peek();
            Optional<Entry<EventMessage>> next = stream.next();

            assertTrue(peeked.isPresent());
            assertTrue(next.isPresent());
            assertEvent(peeked.get().message(), expectedEvent1.event());
            assertEvent(next.get().message(), expectedEvent1.event());
        }

        @Test
        protected void returnsEmptyAfterConsumingAll() throws Exception {
            appendEvents(AppendCondition.none(), taggedEventMessage("event-1", TEST_CRITERIA_TAGS));

            SourcingCondition condition = SourcingCondition.conditionFor(TEST_CRITERIA);
            MessageStream<EventMessage> stream = testSubject.source(condition, processingContext());

            waitUntilHasNextAvailable(stream);
            stream.next(); // consume event
            stream.next(); // consume marker

            assertTrue(stream.peek().isEmpty());
        }
    }

    protected static TaggedEventMessage<EventMessage> taggedEventMessage(String payload, Set<Tag> tags) {
        return taggedEventMessageAt(payload, tags, Instant.now());
    }

    protected static TaggedEventMessage<EventMessage> taggedEventMessageAt(String payload,
                                                                           Set<Tag> tags,
                                                                           Instant timestamp) {
        return new GenericTaggedEventMessage<>(
                new GenericEventMessage(
                        UUID.randomUUID().toString(),
                        new MessageType("event"),
                        payload,
                        Map.of("key", "value"),
                        timestamp
                ),
                tags
        );
    }

    protected static void assertEvent(EventMessage actual, EventMessage expected) {
        if (actual.payload() instanceof byte[] actualPayload) {
            assertEquals(expected.payload(), new String(actualPayload, StandardCharsets.UTF_8));
        } else if (actual.payload() instanceof String actualPayload) {
            assertEquals(expected.payload(), actualPayload);
        } else {
            throw new AssertionError("Unexpected payload type: " + actual.payload().getClass());
        }
        assertEquals(expected.identifier(), actual.identifier());
        assertEquals(expected.timestamp().toEpochMilli(), actual.timestamp().toEpochMilli());
        assertEquals(expected.metadata(), actual.metadata());
    }

    protected static void waitUntilHasNextAvailable(MessageStream<EventMessage> stream) {
        await("Await event availability in stream")
                .atMost(Duration.ofSeconds(2))
                .pollInterval(Duration.ofMillis(100))
                .until(stream::hasNextAvailable);
    }

    protected final ConsistencyMarker appendEvents(AppendCondition condition, TaggedEventMessage<?>... events)
            throws Exception {
        return finishTx(testSubject.appendEvents(condition, processingContext(), events)).get(5, TimeUnit.SECONDS);
    }

    protected final CompletableFuture<ConsistencyMarker> asyncAppendEvents(AppendCondition condition,
                                                                           TaggedEventMessage<?>... events) {
        return finishTx(testSubject.appendEvents(condition, processingContext(), events));
    }

    protected final CompletableFuture<ConsistencyMarker> finishTx(CompletableFuture<AppendTransaction<?>> future) {
        return future
                .thenApply(this::castTransaction)
                .thenCompose(tx -> tx.commit(processingContext())
                                     .thenCompose(r -> tx.afterCommit(r, processingContext()))
                );
    }

    @SuppressWarnings("unchecked")
    protected final AppendTransaction<Object> castTransaction(AppendTransaction<?> at) {
        return (AppendTransaction<Object>) at;
    }
}