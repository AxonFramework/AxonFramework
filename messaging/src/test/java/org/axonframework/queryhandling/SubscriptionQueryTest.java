/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.queryhandling;

import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.queryhandling.annotation.AnnotationQueryHandlerAdapter;
import org.junit.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.test.StepVerifier;
import reactor.util.concurrent.Queues;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Tests for subscription query functionality.
 *
 * @author Milan Savic
 */
public class SubscriptionQueryTest {

    private static final String FOUND = "found";

    private final SimpleQueryUpdateEmitter queryUpdateEmitter = SimpleQueryUpdateEmitter.builder().build();
    private final SimpleQueryBus queryBus = SimpleQueryBus.builder()
                                                          .queryUpdateEmitter(queryUpdateEmitter)
                                                          .build();
    private final ChatQueryHandler chatQueryHandler = new ChatQueryHandler(queryUpdateEmitter);
    private final AnnotationQueryHandlerAdapter<ChatQueryHandler> annotationQueryHandlerAdapter = new AnnotationQueryHandlerAdapter<>(
            chatQueryHandler);

    @Before
    public void setUp() {
        annotationQueryHandlerAdapter.subscribe(queryBus);
    }

    @Test
    public void testEmittingAnUpdate() {
        // given
        SubscriptionQueryMessage<String, List<String>, String> queryMessage1 = new GenericSubscriptionQueryMessage<>(
                "axonFrameworkCR",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class));

        SubscriptionQueryMessage<Integer, Integer, Integer> queryMessage2 = new GenericSubscriptionQueryMessage<>(
                5,
                "numberOfMessages",
                ResponseTypes.instanceOf(Integer.class),
                ResponseTypes.instanceOf(Integer.class));

        // when
        SubscriptionQueryResult<QueryResponseMessage<List<String>>, SubscriptionQueryUpdateMessage<String>> result1 = queryBus
                .subscriptionQuery(queryMessage1);
        SubscriptionQueryResult<QueryResponseMessage<Integer>, SubscriptionQueryUpdateMessage<Integer>> result2 = queryBus
                .subscriptionQuery(queryMessage2);

        // then
        chatQueryHandler.emitter.emit(String.class, "axonFrameworkCR"::equals, "Update11");
        chatQueryHandler.emitter.complete(String.class, "axonFrameworkCR"::equals);
        chatQueryHandler.emitter.emit(String.class,
                                      "axonFrameworkCR"::equals,
                                      GenericSubscriptionQueryUpdateMessage.asUpdateMessage("Update12"));
        StepVerifier.create(result1.initialResult().map(Message::getPayload))
                    .expectNext(Arrays.asList("Message1", "Message2", "Message3"))
                    .expectComplete()
                    .verify();
        StepVerifier.create(result1.updates().map(Message::getPayload))
                    .expectNext("Update11")
                    .expectComplete()
                    .verify();

        chatQueryHandler.emitter.emit(Integer.class,
                                      m -> m == 5,
                                      GenericSubscriptionQueryUpdateMessage.asUpdateMessage(1));
        chatQueryHandler.emitter.complete(Integer.class, m -> m == 5);
        chatQueryHandler.emitter.emit(Integer.class, m -> m == 5, 2);
        StepVerifier.create(result2.initialResult().map(Message::getPayload))
                    .expectNext(0)
                    .verifyComplete();
        StepVerifier.create(result2.updates().map(Message::getPayload))
                    .expectNext(1)
                    .verifyComplete();
    }

    @Test
    public void testEmittingNullUpdate() {
        // given
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "axonFrameworkCR",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class));

        // when
        SubscriptionQueryResult<QueryResponseMessage<List<String>>, SubscriptionQueryUpdateMessage<String>> result = queryBus
                .subscriptionQuery(queryMessage);

        // then
        chatQueryHandler.emitter.emit(String.class,
                                      "axonFrameworkCR"::equals,
                                      new GenericSubscriptionQueryUpdateMessage<>(String.class, null));
        chatQueryHandler.emitter.complete(String.class, "axonFrameworkCR"::equals);

        StepVerifier.create(result.updates())
                    .expectNextMatches(m -> m.getPayload() == null)
                    .verifyComplete();
    }

    @Test
    public void testEmittingUpdateInUnitOfWorkLifecycleRunsUpdatesOnAfterCommit() {
        String testQueryPayload = "axonFrameworkCR";
        String testQueryName = "chatMessages";
        String testUpdate = "some-update";
        List<String> expectedUpdates = Collections.singletonList(testUpdate);

        // Sets given UnitOfWork as the current, active, started UnitOfWork
        DefaultUnitOfWork unitOfWork =
                new DefaultUnitOfWork<>(GenericEventMessage.<String>asEventMessage("some-event-payload"));
        unitOfWork.start();

        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                testQueryPayload,
                testQueryName,
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class)
        );
        SubscriptionQueryResult<QueryResponseMessage<List<String>>, SubscriptionQueryUpdateMessage<String>> result =
                queryBus.subscriptionQuery(queryMessage);

        chatQueryHandler.emitter.emit(String.class, testQueryPayload::equals, testUpdate);

        Flux<SubscriptionQueryUpdateMessage<String>> emittedUpdates = result.updates();

        List<String> updateList = new ArrayList<>();
        emittedUpdates.map(Message::getPayload).subscribe(updateList::add);
        assertTrue(updateList.isEmpty());

        // Move the current UnitOfWork to the AFTER_COMMIT phase, triggering the emitter calls
        unitOfWork.commit();

        assertEquals(expectedUpdates, updateList);
    }

    @Test
    public void testCompletingSubscriptionQueryExceptionally() {
        // given
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "axonFrameworkCR",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class));
        RuntimeException toBeThrown = new RuntimeException();

        // when
        SubscriptionQueryResult<QueryResponseMessage<List<String>>, SubscriptionQueryUpdateMessage<String>> result = queryBus
                .subscriptionQuery(queryMessage, new SubscriptionQueryBackpressure(FluxSink.OverflowStrategy.IGNORE),
                                   Queues.SMALL_BUFFER_SIZE);
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            chatQueryHandler.emitter.emit(String.class, "axonFrameworkCR"::equals, "Update1");
            chatQueryHandler.emitter.completeExceptionally(String.class, "axonFrameworkCR"::equals, toBeThrown);
            chatQueryHandler.emitter.emit(String.class, "axonFrameworkCR"::equals, "Update2");
        }, 500, TimeUnit.MILLISECONDS);

        // then
        StepVerifier.create(result.initialResult().map(Message::getPayload))
                    .expectNext(Arrays.asList("Message1", "Message2", "Message3"))
                    .verifyComplete();
        StepVerifier.create(result.updates().map(Message::getPayload))
                    .expectNext("Update1")
                    .expectErrorMatches(toBeThrown::equals)
                    .verify();
    }

    @Test
    public void testCompletingSubscriptionQueryExceptionallyWhenOneOfSubscriptionFails() {
        // given
        SubscriptionQueryMessage<String, List<String>, String> queryMessage1 = new GenericSubscriptionQueryMessage<>(
                "axonFrameworkCR",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class));
        SubscriptionQueryMessage<String, List<String>, String> queryMessage2 = new GenericSubscriptionQueryMessage<>(
                "axonFrameworkCR",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class));

        // when
        SubscriptionQueryResult<QueryResponseMessage<List<String>>, SubscriptionQueryUpdateMessage<String>> result1 = queryBus
                .subscriptionQuery(queryMessage1);
        SubscriptionQueryResult<QueryResponseMessage<List<String>>, SubscriptionQueryUpdateMessage<String>> result2 = queryBus
                .subscriptionQuery(queryMessage2);

        List<String> query1Updates = new ArrayList<>();
        List<String> query2Updates = new ArrayList<>();
        result1.updates().map(Message::getPayload).subscribe(query1Updates::add, t -> {
            query1Updates.add("Error1");
            throw (RuntimeException) t;
        });
        result2.updates().map(Message::getPayload).subscribe(query2Updates::add, t -> query2Updates.add("Error2"));

        chatQueryHandler.emitter.emit(String.class, "axonFrameworkCR"::equals, "Update1");
        chatQueryHandler.emitter.completeExceptionally(String.class, "axonFrameworkCR"::equals, new RuntimeException());
        chatQueryHandler.emitter.emit(String.class, "axonFrameworkCR"::equals, "Update2");

        // then
        assertEquals(Arrays.asList("Update1", "Error1"), query1Updates);
        assertEquals(Arrays.asList("Update1", "Error2"), query2Updates);
    }

    @Test
    public void testCompletingSubscriptionExceptionallyInUnitOfWorkLifecycleRunsUpdatesOnAfterCommit() {
        String testQueryPayload = "axonFrameworkCR";
        String testQueryName = "chatMessages";
        String testUpdate = "some-update";
        List<String> expectedUpdates = Collections.singletonList(testUpdate);

        // Sets given UnitOfWork as the current, active, started UnitOfWork
        DefaultUnitOfWork unitOfWork =
                new DefaultUnitOfWork<>(GenericEventMessage.<String>asEventMessage("some-event-payload"));
        unitOfWork.start();

        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                testQueryPayload,
                testQueryName,
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class)
        );
        SubscriptionQueryResult<QueryResponseMessage<List<String>>, SubscriptionQueryUpdateMessage<String>> result =
                queryBus.subscriptionQuery(queryMessage);

        chatQueryHandler.emitter.emit(String.class, testQueryPayload::equals, testUpdate);
        chatQueryHandler.emitter.completeExceptionally(String.class, testQueryPayload::equals, new RuntimeException());

        Flux<SubscriptionQueryUpdateMessage<String>> emittedUpdates = result.updates();
        List<String> updateList = new ArrayList<>();
        emittedUpdates.map(Message::getPayload).subscribe(updateList::add);
        assertTrue(updateList.isEmpty());

        // Move the current UnitOfWork to the AFTER_COMMIT phase, triggering the emitter calls
        unitOfWork.commit();

        assertEquals(expectedUpdates, updateList);
        StepVerifier.create(emittedUpdates).expectError();
    }

    @Test
    public void testCompletingSubscriptionQuery() {
        // given
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "axonFrameworkCR",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class));

        // when
        SubscriptionQueryResult<QueryResponseMessage<List<String>>, SubscriptionQueryUpdateMessage<String>> result = queryBus
                .subscriptionQuery(queryMessage);
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            chatQueryHandler.emitter.emit(String.class, "axonFrameworkCR"::equals, "Update1");
            chatQueryHandler.emitter.complete(String.class, "axonFrameworkCR"::equals);
            chatQueryHandler.emitter.emit(String.class, "axonFrameworkCR"::equals, "Update2");
        }, 500, TimeUnit.MILLISECONDS);

        // then
        StepVerifier.create(result.initialResult().map(Message::getPayload))
                    .expectNext(Arrays.asList("Message1", "Message2", "Message3"))
                    .verifyComplete();
        StepVerifier.create(result.updates().map(Message::getPayload))
                    .expectNext("Update1")
                    .verifyComplete();
    }

    @Test
    public void testCompletingSubscriptionInUnitOfWorkLifecycleRunsUpdatesOnAfterCommit() {
        String testQueryPayload = "axonFrameworkCR";
        String testQueryName = "chatMessages";
        String testUpdate = "some-update";
        List<String> expectedUpdates = Collections.singletonList(testUpdate);

        // Sets given UnitOfWork as the current, active, started UnitOfWork
        DefaultUnitOfWork unitOfWork =
                new DefaultUnitOfWork<>(GenericEventMessage.<String>asEventMessage("some-event-payload"));
        unitOfWork.start();

        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                testQueryPayload,
                testQueryName,
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class)
        );
        SubscriptionQueryResult<QueryResponseMessage<List<String>>, SubscriptionQueryUpdateMessage<String>> result =
                queryBus.subscriptionQuery(queryMessage);

        chatQueryHandler.emitter.emit(String.class, testQueryPayload::equals, testUpdate);
        chatQueryHandler.emitter.complete(String.class, testQueryPayload::equals);

        Flux<SubscriptionQueryUpdateMessage<String>> emittedUpdates = result.updates();
        List<String> updateList = new ArrayList<>();
        emittedUpdates.map(Message::getPayload).subscribe(updateList::add);
        assertTrue(updateList.isEmpty());

        // Move the current UnitOfWork to the AFTER_COMMIT phase, triggering the emitter calls
        unitOfWork.commit();

        assertEquals(expectedUpdates, updateList);
        StepVerifier.create(emittedUpdates).expectComplete();
    }

    @Test
    public void testOrderingOfOperationOnUpdateHandler() {
        // given
        SubscriptionQueryMessage<String, String, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "axonFrameworkCR",
                "emitFirstThenReturnInitial",
                ResponseTypes.instanceOf(String.class),
                ResponseTypes.instanceOf(String.class));

        // when
        SubscriptionQueryResult<QueryResponseMessage<String>, SubscriptionQueryUpdateMessage<String>> result = queryBus
                .subscriptionQuery(queryMessage);

        // then
        StepVerifier.create(result.initialResult().map(Message::getPayload))
                    .expectNext("Initial")
                    .expectComplete()
                    .verify();

        StepVerifier.create(result.updates().map(Message::getPayload))
                    .expectNext("Update1", "Update2")
                    .verifyComplete();
    }

    @Test
    public void testSubscribingQueryHandlerFailing() {
        // given
        SubscriptionQueryMessage<String, String, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "axonFrameworkCR",
                "failingQuery",
                ResponseTypes.instanceOf(String.class),
                ResponseTypes.instanceOf(String.class));

        // when
        SubscriptionQueryResult<QueryResponseMessage<String>, SubscriptionQueryUpdateMessage<String>> result = queryBus
                .subscriptionQuery(queryMessage);

        // then
        StepVerifier.create(result.initialResult())
                    .expectErrorMatches(chatQueryHandler.toBeThrown::equals)
                    .verify();
    }

    @Test
    public void testSeveralSubscriptions() {
        // given
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "axonFrameworkCR",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class));

        // when
        SubscriptionQueryResult<QueryResponseMessage<List<String>>, SubscriptionQueryUpdateMessage<String>> result = queryBus
                .subscriptionQuery(queryMessage, new SubscriptionQueryBackpressure(FluxSink.OverflowStrategy.ERROR), 8);
        List<String> initial1 = new ArrayList<>();
        List<String> initial2 = new ArrayList<>();
        List<String> update1 = new ArrayList<>();
        List<String> update2 = new ArrayList<>();
        List<String> update3 = new ArrayList<>();
        result.initialResult().map(Message::getPayload).subscribe(initial1::addAll);
        result.initialResult().map(Message::getPayload).subscribe(initial2::addAll);
        chatQueryHandler.emitter.emit(String.class, "axonFrameworkCR"::equals, "Update1");
        result.updates().map(Message::getPayload).subscribe(update1::add);
        result.updates().map(Message::getPayload).subscribe(update2::add);
        for (int i = 2; i < 10; i++) {
            chatQueryHandler.emitter.emit(String.class, "axonFrameworkCR"::equals, "Update" + i);
        }
        result.updates().map(Message::getPayload).subscribe(update3::add);
        chatQueryHandler.emitter.emit(String.class, "axonFrameworkCR"::equals, "Update10");
        chatQueryHandler.emitter.emit(String.class, "axonFrameworkCR"::equals, "Update11");
        chatQueryHandler.emitter.complete(String.class, "axonFrameworkCR"::equals);

        assertEquals(Arrays.asList("Message1", "Message2", "Message3"), initial1);
        assertEquals(Arrays.asList("Message1", "Message2", "Message3"), initial2);
        assertEquals(Arrays.asList("Update1",
                                   "Update2",
                                   "Update3",
                                   "Update4",
                                   "Update5",
                                   "Update6",
                                   "Update7",
                                   "Update8",
                                   "Update9",
                                   "Update10",
                                   "Update11"), update1);
        assertEquals(Arrays.asList("Update1",
                                   "Update2",
                                   "Update3",
                                   "Update4",
                                   "Update5",
                                   "Update6",
                                   "Update7",
                                   "Update8",
                                   "Update9",
                                   "Update10",
                                   "Update11"), update2);
        assertEquals(Arrays.asList("Update2",
                                   "Update3",
                                   "Update4",
                                   "Update5",
                                   "Update6",
                                   "Update7",
                                   "Update8",
                                   "Update9",
                                   "Update10",
                                   "Update11"), update3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDoubleSubscriptionMessage() {
        // given
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "axonFrameworkCR",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class));

        // when
        queryBus.subscriptionQuery(queryMessage);
        queryBus.subscriptionQuery(queryMessage);
    }

    @Test
    public void testBufferOverflow() {
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "axonFrameworkCR",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class));

        SubscriptionQueryResult<QueryResponseMessage<List<String>>, SubscriptionQueryUpdateMessage<String>> result = queryBus
                .subscriptionQuery(queryMessage,
                                   new SubscriptionQueryBackpressure(FluxSink.OverflowStrategy.ERROR),
                                   200);

        for (int i = 0; i < 201; i++) {
            chatQueryHandler.emitter.emit(String.class, "axonFrameworkCR"::equals, "Update" + i);
        }
        chatQueryHandler.emitter.complete(String.class, "axonFrameworkCR"::equals);

        StepVerifier.create(result.updates())
                    .expectErrorMatches(t -> "The receiver is overrun by more signals than expected (bounded queue...)"
                            .equals(t.getMessage()))
                    .verify();
    }

    @Test
    public void testSubscriptionDisposal() {
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "axonFrameworkCR",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class));

        SubscriptionQueryResult<QueryResponseMessage<List<String>>, SubscriptionQueryUpdateMessage<String>> result = queryBus
                .subscriptionQuery(queryMessage);

        chatQueryHandler.emitter.emit(String.class, "axonFrameworkCR"::equals, "Update1");
        result.close();
        chatQueryHandler.emitter.emit(String.class, "axonFrameworkCR"::equals, "Update2");

        StepVerifier.create(result.updates().map(Message::getPayload))
                    .expectNext("Update1")
                    .verifyComplete();
    }

    @Test
    public void testSubscriptionQueryWithInterceptors() {
        // given
        List<String> interceptedResponse = Arrays.asList("fakeReply1", "fakeReply2");
        queryBus.registerDispatchInterceptor(
                messages -> (i, m) -> m.andMetaData(Collections.singletonMap("key", "value"))
        );
        queryBus.registerHandlerInterceptor((unitOfWork, interceptorChain) -> {
            if (unitOfWork.getMessage().getMetaData().containsKey("key")) {
                return interceptedResponse;
            }
            return interceptorChain.proceed();
        });
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "axonFrameworkCR",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class));

        // when
        SubscriptionQueryResult<QueryResponseMessage<List<String>>, SubscriptionQueryUpdateMessage<String>> result = queryBus
                .subscriptionQuery(queryMessage);

        // then
        StepVerifier.create(result.initialResult().map(Message::getPayload))
                    .expectNext(interceptedResponse)
                    .verifyComplete();
    }

    @Test
    public void testSubscriptionQueryUpdateWithInterceptors() {
        // given
        Map<String, String> metaData = Collections.singletonMap("key", "value");
        queryUpdateEmitter.registerDispatchInterceptor(
                messages -> (i, m) -> m.andMetaData(metaData)
        );
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "axonFrameworkCR",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class));

        // when
        SubscriptionQueryResult<QueryResponseMessage<List<String>>, SubscriptionQueryUpdateMessage<String>> result = queryBus
                .subscriptionQuery(queryMessage);

        chatQueryHandler.emitter.emit(String.class, "axonFrameworkCR"::equals, "Update1");
        result.close();

        // then
        StepVerifier.create(result.updates())
                    .expectNextMatches(m -> m.getMetaData().equals(metaData))
                    .verifyComplete();
    }

    @Test
    public void testActiveSubscriptions() {
        // given
        SubscriptionQueryMessage<String, List<String>, String> queryMessage1 = new GenericSubscriptionQueryMessage<>(
                "axonFrameworkCR",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class));

        SubscriptionQueryMessage<Integer, Integer, Integer> queryMessage2 = new GenericSubscriptionQueryMessage<>(
                5,
                "numberOfMessages",
                ResponseTypes.instanceOf(Integer.class),
                ResponseTypes.instanceOf(Integer.class));

        // when
        queryBus.subscriptionQuery(queryMessage1);
        queryBus.subscriptionQuery(queryMessage2);

        // then
        Set<SubscriptionQueryMessage<?, ?, ?>> expectedSubscriptions = new HashSet<>(Arrays.asList(queryMessage1,
                                                                                                   queryMessage2));
        assertEquals(expectedSubscriptions, queryUpdateEmitter.activeSubscriptions());
    }

    @Test
    public void testSubscriptionQueryResultHandle() throws InterruptedException {
        // given
        SubscriptionQueryMessage<String, String, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "axonFrameworkCR",
                "emitFirstThenReturnInitial",
                ResponseTypes.instanceOf(String.class),
                ResponseTypes.instanceOf(String.class));

        // when
        List<String> initialResult = new ArrayList<>();
        List<String> updates = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);
        queryBus.subscriptionQuery(queryMessage).handle(initial -> {
                                                            initialResult.add(initial.getPayload());
                                                            latch.countDown();
                                                        },
                                                        update -> {
                                                            updates.add(update.getPayload());
                                                            latch.countDown();
                                                        });

        // then
        latch.await(500, TimeUnit.MILLISECONDS);
        assertEquals(Collections.singletonList("Initial"), initialResult);
        assertEquals(Arrays.asList("Update1", "Update2"), updates);
    }

    @Test
    public void testSubscriptionQueryResultHandleWhenThereIsAnErrorConsumingAnInitialResult()
            throws InterruptedException {
        // given
        SubscriptionQueryMessage<String, String, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "axonFrameworkCR",
                "emitFirstThenReturnInitial",
                ResponseTypes.instanceOf(String.class),
                ResponseTypes.instanceOf(String.class));

        // when
        List<String> initialResult = new ArrayList<>();
        List<String> updates = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);
        queryBus.subscriptionQuery(queryMessage).handle(initial -> {
                                                            initialResult.add(initial.getPayload());
                                                            latch.countDown();
                                                            throw new IllegalStateException("oops");
                                                        },
                                                        update -> {
                                                            updates.add(update.getPayload());
                                                            latch.countDown();
                                                        });

        // then
        latch.await(500, TimeUnit.MILLISECONDS);
        assertEquals(Collections.singletonList("Initial"), initialResult);
        assertTrue(updates.isEmpty());
    }

    @Test
    public void testSubscriptionQueryResultHandleWhenThereIsAnErrorConsumingAnUpdate() {
        // given
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "axonFrameworkCR",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class));

        // when
        List<String> initialResult = new ArrayList<>();
        List<String> updates = new ArrayList<>();
        queryBus.subscriptionQuery(queryMessage, new SubscriptionQueryBackpressure(FluxSink.OverflowStrategy.DROP), 1)
                .handle(initial -> initialResult.addAll(initial.getPayload()),
                        update -> {
                            updates.add(update.getPayload());
                            throw new IllegalStateException("oops");
                        });
        chatQueryHandler.emitter.emit(String.class, "axonFrameworkCR"::equals, "Update1");
        chatQueryHandler.emitter.emit(String.class, "axonFrameworkCR"::equals, "Update2");

        // then
        assertEquals(Arrays.asList("Message1", "Message2", "Message3"), initialResult);
        assertEquals(Collections.singletonList("Update1"), updates);

        assertTrue("Expected subscriptions to be cancelled", queryUpdateEmitter.activeSubscriptions().isEmpty());
    }

    @Test
    public void testSubscriptionQueryResultHandleWhenThereIsAnErrorConsumingABufferedUpdate() {
        // given
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "axonFrameworkCR",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class));

        // when
        List<String> initialResult = new ArrayList<>();
        List<String> updates = new ArrayList<>();
        queryBus.subscriptionQuery(queryMessage, new SubscriptionQueryBackpressure(FluxSink.OverflowStrategy.DROP), 1)
                .handle(initial -> {
                            // make sure the update is emitted before subscribing to updates
                            chatQueryHandler.emitter.emit(String.class, "axonFrameworkCR"::equals, "Update1");
                            initialResult.addAll(initial.getPayload());
                        },
                        update -> {
                            updates.add(update.getPayload());
                            throw new IllegalStateException("oops");
                        });
        chatQueryHandler.emitter.emit(String.class, "axonFrameworkCR"::equals, "Update2");

        // then
        assertEquals(Arrays.asList("Message1", "Message2", "Message3"), initialResult);
        assertEquals(Collections.singletonList("Update1"), updates);

        assertTrue("Expected subscriptions to be cancelled", queryUpdateEmitter.activeSubscriptions().isEmpty());
    }

    @Test
    public void testSubscriptionQueryResultHandleWhenThereIsAnErrorOnInitialResult() {
        // given
        SubscriptionQueryMessage<String, String, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "axonFrameworkCR",
                "failingQuery",
                ResponseTypes.instanceOf(String.class),
                ResponseTypes.instanceOf(String.class));

        // when
        List<String> initialResult = new ArrayList<>();
        List<String> updates = new ArrayList<>();
        queryBus.subscriptionQuery(queryMessage).handle(initial -> initialResult.add(initial.getPayload()),
                                                        update -> updates.add(update.getPayload()));
        chatQueryHandler.emitter.emit(String.class, "axonFrameworkCR"::equals, "Update1");
        chatQueryHandler.emitter.emit(String.class, "axonFrameworkCR"::equals, "Update2");
        chatQueryHandler.emitter.complete(String.class, "axonFrameworkCR"::equals);

        // then
        assertTrue(initialResult.isEmpty());
        assertTrue(updates.isEmpty());
    }

    @Test
    public void testSubscriptionQueryResultHandleWhenThereIsAnErrorOnUpdate() {
        // given
        SubscriptionQueryMessage<String, String, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "axonFrameworkCR",
                "failingQuery",
                ResponseTypes.instanceOf(String.class),
                ResponseTypes.instanceOf(String.class));

        // when
        List<String> initialResult = new ArrayList<>();
        List<String> updates = new ArrayList<>();
        queryBus.subscriptionQuery(queryMessage).handle(initial -> initialResult.add(initial.getPayload()),
                                                        update -> updates.add(update.getPayload()));
        chatQueryHandler.emitter.completeExceptionally(String.class, "axonFrameworkCR"::equals, new RuntimeException());
        chatQueryHandler.emitter.emit(String.class, "axonFrameworkCR"::equals, "Update1");

        // then
        assertTrue(initialResult.isEmpty());
        assertTrue(updates.isEmpty());
    }

    @Test
    public void testQueryGatewayCorrectlyReturnsNullOnSubscriptionQueryWithNullInitialResult()
            throws ExecutionException, InterruptedException {
        QueryGateway queryGateway = DefaultQueryGateway.builder().queryBus(queryBus).build();

        assertNull(queryGateway.subscriptionQuery(new SomeQuery("not " + FOUND), String.class, String.class)
                               .initialResult()
                               .toFuture().get());
    }

    @Test
    public void testQueryGatewayCorrectlyReturnsOnSubscriptionQuery() throws ExecutionException, InterruptedException {
        QueryGateway queryGateway = DefaultQueryGateway.builder().queryBus(queryBus).build();
        String result = queryGateway.subscriptionQuery(new SomeQuery(FOUND), String.class, String.class)
                                    .initialResult()
                                    .toFuture().get();

        assertEquals(FOUND, result);
    }

    private static class SomeQuery {

        private final String filter;

        private SomeQuery(String filter) {
            this.filter = filter;
        }

        public String getFilter() {
            return filter;
        }
    }

    @SuppressWarnings("unused")
    private class ChatQueryHandler {

        private final QueryUpdateEmitter emitter;
        private final RuntimeException toBeThrown = new RuntimeException("oops");

        private ChatQueryHandler(QueryUpdateEmitter emitter) {
            this.emitter = emitter;
        }

        @QueryHandler(queryName = "chatMessages")
        public List<String> chatMessages(String chatRoom) {
            return Arrays.asList("Message1", "Message2", "Message3");
        }

        @QueryHandler(queryName = "numberOfMessages")
        public Integer numberOfMessages(Integer i) {
            return 0;
        }

        @QueryHandler(queryName = "failingQuery")
        public String failingQuery(String criteria) {
            throw toBeThrown;
        }

        @QueryHandler(queryName = "emitFirstThenReturnInitial")
        public String emitFirstThenReturnInitial(String criteria) throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            Executors.newSingleThreadExecutor().submit(() -> {
                emitter.emit(String.class,
                             "axonFrameworkCR"::equals,
                             GenericSubscriptionQueryUpdateMessage.asUpdateMessage("Update1"));
                emitter.emit(String.class,
                             "axonFrameworkCR"::equals,
                             GenericSubscriptionQueryUpdateMessage.asUpdateMessage("Update2"));
                emitter.complete(String.class, "axonFrameworkCR"::equals);
                latch.countDown();
            });

            latch.await();

            return "Initial";
        }

        @QueryHandler
        public String someQueryHandler(SomeQuery query) {
            return FOUND.equals(query.getFilter()) ? FOUND : null;
        }
    }
}
