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
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.inmemory.AsyncInMemoryEventStorageEngine;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.AsyncUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Test class validating the {@link DefaultEventStoreTransaction}.
 *
 * @author Steven van Beelen
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class DefaultEventStoreTransactionTest {

    private static final String TEST_AGGREGATE_ID = "someId";
    public static final Tag AGGREGATE_ID_TAG = new Tag("aggregateIdentifier", TEST_AGGREGATE_ID);
    private static final EventCriteria TEST_AGGREGATE_CRITERIA =
            EventCriteria.forAnyEventType().withTags(AGGREGATE_ID_TAG);

    private final Context.ResourceKey<EventStoreTransaction> testEventStoreTransactionKey =
            Context.ResourceKey.withLabel("eventStoreTransaction");
    private final AsyncInMemoryEventStorageEngine eventStorageEngine = new AsyncInMemoryEventStorageEngine();

    @Nested
    class AppendEvent {

        @Test
        void sourcingConditionIsMappedToAppendCondition() {
            // given
            var eventCriteria = TEST_AGGREGATE_CRITERIA;
            var event1 = eventMessage(0);
            var event2 = eventMessage(1);
            var event3 = eventMessage(2);
            var sourcingCondition = SourcingCondition.conditionFor(eventCriteria);

            // when
            var beforeCommitEvents = new AtomicReference<MessageStream<? extends EventMessage<?>>>();
            var afterCommitEvents = new AtomicReference<MessageStream<? extends EventMessage<?>>>();
            var consistencyMarker = new AtomicReference<ConsistencyMarker>();
            var uow = new AsyncUnitOfWork();
            uow.runOnPreInvocation(context -> {
                   EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
                   beforeCommitEvents.set(transaction.source(sourcingCondition));
               })
               .runOnPostInvocation(context -> {
                   EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
                   transaction.appendEvent(event1);
                   transaction.appendEvent(event2);
                   transaction.appendEvent(event3);
               })
               // Event are given to the store in the PREPARE_COMMIT phase.
               // Hence, we retrieve the sourced set after that.
               .runOnAfterCommit(context -> {
                   EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
                   afterCommitEvents.set(transaction.source(sourcingCondition));

                   consistencyMarker.set(transaction.appendPosition());
               });
            awaitCompletion(uow.execute());

            // then
            assertNull(beforeCommitEvents.get().first().asCompletableFuture().join());
            StepVerifier.create(afterCommitEvents.get().asFlux())
                        .assertNext(entry -> assertTagsPositionAndEvent(entry, eventCriteria, 0, event1))
                        .assertNext(entry -> assertTagsPositionAndEvent(entry, eventCriteria, 1, event2))
                        .assertNext(entry -> assertTagsPositionAndEvent(entry, eventCriteria, 2, event3))
                        .verifyComplete();
            assertEquals(
                    GlobalIndexConsistencyMarker.position(new GlobalIndexConsistencyMarker(2)),
                    GlobalIndexConsistencyMarker.position(consistencyMarker.get())
            );
        }

        @Test
        void sourceReturnsOnlyCommitedEvents() {
            // given
            var event1 = eventMessage(0);
            var event2 = eventMessage(1);
            var sourcingCondition = SourcingCondition.conditionFor(TEST_AGGREGATE_CRITERIA);
            var beforeCommitEvents = new AtomicReference<MessageStream<? extends EventMessage<?>>>();
            var afterCommitEvents = new AtomicReference<MessageStream<? extends EventMessage<?>>>();

            // when
            var uow = new AsyncUnitOfWork();
            uow.runOnPreInvocation(context -> {
                   EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
                   transaction.appendEvent(event1);
                   transaction.appendEvent(event2);
                   beforeCommitEvents.set(transaction.source(sourcingCondition));
               })
               .runOnAfterCommit(context -> {
                   EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
                   afterCommitEvents.set(transaction.source(sourcingCondition));
               });
            awaitCompletion(uow.execute());

            // then: before commit - no events should be visible
            StepVerifier.create(beforeCommitEvents.get().asFlux())
                        .verifyComplete();

            // then: after commit - both events should be visible
            StepVerifier.create(afterCommitEvents.get().asFlux())
                        .assertNext(entry -> assertTagsPositionAndEvent(entry, TEST_AGGREGATE_CRITERIA, 0, event1))
                        .assertNext(entry -> assertTagsPositionAndEvent(entry, TEST_AGGREGATE_CRITERIA, 1, event2))
                        .verifyComplete();
        }

        @Test
        void appendCommitsOfNonExistentTagWhenOfTwoNonOverlappingTagsOneYieldedNoEvents() {
            Tag nonExistentTag = new Tag("nonExistent", "tag");
            EventCriteria nonExistingCriteria = EventCriteria.forAnyEventType().withTags(nonExistentTag);
            Tag existentTag = new Tag("existent", "tag");
            EventCriteria existingCriteria = EventCriteria.forAnyEventType().withTags(existentTag);

            appendEventForTag(existentTag);
            testCanCommitTag(nonExistingCriteria, existingCriteria, nonExistentTag);
        }

        @Test
        void appendCommitsOfExistentTagWhenOfTwoNonOverlappingTagsOneYieldedNoEvents() {
            Tag nonExistentTag = new Tag("nonExistent", "tag");
            EventCriteria nonExistingCriteria = EventCriteria.forAnyEventType().withTags(nonExistentTag);
            Tag existentTag = new Tag("existent", "tag");
            EventCriteria existingCriteria = EventCriteria.forAnyEventType().withTags(existentTag);

            appendEventForTag(existentTag);
            testCanCommitTag(nonExistingCriteria, existingCriteria, existentTag);
        }

        private ConsistencyMarker appendEventForTag(Tag tag) {
            return eventStorageEngine.appendEvents(AppendCondition.none(),
                                                   new GenericTaggedEventMessage<>(
                                                           new GenericEventMessage<>(new MessageType(String.class),
                                                                                     "my payload"),
                                                           Set.of(tag)
                                                   )).join().commit().join();
        }

        private void testCanCommitTag(EventCriteria nonExistingCriteria, EventCriteria existingCriteria,
                                      Tag tagToCommitOn) {

            var uow = new AsyncUnitOfWork();
            awaitCompletion(uow.executeWithResult(context -> {
                // Transaction which will result in even being appended for non-existent tag
                EventStoreTransaction transaction = defaultEventStoreTransactionFor(context,
                                                                                    m -> Set.of(tagToCommitOn));

                // Read both streams, with non-existing empty
                transaction.source(SourcingCondition.conditionFor(nonExistingCriteria)).asFlux().blockLast();
                transaction.source(SourcingCondition.conditionFor(existingCriteria)).asFlux().blockLast();

                transaction.appendEvent(new GenericEventMessage<>(new MessageType(String.class), "my payload"));

                return MessageStream.empty().asCompletableFuture();
            }));
        }
    }

    @Nested
    class OnAppendCallbacks {

        @Test
        void appendEventNotifiesRegisteredCallbacks() {
            // given
            var event1 = eventMessage(0);
            var onAppendCallback1 = new ArrayList<EventMessage<?>>();
            var onAppendCallback2 = new ArrayList<EventMessage<?>>();

            // when
            var uow = new AsyncUnitOfWork();
            uow.runOnPreInvocation(context -> {
                EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
                transaction.onAppend(onAppendCallback1::add);
                transaction.onAppend(onAppendCallback2::add);
                transaction.appendEvent(event1);
            });
            awaitCompletion(uow.execute());

            // then
            assertEquals(1, onAppendCallback1.size());
            assertEquals(1, onAppendCallback2.size());
            assertEquals(event1.getIdentifier(), onAppendCallback1.getFirst().getIdentifier());
            assertEquals(event1.getIdentifier(), onAppendCallback2.getFirst().getIdentifier());
        }

        @Test
        void appendEventNotifierRegisteredCallbacksEvenWhenTransactionRollback() {
            // given
            var event1 = eventMessage(0);
            var callbackInvoked = new AtomicBoolean(false);

            // when
            var uow = new AsyncUnitOfWork();
            uow.runOnPreInvocation(context -> {
                EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
                transaction.onAppend(event -> callbackInvoked.set(true));
                transaction.appendEvent(event1);
            }).runOnPrepareCommit(context -> {
                throw new RuntimeException("Simulated failure during prepare commit");
            });

            // then
            assertThrows(RuntimeException.class, () -> awaitCompletion(uow.execute()));
            assertTrue(callbackInvoked.get());
        }
    }

    @Nested
    class AppendPosition {

        @Test
        void appendPositionReturnsMinusOneWhenNoEventsAppended() {
            // when
            var result = new AtomicReference<ConsistencyMarker>();
            var uow = new AsyncUnitOfWork();
            uow.runOnAfterCommit(context -> {
                EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
                result.set(transaction.appendPosition());
            });
            awaitCompletion(uow.execute());

            // then
            assertEquals(ConsistencyMarker.ORIGIN, result.get());
        }

        @Test
        void appendPositionReturnsConsistencyMarkerOfTheResultWhenEventsAppended() {
            // when
            var result = new AtomicReference<ConsistencyMarker>();
            var uow = new AsyncUnitOfWork();
            uow.runOnPreInvocation(context -> {
                EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
                transaction.appendEvent(eventMessage(0));
                transaction.appendEvent(eventMessage(1));
                transaction.appendEvent(eventMessage(2));
                transaction.appendEvent(eventMessage(3));
            }).runOnAfterCommit(context -> {
                EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
                result.set(transaction.appendPosition());
            });
            awaitCompletion(uow.execute());

            // then
            assertEquals(
                    GlobalIndexConsistencyMarker.position(new GlobalIndexConsistencyMarker(3)),
                    GlobalIndexConsistencyMarker.position(result.get())
            );
        }
    }

    @Nested
    class TransactionRollback {

        @Test
        void eventsAreNotAppendedWhenTransactionFails() {
            // given
            var event1 = eventMessage(0);
            var event2 = eventMessage(1);
            var sourcingCondition = SourcingCondition.conditionFor(TEST_AGGREGATE_CRITERIA);

            // when
            var uow = new AsyncUnitOfWork();
            uow.runOnPreInvocation(context -> {
                   EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
                   transaction.appendEvent(event1);
                   transaction.appendEvent(event2);
               })
               .runOnPrepareCommit(context -> {
                   throw new IllegalStateException("Simulated failure during prepare commit");
               });

            // then
            assertThrows(CompletionException.class, () -> awaitException(uow.execute()));

            var verificationUow = new AsyncUnitOfWork();
            var eventsAfterRollback = new AtomicReference<MessageStream<? extends EventMessage<?>>>();
            verificationUow.runOnPreInvocation(context -> {
                EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
                eventsAfterRollback.set(transaction.source(sourcingCondition));
            });
            awaitCompletion(verificationUow.execute());

            StepVerifier.create(eventsAfterRollback.get().asFlux())
                        .verifyComplete();
        }

        @Test
        void errorPropagationIsHandledByOnErrorPhase() {
            // given
            var event1 = eventMessage(0);
            var capturedError = new AtomicReference<Throwable>();
            var onCommitExecuted = new AtomicBoolean(false);
            var onAfterCommitExecuted = new AtomicBoolean(false);
            var onPostInvocationExecuted = new AtomicBoolean(false);

            // when
            var uow = new AsyncUnitOfWork();
            uow.onError((context, phase, error) -> capturedError.set(error)).runOnPreInvocation(context -> {
                   EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
                   transaction.appendEvent(event1);
               })
               .runOnPrepareCommit(context -> {
                   throw new RuntimeException("Simulated failure during prepare commit");
               }).runOnCommit(context -> onCommitExecuted.set(true))
               .runOnAfterCommit(context -> onAfterCommitExecuted.set(true))
               .runOnPostInvocation(context -> onPostInvocationExecuted.set(true));

            RuntimeException exception = assertThrows(CompletionException.class, () -> awaitException(uow.execute()));

            // then
            assertNotNull(capturedError.get());
            assertEquals("Simulated failure during prepare commit", capturedError.get().getMessage());
            assertEquals(exception.getCause(), capturedError.get());
            assertFalse(onCommitExecuted.get(), "Commit step should not execute after an error");
            assertFalse(onAfterCommitExecuted.get(), "After commit step should not execute after an error");
            assertTrue(onPostInvocationExecuted.get(), "Post invocation step should be executed after an error");
        }
    }

    // TODO - Discuss: @Steven - Perfect candidate to move to a commons test utils module?
    private static <R> R awaitCompletion(CompletableFuture<R> completion) {
        await().atMost(Duration.ofMillis(500)).pollDelay(Duration.ofMillis(25)).untilAsserted(() -> assertFalse(
                completion.isCompletedExceptionally(),
                () -> completion.exceptionNow().toString()));
        return completion.join();
    }

    private static <R> R awaitException(CompletableFuture<R> completion) {
        await().atMost(Duration.ofMillis(500)).pollDelay(Duration.ofMillis(25)).untilAsserted(() -> assertTrue(
                completion.isCompletedExceptionally(),
                "Expected exception but none occurred"));
        return completion.join();
    }


    private EventStoreTransaction defaultEventStoreTransactionFor(ProcessingContext processingContext) {
        return defaultEventStoreTransactionFor(processingContext, m -> Set.of(AGGREGATE_ID_TAG));
    }

    private EventStoreTransaction defaultEventStoreTransactionFor(ProcessingContext processingContext,
                                                                  TagResolver tagResolver) {
        return processingContext.computeResourceIfAbsent(testEventStoreTransactionKey,
                                                         () -> new DefaultEventStoreTransaction(
                                                                 eventStorageEngine,
                                                                 processingContext,
                                                                 tagResolver
                                                         )
        );
    }

    protected static EventMessage<?> eventMessage(int seq) {
        return new GenericEventMessage<>(new MessageType("test", "event", "0.0.1"), "event-" + seq);
    }

    private static void assertTagsPositionAndEvent(MessageStream.Entry<? extends EventMessage<?>> actual,
                                                   EventCriteria expectedCriteria, int expectedPosition,
                                                   EventMessage<?> expectedEvent) {
        Optional<Set<Tag>> optionalTags = Tag.fromContext(actual);
        assertTrue(optionalTags.isPresent());
        Set<Tag> actualTags = optionalTags.get();
        assertTrue(actualTags.containsAll(expectedCriteria.tags()));
        assertPositionAndEvent(actual, expectedPosition, expectedEvent);
    }

    private static void assertPositionAndEvent(MessageStream.Entry<? extends EventMessage<?>> actual,
                                               long expectedPosition, EventMessage<?> expectedEvent) {
        Optional<TrackingToken> actualToken = TrackingToken.fromContext(actual);
        assertTrue(actualToken.isPresent());
        OptionalLong actualPosition = actualToken.get().position();
        assertTrue(actualPosition.isPresent());
        assertEquals(expectedPosition, actualPosition.getAsLong());
        assertEvent(actual.message(), expectedEvent);
    }

    private static void assertEvent(EventMessage<?> actual, EventMessage<?> expected) {
        assertEquals(expected.getIdentifier(), actual.getIdentifier());
        assertEquals(expected.getPayload(), actual.getPayload());
        assertEquals(expected.getTimestamp(), actual.getTimestamp());
        assertEquals(expected.getMetaData(), actual.getMetaData());
    }
}