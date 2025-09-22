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
import org.axonframework.messaging.MessageType;
import org.axonframework.queryhandling.annotation.AnnotationQueryHandlerAdapter;
import org.axonframework.queryhandling.annotation.QueryHandler;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
import static org.axonframework.messaging.responsetypes.ResponseTypes.multipleInstancesOf;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for different types of queries hitting query handlers with {@link Future} or {@link CompletableFuture} as the
 * response type.
 *
 * @author Milan Savic
 */
@Disabled("TODO #3488")
class FutureAsResponseTypeToQueryHandlersTest {

    private static final TypeReference<List<String>> LIST_OF_STRINGS = new TypeReference<>() {
    };
    private static final int FUTURE_RESOLVING_TIMEOUT = 500;

    private final QueryBus queryBus = QueryBusTestUtils.aQueryBus();
    private final MyQueryHandler myQueryHandler = new MyQueryHandler();
    private final AnnotationQueryHandlerAdapter<MyQueryHandler> annotationQueryHandlerAdapter =
            new AnnotationQueryHandlerAdapter<>(myQueryHandler);

    @BeforeEach
    void setUp() {
        annotationQueryHandlerAdapter.subscribe(queryBus);
    }

    @Test
    void queryWithMultipleResponses() throws ExecutionException, InterruptedException {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("myQueryWithMultipleResponses"), "criteria",
                multipleInstancesOf(String.class)
        );

        List<?> response = queryBus.query(testQuery).get().payloadAs(List.class);

        assertEquals(asList("Response1", "Response2"), response);
    }

    @Test
    void queryWithSingleResponse() throws ExecutionException, InterruptedException {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("myQueryWithSingleResponse"), "criteria",
                instanceOf(String.class)
        );

        Object response = queryBus.query(testQuery).get().payload();

        assertEquals("Response", response);
    }

    @Test
    void scatterGatherQueryWithMultipleResponses() {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("myQueryWithMultipleResponses"), "criteria",
                multipleInstancesOf(String.class)
        );

        List<String> response =
                queryBus.scatterGather(testQuery, FUTURE_RESOLVING_TIMEOUT * 2, TimeUnit.MILLISECONDS)
                        .map(m -> m.payloadAs(LIST_OF_STRINGS))
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());

        assertEquals(asList("Response1", "Response2"), response);
    }

    @Test
    void scatterGatherQueryWithSingleResponse() {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("myQueryWithSingleResponse"), "criteria",
                instanceOf(String.class)
        );

        Object response =
                queryBus.scatterGather(testQuery, FUTURE_RESOLVING_TIMEOUT + 100, TimeUnit.MILLISECONDS)
                        .map(Message::payload)
                        .findFirst()
                        .orElse(null);

        assertEquals("Response", response);
    }

    @Test
    void subscriptionQueryWithMultipleResponses() {
        SubscriptionQueryMessage<String, List<String>, String> testQuery = new GenericSubscriptionQueryMessage<>(
                new MessageType("myQueryWithMultipleResponses"), "criteria",
                multipleInstancesOf(String.class), instanceOf(String.class)
        );

        Mono<List<String>> response = queryBus.subscriptionQuery(testQuery)
                                              .initialResult()
                                              .map(m -> m.payloadAs(LIST_OF_STRINGS));

        StepVerifier.create(response)
                    .expectNext(asList("Response1", "Response2"))
                    .verifyComplete();
    }

    @Test
    void subscriptionQueryWithSingleResponse() {
        SubscriptionQueryMessage<String, String, String> testQuery = new GenericSubscriptionQueryMessage<>(
                new MessageType("myQueryWithSingleResponse"), "criteria",
                instanceOf(String.class), instanceOf(String.class)
        );

        Mono<String> response = queryBus.subscriptionQuery(testQuery)
                                        .initialResult()
                                        .map(m -> m.payloadAs(String.class));

        StepVerifier.create(response)
                    .expectNext("Response")
                    .verifyComplete();
    }

    @Test
    void futureQueryWithMultipleResponses() throws ExecutionException, InterruptedException {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("myQueryFutureWithMultipleResponses"), "criteria",
                multipleInstancesOf(String.class)
        );

        List<String> result = queryBus.query(testQuery)
                                      .get()
                                      .payloadAs(LIST_OF_STRINGS);

        assertEquals(asList("Response1", "Response2"), result);
    }

    @Test
    void futureScatterGatherQueryWithMultipleResponses() {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("myQueryFutureWithMultipleResponses"), "criteria",
                multipleInstancesOf(String.class)
        );

        List<String> result =
                queryBus.scatterGather(testQuery, FUTURE_RESOLVING_TIMEOUT + 100, TimeUnit.MILLISECONDS)
                        .map(m -> m.payloadAs(LIST_OF_STRINGS))
                        .findFirst()
                        .orElse(null);

        assertEquals(asList("Response1", "Response2"), result);
    }

    @Test
    void futureSubscriptionQueryWithMultipleResponses() {
        SubscriptionQueryMessage<String, List<String>, String> testQuery = new GenericSubscriptionQueryMessage<>(
                new MessageType("myQueryFutureWithMultipleResponses"), "criteria",
                multipleInstancesOf(String.class), instanceOf(String.class)
        );

        Mono<List<String>> response = queryBus.subscriptionQuery(testQuery)
                                              .initialResult()
                                              .map(m -> m.payloadAs(LIST_OF_STRINGS));

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
        public Future<String> queryHandler2(String criteria) {
            return executor.schedule(() -> "Response",
                                     FUTURE_RESOLVING_TIMEOUT,
                                     TimeUnit.MILLISECONDS);
        }

        @QueryHandler(queryName = "myQueryFutureWithMultipleResponses")
        public Future<List<String>> queryHandler3(String criteria) {
            return executor.schedule(() -> asList("Response1", "Response2"),
                                     FUTURE_RESOLVING_TIMEOUT,
                                     TimeUnit.MILLISECONDS);
        }
    }
}
