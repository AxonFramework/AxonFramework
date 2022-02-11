package org.axonframework.queryhandling;

import org.axonframework.messaging.Message;
import org.axonframework.queryhandling.annotation.AnnotationQueryHandlerAdapter;
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
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests Streaming Query functionality.
 *
 * @author Milan Savic
 * @author Stefan Dragisic
 */
class StreamingQueryTest {

    private final SimpleQueryBus queryBus = SimpleQueryBus.builder().build();
    private final MyQueryHandler myQueryHandler = new MyQueryHandler();
    private final AnnotationQueryHandlerAdapter<MyQueryHandler> annotationQueryHandlerAdapter = new AnnotationQueryHandlerAdapter<>(
            myQueryHandler);

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
    void testStreamingFluxResults() {
        StreamingQueryMessage<String, String> queryMessage =
                new GenericStreamingQueryMessage<>("criteria", "fluxQuery", String.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage))
                    .expectNext("a", "b", "c", "d")
                    .verifyComplete();
    }

    @Test
    void testOptionalResults() {
        StreamingQueryMessage<String, String> queryMessage =
                new GenericStreamingQueryMessage<>("criteria", "optionalResultQuery", String.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage))
                    .expectNext("optional")
                    .verifyComplete();
    }

    @Test
    void testEmptyOptionalResults() {
        StreamingQueryMessage<String, String> queryMessage =
                new GenericStreamingQueryMessage<>("criteria", "emptyOptionalResultQuery", String.class);

        StepVerifier.create(streamingQuery(queryMessage))
                    .expectComplete()
                    .verify();
    }

    @Test
    void testStreamingListResults() {
        StreamingQueryMessage<String, String> queryMessage =
                new GenericStreamingQueryMessage<>("criteria", "listQuery", String.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage))
                    .expectNext("a", "b", "c", "d")
                    .verifyComplete();
    }

    @Test
    void testStreamingStreamResults() {
        StreamingQueryMessage<String, String> queryMessage =
                new GenericStreamingQueryMessage<>("criteria", "streamQuery", String.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage))
                    .expectNext("a", "b", "c", "d")
                    .verifyComplete();
    }

    @Test
    void testStreamingSingleResult() {
        StreamingQueryMessage<String, String> queryMessage =
                new GenericStreamingQueryMessage<>("criteria", "singleResultQuery", String.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage))
                    .expectNext("lonely")
                    .verifyComplete();
    }

    @Test
    void testStreamingCompletableFutureResult() {
        StreamingQueryMessage<String, String> queryMessage =
                new GenericStreamingQueryMessage<>("criteria", "completableFutureQuery", String.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage))
                    .expectNext("future")
                    .verifyComplete();
    }

    @Test
    void testStreamingFluxAfterHandlerCompletes() {
        StreamingQueryMessage<String, Long> queryMessage =
                new GenericStreamingQueryMessage<>("criteria",
                                                   "streamingAfterHandlerCompletesQuery",
                                                   Long.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage))
                    .expectNext(0L, 1L, 2L, 3L, 4L)
                    .verifyComplete();
    }

    @Test
    void testStreamingMonoResult() {
        StreamingQueryMessage<String, String> queryMessage =
                new GenericStreamingQueryMessage<>("criteria", "monoQuery", String.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage))
                    .expectNext("helloMono")
                    .verifyComplete();
    }

    @Test
    void testStreamingNullResult() {
        StreamingQueryMessage<String, String> queryMessage =
                new GenericStreamingQueryMessage<>("criteria", "nullQuery", String.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage))
                    .expectComplete()
                    .verify();
    }

    @Test
    void testErrorResult() {
        StreamingQueryMessage<String, String> queryMessage =
                new GenericStreamingQueryMessage<>("criteria", "exceptionQuery", String.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage))
                    .expectErrorMatches(t -> t instanceof NoHandlerForQueryException
                            && t.getMessage().startsWith("No suitable handler"))
                    .verify();
    }

    @Test
    void testThrottledFluxQuery() {
        StreamingQueryMessage<String, Long> queryMessage =
                new GenericStreamingQueryMessage<>("criteria",
                                                   "throttledFluxQuery",
                                                   Long.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage))
                    .expectNext(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L)
                    .verifyComplete();
    }

    @Test
    void testBackpressureFluxQuery() {
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
    void testDispatchInterceptor() {
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
    void testHandlerInterceptor() {
        queryBus.registerHandlerInterceptor((unitOfWork, interceptorChain) ->
                                                    ((Flux) interceptorChain.proceed()).map(it -> "a"));

        StreamingQueryMessage<String, String> queryMessage =
                new GenericStreamingQueryMessage<>("criteria", "fluxQuery", String.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage))
                    .expectNext("a", "a", "a", "a")
                    .verifyComplete();
    }

    @Test
    void testErrorStream() {
        StreamingQueryMessage<String, String> queryMessage =
                new GenericStreamingQueryMessage<>("criteria", "errorStream", String.class);

        StepVerifier.create(streamingQueryPayloads(queryMessage))
                    .verifyErrorMatches(t -> t instanceof RuntimeException && t.getMessage().equals("oops"));
    }

    private static class MyQueryHandler {

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
