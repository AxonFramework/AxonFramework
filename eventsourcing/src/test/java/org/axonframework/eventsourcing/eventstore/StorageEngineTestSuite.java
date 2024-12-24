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
import org.axonframework.eventsourcing.eventstore.AsyncEventStorageEngine.AppendTransaction;
import org.axonframework.messaging.MessageStream;
import org.axonframework.utils.AssertUtils;
import org.junit.jupiter.api.*;
import reactor.test.StepVerifier;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

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

        TEST_CRITERIA = EventCriteria.hasIndex(new Index("TEST", TEST_DOMAIN_ID));
        OTHER_CRITERIA = EventCriteria.hasIndex(new Index("OTHER", OTHER_DOMAIN_ID));

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
        IndexedEventMessage<?> expectedEventOne = indexedEventMessage("event-0", TEST_CRITERIA.indices());
        IndexedEventMessage<?> expectedEventTwo = indexedEventMessage("event-1", TEST_CRITERIA.indices());
        IndexedEventMessage<?> expectedEventThree = indexedEventMessage("event-4", TEST_CRITERIA.indices());
        // Ensure there are "gaps" in the global stream based on events not matching the sourcing condition
        testSubject.appendEvents(AppendCondition.none(),
                                 expectedEventOne,
                                 expectedEventTwo,
                                 indexedEventMessage("event-2", OTHER_CRITERIA.indices()),
                                 indexedEventMessage("event-3", OTHER_CRITERIA.indices()),
                                 expectedEventThree,
                                 indexedEventMessage("event-5", OTHER_CRITERIA.indices()),
                                 indexedEventMessage("event-6", OTHER_CRITERIA.indices()))
                   .thenCompose(AppendTransaction::commit).join();

        MessageStream<EventMessage<?>> result = testSubject.tailToken()
                                                           .thenApply(StreamingCondition::startingFrom)
                                                           .thenApply(sc -> sc.with(TEST_CRITERIA))
                                                           .thenApply(testSubject::stream)
                                                           .join();

        StepVerifier.create(result.asFlux())
                    .assertNext(entry -> assertEvent(entry.message(), expectedEventOne))
                    .assertNext(entry -> assertEvent(entry.message(), expectedEventTwo))
                    .assertNext(entry -> assertEvent(entry.message(), expectedEventThree))
                    .thenCancel()
                    .verify();
    }

    @Test
    void streamingFromSpecificPositionReturnsSelectedMessages() {
        IndexedEventMessage<?> expectedEventOne = indexedEventMessage("event-0", TEST_CRITERIA.indices());
        IndexedEventMessage<?> expectedEventTwo = indexedEventMessage("event-1", TEST_CRITERIA.indices());
        IndexedEventMessage<?> expectedEventThree = indexedEventMessage("event-4", TEST_CRITERIA.indices());
        // Ensure there are "gaps" in the global stream based on events not matching the sourcing condition
        testSubject.appendEvents(AppendCondition.none(),
                                 expectedEventOne,
                                 expectedEventTwo,
                                 indexedEventMessage("event-2", OTHER_CRITERIA.indices()),
                                 indexedEventMessage("event-3", OTHER_CRITERIA.indices()),
                                 expectedEventThree,
                                 indexedEventMessage("event-5", OTHER_CRITERIA.indices()),
                                 indexedEventMessage("event-6", OTHER_CRITERIA.indices())
                   )
                   .thenCompose(AppendTransaction::commit).join();

        TrackingToken tokenOfFirstMessage = testSubject.tailToken()
                                                       .thenApply(StreamingCondition::startingFrom)
                                                       .thenApply(testSubject::stream)
                                                       .thenCompose(MessageStream::firstAsCompletableFuture)
                                                       .thenApply(r -> r.getResource(TrackingToken.RESOURCE_KEY))
                                                       .join();

        StepVerifier.create(testSubject.stream(StreamingCondition.startingFrom(tokenOfFirstMessage).with(TEST_CRITERIA))
                                       .asFlux())
                    // we've skipped the first two
                    .assertNext(entry -> assertEvent(entry.message(), expectedEventTwo))
                    .assertNext(entry -> assertEvent(entry.message(), expectedEventThree))
                    .thenCancel()
                    .verify();
    }

    @Test
    void streamingAfterLastPositionReturnsEmptyStream() {
        testSubject.appendEvents(AppendCondition.none(),
                                 indexedEventMessage("event-0", TEST_CRITERIA.indices()),
                                 indexedEventMessage("event-1", TEST_CRITERIA.indices()),
                                 indexedEventMessage("event-2", TEST_CRITERIA.indices()),
                                 indexedEventMessage("event-3", TEST_CRITERIA.indices()),
                                 indexedEventMessage("event-4", TEST_CRITERIA.indices()),
                                 indexedEventMessage("event-5", TEST_CRITERIA.indices()),
                                 indexedEventMessage("event-6", TEST_CRITERIA.indices()))
                   .thenCompose(AppendTransaction::commit)
                   .join();

        MessageStream<EventMessage<?>> result = testSubject.stream(StreamingCondition.startingFrom(new GlobalSequenceTrackingToken(
                                                                                             10))
                                                                                     .with(TEST_CRITERIA));

        try {
            assertTrue(result.next().isEmpty());
        } finally {
            result.close();
        }
    }

    @Test
    void sourcingEventsReturnsMatchingAggregateEvents() {
        testSubject.appendEvents(AppendCondition.none(),
                                 indexedEventMessage("event-0", TEST_CRITERIA.indices()),
                                 indexedEventMessage("event-1", TEST_CRITERIA.indices()),
                                 indexedEventMessage("event-2", OTHER_CRITERIA.indices()),
                                 indexedEventMessage("event-3", OTHER_CRITERIA.indices()),
                                 indexedEventMessage("event-4", OTHER_CRITERIA.indices()),
                                 indexedEventMessage("event-5", TEST_CRITERIA.indices()))
                   .thenCompose(AppendTransaction::commit)
                   .join();

        StepVerifier.create(
                            testSubject.source(SourcingCondition.conditionFor(TEST_CRITERIA)).asFlux())
                    .expectNextCount(3)
                    .verifyComplete();
    }

    @Test
    void transactionRejectedWithConflictingEventsInStore() {
        testSubject.appendEvents(AppendCondition.none(),
                                 indexedEventMessage("event-0", TEST_CRITERIA.indices()),
                                 indexedEventMessage("event-1", TEST_CRITERIA.indices()))
                   .thenApply(AppendTransaction::commit)
                   .join();


        CompletableFuture<Long> actual = testSubject.appendEvents(AppendCondition.withCriteria(TEST_CRITERIA),
                                                                  indexedEventMessage("event-2",
                                                                                      TEST_CRITERIA.indices()))
                                                    .thenCompose(AppendTransaction::commit);

        ExecutionException actualException = assertThrows(ExecutionException.class,
                                                          () -> actual.get(1, TimeUnit.SECONDS));
        assertInstanceOf(AppendConditionAssertionException.class, actualException.getCause());
    }

    @Test
    void transactionRejectedWhenConcurrentlyCreatedTransactionIsCommittedFirst() {
        AppendCondition appendCondition = AppendCondition.withCriteria(TEST_CRITERIA);

        var firstTx = testSubject.appendEvents(appendCondition,
                                               indexedEventMessage("event-0", TEST_CRITERIA.indices()));
        var secondTx = testSubject.appendEvents(appendCondition,
                                                indexedEventMessage("event-1", TEST_CRITERIA.indices()));

        CompletableFuture<Long> firstCommit = firstTx.thenCompose(AppendTransaction::commit);
        assertDoesNotThrow(() -> firstCommit.get(1, TimeUnit.SECONDS));

        CompletableFuture<Long> secondCommit = secondTx.thenCompose(AppendTransaction::commit);
        var actual = assertThrows(ExecutionException.class, () -> secondCommit.get(1, TimeUnit.SECONDS));
        assertInstanceOf(AppendConditionAssertionException.class, actual.getCause());
    }

    @Test
    void concurrentTransactionsForNonOverlappingIndicesBothCommit()
            throws ExecutionException, InterruptedException, TimeoutException {
        AppendCondition appendCondition1 = new DefaultAppendCondition(-1, TEST_CRITERIA);
        AppendCondition appendCondition2 = new DefaultAppendCondition(-1, OTHER_CRITERIA);

        AppendTransaction firstTx = testSubject.appendEvents(appendCondition1,
                                                             indexedEventMessage("event-0", TEST_CRITERIA.indices()))
                                               .get(1, TimeUnit.SECONDS);
        AppendTransaction secondTx = testSubject.appendEvents(appendCondition2,
                                                              indexedEventMessage("event-0",
                                                                                  TEST_CRITERIA.indices()))
                                                .get(1, TimeUnit.SECONDS);

        CompletableFuture<Long> firstCommit = firstTx.commit();
        CompletableFuture<Long> secondCommit = secondTx.commit();

        assertDoesNotThrow(() -> firstCommit.get(1, TimeUnit.SECONDS));
        assertDoesNotThrow(() -> secondCommit.get(1, TimeUnit.SECONDS));

        Long actualIndex = firstCommit.join();
        assertNotNull(actualIndex);
        assertEquals(actualIndex + 1, secondCommit.join());
    }

    @Test
    void eventsPublishedAreIncludedInOpenStreams() {
        testSubject.appendEvents(AppendCondition.none(),
                                 indexedEventMessage("event-0", TEST_CRITERIA.indices()),
                                 indexedEventMessage("event-1", TEST_CRITERIA.indices()))
                   .thenCompose(AppendTransaction::commit)
                   .join();

        MessageStream<EventMessage<?>> stream = testSubject.tailToken()
                                                           .thenApply(StreamingCondition::startingFrom)
                                                           .thenApply(testSubject::stream)
                                                           .join();

        AssertUtils.assertWithin(1, TimeUnit.SECONDS, () -> assertTrue(stream.next().isPresent()));
        AssertUtils.assertWithin(1, TimeUnit.SECONDS, () -> assertTrue(stream.next().isPresent()));

        testSubject.appendEvents(AppendCondition.none(),
                                 indexedEventMessage("event-3", TEST_CRITERIA.indices()))
                   .thenCompose(AppendTransaction::commit)
                   .join();

        AssertUtils.assertWithin(1,
                                 TimeUnit.SECONDS,
                                 () -> assertTrue(stream.hasNextAvailable()));

        assertEquals("event-3",
                     stream.next().map(e -> (String) e.message().getPayload())
                           .orElse("none"));
    }

    private static void assertEvent(EventMessage<?> actual,
                                    EventMessage<?> expected) {
        assertEquals(expected.getPayload(), actual.getPayload());
        assertEquals(expected.getIdentifier(), actual.getIdentifier());
        assertEquals(expected.getTimestamp().toEpochMilli(), actual.getTimestamp().toEpochMilli());
        assertEquals(expected.getMetaData(), actual.getMetaData());
    }

    protected static IndexedEventMessage<?> indexedEventMessage(String payload, Set<Index> indices) {
        return IndexedEventMessage.asIndexedEvent(GenericEventMessage.asEventMessage(payload),
                                                  indices);
    }
}