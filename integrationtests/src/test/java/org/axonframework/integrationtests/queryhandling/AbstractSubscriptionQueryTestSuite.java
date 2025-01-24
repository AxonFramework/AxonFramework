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

package org.axonframework.integrationtests.queryhandling;

import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.queryhandling.DefaultQueryGateway;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.annotation.AnnotationQueryHandlerAdapter;
import org.junit.jupiter.api.*;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.test.StepVerifier;
import reactor.test.StepVerifierOptions;
import reactor.util.concurrent.Queues;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.axonframework.integrationtests.utils.AssertUtils.assertWithin;
import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
import static org.axonframework.messaging.responsetypes.ResponseTypes.multipleInstancesOf;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Abstract test suite for the {@link QueryBus#subscriptionQuery(SubscriptionQueryMessage)} functionality.
 *
 * @author Milan Savic
 */
public abstract class AbstractSubscriptionQueryTestSuite {

    private static final String FOUND = "found";
    private static final String TEST_PAYLOAD = "axonFrameworkCR";

    private QueryBus queryBus;
    private QueryUpdateEmitter queryUpdateEmitter;

    private ChatQueryHandler chatQueryHandler;

    @BeforeEach
    void setUp() {
        queryBus = queryBus();
        queryUpdateEmitter = queryUpdateEmitter();
        chatQueryHandler = new ChatQueryHandler(queryUpdateEmitter);

        AnnotationQueryHandlerAdapter<ChatQueryHandler> annotationQueryHandlerAdapter =
                new AnnotationQueryHandlerAdapter<>(chatQueryHandler);
        annotationQueryHandlerAdapter.subscribe(queryBus);

        Hooks.onErrorDropped(error -> {/*Ignore these exceptions for these test cases*/});
    }

    @AfterEach
    void tearDown() {
        Hooks.resetOnErrorDropped();
    }

    /**
     * Return the {@link QueryBus} used to test the {@link QueryBus#subscriptionQuery(SubscriptionQueryMessage)}
     * functionality with.
     *
     * @return the {@link QueryBus} used to test the {@link QueryBus#subscriptionQuery(SubscriptionQueryMessage)}
     * functionality with
     */
    public abstract QueryBus queryBus();

    /**
     * Return the {@link QueryUpdateEmitter} used to test the
     * {@link QueryBus#subscriptionQuery(SubscriptionQueryMessage)} functionality with
     *
     * @return the {@link QueryUpdateEmitter} used to test the
     * {@link QueryBus#subscriptionQuery(SubscriptionQueryMessage)} functionality with
     */
    public abstract QueryUpdateEmitter queryUpdateEmitter();

    @Test
    void emittingAnUpdate() {
        // given
        SubscriptionQueryMessage<String, List<String>, String> queryMessage1 = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), "chatMessages", TEST_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );
        SubscriptionQueryMessage<Integer, Integer, Integer> queryMessage2 = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), "numberOfMessages", 5,
                instanceOf(Integer.class), instanceOf(Integer.class)
        );

        // when
        SubscriptionQueryResult<QueryResponseMessage<List<String>>, SubscriptionQueryUpdateMessage<String>> result1 =
                queryBus.subscriptionQuery(queryMessage1);
        SubscriptionQueryResult<QueryResponseMessage<Integer>, SubscriptionQueryUpdateMessage<Integer>> result2 =
                queryBus.subscriptionQuery(queryMessage2);

        // then
        chatQueryHandler.emitter.emit(String.class, TEST_PAYLOAD::equals, "Update11");
        chatQueryHandler.emitter.complete(String.class, TEST_PAYLOAD::equals);
        chatQueryHandler.emitter.emit(String.class,
                                      TEST_PAYLOAD::equals,
                                      new GenericSubscriptionQueryUpdateMessage<>(new MessageType("query-string"),
                                                                                  "Update12"));


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
                                      new GenericSubscriptionQueryUpdateMessage<>(new MessageType("query-integer"), 1));
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
    void emittingNullUpdate() {
        // given
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), "chatMessages", TEST_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );

        // when
        SubscriptionQueryResult<QueryResponseMessage<List<String>>, SubscriptionQueryUpdateMessage<String>> result =
                queryBus.subscriptionQuery(queryMessage);

        // then
        chatQueryHandler.emitter.emit(
                String.class,
                TEST_PAYLOAD::equals,
                new GenericSubscriptionQueryUpdateMessage<>(
                        new MessageType("query"), null, String.class
                )
        );
        chatQueryHandler.emitter.complete(String.class, TEST_PAYLOAD::equals);

        StepVerifier.create(result.updates())
                    .expectNextMatches(m -> m.getPayload() == null)
                    .verifyComplete();
    }

    @Test
    void emittingUpdateInUnitOfWorkLifecycleRunsUpdatesOnAfterCommit() {
        String testQueryPayload = TEST_PAYLOAD;
        String testQueryName = "chatMessages";
        String testUpdate = "some-update";
        List<String> expectedUpdates = Collections.singletonList(testUpdate);

        // Sets given UnitOfWork as the current, active, started UnitOfWork
        DefaultUnitOfWork<?> unitOfWork =
                new DefaultUnitOfWork<>(EventTestUtils.<String>asEventMessage("some-event-payload"));
        unitOfWork.start();

        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), testQueryName, testQueryPayload,
                multipleInstancesOf(String.class), instanceOf(String.class)
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
    void completingSubscriptionQueryExceptionally() {
        // given
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), "chatMessages", TEST_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );
        RuntimeException toBeThrown = new RuntimeException();

        // when
        SubscriptionQueryResult<QueryResponseMessage<List<String>>, SubscriptionQueryUpdateMessage<String>> result =
                queryBus.subscriptionQuery(queryMessage,
                                           Queues.SMALL_BUFFER_SIZE);
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            chatQueryHandler.emitter.emit(String.class, TEST_PAYLOAD::equals, "Update1");
            chatQueryHandler.emitter.completeExceptionally(String.class, TEST_PAYLOAD::equals, toBeThrown);
            chatQueryHandler.emitter.emit(String.class, TEST_PAYLOAD::equals, "Update2");
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
    void completingSubscriptionQueryExceptionallyWhenOneOfSubscriptionFails() {
        // given
        SubscriptionQueryMessage<String, List<String>, String> queryMessage1 = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), "chatMessages", TEST_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );
        SubscriptionQueryMessage<String, List<String>, String> queryMessage2 = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), "chatMessages", TEST_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );

        // when
        SubscriptionQueryResult<QueryResponseMessage<List<String>>, SubscriptionQueryUpdateMessage<String>> result1 =
                queryBus.subscriptionQuery(queryMessage1);
        SubscriptionQueryResult<QueryResponseMessage<List<String>>, SubscriptionQueryUpdateMessage<String>> result2 =
                queryBus.subscriptionQuery(queryMessage2);

        List<String> query1Updates = new ArrayList<>();
        List<String> query2Updates = new ArrayList<>();
        result1.updates().map(Message::getPayload).subscribe(query1Updates::add, t -> {
            query1Updates.add("Error1");
            throw (RuntimeException) t;
        });
        result2.updates().map(Message::getPayload).subscribe(query2Updates::add, t -> query2Updates.add("Error2"));

        chatQueryHandler.emitter.emit(String.class, TEST_PAYLOAD::equals, "Update1");
        chatQueryHandler.emitter.completeExceptionally(String.class, TEST_PAYLOAD::equals, new RuntimeException());
        chatQueryHandler.emitter.emit(String.class, TEST_PAYLOAD::equals, "Update2");

        // then
        assertEquals(Arrays.asList("Update1", "Error1"), query1Updates);
        assertEquals(Arrays.asList("Update1", "Error2"), query2Updates);
    }

    @Test
    void completingSubscriptionExceptionallyInUnitOfWorkLifecycleRunsUpdatesOnAfterCommit() {
        String testQueryPayload = TEST_PAYLOAD;
        String testQueryName = "chatMessages";
        String testUpdate = "some-update";
        List<String> expectedUpdates = Collections.singletonList(testUpdate);

        // Sets given UnitOfWork as the current, active, started UnitOfWork
        DefaultUnitOfWork<?> unitOfWork =
                new DefaultUnitOfWork<>(EventTestUtils.<String>asEventMessage("some-event-payload"));
        unitOfWork.start();

        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), testQueryName, testQueryPayload,
                multipleInstancesOf(String.class), instanceOf(String.class)
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
        StepVerifier.create(emittedUpdates)
                    .expectNextMatches(updateMessage -> updateMessage.getPayload().equals(testUpdate))
                    .verifyError(RuntimeException.class);
    }

    @Test
    void completingSubscriptionQuery() {
        // given
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), "chatMessages", TEST_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );

        // when
        SubscriptionQueryResult<QueryResponseMessage<List<String>>, SubscriptionQueryUpdateMessage<String>> result =
                queryBus.subscriptionQuery(queryMessage);
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            chatQueryHandler.emitter.emit(String.class, TEST_PAYLOAD::equals, "Update1");
            chatQueryHandler.emitter.complete(String.class, TEST_PAYLOAD::equals);
            chatQueryHandler.emitter.emit(String.class, TEST_PAYLOAD::equals, "Update2");
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
    void completingSubscriptionInUnitOfWorkLifecycleRunsUpdatesOnAfterCommit() {
        String testQueryPayload = TEST_PAYLOAD;
        String testQueryName = "chatMessages";
        String testUpdate = "some-update";
        List<String> expectedUpdates = Collections.singletonList(testUpdate);

        // Sets given UnitOfWork as the current, active, started UnitOfWork
        DefaultUnitOfWork<?> unitOfWork =
                new DefaultUnitOfWork<>(EventTestUtils.<String>asEventMessage("some-event-payload"));
        unitOfWork.start();

        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), testQueryName, testQueryPayload,
                multipleInstancesOf(String.class), instanceOf(String.class)
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
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertEquals(expectedUpdates, updateList));
        StepVerifier.create(emittedUpdates)
                    .expectNextMatches(updateMessage -> updateMessage.getPayload().equals(testUpdate))
                    .verifyComplete();
    }

    @Test
    void orderingOfOperationOnUpdateHandler() {
        // given
        SubscriptionQueryMessage<String, String, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), "emitFirstThenReturnInitial", TEST_PAYLOAD,
                instanceOf(String.class), instanceOf(String.class)
        );

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
    void subscribingQueryHandlerFailing() {
        // given
        SubscriptionQueryMessage<String, String, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), "failingQuery", TEST_PAYLOAD,
                instanceOf(String.class), instanceOf(String.class)
        );

        // when
        SubscriptionQueryResult<QueryResponseMessage<String>, SubscriptionQueryUpdateMessage<String>> result =
                queryBus.subscriptionQuery(queryMessage);

        // then
        StepVerifier.create(result.initialResult())
                    .assertNext(m -> {
                        Assertions.assertTrue(m.isExceptional());
                        Assertions.assertEquals(chatQueryHandler.toBeThrown, m.exceptionResult());
                    })
                    .expectComplete()
                    .verify();
    }

    @Test
    void severalSubscriptions() {
        // given
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), "chatMessages", TEST_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );

        // when
        SubscriptionQueryResult<QueryResponseMessage<List<String>>, SubscriptionQueryUpdateMessage<String>> result =
                queryBus.subscriptionQuery(queryMessage,
                                           8);
        List<String> initial1 = new ArrayList<>();
        List<String> initial2 = new ArrayList<>();
        List<String> update1 = new ArrayList<>();
        List<String> update2 = new ArrayList<>();
        List<String> update3 = new ArrayList<>();
        result.initialResult().map(Message::getPayload).subscribe(initial1::addAll);
        result.initialResult().map(Message::getPayload).subscribe(initial2::addAll);
        chatQueryHandler.emitter.emit(String.class, TEST_PAYLOAD::equals, "Update1");
        result.updates().map(Message::getPayload).subscribe(update1::add);
        result.updates().map(Message::getPayload).subscribe(update2::add);
        for (int i = 2; i < 10; i++) {
            chatQueryHandler.emitter.emit(String.class, TEST_PAYLOAD::equals, "Update" + i);
        }
        result.updates().map(Message::getPayload).subscribe(update3::add);
        chatQueryHandler.emitter.emit(String.class, TEST_PAYLOAD::equals, "Update10");
        chatQueryHandler.emitter.emit(String.class, TEST_PAYLOAD::equals, "Update11");
        chatQueryHandler.emitter.complete(String.class, TEST_PAYLOAD::equals);

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

    @Test
    void doubleSubscriptionMessage() {
        // given
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), "chatMessages", TEST_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );

        // when
        queryBus.subscriptionQuery(queryMessage);

        assertThrows(IllegalArgumentException.class, () -> queryBus.subscriptionQuery(queryMessage));
    }

    @Test
    void replayBufferOverflow() {
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), "chatMessages", TEST_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );

        SubscriptionQueryResult<QueryResponseMessage<List<String>>, SubscriptionQueryUpdateMessage<String>> result =
                queryBus.subscriptionQuery(queryMessage, 100);

        for (int i = 0; i <= 200; i++) {
            chatQueryHandler.emitter.emit(String.class, TEST_PAYLOAD::equals, "Update" + i);
        }
        chatQueryHandler.emitter.complete(String.class, TEST_PAYLOAD::equals);

        StepVerifier.create(result.updates())
                    .recordWith(LinkedList::new)
                    .thenConsumeWhile(x -> true)
                    .expectRecordedMatches(AbstractSubscriptionQueryTestSuite::assertRecorded)
                    .verifyComplete();
    }

    private static boolean assertRecorded(Collection<SubscriptionQueryUpdateMessage<String>> elements) {
        LinkedList<SubscriptionQueryUpdateMessage<String>> recordedMessages = new LinkedList<>(elements);

        assert recordedMessages.peekFirst() != null;
        boolean firstIs101 = recordedMessages.peekFirst().getPayload().equals("Update101");

        assert recordedMessages.peekLast() != null;
        boolean lastIs200 = recordedMessages.peekLast().getPayload().equals("Update200");

        return elements.size() == 100 && firstIs101 && lastIs200;
    }

    @Test
    void onBackpressureError() {
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), "chatMessages", TEST_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );

        SubscriptionQueryResult<QueryResponseMessage<List<String>>, SubscriptionQueryUpdateMessage<String>> result =
                queryBus.subscriptionQuery(queryMessage, 100);

        Flux<SubscriptionQueryUpdateMessage<String>> test1 = result.updates().onBackpressureBuffer(100);

        StepVerifier.create(test1, StepVerifierOptions.create().initialRequest(0))
                    .expectSubscription()
                    .then(() -> {
                        for (int i = 0; i < 200; i++) {
                            chatQueryHandler.emitter.emit(String.class, TEST_PAYLOAD::equals, "Update" + i);
                        }
                        chatQueryHandler.emitter.complete(String.class, TEST_PAYLOAD::equals);
                    })
                    .expectNoEvent(Duration.ofMillis(100))
                    .thenRequest(100)
                    .expectNextCount(100)
                    .expectErrorMatches(Exceptions::isOverflow)
                    .verify(Duration.ofSeconds(5));
    }

    @Test
    void subscriptionDisposal() {
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), "chatMessages", TEST_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );

        SubscriptionQueryResult<QueryResponseMessage<List<String>>, SubscriptionQueryUpdateMessage<String>> result =
                queryBus.subscriptionQuery(queryMessage);

        chatQueryHandler.emitter.emit(String.class, TEST_PAYLOAD::equals, "Update1");
        result.cancel();
        chatQueryHandler.emitter.emit(String.class, TEST_PAYLOAD::equals, "Update2");

        StepVerifier.create(result.updates().map(Message::getPayload))
                    .expectNext("Update1")
                    .verifyComplete();
    }

    @Test
    void subscriptionQueryWithInterceptors() {
        // given
        List<String> interceptedResponse = Arrays.asList("fakeReply1", "fakeReply2");
        queryBus.registerDispatchInterceptor(
                messages -> (i, m) -> m.andMetaData(Collections.singletonMap("key", "value"))
        );
        queryBus.registerHandlerInterceptor((unitOfWork, interceptorChain) -> {
            if (unitOfWork.getMessage().getMetaData().containsKey("key")) {
                return interceptedResponse;
            }
            return interceptorChain.proceedSync();
        });
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), "chatMessages", TEST_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );

        // when
        SubscriptionQueryResult<QueryResponseMessage<List<String>>, SubscriptionQueryUpdateMessage<String>> result =
                queryBus.subscriptionQuery(queryMessage);

        // then
        StepVerifier.create(result.initialResult().map(Message::getPayload))
                    .expectNext(interceptedResponse)
                    .verifyComplete();
    }

    @Test
    void subscriptionQueryUpdateWithInterceptors() {
        // given
        Map<String, String> metaData = Collections.singletonMap("key", "value");
        queryUpdateEmitter.registerDispatchInterceptor(
                messages -> (i, m) -> m.andMetaData(metaData)
        );
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), "chatMessages", TEST_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );

        // when
        SubscriptionQueryResult<QueryResponseMessage<List<String>>, SubscriptionQueryUpdateMessage<String>> result =
                queryBus.subscriptionQuery(queryMessage);

        chatQueryHandler.emitter.emit(String.class, TEST_PAYLOAD::equals, "Update1");
        result.cancel();

        // then
        StepVerifier.create(result.updates())
                    .expectNextMatches(m -> m.getMetaData().equals(metaData))
                    .verifyComplete();
    }

    @Test
    void activeSubscriptions() {
        // given
        SubscriptionQueryMessage<String, List<String>, String> queryMessage1 = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), "chatMessages", TEST_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );
        SubscriptionQueryMessage<Integer, Integer, Integer> queryMessage2 = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), "numberOfMessages", 5,
                instanceOf(Integer.class), instanceOf(Integer.class)
        );

        // when
        queryBus.subscriptionQuery(queryMessage1);
        queryBus.subscriptionQuery(queryMessage2);

        // then
        Set<SubscriptionQueryMessage<?, ?, ?>> expectedSubscriptions =
                new HashSet<>(Arrays.asList(queryMessage1, queryMessage2));
        assertEquals(expectedSubscriptions, queryUpdateEmitter.activeSubscriptions());
    }

    @Test
    void subscriptionQueryResultHandle() throws InterruptedException {
        // given
        SubscriptionQueryMessage<String, String, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), "emitFirstThenReturnInitial", TEST_PAYLOAD,
                instanceOf(String.class), instanceOf(String.class)
        );

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
    void subscriptionQueryResultHandleWhenThereIsAnErrorConsumingAnInitialResult()
            throws InterruptedException {
        // given
        SubscriptionQueryMessage<String, String, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), "emitFirstThenReturnInitial", TEST_PAYLOAD,
                instanceOf(String.class), instanceOf(String.class)
        );

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
    void subscriptionQueryResultHandleWhenThereIsAnErrorConsumingAnUpdate() {
        // given
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), "chatMessages", TEST_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );

        // when
        List<String> initialResult = new ArrayList<>();
        List<String> updates = new ArrayList<>();
        queryBus.subscriptionQuery(queryMessage, 1)
                .handle(initial -> initialResult.addAll(initial.getPayload()),
                        update -> {
                            updates.add(update.getPayload());
                            throw new IllegalStateException("oops");
                        });
        chatQueryHandler.emitter.emit(String.class, TEST_PAYLOAD::equals, "Update1");
        chatQueryHandler.emitter.emit(String.class, TEST_PAYLOAD::equals, "Update2");

        // then
        assertEquals(Arrays.asList("Message1", "Message2", "Message3"), initialResult);
        assertEquals(Collections.singletonList("Update1"), updates);

        assertTrue(queryUpdateEmitter.activeSubscriptions().isEmpty(), "Expected subscriptions to be cancelled");
    }

    @Test
    void subscriptionQueryResultHandleWhenThereIsAnErrorConsumingABufferedUpdate() {
        // given
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), "chatMessages", TEST_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );

        // when
        List<String> initialResult = new ArrayList<>();
        List<String> updates = new ArrayList<>();
        queryBus.subscriptionQuery(queryMessage, 1)
                .handle(initial -> {
                            // make sure the update is emitted before subscribing to updates
                            chatQueryHandler.emitter.emit(String.class, TEST_PAYLOAD::equals, "Update1");
                            initialResult.addAll(initial.getPayload());
                        },
                        update -> {
                            updates.add(update.getPayload());
                            throw new IllegalStateException("oops");
                        });
        chatQueryHandler.emitter.emit(String.class, TEST_PAYLOAD::equals, "Update2");

        // then
        assertEquals(Arrays.asList("Message1", "Message2", "Message3"), initialResult);
        assertEquals(Collections.singletonList("Update1"), updates);

        assertTrue(queryUpdateEmitter.activeSubscriptions().isEmpty(), "Expected subscriptions to be cancelled");
    }

    @Test
    void subscriptionQueryResultHandleWhenThereIsAnErrorOnInitialResult() {
        // given
        SubscriptionQueryMessage<String, String, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), "failingQuery", TEST_PAYLOAD,
                instanceOf(String.class), instanceOf(String.class)
        );

        // when
        List<String> initialResult = new ArrayList<>();
        List<String> updates = new ArrayList<>();
        queryBus.subscriptionQuery(queryMessage).handle(initial -> initialResult.add(initial.getPayload()),
                                                        update -> updates.add(update.getPayload()));
        chatQueryHandler.emitter.emit(String.class, TEST_PAYLOAD::equals, "Update1");
        chatQueryHandler.emitter.emit(String.class, TEST_PAYLOAD::equals, "Update2");
        chatQueryHandler.emitter.complete(String.class, TEST_PAYLOAD::equals);

        // then
        assertTrue(initialResult.isEmpty());
        assertTrue(updates.isEmpty());
    }

    @Test
    void subscriptionQueryResultHandleWhenThereIsAnErrorOnUpdate() {
        // given
        SubscriptionQueryMessage<String, String, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                new MessageType("query"), "failingQuery", TEST_PAYLOAD,
                instanceOf(String.class), instanceOf(String.class)
        );

        // when
        List<String> initialResult = new ArrayList<>();
        List<String> updates = new ArrayList<>();
        queryBus.subscriptionQuery(queryMessage)
                .handle(initial -> initialResult.add(initial.getPayload()), update -> updates.add(update.getPayload()));
        chatQueryHandler.emitter.completeExceptionally(String.class, TEST_PAYLOAD::equals, new RuntimeException());
        chatQueryHandler.emitter.emit(String.class, TEST_PAYLOAD::equals, "Update1");

        // then
        assertTrue(initialResult.isEmpty());
        assertTrue(updates.isEmpty());
    }

    @Test
    void queryGatewayCorrectlyReturnsNullOnSubscriptionQueryWithNullInitialResult()
            throws ExecutionException, InterruptedException {
        QueryGateway queryGateway = DefaultQueryGateway.builder().queryBus(queryBus).build();

        assertNull(queryGateway.subscriptionQuery(new SomeQuery("not " + FOUND), String.class, String.class)
                               .initialResult()
                               .toFuture().get());
    }

    @Test
    void queryGatewayCorrectlyReturnsOnSubscriptionQuery() throws ExecutionException, InterruptedException {
        QueryGateway queryGateway = DefaultQueryGateway.builder().queryBus(queryBus).build();
        String result = queryGateway.subscriptionQuery(new SomeQuery(FOUND), String.class, String.class)
                                    .initialResult()
                                    .toFuture().get();

        assertEquals(FOUND, result);
    }

    private record SomeQuery(String filter) {

    }

    @SuppressWarnings("unused")
    private static class ChatQueryHandler {

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
                             TEST_PAYLOAD::equals,
                             new GenericSubscriptionQueryUpdateMessage<>(new MessageType("query-string"), "Update1"));
                emitter.emit(String.class,
                             TEST_PAYLOAD::equals,
                             new GenericSubscriptionQueryUpdateMessage<>(new MessageType("query-string"), "Update2"));
                emitter.complete(String.class, TEST_PAYLOAD::equals);
                latch.countDown();
            });

            latch.await();

            return "Initial";
        }

        @QueryHandler
        public String someQueryHandler(SomeQuery query) {
            return FOUND.equals(query.filter()) ? FOUND : null;
        }
    }
}
