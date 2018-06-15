/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.queryhandling;

import org.axonframework.messaging.Message;
import org.axonframework.queryhandling.annotation.AnnotationQueryHandlerAdapter;
import org.axonframework.queryhandling.responsetypes.ResponseTypes;
import org.junit.*;
import reactor.core.publisher.FluxSink;
import reactor.test.StepVerifier;
import reactor.util.concurrent.Queues;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Tests for subscription query functionality.
 *
 * @author Milan Savic
 */
public class SubscriptionQueryTest {

    private final SimpleQueryBus queryBus = new SimpleQueryBus();
    private final ChatQueryHandler chatQueryHandler = new ChatQueryHandler(queryBus);
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
                                      GenericSubscriptionQueryUpdateMessage.from("Update12"));
        StepVerifier.create(result1.initialResult().map(Message::getPayload))
                    .expectNext(Arrays.asList("Message1", "Message2", "Message3"))
                    .expectComplete()
                    .verify();
        StepVerifier.create(result1.updates().map(Message::getPayload))
                    .expectNext("Update11")
                    .expectComplete()
                    .verify();

        chatQueryHandler.emitter.emit(Integer.class, m -> m == 5, GenericSubscriptionQueryUpdateMessage.from(1));
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
        assertEquals(Arrays.asList("Update1", "Update2", "Update3", "Update4", "Update5", "Update6", "Update7", "Update8", "Update9", "Update10", "Update11"), update1);
        assertEquals(Arrays.asList("Update1", "Update2", "Update3", "Update4", "Update5", "Update6", "Update7", "Update8", "Update9", "Update10", "Update11"), update2);
        assertEquals(Arrays.asList("Update2", "Update3", "Update4", "Update5", "Update6", "Update7", "Update8", "Update9", "Update10", "Update11"), update3);
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
                .subscriptionQuery(queryMessage, new SubscriptionQueryBackpressure(FluxSink.OverflowStrategy.ERROR), 200);

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
            Executors.newSingleThreadExecutor().submit(() -> {
                emitter.emit(String.class,
                             "axonFrameworkCR"::equals,
                             GenericSubscriptionQueryUpdateMessage.from("Update1"));
                emitter.emit(String.class,
                             "axonFrameworkCR"::equals,
                             GenericSubscriptionQueryUpdateMessage.from("Update2"));
                emitter.complete(String.class, "axonFrameworkCR"::equals);
            });

            Thread.sleep(200);

            return "Initial";
        }
    }
}
