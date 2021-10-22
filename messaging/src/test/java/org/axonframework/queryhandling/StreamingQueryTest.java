package org.axonframework.queryhandling;

import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.annotation.AnnotationQueryHandlerAdapter;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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
public class StreamingQueryTest {

    private final SimpleQueryBus queryBus = SimpleQueryBus.builder().build();
    private final MyQueryHandler myQueryHandler = new MyQueryHandler();
    private final AnnotationQueryHandlerAdapter<MyQueryHandler> annotationQueryHandlerAdapter = new AnnotationQueryHandlerAdapter<>(
            myQueryHandler);

    @BeforeEach
    void setUp() {
        annotationQueryHandlerAdapter.subscribe(queryBus);
    }

    @Test
    void testFluxWaitingAllResults() throws ExecutionException, InterruptedException {
        QueryMessage<String, List<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "fluxQuery", ResponseTypes.multipleInstancesOf(String.class));
        assertEquals(asList("a", "b", "c", "d"), queryBus.query(queryMessage).get().getPayload());
    }

    @Test
    void testSingleInstancesOfTypeResults() {
        QueryMessage<String, String> queryMessage =
                new GenericQueryMessage<>("criteria", "fluxQuery", ResponseTypes.instanceOf(String.class));

        assertThrows(NoHandlerForQueryException.class, () -> queryBus.streamingQuery(queryMessage));
    }

    @Test
    void testOptionalInstancesOfTypeResults() {
        QueryMessage<String, Optional<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "fluxQuery", ResponseTypes.optionalInstanceOf(String.class));

        assertThrows(NoHandlerForQueryException.class, () -> queryBus.streamingQuery(queryMessage));
    }

    @Test
    void testMultipleInstancesOfTypeResults() {
        QueryMessage<String, List<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "fluxQuery", ResponseTypes.multipleInstancesOf(String.class));
        assertEquals(asList("a", "b", "c", "d"), queryBus.streamingQuery(queryMessage).getPayload());
    }

    @Test
    void testStreamingFluxResults() {
        QueryMessage<String, Flux<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "fluxQuery", ResponseTypes.streamOf(String.class));

        StepVerifier.create(queryBus.streamingQuery(queryMessage)
                                    .getPayload())
                    .expectNext("a", "b", "c", "d")
                    .verifyComplete();
    }

    @Test
    void testOptionalResults() {
        QueryMessage<String, Flux<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "optionalResultQuery", ResponseTypes.streamOf(String.class));

        StepVerifier.create(queryBus.streamingQuery(queryMessage)
                                    .getPayload())
                    .expectNext("optional")
                    .verifyComplete();
    }

    @Test
    void testEmptyOptionalResults() {
        QueryMessage<String, Flux<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "emptyOptionalResultQuery", ResponseTypes.streamOf(String.class));

        StepVerifier.create(queryBus.streamingQuery(queryMessage)
                                    .getPayload())
                    .expectComplete()
                    .verify();
    }

    @Test
    void testStreamingListResults() {
        QueryMessage<String, Flux<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "listQuery", ResponseTypes.streamOf(String.class));

        StepVerifier.create(queryBus.streamingQuery(queryMessage)
                                    .getPayload())
                    .expectNext("a", "b", "c", "d")
                    .verifyComplete();
    }

    @Test
    void testStreamingMultipleInstanceOfResults() {
        QueryMessage<String, List<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "listQuery", ResponseTypes.multipleInstancesOf(String.class));

        assertEquals(asList("a", "b", "c", "d"), queryBus.streamingQuery(queryMessage)
                                    .getPayload());
    }

    @Test
    void testStreamingStreamResults() {
        QueryMessage<String, Flux<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "streamQuery", ResponseTypes.streamOf(String.class));

        StepVerifier.create(queryBus.streamingQuery(queryMessage)
                                    .getPayload())
                    .expectNext("a", "b", "c", "d")
                    .verifyComplete();
    }

    @Test
    void testStreamingSingleResult() {
        QueryMessage<String, Flux<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "singleResultQuery", ResponseTypes.streamOf(String.class));

        StepVerifier.create(queryBus.streamingQuery(queryMessage)
                                    .getPayload())
                    .expectNext("lonely")
                    .verifyComplete();
    }

    @Test
    void testStreamingCompletableFutureResult() {
        QueryMessage<String, Flux<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "completableFutureQuery", ResponseTypes.streamOf(String.class));

        StepVerifier.create(queryBus.streamingQuery(queryMessage)
                                    .getPayload())
                    .expectNext("future")
                    .verifyComplete();
    }

    @Test
    void testStreamingFluxAfterHandlerCompletes() {
        QueryMessage<String, Flux<Long>> queryMessage =
                new GenericQueryMessage<>("criteria",
                                          "streamingAfterHandlerCompletesQuery",
                                          ResponseTypes.streamOf(Long.class));

        StepVerifier.create(queryBus.streamingQuery(queryMessage)
                                    .getPayload())
                    .expectNext(0L, 1L, 2L, 3L, 4L)
                    .verifyComplete();
    }

    @Test
    void testStreamingMonoResult() {
        QueryMessage<String, Flux<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "monoQuery", ResponseTypes.streamOf(String.class));

        StepVerifier.create(queryBus.streamingQuery(queryMessage)
                                    .getPayload())
                    .expectNext("helloMono")
                    .verifyComplete();
    }

    @Test
    void testStreamingNullResult() {
        QueryMessage<String, Flux<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "nullQuery", ResponseTypes.streamOf(String.class));

        StepVerifier.create(queryBus.streamingQuery(queryMessage)
                                    .getPayload())
                    .expectComplete()
                    .verify();
    }

    @Test
    void testErrorResult() {
        QueryMessage<String, Flux<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "exceptionQuery", ResponseTypes.streamOf(String.class));

        StepVerifier.create(queryBus.streamingQuery(queryMessage)
                                    .getPayload())
                    .expectErrorMatches(t -> t instanceof RuntimeException
                            && t.getMessage().equals("oops"))
                    .verify();
    }

    @Test
    void testThrottledFluxQuery() {
        QueryMessage<String, Flux<Long>> queryMessage =
                new GenericQueryMessage<>("criteria",
                                          "throttledFluxQuery",
                                          ResponseTypes.streamOf(Long.class));

        StepVerifier.create(queryBus.streamingQuery(queryMessage)
                                    .getPayload())
                    .expectNext(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L)
                    .verifyComplete();
    }

    @Test
    void testBackpressureFluxQuery() {
        QueryMessage<String, Flux<Long>> queryMessage =
                new GenericQueryMessage<>("criteria",
                                          "backPressure",
                                          ResponseTypes.streamOf(Long.class));

        StepVerifier.create(queryBus.streamingQuery(queryMessage)
                                    .getPayload(), 10L)
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

        QueryMessage<String, Flux<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "fluxQuery", ResponseTypes.streamOf(String.class));

        StepVerifier.create(queryBus.streamingQuery(queryMessage)
                                    .getPayload())
                    .expectNext("a", "b", "c", "d")
                    .verifyComplete();

        assertTrue(hasBeenCalled.get());
    }

    @Test
    void testHandlerInterceptor() {
        queryBus.registerHandlerInterceptor((unitOfWork, interceptorChain) ->
                                                    ((Flux) interceptorChain.proceed()).map(it -> "a"));

        QueryMessage<String, Flux<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "fluxQuery", ResponseTypes.streamOf(String.class));

        StepVerifier.create(queryBus.streamingQuery(queryMessage)
                                    .getPayload())
                    .expectNext("a", "a", "a", "a")
                    .verifyComplete();
    }

    @Test
    void testErrorStream() {
        QueryMessage<String, Flux<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "errorStream", ResponseTypes.streamOf(String.class));

        QueryResponseMessage<Flux<String>> result = queryBus.streamingQuery(queryMessage);
        StepVerifier.create(result.getPayload())
                    .verifyErrorMatches(t -> t instanceof RuntimeException && t.getMessage().equals("oops"));
    }

    @Test
    void testErrorStreamOnMultipleResponses() {
        QueryMessage<String, List<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "errorStream", ResponseTypes.multipleInstancesOf(String.class));

        QueryResponseMessage<List<String>> result = queryBus.streamingQuery(queryMessage);
        assertTrue(result.isExceptional());
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
