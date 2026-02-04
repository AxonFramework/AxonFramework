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

package org.axonframework.messaging.queryhandling;

import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.annotation.AnnotationMessageTypeResolver;
import org.axonframework.messaging.core.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.core.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.core.conversion.DelegatingMessageConverter;
import org.axonframework.messaging.queryhandling.annotation.AnnotatedQueryHandlingComponent;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.axonframework.conversion.PassThroughConverter;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static org.axonframework.messaging.core.FluxUtils.streamToPublisher;

/**
 * Tests Streaming Query functionality using a {@link SimpleQueryBus}. Query Handlers are subscribed using
 * {@link AnnotatedQueryHandlingComponent}.
 *
 * @author Milan Savic
 * @author Stefan Dragisic
 */
class StreamingQueryTest {

    private final QueryBus queryBus = QueryBusTestUtils.aQueryBus();

    private MyQueryHandler myQueryHandler;

    @BeforeEach
    void setUp() {

        myQueryHandler = new MyQueryHandler();
        QueryHandlingComponent queryHandlingComponent = new AnnotatedQueryHandlingComponent<>(
                myQueryHandler,
                ClasspathParameterResolverFactory.forClass(myQueryHandler.getClass()),
                ClasspathHandlerDefinition.forClass(myQueryHandler.getClass()),
                new AnnotationMessageTypeResolver(),
                new DelegatingMessageConverter(PassThroughConverter.INSTANCE)
        );
        queryBus.subscribe(queryHandlingComponent);
    }

    @AfterEach
    void reset() {
        myQueryHandler.errorThrown.set(false);
    }

    private <R> Flux<R> streamingQueryPayloads(QueryMessage testQuery, Class<R> cls) {
        return streamingQuery(testQuery).mapNotNull(m -> m.payloadAs(cls));
    }

    private Flux<QueryResponseMessage> streamingQuery(QueryMessage testQuery) {
        return Flux.from(streamToPublisher(
                () -> queryBus.query(testQuery, null))
        );
    }

    @Test
    void streamingFluxResults() {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("fluxQuery"), "criteria"
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class))
                    .expectNext("a", "b", "c", "d")
                    .verifyComplete();
    }

    @Test
    void optionalResults() {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("optionalResultQuery"), "criteria"
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class))
                    .expectNext("optional")
                    .verifyComplete();
    }

    @Test
    void emptyOptionalResults() {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("emptyOptionalResultQuery"), "criteria"
        );

        StepVerifier.create(streamingQuery(testQuery))
                    .expectComplete()
                    .verify();
    }

    @Test
    void streamingListResults() {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("listQuery"), "criteria"
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class))
                    .expectNext("a", "b", "c", "d")
                    .verifyComplete();
    }

    @Test
    void streamingStreamResults() {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("streamQuery"), "criteria"
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class))
                    .expectNext("a", "b", "c", "d")
                    .verifyComplete();
    }

    @Test
    void streamingSingleResult() {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("singleResultQuery"), "criteria"
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class))
                    .expectNext("lonely")
                    .verifyComplete();
    }

    @Test
    void streamingCompletableFutureResult() {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("completableFutureQuery"), "criteria"
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class))
                    .expectNext("future")
                    .verifyComplete();
    }

    @Test
    void streamingFluxAfterHandlerCompletes() {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("streamingAfterHandlerCompletesQuery"),
                "criteria"
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, Long.class))
                    .expectNext(0L, 1L, 2L, 3L, 4L)
                    .verifyComplete();
    }

    @Test
    void streamingMonoResult() {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("monoQuery"), "criteria"
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class))
                    .expectNext("helloMono")
                    .verifyComplete();
    }

    @Test
    void streamingNullResult() {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("nullQuery"), "criteria"
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class))
                    .expectComplete()
                    .verify();
    }

    @Test
    void errorResult() {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("exceptionQuery"), "criteria"
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class))
                    .expectErrorMatches(t -> t instanceof QueryExecutionException)
                    .verify();
    }

    @Test
    void throttledFluxQuery() {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("throttledFluxQuery"), "criteria"
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, Long.class))
                    .expectNext(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L)
                    .verifyComplete();
    }

    @Test
    void backpressureFluxQuery() {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("backPressure"), "criteria"
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
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("errorStream"), "criteria"
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class))
                    .verifyErrorMatches(t -> t instanceof RuntimeException && t.getMessage().equals("oops"));
    }

    @Test
    void queryNotExists() {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("queryNotExists"), "criteria"
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class))
                    .verifyErrorMatches(t -> t instanceof NoHandlerForQueryException);
    }

    @Test
    void resubscribeWorksEvenWhenAnErrorHasBeenCashed() {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("exceptionQueryOnce"), "criteria"
        );

        Flux<String> flux = streamingQueryPayloads(testQuery, String.class);

        StepVerifier.create(flux)
                    .expectErrorMatches(t -> t instanceof QueryExecutionException)
                    .verify();

        StepVerifier.create(flux)
                    .expectNext("correctNow")
                    .verifyComplete();
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
            return Flux.interval(Duration.ofMillis(100))
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
