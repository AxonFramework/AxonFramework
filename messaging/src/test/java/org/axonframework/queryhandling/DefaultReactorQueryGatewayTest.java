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
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.junit.jupiter.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.axonframework.common.ReflectionUtils.methodOf;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link DefaultReactorQueryGateway}.
 *
 * @author Milan Savic
 */
@ExtendWith(MockitoExtension.class)
class DefaultReactorQueryGatewayTest {

    private DefaultReactorQueryGateway reactiveQueryGateway;
    private QueryUpdateEmitter queryUpdateEmitter;
    private MessageHandler<QueryMessage<?, Object>> queryMessageHandler1;
    private MessageHandler<QueryMessage<?, Object>> queryMessageHandler2;
    private MessageHandler<QueryMessage<?, Object>> queryMessageHandler3;
    private SimpleQueryBus queryBus;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        Hooks.enableContextLossTracking();
        Hooks.onOperatorDebug();

        queryBus = spy(SimpleQueryBus.builder().build());

        queryUpdateEmitter = queryBus.queryUpdateEmitter();
        AtomicInteger count = new AtomicInteger();
        queryMessageHandler1 = spy(new MessageHandler<QueryMessage<?, Object>>() {

            @Override
            public Object handle(QueryMessage<?, Object> message) {
                if ("backpressure".equals(message.getPayload())) {
                    return count.incrementAndGet();
                }
                return "handled";
            }
        });
        queryMessageHandler2 = spy(new MessageHandler<QueryMessage<?, Object>>() {
            @Override
            public Object handle(QueryMessage<?, Object> message) {
                if ("backpressure".equals(message.getPayload())) {
                    return count.incrementAndGet();
                }
                return "handled";
            }
        });

        queryMessageHandler3 = spy(new MessageHandler<QueryMessage<?, Object>>() {
            @Override
            public Object handle(QueryMessage<?, Object> message) {
                throw new RuntimeException();
            }
        });

        queryBus.subscribe(String.class.getName(), String.class, queryMessageHandler1);
        queryBus.subscribe(String.class.getName(), String.class, queryMessageHandler2);
        queryBus.subscribe(Integer.class.getName(), Integer.class, queryMessageHandler3);

        queryBus.subscribe(Boolean.class.getName(),
                           String.class,
                           message -> "" + message.getMetaData().getOrDefault("key1", "")
                                   + message.getMetaData().getOrDefault("key2", ""));

        queryBus.subscribe(Long.class.getName(), String.class, message -> null);

        queryBus.subscribe(Double.class.getName(),
                           methodOf(this.getClass(), "stringListQueryHandler").getGenericReturnType(),
                           message -> Arrays.asList("value1", "value2", "value3"));

        reactiveQueryGateway = DefaultReactorQueryGateway.builder()
                                                         .queryBus(queryBus)
                                                         .build();
    }

    @SuppressWarnings("unused") // Used by 'testSubscriptionQueryMany()' to generate query handler response type
    public List<String> stringListQueryHandler() {
        return new ArrayList<>();
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
    void testQueryWithContext() throws Exception {
        Context context = Context.of("k1", "v1");

        Mono<String> result = reactiveQueryGateway.query("criteria", String.class).subscriberContext(context);

        verifyZeroInteractions(queryMessageHandler1);
        verifyZeroInteractions(queryMessageHandler2);
        StepVerifier.create(result)
                    .expectNext("handled")
                    .expectAccessibleContext()
                    .containsOnly(context)
                    .then()
                    .verifyComplete();
        verify(queryMessageHandler1).handle(any());
    }

    @Test
    void testMultipleQueries() throws Exception {
        Flux<QueryMessage<?, ?>> queries = Flux.fromIterable(Arrays.asList(
                new GenericQueryMessage<>("query1", ResponseTypes.instanceOf(String.class)),
                new GenericQueryMessage<>(4, ResponseTypes.instanceOf(Integer.class)),
                new GenericQueryMessage<>("query2", ResponseTypes.instanceOf(String.class)),
                new GenericQueryMessage<>(5, ResponseTypes.instanceOf(Integer.class)),
                new GenericQueryMessage<>(true, ResponseTypes.instanceOf(String.class))));

        Flux<Object> result = reactiveQueryGateway.query(queries);
        verifyZeroInteractions(queryMessageHandler1);
        verifyZeroInteractions(queryMessageHandler2);

        List<Throwable> exceptions = new ArrayList<>(2);
        StepVerifier.create(result.onErrorContinue((t, o) -> exceptions.add(t)))
                    .expectNext("handled", "handled", "")
                    .verifyComplete();

        assertEquals(2, exceptions.size());
        assertTrue(exceptions.get(0) instanceof RuntimeException);
        assertTrue(exceptions.get(1) instanceof RuntimeException);
        verify(queryMessageHandler1, times(2)).handle(any());
    }

    @Test
    void testMultipleQueriesOrdering() throws Exception {
        int numberOfQueries = 10_000;
        Flux<QueryMessage<?, ?>> queries = Flux
                .fromStream(IntStream.range(0, numberOfQueries)
                                     .mapToObj(i -> new GenericQueryMessage<>("backpressure",
                                                                              ResponseTypes.instanceOf(String.class))));
        List<Integer> expectedResults = IntStream.range(1, numberOfQueries + 1)
                                                 .boxed()
                                                 .collect(Collectors.toList());
        Flux<Object> result = reactiveQueryGateway.query(queries);
        StepVerifier.create(result)
                    .expectNext(expectedResults.toArray(new Integer[0]))
                    .verifyComplete();
        verify(queryMessageHandler1, times(numberOfQueries)).handle(any());
    }

    @Test
    void testQueryReturningNull() {
        assertNull(reactiveQueryGateway.query(0L, String.class).block());
        StepVerifier.create(reactiveQueryGateway.query(0L, String.class))
                    .expectComplete()
                    .verify();
    }

    @Test
    void testQueryWithDispatchInterceptor() {
        reactiveQueryGateway
                .registerDispatchInterceptor(queryMono -> queryMono
                        .map(query -> query.andMetaData(Collections.singletonMap("key1", "value1"))));
        Registration registration2 = reactiveQueryGateway
                .registerDispatchInterceptor(queryMono -> queryMono
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
    void testQueryWithDispatchInterceptorWithContext() {
        Context context = Context.of("security", true);

        reactiveQueryGateway
                .registerDispatchInterceptor(queryMono -> queryMono
                        .filterWhen(v -> Mono.subscriberContext()
                                             .filter(ctx -> ctx.hasKey("security"))
                                             .map(ctx -> ctx.get("security")))
                        .map(query -> query.andMetaData(Collections.singletonMap("key1", "value1"))));

        StepVerifier.create(reactiveQueryGateway.query(true, String.class).subscriberContext(context))
                    .expectNext("value1")
                    .expectAccessibleContext()
                    .containsOnly(context)
                    .then()
                    .verifyComplete();
    }

    @Test
    void testQueryWithResultInterceptorAlterResult() throws Exception {
        reactiveQueryGateway.registerResultHandlerInterceptor((q, results) -> results
                .map(it -> new GenericQueryResponseMessage<>("handled-modified")));

        Mono<String> result = reactiveQueryGateway.query("criteria", String.class);
        verifyZeroInteractions(queryMessageHandler1);
        verifyZeroInteractions(queryMessageHandler2);
        StepVerifier.create(result)
                    .expectNext("handled-modified")
                    .verifyComplete();
        verify(queryMessageHandler1).handle(any());
    }

    @Test
    void testQueryWithResultInterceptorFilterResult() throws Exception {
        reactiveQueryGateway.registerResultHandlerInterceptor((q, results) -> results
                .filter(it -> !it.getPayload().equals("handled")));

        Mono<String> result = reactiveQueryGateway.query("criteria", String.class);
        verifyZeroInteractions(queryMessageHandler1);
        verifyZeroInteractions(queryMessageHandler2);
        StepVerifier.create(result)
                    .expectComplete()
                    .verify();
        verify(queryMessageHandler1).handle(any());
    }

    @Test
    void testQueryWithDispatchInterceptorThrowingAnException() {
        reactiveQueryGateway
                .registerDispatchInterceptor(queryMono -> {
                    throw new RuntimeException();
                });
        StepVerifier.create(reactiveQueryGateway.query(true, String.class))
                    .verifyError(RuntimeException.class);
    }

    @Test
    void testQueryWithDispatchInterceptorReturningErrorMono() {
        reactiveQueryGateway
                .registerDispatchInterceptor(queryMono -> Mono.error(new RuntimeException()));
        StepVerifier.create(reactiveQueryGateway.query(true, String.class))
                    .verifyError(RuntimeException.class);
    }

    @Test
    void testQueryFails() {
        StepVerifier.create(reactiveQueryGateway.query(5, Integer.class))
                    .verifyError(RuntimeException.class);
    }

    @Test
    void testQueryFailsWithRetry() throws Exception {

        Mono<Integer> query = reactiveQueryGateway.query(5, Integer.class).retry(5);

        StepVerifier.create(query)
                    .verifyError(RuntimeException.class);

        verify(queryMessageHandler3, times(6)).handle(any());
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
    void testMultipleScatterGather() throws Exception {
        Flux<QueryMessage<?, ?>> queries = Flux.fromIterable(Arrays.asList(
                new GenericQueryMessage<>("query1", ResponseTypes.instanceOf(String.class)),
                new GenericQueryMessage<>(4, ResponseTypes.instanceOf(Integer.class)),
                new GenericQueryMessage<>("query2", ResponseTypes.instanceOf(String.class)),
                new GenericQueryMessage<>(5, ResponseTypes.instanceOf(Integer.class)),
                new GenericQueryMessage<>(true, ResponseTypes.instanceOf(String.class))));

        Flux<Object> result = reactiveQueryGateway.scatterGather(queries, 1, TimeUnit.SECONDS);
        verifyZeroInteractions(queryMessageHandler1);
        verifyZeroInteractions(queryMessageHandler2);

        StepVerifier.create(result)
                    .expectNext("handled", "handled", "handled", "handled", "")
                    .verifyComplete();

        verify(queryMessageHandler1, times(2)).handle(any());
        verify(queryMessageHandler2, times(2)).handle(any());
    }

    @Test
    void testMultipleScatterGatherOrdering() throws Exception {
        int numberOfQueries = 10_000;
        Flux<QueryMessage<?, ?>> queries = Flux
                .fromStream(IntStream.range(0, numberOfQueries)
                                     .mapToObj(i -> new GenericQueryMessage<>("backpressure",
                                                                              ResponseTypes.instanceOf(String.class))));
        List<Integer> expectedResults = IntStream.range(1, 2 * numberOfQueries + 1)
                                                 .boxed()
                                                 .collect(Collectors.toList());
        Flux<Object> result = reactiveQueryGateway.scatterGather(queries, 1, TimeUnit.SECONDS);
        StepVerifier.create(result)
                    .expectNext(expectedResults.toArray(new Integer[0]))
                    .verifyComplete();
        verify(queryMessageHandler1, times(numberOfQueries)).handle(any());
        verify(queryMessageHandler2, times(numberOfQueries)).handle(any());
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
                .registerDispatchInterceptor(queryMono -> queryMono
                        .map(query -> query.andMetaData(Collections.singletonMap("key1", "value1"))));
        Registration registration2 = reactiveQueryGateway
                .registerDispatchInterceptor(queryMono -> queryMono
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
    void testScatterGatherWithResultIntercept() throws Exception {
        reactiveQueryGateway
                .registerResultHandlerInterceptor(
                        (query, results) -> results
                                .map(it -> new GenericQueryResponseMessage<>("handled-modified"))
                );

        Flux<QueryMessage<?, ?>> queries = Flux.fromIterable(Arrays.asList(
                new GenericQueryMessage<>("query1", ResponseTypes.instanceOf(String.class)),
                new GenericQueryMessage<>(4, ResponseTypes.instanceOf(Integer.class)),
                new GenericQueryMessage<>("query2", ResponseTypes.instanceOf(String.class)),
                new GenericQueryMessage<>(5, ResponseTypes.instanceOf(Integer.class)),
                new GenericQueryMessage<>(true, ResponseTypes.instanceOf(String.class))));

        Flux<Object> result = reactiveQueryGateway.scatterGather(queries, 1, TimeUnit.SECONDS);
        verifyZeroInteractions(queryMessageHandler1);
        verifyZeroInteractions(queryMessageHandler2);

        StepVerifier.create(result)
                    .expectNext("handled-modified",
                                "handled-modified",
                                "handled-modified",
                                "handled-modified",
                                "handled-modified")
                    .verifyComplete();

        verify(queryMessageHandler1, times(2)).handle(any());
        verify(queryMessageHandler2, times(2)).handle(any());
    }

    @Test
    void testQueryWithResultInterceptorModifyResultBasedOnQuery() throws Exception {
        reactiveQueryGateway.registerDispatchInterceptor(q -> q.map(it ->
                                                                            it.andMetaData(Collections.singletonMap(
                                                                                    "block",
                                                                                    it.getPayload() instanceof Boolean)
                                                                            )));
        reactiveQueryGateway
                .registerResultHandlerInterceptor((q, results) -> results
                        .filter(it -> !((boolean) q.getMetaData().get("block")))
                );

        Flux<QueryMessage<?, ?>> queries = Flux.fromIterable(Arrays.asList(
                new GenericQueryMessage<>("query1", ResponseTypes.instanceOf(String.class)),
                new GenericQueryMessage<>(4, ResponseTypes.instanceOf(Integer.class)),
                new GenericQueryMessage<>("query2", ResponseTypes.instanceOf(String.class)),
                new GenericQueryMessage<>(5, ResponseTypes.instanceOf(Integer.class)),
                new GenericQueryMessage<>(Boolean.TRUE, ResponseTypes.instanceOf(String.class))));

        Flux<Object> result = reactiveQueryGateway.scatterGather(queries, 1, TimeUnit.SECONDS);
        verifyZeroInteractions(queryMessageHandler1);
        verifyZeroInteractions(queryMessageHandler2);

        StepVerifier.create(result)
                    .expectNext("handled", "handled", "handled", "handled")
                    .verifyComplete();

        verify(queryMessageHandler1, times(2)).handle(any());
        verify(queryMessageHandler2, times(2)).handle(any());
    }

    @Test
    void testScatterGatherWithResultInterceptReplacedWithError() throws Exception {
        reactiveQueryGateway
                .registerResultHandlerInterceptor(
                        (query, results) -> results.flatMap(r -> {
                            if (r.getPayload().equals("")) {
                                return Flux.<ResultMessage<?>>error(new RuntimeException("no empty strings allowed"));
                            } else {
                                return Flux.just(r);
                            }
                        })
                );

        Flux<QueryMessage<?, ?>> queries = Flux.fromIterable(Arrays.asList(
                new GenericQueryMessage<>("query1", ResponseTypes.instanceOf(String.class)),
                new GenericQueryMessage<>(4, ResponseTypes.instanceOf(Integer.class)),
                new GenericQueryMessage<>("query2", ResponseTypes.instanceOf(String.class)),
                new GenericQueryMessage<>(5, ResponseTypes.instanceOf(Integer.class)),
                new GenericQueryMessage<>(true, ResponseTypes.instanceOf(String.class))));

        Flux<Object> result = reactiveQueryGateway.scatterGather(queries, 1, TimeUnit.SECONDS);
        verifyZeroInteractions(queryMessageHandler1);
        verifyZeroInteractions(queryMessageHandler2);

        StepVerifier.create(result)
                    .expectNext("handled", "handled", "handled", "handled")
                    .expectError(RuntimeException.class)
                    .verify();

        verify(queryMessageHandler1, times(2)).handle(any());
        verify(queryMessageHandler2, times(2)).handle(any());
    }

    @Test
    void testScatterGatherWithDispatchInterceptorThrowingAnException() {
        reactiveQueryGateway
                .registerDispatchInterceptor(queryMono -> {
                    throw new RuntimeException();
                });
        StepVerifier.create(reactiveQueryGateway
                                    .scatterGather(true, ResponseTypes.instanceOf(String.class), 1, TimeUnit.SECONDS))
                    .verifyError(RuntimeException.class);
    }

    @Test
    void testScatterGatherWithDispatchInterceptorReturningErrorMono() {
        reactiveQueryGateway
                .registerDispatchInterceptor(queryMono -> Mono.error(new RuntimeException()));
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
                    .expectNextCount(0)
                    .verifyComplete();
    }

    @Test
    void testScatterGatherFailsWithRetry() throws Exception {
        doThrow(new RuntimeException(":(")).when(queryBus).scatterGather(any(), anyLong(), any());

        Flux<Integer> query = reactiveQueryGateway.scatterGather(6,
                                                                 ResponseTypes.instanceOf(Integer.class),
                                                                 1,
                                                                 TimeUnit.SECONDS).retry(5);

        StepVerifier.create(query)
                    .verifyError();


        verify(queryBus, times(6)).scatterGather(any(), anyLong(), any());
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
    void testMultipleSubscriptionQueries() throws Exception {
        Flux<SubscriptionQueryMessage<?, ?, ?>> queries = Flux.fromIterable(Arrays.asList(
                new GenericSubscriptionQueryMessage<>("query1",
                                                      ResponseTypes.instanceOf(String.class),
                                                      ResponseTypes.instanceOf(String.class)),
                new GenericSubscriptionQueryMessage<>(4,
                                                      ResponseTypes.instanceOf(Integer.class),
                                                      ResponseTypes.instanceOf(String.class))));

        Flux<SubscriptionQueryResult<?, ?>> result = reactiveQueryGateway.subscriptionQuery(queries);
        verifyZeroInteractions(queryMessageHandler1);
        verifyZeroInteractions(queryMessageHandler2);
        List<Mono<Object>> initialResults = new ArrayList<>(2);
        //noinspection unchecked
        result.subscribe(sqr -> initialResults.add((Mono<Object>) sqr.initialResult()));
        assertEquals(2, initialResults.size());
        StepVerifier.create(initialResults.get(0))
                    .expectNext("handled")
                    .verifyComplete();
        StepVerifier.create(initialResults.get(1))
                    .verifyError(RuntimeException.class);

        verify(queryMessageHandler1).handle(any());
    }

    @Test
    void testMultipleSubscriptionQueriesOrdering() throws Exception {
        int numberOfQueries = 10_000;
        Flux<SubscriptionQueryMessage<?, ?, ?>> queries = Flux
                .fromStream(IntStream.range(0, numberOfQueries)
                                     .mapToObj(i -> new GenericSubscriptionQueryMessage<>("backpressure",
                                                                                          ResponseTypes
                                                                                                  .instanceOf(String.class),
                                                                                          ResponseTypes
                                                                                                  .instanceOf(String.class))));
        List<Integer> expectedResults = IntStream.range(1, numberOfQueries + 1)
                                                 .boxed()
                                                 .collect(Collectors.toList());
        Flux<SubscriptionQueryResult<?, ?>> result = reactiveQueryGateway.subscriptionQuery(queries);
        List<Mono<Object>> initialResults = new ArrayList<>(numberOfQueries);
        //noinspection unchecked
        result.subscribe(sqr -> initialResults.add((Mono<Object>) sqr.initialResult()));
        assertEquals(numberOfQueries, initialResults.size());
        for (int i = 0; i < numberOfQueries; i++) {
            StepVerifier.create(initialResults.get(i))
                        .expectNext(expectedResults.get(i))
                        .verifyComplete();
        }

        verify(queryMessageHandler1, times(numberOfQueries)).handle(any());
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
                .registerDispatchInterceptor(queryMono -> queryMono
                        .map(query -> query.andMetaData(Collections.singletonMap("key1", "value1"))));
        Registration registration2 = reactiveQueryGateway
                .registerDispatchInterceptor(queryMono -> queryMono
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
                .registerDispatchInterceptor(queryMono -> {
                    throw new RuntimeException();
                });
        StepVerifier.create(reactiveQueryGateway.subscriptionQuery(true, String.class, String.class))
                    .verifyError(RuntimeException.class);
    }

    @Test
    void testSubscriptionQueryWithDispatchInterceptorReturningErrorMono() {
        reactiveQueryGateway
                .registerDispatchInterceptor(queryMono -> Mono.error(new RuntimeException()));
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

    @Test
    void testSubscriptionQueryFailsRetryInitialDispatchQuery() throws Exception {

        doThrow(new RuntimeException(":(")).when(queryBus).subscriptionQuery(any(), any(), anyInt());

        Mono<SubscriptionQueryResult<Integer, Integer>> monoResult = reactiveQueryGateway.subscriptionQuery(6,
                                                                                                            Integer.class,
                                                                                                            Integer.class)
                                                                                         .retry(5);

        StepVerifier.create(monoResult)
                    .verifyError(RuntimeException.class);

        verify(queryBus, times(6)).subscriptionQuery(any(), any(), anyInt());
    }

    @Test
    void testSubscriptionQueryFailsRetryInitialResult() throws Exception {
        Mono<SubscriptionQueryResult<Integer, Integer>> monoResult = reactiveQueryGateway.subscriptionQuery(6,
                                                                                                            Integer.class,
                                                                                                            Integer.class);

        SubscriptionQueryResult<Integer, Integer> result = monoResult.block();
        assertNotNull(result);
        StepVerifier.create(result.initialResult().retry(5))
                    .verifyError(RuntimeException.class);

        verify(queryMessageHandler3, times(6)).handle(any());
    }

    @Test
    void testSubscriptionQuerySingleInitialResultAndUpdates() {
        Flux<String> result = reactiveQueryGateway.subscriptionQuery("6", String.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);

        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.execute(() -> {
            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                fail(e.getMessage());
            }
            queryUpdateEmitter.emit(String.class, q -> true, "1");
            queryUpdateEmitter.emit(String.class, q -> true, "2");
            queryUpdateEmitter.emit(String.class, q -> true, "3");
            queryUpdateEmitter.emit(String.class, q -> true, "4");
            queryUpdateEmitter.emit(String.class, q -> true, "5");
            queryUpdateEmitter.complete(String.class, p -> true);
        });
        StepVerifier.create(result.doOnNext(s -> countDownLatch.countDown()))
                    .expectNext("handled")
                    .expectNext("1", "2", "3", "4", "5")
                    .verifyComplete();
    }

    @Test
    void testQueryUpdates() {
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();

            queryUpdateEmitter.emit(String.class, q -> true, "1");
            queryUpdateEmitter.emit(String.class, q -> true, "2");
            queryUpdateEmitter.emit(String.class, q -> true, "3");
            queryUpdateEmitter.emit(String.class, q -> true, "4");
            queryUpdateEmitter.emit(String.class, q -> true, "5");
            queryUpdateEmitter.complete(String.class, p -> true);

            return result;
        }).when(queryBus)
          .subscriptionQuery(any(), any(), anyInt());

        StepVerifier.create(reactiveQueryGateway.queryUpdates("6", String.class))
                    .expectNext("1", "2", "3", "4", "5")
                    .verifyComplete();
    }

    @Test
    void testSubscriptionQueryMany() {
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();

            queryUpdateEmitter.emit(Double.class, q -> true, "update1");
            queryUpdateEmitter.emit(Double.class, q -> true, "update2");
            queryUpdateEmitter.emit(Double.class, q -> true, "update3");
            queryUpdateEmitter.emit(Double.class, q -> true, "update4");
            queryUpdateEmitter.emit(Double.class, q -> true, "update5");
            queryUpdateEmitter.complete(Double.class, p -> true);

            return result;
        }).when(queryBus)
          .subscriptionQuery(any(), any(), anyInt());

        StepVerifier.create(reactiveQueryGateway.subscriptionQueryMany(2.3, String.class))
                    .expectNext("value1", "value2", "value3", "update1", "update2", "update3", "update4", "update5")
                    .verifyComplete();
    }
}
