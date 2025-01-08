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
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.inmemory.AsyncInMemoryEventStorageEngine;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.AsyncUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

class DefaultEventStoreTransactionTest {

    protected static final String TEST_AGGREGATE_ID = "someId";
    protected static final EventCriteria TEST_AGGREGATE_CRITERIA =
            EventCriteria.hasTag(new Tag("aggregateIdentifier", TEST_AGGREGATE_ID));

    private final Context.ResourceKey<EventStoreTransaction> testEventStoreTransactionKey = Context.ResourceKey.withLabel(
            "eventStoreTransaction");

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    // TODO - Discuss: @Steven - Perfect candidate to move to a commons test utils module?
    private static <R> R awaitCompletion(CompletableFuture<R> completion) {
        await().atMost(Duration.ofMillis(500))
               .pollDelay(Duration.ofMillis(25))
               .untilAsserted(() -> assertFalse(completion.isCompletedExceptionally(),
                                                () -> completion.exceptionNow().toString()));
        return completion.join();
    }

    private EventStoreTransaction defaultEventStoreTransactionFor(ProcessingContext processingContext) {
        return processingContext.computeResourceIfAbsent(
                testEventStoreTransactionKey,
                () -> new DefaultEventStoreTransaction(new AsyncInMemoryEventStorageEngine(), processingContext)
        );
    }

    @Test
    void appendEvent() {
    }

    @Test
    void onAppend() {
    }

    @Test
    void appendPosition() {
    }

    // TODO - Discuss: @Steven - Perfect candidate to move to a commons test utils module?
    private static EventMessage<?> eventMessage(int seq) {
        return EventTestUtils.asEventMessage("Event[" + seq + "]");
    }

    private static void assertTagsPositionAndEvent(MessageStream.Entry<? extends EventMessage<?>> actual,
                                                   EventCriteria expectedCriteria,
                                                   int expectedPosition,
                                                   EventMessage<?> expectedEvent) {
        Optional<Set<Tag>> optionalTags = Tag.fromContext(actual);
        assertTrue(optionalTags.isPresent());
        Set<Tag> actualTags = optionalTags.get();
        assertTrue(actualTags.containsAll(expectedCriteria.tags()));
        assertPositionAndEvent(actual, expectedPosition, expectedEvent);
    }

    private static void assertPositionAndEvent(MessageStream.Entry<? extends EventMessage<?>> actual,
                                               long expectedPosition,
                                               EventMessage<?> expectedEvent) {
        Optional<TrackingToken> actualToken = TrackingToken.fromContext(actual);
        assertTrue(actualToken.isPresent());
        OptionalLong actualPosition = actualToken.get().position();
        assertTrue(actualPosition.isPresent());
        assertEquals(expectedPosition, actualPosition.getAsLong());
        assertEvent(actual.message(), expectedEvent);
    }

    private static void assertEvent(EventMessage<?> actual,
                                    EventMessage<?> expected) {
        assertEquals(expected.getIdentifier(), actual.getIdentifier());
        assertEquals(expected.getPayload(), actual.getPayload());
        assertEquals(expected.getTimestamp(), actual.getTimestamp());
        assertEquals(expected.getMetaData(), actual.getMetaData());
    }

    @Test
    void sourcingConditionIsMappedToAppendCondition() {
        // given
        var expectedCriteria = TEST_AGGREGATE_CRITERIA;
        var expectedEventOne = eventMessage(0);
        var expectedEventTwo = eventMessage(1);
        var expectedEventThree = eventMessage(2);
        var sourcingCondition = SourcingCondition.conditionFor(expectedCriteria);

        AtomicReference<MessageStream<? extends EventMessage<?>>> initialStreamReference = new AtomicReference<>();
        AtomicReference<MessageStream<? extends EventMessage<?>>> finalStreamReference = new AtomicReference<>();

        // when
        var uow = new AsyncUnitOfWork();
        uow.runOnPreInvocation(context -> {
               EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
               initialStreamReference.set(transaction.source(sourcingCondition, context));
           })
           .runOnPostInvocation(context -> {
               EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
               transaction.appendEvent(expectedEventOne);
               transaction.appendEvent(expectedEventTwo);
               transaction.appendEvent(expectedEventThree);
           })
           // Event are given to the store in the PREPARE_COMMIT phase.
           // Hence, we retrieve the sourced set after that.
           .runOnAfterCommit(context -> {
               EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
               finalStreamReference.set(transaction.source(sourcingCondition, context));
           });
        awaitCompletion(uow.execute());

        // then
        assertNull(initialStreamReference.get().firstAsCompletableFuture().join());

        StepVerifier.create(finalStreamReference.get().asFlux())
                    .assertNext(entry -> assertTagsPositionAndEvent(entry, expectedCriteria, 0, expectedEventOne))
                    .assertNext(entry -> assertTagsPositionAndEvent(entry, expectedCriteria, 1, expectedEventTwo))
                    .assertNext(entry -> assertTagsPositionAndEvent(entry, expectedCriteria, 2, expectedEventThree))
                    .verifyComplete();
    }
}