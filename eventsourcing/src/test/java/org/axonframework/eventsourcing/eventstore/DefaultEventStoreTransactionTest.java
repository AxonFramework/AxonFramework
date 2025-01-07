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
import org.axonframework.eventsourcing.eventstore.inmemory.AsyncInMemoryEventStorageEngine;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.unitofwork.AsyncUnitOfWork;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

import static org.axonframework.eventsourcing.eventstore.EventCriteria.anyEvent;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

class DefaultEventStoreTransactionTest {

    public static final Context.ResourceKey<Object> APPEND_CONDITION_KEY = Context.ResourceKey.withLabel(
            "appendCondition");
    private AsyncEventStorageEngine eventStorageEngine;
    private EventStoreTransaction eventStoreTransaction;
    private AsyncUnitOfWork unitOfWork;

    private static SourcingCondition aSourcingCondition() {
        return new DefaultSourcingCondition(anyEvent(), 1L, 999L);
    }

    private static AppendCondition anAppendCondition() {
        return new DefaultAppendCondition(999L, anyEvent());
    }

    // TODO - Discuss: Perfect candidate to move to a commons test utils module?
    protected static <R> R awaitCompletion(CompletableFuture<R> completion) {
        await().atMost(Duration.ofMillis(500)) // todo: testcontainers shaded dependency?
               .pollDelay(Duration.ofMillis(25))
               .untilAsserted(() -> assertFalse(completion.isCompletedExceptionally(),
                                                () -> completion.exceptionNow().toString()));
        return completion.join();
    }

    // TODO - Discuss: Perfect candidate to move to a commons test utils module?
    protected static EventMessage<?> eventMessage(int seq) {
        return EventTestUtils.asEventMessage("Event[" + seq + "]");
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

    @Captor
    ArgumentCaptor<Context.ResourceKey<AppendCondition>> keyCaptor;
    @Captor
    ArgumentCaptor<UnaryOperator<AppendCondition>> valueCaptor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        unitOfWork = new AsyncUnitOfWork();
        eventStorageEngine = new AsyncInMemoryEventStorageEngine();
    }

    @Test
    void whenSourceThenCreateAppendCondition() {
        // given
        var sourcingCondition = aSourcingCondition();

        // when
        var context = awaitCompletion(unitOfWork.executeWithResult(unitOfWorkContext -> {
            var spiedContext = spy(unitOfWorkContext);
            eventStoreTransaction = new DefaultEventStoreTransaction(eventStorageEngine, spiedContext);
            eventStoreTransaction.source(aSourcingCondition(),
                                         spiedContext); // how differ the context from constructor?
            return CompletableFuture.completedFuture(spiedContext);
        }));

        // then
        verify(context).updateResource(keyCaptor.capture(), valueCaptor.capture());
        var key = keyCaptor.getValue().toString();
        assertTrue(key.contains("appendCondition"));
//        var value = valueCaptor.getValue()

    }

//    @Test
//    void sourcingConditionIsMappedToAppendConditionByEventStoreTransaction() {
//        EventCriteria expectedCriteria = TEST_AGGREGATE_CRITERIA;
//        EventMessage<?> expectedEventOne = eventMessage(0);
//        EventMessage<?> expectedEventTwo = eventMessage(1);
//        EventMessage<?> expectedEventThree = eventMessage(2);
//        SourcingCondition testSourcingCondition = SourcingCondition.conditionFor(expectedCriteria);
//
//        AtomicReference<MessageStream<EventMessage<?>>> initialStreamReference = new AtomicReference<>();
//        AtomicReference<MessageStream<EventMessage<?>>> finalStreamReference = new AtomicReference<>();
//
//        AsyncUnitOfWork uow = new AsyncUnitOfWork();
//        uow.runOnPreInvocation(context -> {
//               EventStoreTransaction transaction = testSubject.transaction(context, TEST_CONTEXT);
//               initialStreamReference.set(transaction.source(testSourcingCondition, context));
//           })
//           .runOnPostInvocation(context -> {
//               EventStoreTransaction transaction = testSubject.transaction(context, TEST_CONTEXT);
//               transaction.appendEvent(expectedEventOne);
//               transaction.appendEvent(expectedEventTwo);
//               transaction.appendEvent(expectedEventThree);
//           })
//           // Event are given to the store in the PREPARE_COMMIT phase.
//           // Hence, we retrieve the sourced set after that.
//           .runOnAfterCommit(context -> {
//               EventStoreTransaction transaction = testSubject.transaction(context, TEST_CONTEXT);
//               finalStreamReference.set(transaction.source(testSourcingCondition, context));
//           });
//        awaitCompletion(uow.execute());
//
//        assertNull(initialStreamReference.get().firstAsCompletableFuture().join());
//
//        StepVerifier.create(finalStreamReference.get().asFlux())
//                    .assertNext(entry -> assertTagsPositionAndEvent(entry, expectedCriteria, 0, expectedEventOne))
//                    .assertNext(entry -> assertTagsPositionAndEvent(entry, expectedCriteria, 1, expectedEventTwo))
//                    .assertNext(entry -> assertTagsPositionAndEvent(entry, expectedCriteria, 2, expectedEventThree))
//                    .verifyComplete();
//    }
}