/*
 * Copyright (c) 2010-2020. Axon Framework
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
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link DefaultReactiveQueryGateway}.
 *
 * @author Milan Savic
 */
public class DefaultReactiveQueryGatewayTest {

    private DefaultReactiveQueryGateway reactiveQueryGateway;
    private QueryUpdateEmitter queryUpdateEmitter;
    private MessageHandler<QueryMessage<?, Object>> queryMessageHandler1;
    private MessageHandler<QueryMessage<?, Object>> queryMessageHandler2;

    @BeforeEach
    void setUp() {
        SimpleQueryBus queryBus = SimpleQueryBus.builder().build();
        queryUpdateEmitter = queryBus.queryUpdateEmitter();
        queryMessageHandler1 = spy(new MessageHandler<QueryMessage<?, Object>>() {
            @Override
            public Object handle(QueryMessage<?, Object> message) {
                return "handled";
            }
        });
        queryMessageHandler2 = spy(new MessageHandler<QueryMessage<?, Object>>() {
            @Override
            public Object handle(QueryMessage<?, Object> message) {
                return "handled";
            }
        });
        queryBus.subscribe(String.class.getName(), String.class, queryMessageHandler1);
        queryBus.subscribe(String.class.getName(), String.class, queryMessageHandler2);
        queryBus.subscribe(Integer.class.getName(), Integer.class, message -> {
            throw new RuntimeException();
        });
        queryBus.subscribe(Boolean.class.getName(),
                           String.class,
                           message -> "" + message.getMetaData().getOrDefault("key1", "")
                                   + message.getMetaData().getOrDefault("key2", ""));
        queryBus.subscribe(Long.class.getName(), String.class, message -> null);
        reactiveQueryGateway = DefaultReactiveQueryGateway.builder()
                                                          .queryBus(queryBus)
                                                          .build();
    }

    @Test
    void testQuery() throws Exception {
        Mono<String> result = reactiveQueryGateway.query("criteria", String.class);
        verifyZeroInteractions(queryMessageHandler1);
        verifyZeroInteractions(queryMessageHandler2);
        StepVerifier.create(result)
                    .expectNext("handled")
                    .verifyComplete();
        verify(queryMessageHandler1).handle(any());
    }

    @Test
    void testQueryReturningNull() {
        assertNull(reactiveQueryGateway.query(0L, String.class).block());
        StepVerifier.create(reactiveQueryGateway.query(0L, String.class))
                    .expectNext()
                    .verifyComplete();
    }

    @Test
    void testQueryWithDispatchInterceptor() {
        reactiveQueryGateway
                .registerDispatchInterceptor(() -> queryMono -> queryMono
                        .map(query -> query.andMetaData(Collections.singletonMap("key1", "value1"))));
        Registration registration2 = reactiveQueryGateway
                .registerDispatchInterceptor(() -> queryMono -> queryMono
                        .map(query -> query.andMetaData(Collections.singletonMap("key2", "value2"))));

        StepVerifier.create(reactiveQueryGateway.query(true, String.class))
                    .expectNext("value1value2")
                    .verifyComplete();

        registration2.cancel();

        StepVerifier.create(reactiveQueryGateway.query(true, String.class))
                    .expectNext("value1")
                    .verifyComplete();
    }

    @Test
    void testQueryWithDispatchInterceptorThrowingAnException() {
        reactiveQueryGateway
                .registerDispatchInterceptor(() -> queryMono -> {
                    throw new RuntimeException();
                });
        StepVerifier.create(reactiveQueryGateway.query(true, String.class))
                    .verifyError(RuntimeException.class);
    }

    @Test
    void testQueryWithDispatchInterceptorReturningErrorMono() {
        reactiveQueryGateway
                .registerDispatchInterceptor(() -> queryMono -> Mono.error(new RuntimeException()));
        StepVerifier.create(reactiveQueryGateway.query(true, String.class))
                    .verifyError(RuntimeException.class);
    }

    @Test
    void testQueryFails() {
        StepVerifier.create(reactiveQueryGateway.query(5, Integer.class))
                    .verifyError(RuntimeException.class);
    }

    @Test
    void testScatterGather() throws Exception {
        Flux<String> result = reactiveQueryGateway.scatterGather("criteria",
                                                                 ResponseTypes.instanceOf(String.class),
                                                                 1,
                                                                 TimeUnit.SECONDS);
        verifyZeroInteractions(queryMessageHandler1);
        verifyZeroInteractions(queryMessageHandler2);
        StepVerifier.create(result)
                    .expectNext("handled", "handled")
                    .verifyComplete();
        verify(queryMessageHandler1).handle(any());
        verify(queryMessageHandler2).handle(any());
    }

    @Test
    void testScatterGatherReturningNull() {
        assertNull(reactiveQueryGateway.scatterGather(0L, ResponseTypes.instanceOf(String.class), 1, TimeUnit.SECONDS)
                                       .blockFirst());
        StepVerifier.create(reactiveQueryGateway
                                    .scatterGather(0L, ResponseTypes.instanceOf(String.class), 1, TimeUnit.SECONDS))
                    .expectNext()
                    .verifyComplete();
    }

    @Test
    void testScatterGatherWithDispatchInterceptor() {
        reactiveQueryGateway
                .registerDispatchInterceptor(() -> queryMono -> queryMono
                        .map(query -> query.andMetaData(Collections.singletonMap("key1", "value1"))));
        Registration registration2 = reactiveQueryGateway
                .registerDispatchInterceptor(() -> queryMono -> queryMono
                        .map(query -> query.andMetaData(Collections.singletonMap("key2", "value2"))));

        StepVerifier.create(reactiveQueryGateway
                                    .scatterGather(true, ResponseTypes.instanceOf(String.class), 1, TimeUnit.SECONDS))
                    .expectNext("value1value2")
                    .verifyComplete();

        registration2.cancel();

        StepVerifier.create(reactiveQueryGateway.query(true, String.class))
                    .expectNext("value1")
                    .verifyComplete();
    }

    @Test
    void testScatterGatherWithDispatchInterceptorThrowingAnException() {
        reactiveQueryGateway
                .registerDispatchInterceptor(() -> queryMono -> {
                    throw new RuntimeException();
                });
        StepVerifier.create(reactiveQueryGateway
                                    .scatterGather(true, ResponseTypes.instanceOf(String.class), 1, TimeUnit.SECONDS))
                    .verifyError(RuntimeException.class);
    }

    @Test
    void testScatterGatherWithDispatchInterceptorReturningErrorMono() {
        reactiveQueryGateway
                .registerDispatchInterceptor(() -> queryMono -> Mono.error(new RuntimeException()));
        StepVerifier.create(reactiveQueryGateway
                                    .scatterGather(true, ResponseTypes.instanceOf(String.class), 1, TimeUnit.SECONDS))
                    .verifyError(RuntimeException.class);
    }

    @Test
    void testScatterGatherFails() {
        StepVerifier.create(reactiveQueryGateway.scatterGather(6,
                                                               ResponseTypes.instanceOf(Integer.class),
                                                               1,
                                                               TimeUnit.SECONDS))
                    .verifyError(RuntimeException.class);
    }

    @Test
    void testSubscriptionQuery() throws Exception {
        Mono<SubscriptionQueryResult<String, String>> monoResult = reactiveQueryGateway.subscriptionQuery("criteria",
                                                                                                          String.class,
                                                                                                          String.class);
        verifyZeroInteractions(queryMessageHandler1);
        verifyZeroInteractions(queryMessageHandler2);

        SubscriptionQueryResult<String, String> result = monoResult.block();
        assertNotNull(result);
        StepVerifier.create(result.initialResult())
                    .expectNext("handled")
                    .verifyComplete();
        StepVerifier.create(result.updates()
                                  .doOnSubscribe(s -> {
                                      queryUpdateEmitter.emit(String.class, q -> true, "update");
                                      queryUpdateEmitter.complete(String.class, q -> true);
                                  }))
                    .expectNext("update")
                    .verifyComplete();
        verify(queryMessageHandler1).handle(any());
    }

    @Test
    void testSubscriptionQueryReturningNull() {
        SubscriptionQueryResult<String, String> result = reactiveQueryGateway.subscriptionQuery(0L,
                                                                                                String.class,
                                                                                                String.class)
                                                                             .block();
        assertNotNull(result);
        assertNull(result.initialResult().block());
        StepVerifier.create(result.initialResult())
                    .expectNext()
                    .verifyComplete();
        StepVerifier.create(result.updates()
                                  .doOnSubscribe(s -> {
                                      queryUpdateEmitter.emit(Long.class, q -> true, (String) null);
                                      queryUpdateEmitter.complete(Long.class, q -> true);
                                  }))
                    .expectNext()
                    .verifyComplete();
    }

    @Test
    void testSubscriptionQueryWithDispatchInterceptor() {
        reactiveQueryGateway
                .registerDispatchInterceptor(() -> queryMono -> queryMono
                        .map(query -> query.andMetaData(Collections.singletonMap("key1", "value1"))));
        Registration registration2 = reactiveQueryGateway
                .registerDispatchInterceptor(() -> queryMono -> queryMono
                        .map(query -> query.andMetaData(Collections.singletonMap("key2", "value2"))));

        Mono<SubscriptionQueryResult<String, String>> monoResult = reactiveQueryGateway
                .subscriptionQuery(true, String.class, String.class);
        SubscriptionQueryResult<String, String> result = monoResult.block();
        assertNotNull(result);
        StepVerifier.create(result.initialResult())
                    .expectNext("value1value2")
                    .verifyComplete();

        registration2.cancel();

        monoResult = reactiveQueryGateway.subscriptionQuery(true, String.class, String.class);
        result = monoResult.block();
        assertNotNull(result);
        StepVerifier.create(result.initialResult())
                    .expectNext("value1")
                    .verifyComplete();
    }

    @Test
    void testSubscriptionQueryWithDispatchInterceptorThrowingAnException() {
        reactiveQueryGateway
                .registerDispatchInterceptor(() -> queryMono -> {
                    throw new RuntimeException();
                });
        StepVerifier.create(reactiveQueryGateway.subscriptionQuery(true, String.class, String.class))
                    .verifyError(RuntimeException.class);
    }

    @Test
    void testSubscriptionQueryWithDispatchInterceptorReturningErrorMono() {
        reactiveQueryGateway
                .registerDispatchInterceptor(() -> queryMono -> Mono.error(new RuntimeException()));
        StepVerifier.create(reactiveQueryGateway.subscriptionQuery(true, String.class, String.class))
                    .verifyError(RuntimeException.class);
    }

    @Test
    void testSubscriptionQueryFails() {
        Mono<SubscriptionQueryResult<Integer, Integer>> monoResult = reactiveQueryGateway.subscriptionQuery(6,
                                                                                                            Integer.class,
                                                                                                            Integer.class);
        SubscriptionQueryResult<Integer, Integer> result = monoResult.block();
        assertNotNull(result);
        StepVerifier.create(result.initialResult())
                    .verifyError(RuntimeException.class);
    }
}
