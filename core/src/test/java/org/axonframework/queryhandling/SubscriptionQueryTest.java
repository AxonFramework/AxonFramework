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

import org.axonframework.common.Registration;
import org.axonframework.queryhandling.annotation.AnnotationQueryHandlerAdapter;
import org.axonframework.queryhandling.responsetypes.ResponseTypes;
import org.junit.*;
import org.mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

import static org.mockito.Mockito.*;

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

    @SuppressWarnings("unchecked")
    @Test
    public void testEmittingAnUpdate() {
        // given
        SubscriptionQueryMessage<String, List<String>, String> queryMessage1 = new GenericSubscriptionQueryMessage<>(
                "axonFrameworkCR",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class));
        UpdateHandler<List<String>, String> updateHandler1 = mock(UpdateHandler.class);

        SubscriptionQueryMessage<Integer, Integer, Integer> queryMessage2 = new GenericSubscriptionQueryMessage<>(
                5,
                "numberOfMessages",
                ResponseTypes.instanceOf(Integer.class),
                ResponseTypes.instanceOf(Integer.class));
        UpdateHandler<Integer, Integer> updateHandler2 = mock(UpdateHandler.class);

        // when
        Registration registration1 = queryBus.subscriptionQuery(queryMessage1, updateHandler1);
        Registration registration2 = queryBus.subscriptionQuery(queryMessage2, updateHandler2);

        // then
        chatQueryHandler.emitter.emit(String.class, "axonFrameworkCR"::equals, "Update11");
        registration1.cancel();
        chatQueryHandler.emitter.emit(String.class,
                                      "axonFrameworkCR"::equals,
                                      GenericSubscriptionQueryUpdateMessage.from("Update12"));
        verify(updateHandler1).onInitialResult(Arrays.asList("Message1", "Message2", "Message3"));
        verify(updateHandler1).onUpdate("Update11");
        verify(updateHandler1, times(0)).onUpdate("Update12");

        chatQueryHandler.emitter.emit(Integer.class, m -> m == 5, GenericSubscriptionQueryUpdateMessage.from(1));
        registration2.cancel();
        chatQueryHandler.emitter.emit(Integer.class, m -> m == 5, 2);
        verify(updateHandler2).onInitialResult(0);
        verify(updateHandler2).onUpdate(1);
        verify(updateHandler2, times(0)).onUpdate(2);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCompletingSubscriptionQuery() {
        // given
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "axonFrameworkCR",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class));
        UpdateHandler<List<String>, String> updateHandler = mock(UpdateHandler.class);

        // when
        queryBus.subscriptionQuery(queryMessage, updateHandler);
        chatQueryHandler.emitter.emit(String.class,
                                      "axonFrameworkCR"::equals,
                                      GenericSubscriptionQueryUpdateMessage.from("Update1"));
        chatQueryHandler.emitter.complete(String.class, "axonFrameworkCR"::equals);

        // then
        verify(updateHandler).onInitialResult(Arrays.asList("Message1", "Message2", "Message3"));
        verify(updateHandler).onUpdate("Update1");
        verify(updateHandler).onCompleted();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCompletingSubscriptionQueryExceptionally() {
        // given
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "axonFrameworkCR",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class));
        UpdateHandler<List<String>, String> updateHandler = mock(UpdateHandler.class);
        RuntimeException toBeThrown = new RuntimeException();

        // when
        queryBus.subscriptionQuery(queryMessage, updateHandler);
        chatQueryHandler.emitter.emit(String.class, "axonFrameworkCR"::equals, "Update1");
        chatQueryHandler.emitter.completeExceptionally(String.class, "axonFrameworkCR"::equals, toBeThrown);

        // then
        verify(updateHandler).onInitialResult(Arrays.asList("Message1", "Message2", "Message3"));
        verify(updateHandler).onUpdate("Update1");
        verify(updateHandler).onCompletedExceptionally(toBeThrown);
    }


    @SuppressWarnings("unchecked")
    @Test
    public void testOrderingOfOperationOnUpdateHandler() throws InterruptedException {
        // given
        SubscriptionQueryMessage<String, String, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "axonFrameworkCR",
                "emitFirstThenReturnInitial",
                ResponseTypes.instanceOf(String.class),
                ResponseTypes.instanceOf(String.class));
        UpdateHandler<String, String> updateHandler = mock(UpdateHandler.class);

        // when
        queryBus.subscriptionQuery(queryMessage, updateHandler);
        // give some time to emits waiting on the lock to be emitted
        Thread.sleep(400);

        // then
        InOrder inOrder = inOrder(updateHandler);
        inOrder.verify(updateHandler).onInitialResult("Initial");
        inOrder.verify(updateHandler).onUpdate("Update1");
        inOrder.verify(updateHandler).onUpdate("Update2");
        inOrder.verify(updateHandler).onCompleted();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSubscribingQueryHandlerFailing() {
        // given
        SubscriptionQueryMessage<String, String, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "axonFrameworkCR",
                "failingQuery",
                ResponseTypes.instanceOf(String.class),
                ResponseTypes.instanceOf(String.class));
        UpdateHandler<String, String> updateHandler = mock(UpdateHandler.class);

        // when
        queryBus.subscriptionQuery(queryMessage, updateHandler);

        // then
        verify(updateHandler).onCompletedExceptionally(chatQueryHandler.toBeThrown);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testUpdateHandlerFailingOnInitialResult() {
        // given
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "axonFrameworkCR",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class));
        UpdateHandler<List<String>, String> updateHandler = mock(UpdateHandler.class);
        RuntimeException toBeThrown = new RuntimeException();
        doThrow(toBeThrown).when(updateHandler).onInitialResult(any());

        // when
        queryBus.subscriptionQuery(queryMessage, updateHandler);

        // then
        verify(updateHandler).onCompletedExceptionally(toBeThrown);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testUpdateHandlerFailingOnUpdate() {
        // given
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "axonFrameworkCR",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class));
        UpdateHandler<List<String>, String> updateHandler = mock(UpdateHandler.class);
        RuntimeException toBeThrown = new RuntimeException();
        doThrow(toBeThrown).when(updateHandler).onUpdate(any());

        // when
        queryBus.subscriptionQuery(queryMessage, updateHandler);
        chatQueryHandler.emitter.emit(String.class,
                                      "axonFrameworkCR"::equals,
                                      GenericSubscriptionQueryUpdateMessage.from("Update"));

        // then
        verify(updateHandler).onInitialResult(Arrays.asList("Message1", "Message2", "Message3"));
        verify(updateHandler).onCompletedExceptionally(toBeThrown);
    }


    @SuppressWarnings("unchecked")
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
        UpdateHandler<List<String>, String> updateHandler = mock(UpdateHandler.class);

        // when
        queryBus.subscriptionQuery(queryMessage, updateHandler);

        // then
        verify(updateHandler).onInitialResult(interceptedResponse);
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
