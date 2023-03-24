/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.axonframework.messaging.Message;
import org.axonframework.queryhandling.annotation.AnnotationQueryHandlerAdapter;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests Streaming Query functionality using a {@link SimpleQueryBus}. Query Handlers are subscribed using
 * {@link AnnotationQueryHandlerAdapter}.
 *
 * @author Milan Savic
 * @author Stefan Dragisic
 */
class StreamingQueryTest {

    private final SimpleQueryBus queryBus = SimpleQueryBus.builder().build();
    private final MyQueryHandler myQueryHandler = new MyQueryHandler();
    private final AnnotationQueryHandlerAdapter<MyQueryHandler> annotationQueryHandlerAdapter = new AnnotationQueryHandlerAdapter<>(
            myQueryHandler);

    private final ErrorQueryHandler errorQueryHandler = new ErrorQueryHandler();

    private final AnnotationQueryHandlerAdapter<ErrorQueryHandler> errorQueryHandlerAdapter = new AnnotationQueryHandlerAdapter<>(
            errorQueryHandler);

    private static final ConcurrentLinkedQueue<String> handlersInvoked = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void setUp() {
        annotationQueryHandlerAdapter.subscribe(queryBus);
    }

    private <Q, R> Flux<R> streamingQueryPayloads(StreamingQueryMessage<Q, R> queryMessage) {
        return streamingQuery(queryMessage).map(Message::getPayload);
    }

    private <Q, R> Flux<QueryResponseMessage<R>> streamingQuery(StreamingQueryMessage<Q, R> queryMessage) {
        return Flux.from(queryBus.streamingQuery(queryMessage));
    }

    @Test
    void streamingFluxResults() {
        StreamingQueryMessage<String, String> queryMessage =
                new GenericStreamingQueryMessage<>("criteria", "fluxQuery", String.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage))
                    .expectNext("a", "b", "c", "d")
                    .verifyComplete();
    }

    @Test
    void switchHandlerOnError() {
        handlersInvoked.removeIf(n -> true);
        errorQueryHandlerAdapter.subscribe(queryBus);

        StreamingQueryMessage<String, String> queryMessage =
                new GenericStreamingQueryMessage<>("criteria", "listQuery", String.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage))
                    .expectNext("a", "b", "c", "d")
                    .verifyComplete();

        List<String> handlers_invoked = new ArrayList<>(handlersInvoked);
        Assertions.assertEquals(asList("handler_error", "handler_healthy"), handlers_invoked);
    }

    @Test
    void optionalResults() {
        StreamingQueryMessage<String, String> queryMessage =
                new GenericStreamingQueryMessage<>("criteria", "optionalResultQuery", String.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage))
                    .expectNext("optional")
                    .verifyComplete();
    }

    @Test
    void emptyOptionalResults() {
        StreamingQueryMessage<String, String> queryMessage =
                new GenericStreamingQueryMessage<>("criteria", "emptyOptionalResultQuery", String.class);

        StepVerifier.create(streamingQuery(queryMessage))
                    .expectComplete()
                    .verify();
    }

    @Test
    void streamingListResults() {
        StreamingQueryMessage<String, String> queryMessage =
                new GenericStreamingQueryMessage<>("criteria", "listQuery", String.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage))
                    .expectNext("a", "b", "c", "d")
                    .verifyComplete();
    }

    @Test
    void streamingStreamResults() {
        StreamingQueryMessage<String, String> queryMessage =
                new GenericStreamingQueryMessage<>("criteria", "streamQuery", String.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage))
                    .expectNext("a", "b", "c", "d")
                    .verifyComplete();
    }

    @Test
    void streamingSingleResult() {
        StreamingQueryMessage<String, String> queryMessage =
                new GenericStreamingQueryMessage<>("criteria", "singleResultQuery", String.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage))
                    .expectNext("lonely")
                    .verifyComplete();
    }

    @Test
    void streamingCompletableFutureResult() {
        StreamingQueryMessage<String, String> queryMessage =
                new GenericStreamingQueryMessage<>("criteria", "completableFutureQuery", String.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage))
                    .expectNext("future")
                    .verifyComplete();
    }

    @Test
    void streamingFluxAfterHandlerCompletes() {
        StreamingQueryMessage<String, Long> queryMessage =
                new GenericStreamingQueryMessage<>("criteria",
                                                   "streamingAfterHandlerCompletesQuery",
                                                   Long.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage))
                    .expectNext(0L, 1L, 2L, 3L, 4L)
                    .verifyComplete();
    }

    @Test
    void streamingMonoResult() {
        StreamingQueryMessage<String, String> queryMessage =
                new GenericStreamingQueryMessage<>("criteria", "monoQuery", String.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage))
                    .expectNext("helloMono")
                    .verifyComplete();
    }

    @Test
    void streamingNullResult() {
        StreamingQueryMessage<String, String> queryMessage =
                new GenericStreamingQueryMessage<>("criteria", "nullQuery", String.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage))
                    .expectComplete()
                    .verify();
    }

    @Test
    void errorResult() {
        StreamingQueryMessage<String, String> queryMessage =
                new GenericStreamingQueryMessage<>("criteria", "exceptionQuery", String.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage))
                    .expectErrorMatches(t -> t instanceof QueryExecutionException
                            && t.getMessage().startsWith("Error starting stream"))
                    .verify();
    }

    @Test
    void throttledFluxQuery() {
        StreamingQueryMessage<String, Long> queryMessage =
                new GenericStreamingQueryMessage<>("criteria",
                                                   "throttledFluxQuery",
                                                   Long.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage))
                    .expectNext(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L)
                    .verifyComplete();
    }

    @Test
    void backpressureFluxQuery() {
        StreamingQueryMessage<String, Long> queryMessage =
                new GenericStreamingQueryMessage<>("criteria",
                                                   "backPressure",
                                                   Long.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage), 10L)
                    .expectNextCount(10)
                    .thenRequest(10)
                    .expectNextCount(10)
                    .thenCancel()
                    .verify();
    }

    @Test
    void dispatchInterceptor() {
        AtomicBoolean hasBeenCalled = new AtomicBoolean();

        queryBus.registerDispatchInterceptor(messages -> {
            hasBeenCalled.set(true);
            return (i, m) -> m;
        });

        StreamingQueryMessage<String, String> queryMessage =
                new GenericStreamingQueryMessage<>("criteria", "fluxQuery", String.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage))
                    .expectNext("a", "b", "c", "d")
                    .verifyComplete();

        assertTrue(hasBeenCalled.get());
    }

    @Test
    void handlerInterceptor() {
        queryBus.registerHandlerInterceptor((unitOfWork, interceptorChain) ->
                                                    ((Flux) interceptorChain.proceed()).map(it -> "a"));

        StreamingQueryMessage<String, String> queryMessage =
                new GenericStreamingQueryMessage<>("criteria", "fluxQuery", String.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage))
                    .expectNext("a", "a", "a", "a")
                    .verifyComplete();
    }

    @Test
    void errorStream() {
        StreamingQueryMessage<String, String> queryMessage =
                new GenericStreamingQueryMessage<>("criteria", "errorStream", String.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage))
                    .verifyErrorMatches(t -> t instanceof RuntimeException && t.getMessage().equals("oops"));
    }

    @Test
    void queryNotExists() {
        StreamingQueryMessage<String, String> queryMessage =
                new GenericStreamingQueryMessage<>("criteria", "queryNotExists", String.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage))
                    .verifyErrorMatches(t -> t instanceof NoHandlerForQueryException);
    }

    private static class ErrorQueryHandler {

        @QueryHandler(queryName = "listQuery")
        public Flux<String> listQuery(String criteria) {
            handlersInvoked.add("handler_error");
            throw new RuntimeException("ooops");
        }
    }

    private static class MyQueryHandler {

        @QueryHandler(queryName = "fluxQuery")
        public Flux<String> fluxQuery(String criteria) {
            return Flux.just("a", "b", "c", "d");
        }

        @QueryHandler(queryName = "listQuery")
        public List<String> listQuery(String criteria) {
            handlersInvoked.add("handler_healthy");
            return asList("a", "b", "c", "d");
        }

        @QueryHandler(queryName = "streamQuery")
        public Stream<String> streamQuery(String criteria) {
            return Stream.of("a", "b", "c", "d");
        }

        @QueryHandler(queryName = "singleResultQuery")
        public String singleResultQuery(String criteria) {
            return "lonely";
        }

        @QueryHandler(queryName = "optionalResultQuery")
        public Optional<String> optionalResultQuery(String criteria) {
            return Optional.of("optional");
        }

        @QueryHandler(queryName = "emptyOptionalResultQuery")
        public Optional<String> emptyOptionalResultQuery(String criteria) {
            return Optional.empty();
        }

        @QueryHandler(queryName = "completableFutureQuery")
        public CompletableFuture<String> completableFutureQuery(String criteria) {
            return CompletableFuture.completedFuture("future");
        }

        @QueryHandler(queryName = "streamingAfterHandlerCompletesQuery")
        public Flux<Long> streamingAfterHandlerCompletesQuery(String criteria) {
            return Flux.interval(Duration.ofSeconds(1))
                       .take(5);
        }

        @QueryHandler(queryName = "monoQuery")
        public Mono<String> monoQuery(String criteria) {
            return Mono.fromCallable(() -> "helloMono")
                       .delayElement(Duration.ofMillis(100));
        }

        @QueryHandler(queryName = "nullQuery")
        public Flux<String> nullQuery(String criteria) {
            return null;
        }

        @QueryHandler(queryName = "exceptionQuery")
        public Flux<String> exceptionQuery(String criteria) {
            throw new RuntimeException("oops");
        }

        @QueryHandler(queryName = "throttledFluxQuery")
        public Flux<Long> throttledFlux(String criteria) {
            return Flux.interval(Duration.ofMillis(100))
                       .window(2)
                       .take(4)
                       .flatMap(Function.identity());
        }

        @QueryHandler(queryName = "backPressure")
        public Flux<Long> backPressureQuery(String criteria) {
            return Flux.create(longFluxSink -> longFluxSink
                    .onRequest(r -> LongStream.range(0, r).forEach(longFluxSink::next)));
        }

        @QueryHandler(queryName = "errorStream")
        public Flux<String> errorStream(String criteria) {
            return Flux.error(new RuntimeException("oops"));
        }
    }
}
