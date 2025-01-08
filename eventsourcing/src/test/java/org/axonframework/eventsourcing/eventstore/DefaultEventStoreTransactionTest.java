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
import java.util.ArrayList;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
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
            var preCommitEvents = new AtomicReference<MessageStream<? extends EventMessage<?>>>();
            var postCommitEvents = new AtomicReference<MessageStream<? extends EventMessage<?>>>();
            var consistencyMarker = new AtomicLong();
            var uow = new AsyncUnitOfWork();
            uow.runOnPreInvocation(context -> {
                   EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
                   preCommitEvents.set(transaction.source(sourcingCondition, context));
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
                   postCommitEvents.set(transaction.source(sourcingCondition, context));

                   consistencyMarker.set(transaction.appendPosition(context));
               });
            awaitCompletion(uow.execute());

            // then
            assertNull(preCommitEvents.get().firstAsCompletableFuture().join());
            StepVerifier.create(postCommitEvents.get().asFlux())
                        .assertNext(entry -> assertTagsPositionAndEvent(entry, eventCriteria, 0, event1))
                        .assertNext(entry -> assertTagsPositionAndEvent(entry, eventCriteria, 1, event2))
                        .assertNext(entry -> assertTagsPositionAndEvent(entry, eventCriteria, 2, event3))
                        .verifyComplete();
            assertEquals(consistencyMarker.get(), 2);
        }

        @Test
        void eventsAreOnlyVisibleAfterCommit() {
            // given
            var event1 = eventMessage(0);
            var event2 = eventMessage(1);
            var sourcingCondition = SourcingCondition.conditionFor(TEST_AGGREGATE_CRITERIA);
            var preCommitEvents = new AtomicReference<MessageStream<? extends EventMessage<?>>>();
            var postCommitEvents = new AtomicReference<MessageStream<? extends EventMessage<?>>>();

            // when
            var uow = new AsyncUnitOfWork();
            uow.runOnPreInvocation(context -> {
                   EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
                   transaction.appendEvent(event1);
                   transaction.appendEvent(event2);
                   // Check events before commit
                   preCommitEvents.set(transaction.source(sourcingCondition, context));
               })
               .runOnAfterCommit(context -> {
                   EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
                   // Check events after commit
                   postCommitEvents.set(transaction.source(sourcingCondition, context));
               });
            awaitCompletion(uow.execute());

            // then
            // Before commit - no events should be visible
            StepVerifier.create(preCommitEvents.get().asFlux())
                        .verifyComplete();

            // After commit - both events should be visible
            StepVerifier.create(postCommitEvents.get().asFlux())
                        .assertNext(entry -> assertTagsPositionAndEvent(entry, TEST_AGGREGATE_CRITERIA, 0, event1))
                        .assertNext(entry -> assertTagsPositionAndEvent(entry, TEST_AGGREGATE_CRITERIA, 1, event2))
                        .verifyComplete();
        }

    }

    @Nested
    class OnAppendCallbacks {

        @Test
        void appendEventNotifiesRegisteredCallbacks() {
            // given
            var event1 = eventMessage(0);
            var callbackEvent = new AtomicReference<EventMessage<?>>();

            // when
            var uow = new AsyncUnitOfWork();
            uow.runOnPreInvocation(context -> {
                EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
                transaction.onAppend(callbackEvent::set);
                transaction.appendEvent(event1);
            });
            awaitCompletion(uow.execute());

            // then
            assertNotNull(callbackEvent.get());
            assertEquals(event1.getIdentifier(), callbackEvent.get().getIdentifier());
        }

        @Test
        void multipleCallbacksAreNotifiedOnAppend() {
            // given
            var event1 = eventMessage(0);
            var callback1Events = new ArrayList<EventMessage<?>>();
            var callback2Events = new ArrayList<EventMessage<?>>();

            // when
            var uow = new AsyncUnitOfWork();
            uow.runOnPreInvocation(context -> {
                EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
                transaction.onAppend(callback1Events::add);
                transaction.onAppend(callback2Events::add);
                transaction.appendEvent(event1);
            });
            awaitCompletion(uow.execute());

            // then
            assertEquals(1, callback1Events.size());
            assertEquals(1, callback2Events.size());
            assertEquals(event1.getIdentifier(), callback1Events.getFirst().getIdentifier());
            assertEquals(event1.getIdentifier(), callback2Events.getFirst().getIdentifier());
        }
    }

    @Nested
    class AppendPosition {

        @Test
        void appendPositionReturnsMinusOneWhenNoEventsAppended() {
            // when
            var result = new AtomicLong();
            var uow = new AsyncUnitOfWork();
            uow.runOnPreInvocation(context -> {
                EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
                result.set(transaction.appendPosition(context));
            });
            awaitCompletion(uow.execute());

            // then
            assertEquals(-1L, result.get());
        }

        // todo: test changing
    }

    @Nested
    class TransactionRollback {

        @Test
        void eventsAreNotAppendedWhenTransactionFails() {
            // given
            var event1 = eventMessage(0);
            var event2 = eventMessage(1);
            var sourcingCondition = SourcingCondition.conditionFor(TEST_AGGREGATE_CRITERIA);
            var postFailureEvents = new AtomicReference<MessageStream<? extends EventMessage<?>>>();

            // when
            var uow = new AsyncUnitOfWork();
            uow.runOnPreInvocation(context -> {
                   EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
                   transaction.appendEvent(event1);
                   transaction.appendEvent(event2);
               })
               .runOnPrepareCommit(context -> {
                   throw new RuntimeException("Simulated failure during prepare commit");
               })
               .runOnAfterCommit(context -> {
                   // This shouldn't be reached, but if it is, we'll catch it in our assertions
                   EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
                   postFailureEvents.set(transaction.source(sourcingCondition, context));
               });

            // then
            assertThrows(RuntimeException.class, () -> awaitCompletion(uow.execute()));

            // Create a new transaction to verify no events were persisted
            var verificationUow = new AsyncUnitOfWork();
            var finalEvents = new AtomicReference<MessageStream<? extends EventMessage<?>>>();
            verificationUow.runOnPreInvocation(context -> {
                EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
                finalEvents.set(transaction.source(sourcingCondition, context));
            });
            awaitCompletion(verificationUow.execute());

            // Verify no events were stored
            StepVerifier.create(finalEvents.get().asFlux())
                        .verifyComplete();
        }

        @Test
        void rollbackPreventsEventPersistence() {
            // given
            var event1 = eventMessage(0);
            var event2 = eventMessage(1);
            var sourcingCondition = SourcingCondition.conditionFor(TEST_AGGREGATE_CRITERIA);
            var eventsAfterRollback = new AtomicReference<MessageStream<? extends EventMessage<?>>>();

            // when
            var uow = new AsyncUnitOfWork();
            uow.runOnPreInvocation(context -> {
                   EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
                   transaction.appendEvent(event1);
                   transaction.appendEvent(event2);
               })
               .runOnPrepareCommit(context -> {
                   throw new RuntimeException("Simulated failure triggering rollback");
               });

            assertThrows(RuntimeException.class, () -> awaitCompletion(uow.execute()));

            // Create a new transaction to verify the rollback effect
            var verificationUow = new AsyncUnitOfWork();
            verificationUow.runOnPreInvocation(context -> {
                EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
                eventsAfterRollback.set(transaction.source(sourcingCondition, context));
            });
            awaitCompletion(verificationUow.execute());

            // then
            StepVerifier.create(eventsAfterRollback.get().asFlux())
                        .verifyComplete(); // No events should be visible
        }

    }

//    @Test
//    void callbacksAreNotExecutedOnRollback() {
//        // given
//        var event1 = eventMessage(0);
//        var callbackInvoked = new AtomicBoolean(false);
//
//        // when
//        var uow = new AsyncUnitOfWork();
//        uow.runOnPreInvocation(context -> {
//               EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
//               transaction.onAppend(event -> callbackInvoked.set(true)); // Register callback
//               transaction.appendEvent(event1); // Append an event
//           })
//           .runOnPrepareCommit(context -> {
//               throw new RuntimeException("Simulated failure during prepare commit");
//           }); // Simulate failure to trigger rollback
//
//        // then
//        assertThrows(RuntimeException.class, () -> awaitCompletion(uow.execute()));
//
//        // Verify that the callback was not invoked
//        assertFalse(callbackInvoked.get(), "Callback should not be executed during rollback");
//    }

//    @Test
//    void errorPropagationIsHandledByOnErrorPhase() {
//        // given
//        var event1 = eventMessage(0);
//        var capturedError = new AtomicReference<Throwable>();
//        var postErrorCheck = new AtomicBoolean(false);
//
//        // when
//        var uow = new AsyncUnitOfWork();
//        uow.onError((context, phase, error) -> capturedError.set(error)) // Capture errors
//           .runOnPreInvocation(context -> {
//               EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
//               transaction.appendEvent(event1);
//           })
//           .runOnPrepareCommit(context -> {
//               throw new RuntimeException("Simulated failure during prepare commit");
//           })
//           .runOnPostInvocation(context -> postErrorCheck.set(true)); // Should not execute
//
//        // then
//        RuntimeException exception = assertThrows(CompletionException.class, () -> awaitCompletion(uow.execute()));
//
//        // Assert that the error was captured
//        assertNotNull(capturedError.get());
//        assertEquals("Simulated failure during prepare commit", capturedError.get().getMessage());
//
//        // Assert the same exception is propagated
//        assertEquals(exception, capturedError.get());
//
//        // Ensure post-invocation steps are not executed
//        assertFalse(postErrorCheck.get(), "Post invocation steps should not execute after an error");
//    }

//    @Test
//    void eventsRemainVisibleAfterSuccessfulCommitEvenIfLaterTransactionFails() {
//        // given
//        var event1 = eventMessage(0);
//        var event2 = eventMessage(1);
//        var sourcingCondition = SourcingCondition.conditionFor(TEST_AGGREGATE_CRITERIA);
//        var finalEvents = new AtomicReference<MessageStream<? extends EventMessage<?>>>();
//
//        // First transaction - should succeed
//        var successfulUow = new AsyncUnitOfWork();
//        successfulUow.runOnPreInvocation(context -> {
//            EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
//            transaction.appendEvent(event1);
//        });
//        awaitCompletion(successfulUow.execute());
//
//        // Second transaction - should fail
//        var failingUow = new AsyncUnitOfWork();
//        failingUow.runOnPreInvocation(context -> {
//                      EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
//                      transaction.appendEvent(event2);
//                  })
//                  .runOnPrepareCommit(context -> {
//                      throw new RuntimeException("Simulated failure");
//                  });
//        assertThrows(RuntimeException.class, () -> awaitCompletion(failingUow.execute()));
//
//        // Verify final state
//        var verificationUow = new AsyncUnitOfWork();
//        verificationUow.runOnPreInvocation(context -> {
//            EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
//            finalEvents.set(transaction.source(sourcingCondition, context));
//        });
//        awaitCompletion(verificationUow.execute());
//
//        // then
//        StepVerifier.create(finalEvents.get().asFlux())
//                    .assertNext(entry -> assertTagsPositionAndEvent(entry, TEST_AGGREGATE_CRITERIA, 0, event1))
//                    .verifyComplete();
//    }

//    @Test
//    void multipleSourcingOperationsCombineConditions() {
//        // given
//        var eventCriteria1 = EventCriteria.hasTag(new Tag("criteria1", "value1"));
//        var eventCriteria2 = EventCriteria.hasTag(new Tag("criteria2", "value2"));
//        var event1 = eventMessage(0);
//        var sourcingCondition1 = SourcingCondition.conditionFor(eventCriteria1);
//        var sourcingCondition2 = SourcingCondition.conditionFor(eventCriteria2);
//
//        // when
//        var uow = new AsyncUnitOfWork();
//        uow.runOnPreInvocation(context -> {
//            EventStoreTransaction transaction = defaultEventStoreTransactionFor(context);
//            transaction.source(sourcingCondition1, context);
//            transaction.source(sourcingCondition2, context);
//            transaction.appendEvent(event1);
//        });
//        awaitCompletion(uow.execute());
//
//        // Verify the event has both tags
//        StepVerifier.create(storageEngine.source(sourcingCondition1).asFlux())
//                    .assertNext(entry -> {
//                        Optional<Set<Tag>> tags = Tag.fromContext(entry);
//                        assertTrue(tags.isPresent());
//                        assertTrue(tags.get().containsAll(eventCriteria1.tags()));
//                        assertTrue(tags.get().containsAll(eventCriteria2.tags()));
//                    })
//                    .verifyComplete();
//    }
}