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

import org.axonframework.messaging.MessageType;
import org.axonframework.queryhandling.annotations.AnnotatedQueryHandlingComponent;
import org.axonframework.queryhandling.annotations.QueryHandler;
import org.axonframework.serialization.PassThroughConverter;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.util.Arrays.asList;

/**
 * Tests Streaming Query functionality using a {@link SimpleQueryBus}. Query Handlers are subscribed using
 * {@link AnnotatedQueryHandlingComponent}.
 *
 * @author Milan Savic
 * @author Stefan Dragisic
 */
class StreamingQueryTest {

    private QueryBus queryBus = QueryBusTestUtils.aQueryBus();

    private MyQueryHandler myQueryHandler;

    private static final ConcurrentLinkedQueue<String> handlersInvoked = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void setUp() {
        queryBus = QueryBusTestUtils.aQueryBus();

        myQueryHandler = new MyQueryHandler();
        QueryHandlingComponent queryHandlingComponent =
                new AnnotatedQueryHandlingComponent<>(myQueryHandler, PassThroughConverter.MESSAGE_INSTANCE);
        queryBus.subscribe(queryHandlingComponent);
    }

    @AfterEach
    void reset() {
        myQueryHandler.errorThrown.set(false);
    }

    private <R> Flux<R> streamingQueryPayloads(StreamingQueryMessage testQuery, Class<R> cls) {
        return streamingQuery(testQuery).mapNotNull(m -> m.payloadAs(cls));
    }

    private Flux<QueryResponseMessage> streamingQuery(StreamingQueryMessage testQuery) {
        return Flux.from(queryBus.streamingQuery(testQuery, null));
    }

    @Test
    void streamingFluxResults() {
        StreamingQueryMessage testQuery = new GenericStreamingQueryMessage(
                new MessageType("fluxQuery"), "criteria", String.class
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class))
                    .expectNext("a", "b", "c", "d")
                    .verifyComplete();
    }

    @Test
    void optionalResults() {
        StreamingQueryMessage testQuery = new GenericStreamingQueryMessage(
                new MessageType("optionalResultQuery"), "criteria", String.class
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class))
                    .expectNext("optional")
                    .verifyComplete();
    }

    @Test
    void emptyOptionalResults() {
        StreamingQueryMessage testQuery = new GenericStreamingQueryMessage(
                new MessageType("emptyOptionalResultQuery"), "criteria", String.class
        );

        StepVerifier.create(streamingQuery(testQuery))
                    .expectComplete()
                    .verify();
    }

    @Test
    void streamingListResults() {
        StreamingQueryMessage testQuery = new GenericStreamingQueryMessage(
                new MessageType("listQuery"), "criteria", String.class
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class))
                    .expectNext("a", "b", "c", "d")
                    .verifyComplete();
    }

    @Test
    void streamingStreamResults() {
        StreamingQueryMessage testQuery = new GenericStreamingQueryMessage(
                new MessageType("streamQuery"), "criteria", String.class
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class))
                    .expectNext("a", "b", "c", "d")
                    .verifyComplete();
    }

    @Test
    void streamingSingleResult() {
        StreamingQueryMessage testQuery = new GenericStreamingQueryMessage(
                new MessageType("singleResultQuery"), "criteria", String.class
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class))
                    .expectNext("lonely")
                    .verifyComplete();
    }

    @Test
    void streamingCompletableFutureResult() {
        StreamingQueryMessage testQuery = new GenericStreamingQueryMessage(
                new MessageType("completableFutureQuery"), "criteria", String.class
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class))
                    .expectNext("future")
                    .verifyComplete();
    }

    @Test
    void streamingFluxAfterHandlerCompletes() {
        StreamingQueryMessage testQuery = new GenericStreamingQueryMessage(
                new MessageType("streamingAfterHandlerCompletesQuery"),
                "criteria",
                Long.class
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, Long.class))
                    .expectNext(0L, 1L, 2L, 3L, 4L)
                    .verifyComplete();
    }

    @Test
    void streamingMonoResult() {
        StreamingQueryMessage testQuery = new GenericStreamingQueryMessage(
                new MessageType("monoQuery"), "criteria", String.class
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class))
                    .expectNext("helloMono")
                    .verifyComplete();
    }

    @Test
    void streamingNullResult() {
        StreamingQueryMessage testQuery = new GenericStreamingQueryMessage(
                new MessageType("nullQuery"), "criteria", String.class
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class))
                    .expectComplete()
                    .verify();
    }

    @Test
    void errorResult() {
        StreamingQueryMessage testQuery = new GenericStreamingQueryMessage(
                new MessageType("exceptionQuery"), "criteria", String.class
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class))
                    .expectErrorMatches(t -> t instanceof QueryExecutionException)
                    .verify();
    }

    @Test
    void throttledFluxQuery() {
        StreamingQueryMessage testQuery = new GenericStreamingQueryMessage(
                new MessageType("throttledFluxQuery"), "criteria", Long.class
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, Long.class))
                    .expectNext(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L)
                    .verifyComplete();
    }

    @Test
    void backpressureFluxQuery() {
        StreamingQueryMessage testQuery = new GenericStreamingQueryMessage(
                new MessageType("backPressure"), "criteria", Long.class
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, Long.class), 10L)
                    .expectNextCount(10)
                    .thenRequest(10)
                    .expectNextCount(10)
                    .thenCancel()
                    .verify();
    }

    @Test
    void errorStream() {
        StreamingQueryMessage testQuery = new GenericStreamingQueryMessage(
                new MessageType("errorStream"), "criteria", String.class
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class))
                    .verifyErrorMatches(t -> t instanceof RuntimeException && t.getMessage().equals("oops"));
    }

    @Test
    void queryNotExists() {
        StreamingQueryMessage testQuery = new GenericStreamingQueryMessage(
                new MessageType("queryNotExists"), "criteria", String.class
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class))
                    .verifyErrorMatches(t -> t instanceof NoHandlerForQueryException);
    }

    @Test
    void resubscribeWorksEvenWhenAnErrorHasBeenCashed() {
        StreamingQueryMessage testQuery = new GenericStreamingQueryMessage(
                new MessageType("exceptionQueryOnce"), "criteria", String.class
        );

        Flux<String> flux = streamingQueryPayloads(testQuery, String.class);

        StepVerifier.create(flux)
                    .expectErrorMatches(t -> t instanceof QueryExecutionException)
                    .verify();

        StepVerifier.create(flux)
                    .expectNext("correctNow")
                    .verifyComplete();
    }

    private static class ErrorQueryHandler {

        @SuppressWarnings("unused")
        @QueryHandler(queryName = "faultyListQuery")
        public Flux<String> listQuery(String criteria) {
            handlersInvoked.add("handler_error");
            throw new RuntimeException("ooops");
        }
    }

    @SuppressWarnings("unused")
    private static class MyQueryHandler {

        AtomicBoolean errorThrown = new AtomicBoolean(false);

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

        @QueryHandler(queryName = "exceptionQueryOnce")
        public Flux<String> exceptionQueryOnce(String criteria) {
            if (errorThrown.compareAndSet(false, true)) {
                throw new RuntimeException("oops");
            }
            return Flux.just("correctNow");
        }
    }
}
