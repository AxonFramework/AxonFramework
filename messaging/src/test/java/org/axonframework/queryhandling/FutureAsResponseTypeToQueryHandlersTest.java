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

package org.axonframework.queryhandling;

import org.axonframework.common.TypeReference;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.queryhandling.annotations.AnnotatedQueryHandlingComponent;
import org.axonframework.queryhandling.annotations.QueryHandler;
import org.axonframework.serialization.PassThroughConverter;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for different types of queries hitting query handlers with {@link Future} or {@link CompletableFuture} as the
 * response type.
 *
 * @author Milan Savic
 */
class FutureAsResponseTypeToQueryHandlersTest {

    private static final MessageType RESPONSE_TYPE = new MessageType(String.class);
    private static final TypeReference<List<String>> LIST_OF_STRINGS = new TypeReference<>() {
    };
    private static final int FUTURE_RESOLVING_TIMEOUT = 500;

    private QueryBus queryBus;

    @BeforeEach
    void setUp() {
        queryBus = QueryBusTestUtils.aQueryBus();

        MyQueryHandler myQueryHandler = new MyQueryHandler();
        QueryHandlingComponent queryHandlingComponent =
                new AnnotatedQueryHandlingComponent<>(myQueryHandler, PassThroughConverter.MESSAGE_INSTANCE);
        queryBus.subscribe(queryHandlingComponent);
    }

    @Disabled("TODO #3717")
    @Test
    void queryWithMultipleResponses() throws ExecutionException, InterruptedException {
        QueryMessage testQuery =
                new GenericQueryMessage(new MessageType("myQueryWithMultipleResponses"), "criteria", RESPONSE_TYPE);

        List<String> result = queryBus.query(testQuery, null)
                                      .reduce(new ArrayList<String>(), (list, entry) -> {
                                          list.add(entry.message().payloadAs(String.class));
                                          return list;
                                      })
                                      .get();

        assertEquals(asList("Response1", "Response2"), result);
    }

    @Test
    void queryWithSingleResponse() throws ExecutionException, InterruptedException {
        QueryMessage testQuery =
                new GenericQueryMessage(new MessageType("myQueryWithSingleResponse"), "criteria", RESPONSE_TYPE);

        MessageStream<QueryResponseMessage> resultStream = queryBus.query(testQuery, null);
        Object result = resultStream.first()
                                    .asCompletableFuture()
                                    .thenApply(MessageStream.Entry::message)
                                    .thenApply(Message::payload)
                                    .get();

        assertEquals("Response", result);
        assertThat(resultStream.hasNextAvailable()).isTrue();
    }

    @Test
    void subscriptionQueryWithMultipleResponses() {
        SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                new MessageType("myQueryWithMultipleResponses"), "criteria",
                RESPONSE_TYPE
        );

        Flux<List<String>> response = queryBus.subscriptionQuery(testQuery, null, 50)
                                              .asFlux()
                                              .map(MessageStream.Entry::message)
                                              .mapNotNull(m -> m.payloadAs(LIST_OF_STRINGS));
        queryBus.completeSubscriptions(s -> true, null);
        StepVerifier.create(response)
                    .expectNext(asList("Response1", "Response2"))
                    .verifyComplete();
    }

    @Test
    void subscriptionQueryWithSingleResponse() {
        SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                new MessageType("myQueryWithSingleResponse"), "criteria", RESPONSE_TYPE);

        Flux<String> response = queryBus.subscriptionQuery(testQuery, null, 50)
                                        .asFlux()
                                        .map(MessageStream.Entry::message)
                                        .mapNotNull(m -> m.payloadAs(String.class));
        queryBus.completeSubscriptions(s -> true, null);

        StepVerifier.create(response)
                    .expectNext("Response")
                    .verifyComplete();
    }

    @Disabled("TODO #3717")
    @Test
    void futureQueryWithMultipleResponses() throws ExecutionException, InterruptedException {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("myQueryFutureWithMultipleResponses"), "criteria", RESPONSE_TYPE
        );

        MessageStream<QueryResponseMessage> query = queryBus.query(testQuery, null);
        queryBus.completeSubscriptions(s -> true, null);
        List<String> result = query.reduce(new ArrayList<String>(), (list, entry) -> {
                                          list.add(entry.message().payloadAs(String.class));
                                          return list;
                                      })
                                      .get();
        assertEquals(asList("Response1", "Response2"), result);
    }

    @Disabled("TODO #3717")
    @Test
    void futureSubscriptionQueryWithMultipleResponses() {
        SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                new MessageType("myQueryFutureWithMultipleResponses"), "criteria", RESPONSE_TYPE);

        Flux<List<String>> response = queryBus.subscriptionQuery(testQuery, null, 50)
                                              .asFlux()
                                              .map(MessageStream.Entry::message)
                                              .mapNotNull(m -> m.payloadAs(LIST_OF_STRINGS));
        queryBus.completeSubscriptions(s -> true, null);

        StepVerifier.create(response)
                    .expectNext(asList("Response1", "Response2"))
                    .verifyComplete();
    }

    @SuppressWarnings("unused")
    private static class MyQueryHandler {

        private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        @QueryHandler(queryName = "myQueryWithMultipleResponses")
        public CompletableFuture<List<String>> queryHandler1(String criteria) {
            CompletableFuture<List<String>> completableFuture = new CompletableFuture<>();
            executor.schedule(() -> completableFuture.complete(asList("Response1", "Response2")),
                              FUTURE_RESOLVING_TIMEOUT,
                              TimeUnit.MILLISECONDS);
            return completableFuture;
        }

        @QueryHandler(queryName = "myQueryWithSingleResponse")
        public CompletableFuture<String> queryHandler2(String criteria) {
            CompletableFuture<String> completableFuture = new CompletableFuture<>();
            executor.schedule(() -> completableFuture.complete("Response"),
                              FUTURE_RESOLVING_TIMEOUT,
                              TimeUnit.MILLISECONDS);
            return completableFuture;
        }

        @QueryHandler(queryName = "myQueryFutureWithMultipleResponses")
        public Future<List<String>> queryHandler3(String criteria) {
            return executor.schedule(() -> asList("Response1", "Response2"),
                                     FUTURE_RESOLVING_TIMEOUT,
                                     TimeUnit.MILLISECONDS);
        }
    }
}
