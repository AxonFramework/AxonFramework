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

package org.axonframework.test.af5;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.eventstore.SimpleEventStore;
import org.axonframework.eventsourcing.eventstore.TagResolver;
import org.axonframework.eventsourcing.eventstore.inmemory.AsyncInMemoryEventStorageEngine;
import org.axonframework.messaging.unitofwork.AsyncUnitOfWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

class RecordingAsyncEventStoreTest {

    private static final String TEST_CONTEXT = "test-context";

    private AsyncInMemoryEventStorageEngine storageEngine;
    private SimpleEventStore delegateEventStore;
    private RecordingAsyncEventStore testSubject;
    private TagResolver mockTagResolver;

    @BeforeEach
    void setUp() {
        // given
        storageEngine = new AsyncInMemoryEventStorageEngine();
        mockTagResolver = mock(TagResolver.class);
        delegateEventStore = new SimpleEventStore(storageEngine, TEST_CONTEXT, mockTagResolver);
        testSubject = new RecordingAsyncEventStore(delegateEventStore);
    }

    @Nested
    class EventRecording {

        @Test
        void shouldRecordEventsWhenPublished() {
            // given
            EventMessage<String> testEvent1 = eventMessage("test-event-1");
            EventMessage<String> testEvent2 = eventMessage("test-event-2");
            List<EventMessage<?>> events = List.of(testEvent1, testEvent2);

            // when
            var uow = new AsyncUnitOfWork();
            uow.runOnPreInvocation(context ->
                                           testSubject.publish(context, TEST_CONTEXT, events));

            awaitCompletion(uow.execute());

            // then
            List<EventMessage<?>> recordedEvents = testSubject.recorded();
            assertEquals(2, recordedEvents.size());
            assertEquals(testEvent1.getPayload(), recordedEvents.get(0).getPayload());
            assertEquals(testEvent2.getPayload(), recordedEvents.get(1).getPayload());
        }

        @Test
        void shouldRecordEventsWhenAppendedThroughTransaction() {
            // given
            EventMessage<String> testEvent = eventMessage("test-event");

            // when
            var uow = new AsyncUnitOfWork();
            uow.runOnInvocation(context -> {
                var transaction = testSubject.transaction(context, TEST_CONTEXT);
                transaction.appendEvent(testEvent);
            });

            awaitCompletion(uow.execute());

            // then
            List<EventMessage<?>> recordedEvents = testSubject.recorded();
            assertEquals(1, recordedEvents.size());
            assertEquals(testEvent.getPayload(), recordedEvents.get(0).getPayload());
        }

        @Test
        void shouldPreserveExistingRecordedEvents() {
            // given
            EventMessage<String> existingEvent = eventMessage("existing-event");
            List<EventMessage<?>> existingEvents = new ArrayList<>();
            existingEvents.add(existingEvent);
            testSubject = new RecordingAsyncEventStore(delegateEventStore, existingEvents);

            EventMessage<String> newEvent = eventMessage("new-event");

            // when
            var uow = new AsyncUnitOfWork();
            uow.runOnInvocation(context -> {
                var transaction = testSubject.transaction(context, TEST_CONTEXT);
                transaction.appendEvent(newEvent);
            });

            awaitCompletion(uow.execute());

            // then
            List<EventMessage<?>> recordedEvents = testSubject.recorded();
            assertEquals(2, recordedEvents.size());
            assertEquals(existingEvent.getPayload(), recordedEvents.get(0).getPayload());
            assertEquals(newEvent.getPayload(), recordedEvents.get(1).getPayload());
        }
    }

    @Nested
    class ImmutabilityAndRollback {

        @Test
        void shouldStillRecordEventsWhenUnitOfWorkRollsBack() {
            // given
            EventMessage<String> testEvent = eventMessage("test-event");

            // when
            var uow = new AsyncUnitOfWork();
            uow.runOnInvocation(context -> {
                var transaction = testSubject.transaction(context, TEST_CONTEXT);
                transaction.appendEvent(testEvent);
            }).runOnPrepareCommit(context -> {
                throw new RuntimeException("Simulated failure during prepare commit");
            });

            assertThrows(RuntimeException.class, () -> awaitException(uow.execute()));

            // then
            List<EventMessage<?>> recordedEvents = testSubject.recorded();
            assertEquals(1, recordedEvents.size(), "Should record events even when transaction rolls back");
            assertEquals(testEvent.getPayload(), recordedEvents.get(0).getPayload());
        }
    }

    @Nested
    class MultipleTransactions {

        @Test
        void shouldRecordMultipleEventsAcrossMultipleTransactions() {
            // given
            EventMessage<String> firstEvent = eventMessage("first-event");
            EventMessage<String> secondEvent = eventMessage("second-event");
            EventMessage<String> thirdEvent = eventMessage("third-event");

            // when
            // First transaction
            var firstUow = new AsyncUnitOfWork();
            AtomicReference<Object> firstTransactionRef = new AtomicReference<>();

            firstUow.runOnInvocation(context -> {
                var transaction = testSubject.transaction(context, TEST_CONTEXT);
                firstTransactionRef.set(transaction);
                transaction.appendEvent(firstEvent);
            });

            awaitCompletion(firstUow.execute());

            // Second transaction
            var secondUow = new AsyncUnitOfWork();
            secondUow.runOnInvocation(context -> {
                var transaction = testSubject.transaction(context, TEST_CONTEXT);
                transaction.appendEvent(secondEvent);
                transaction.appendEvent(thirdEvent);
            });

            awaitCompletion(secondUow.execute());

            // then
            List<EventMessage<?>> recordedEvents = testSubject.recorded();
            assertEquals(3, recordedEvents.size());
            assertEquals(firstEvent.getPayload(), recordedEvents.get(0).getPayload());
            assertEquals(secondEvent.getPayload(), recordedEvents.get(1).getPayload());
            assertEquals(thirdEvent.getPayload(), recordedEvents.get(2).getPayload());
        }
    }

    protected static EventMessage<String> eventMessage(String payload) {
        return new GenericEventMessage<>(new org.axonframework.messaging.MessageType("test", "event", "0.0.1"),
                                         payload);
    }

    private static <R> R awaitCompletion(CompletableFuture<R> completion) {
        await().atMost(Duration.ofMillis(500))
               .pollDelay(Duration.ofMillis(25))
               .untilAsserted(() -> assertFalse(
                       completion.isCompletedExceptionally(),
                       () -> completion.exceptionNow().toString()));
        return completion.join();
    }

    private static <R> R awaitException(CompletableFuture<R> completion) {
        await().atMost(Duration.ofMillis(500))
               .pollDelay(Duration.ofMillis(25))
               .untilAsserted(() -> assertTrue(
                       completion.isCompletedExceptionally(),
                       "Expected exception but none occurred"));
        return completion.join();
    }
}