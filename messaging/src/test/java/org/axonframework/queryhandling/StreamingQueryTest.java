package org.axonframework.queryhandling;

import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.annotation.AnnotationQueryHandlerAdapter;
import org.junit.jupiter.api.*;
import reactor.core.publisher.ConnectableFlux;
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
    void testSingleInstancesOfTypeResults() throws ExecutionException, InterruptedException {
        QueryMessage<String, String> queryMessage =
                new GenericQueryMessage<>("criteria", "fluxQuery", ResponseTypes.instanceOf(String.class));

        assertEquals("a", queryBus.query(queryMessage).get().getPayload());
    }

    @Test
    void testOptionalInstancesOfTypeResults() {
        QueryMessage<String, Optional<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "fluxQuery", ResponseTypes.optionalInstanceOf(String.class));

        assertThrows(NoHandlerForQueryException.class, () -> {
            try {
                queryBus.query(queryMessage).get();
            } catch (ExecutionException e) {
                throw e.getCause();
            }
        });
    }

    @Test
    void testMultipleInstancesOfTypeResults() throws ExecutionException, InterruptedException {
        QueryMessage<String, List<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "fluxQuery", ResponseTypes.multipleInstancesOf(String.class));
        assertEquals(asList("a", "b", "c", "d"), queryBus.query(queryMessage).get().getPayload());
    }

    @Test
    void testStreamingFluxResults() throws ExecutionException, InterruptedException {
        QueryMessage<String, Flux<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "fluxQuery", ResponseTypes.fluxOf(String.class));

        StepVerifier.create(queryBus.query(queryMessage)
                                    .get()
                                    .getPayload())
                    .expectNext("a", "b", "c", "d")
                    .verifyComplete();
    }

    @Test
    void testOptionalResults() throws ExecutionException, InterruptedException {
        QueryMessage<String, Flux<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "optionalResultQuery", ResponseTypes.fluxOf(String.class));

        StepVerifier.create(queryBus.query(queryMessage)
                                    .get()
                                    .getPayload())
                    .expectNext("optional")
                    .verifyComplete();
    }

    @Test
    void testEmptyOptionalResults() throws ExecutionException, InterruptedException {
        QueryMessage<String, Flux<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "emptyOptionalResultQuery", ResponseTypes.fluxOf(String.class));

        StepVerifier.create(queryBus.query(queryMessage)
                                    .get()
                                    .getPayload())
                    .expectComplete()
                    .verify();
    }

    @Test
    void testStreamingListResults() throws ExecutionException, InterruptedException {
        QueryMessage<String, Flux<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "listQuery", ResponseTypes.fluxOf(String.class));

        StepVerifier.create(queryBus.query(queryMessage)
                                    .get()
                                    .getPayload())
                    .expectNext("a", "b", "c", "d")
                    .verifyComplete();
    }

    @Test
    void testStreamingMultipleInstanceOfResults() throws ExecutionException, InterruptedException {
        QueryMessage<String, List<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "listQuery", ResponseTypes.multipleInstancesOf(String.class));

        assertEquals(asList("a", "b", "c", "d"), queryBus.query(queryMessage)
                                                         .get()
                                                         .getPayload());
    }

    @Test
    void testStreamingStreamResults() throws ExecutionException, InterruptedException {
        QueryMessage<String, Flux<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "streamQuery", ResponseTypes.fluxOf(String.class));

        StepVerifier.create(queryBus.query(queryMessage)
                                    .get()
                                    .getPayload())
                    .expectNext("a", "b", "c", "d")
                    .verifyComplete();
    }

    @Test
    void testStreamingSingleResult() throws ExecutionException, InterruptedException {
        QueryMessage<String, Flux<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "singleResultQuery", ResponseTypes.fluxOf(String.class));

        StepVerifier.create(queryBus.query(queryMessage)
                                    .get()
                                    .getPayload())
                    .expectNext("lonely")
                    .verifyComplete();
    }

    @Test
    void testStreamingCompletableFutureResult() throws ExecutionException, InterruptedException {
        QueryMessage<String, Flux<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "completableFutureQuery", ResponseTypes.fluxOf(String.class));

        StepVerifier.create(queryBus.query(queryMessage)
                                    .get()
                                    .getPayload())
                    .expectNext("future")
                    .verifyComplete();
    }

    @Test
    void testStreamingFluxAfterHandlerCompletes() throws ExecutionException, InterruptedException {
        QueryMessage<String, Flux<Long>> queryMessage =
                new GenericQueryMessage<>("criteria",
                                          "streamingAfterHandlerCompletesQuery",
                                          ResponseTypes.fluxOf(Long.class));

        StepVerifier.create(queryBus.query(queryMessage)
                                    .get()
                                    .getPayload())
                    .expectNext(0L, 1L, 2L, 3L, 4L)
                    .verifyComplete();
    }

    @Test
    void testStreamingMonoResult() throws ExecutionException, InterruptedException {
        QueryMessage<String, Flux<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "monoQuery", ResponseTypes.fluxOf(String.class));

        StepVerifier.create(queryBus.query(queryMessage)
                                    .get()
                                    .getPayload())
                    .expectNext("helloMono")
                    .verifyComplete();
    }

    @Test
    void testStreamingNullResult() throws ExecutionException, InterruptedException {
        QueryMessage<String, Flux<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "nullQuery", ResponseTypes.fluxOf(String.class));

        StepVerifier.create(queryBus.query(queryMessage)
                                    .get()
                                    .getPayload())
                    .expectComplete()
                    .verify();
    }

    @Test
    void testErrorResult() throws ExecutionException, InterruptedException {
        QueryMessage<String, Flux<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "exceptionQuery", ResponseTypes.fluxOf(String.class));

        StepVerifier.create(queryBus.query(queryMessage).get()
                                    .getPayload())
                    .expectErrorMatches(t -> t instanceof RuntimeException
                            && t.getMessage().equals("oops"))
                    .verify();
    }

    @Test
    void testThrottledFluxQuery() throws ExecutionException, InterruptedException {
        QueryMessage<String, Flux<Long>> queryMessage =
                new GenericQueryMessage<>("criteria",
                                          "throttledFluxQuery",
                                          ResponseTypes.fluxOf(Long.class));

        StepVerifier.create(queryBus.query(queryMessage).get()
                                    .getPayload())
                    .expectNext(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L)
                    .verifyComplete();
    }

    @Test
    void testBackpressureFluxQuery() throws ExecutionException, InterruptedException {
        QueryMessage<String, Flux<Long>> queryMessage =
                new GenericQueryMessage<>("criteria",
                                          "backPressure",
                                          ResponseTypes.fluxOf(Long.class));

        StepVerifier.create(queryBus.query(queryMessage).get()
                                    .getPayload(), 10L)
                    .expectNextCount(10)
                    .thenRequest(10)
                    .expectNextCount(10)
                    .thenCancel()
                    .verify();
    }

    @Test
    void testDispatchInterceptor() throws ExecutionException, InterruptedException {
        AtomicBoolean hasBeenCalled = new AtomicBoolean();

        queryBus.registerDispatchInterceptor(messages -> {
            hasBeenCalled.set(true);
            return (i, m) -> m;
        });

        QueryMessage<String, Flux<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "fluxQuery", ResponseTypes.fluxOf(String.class));

        StepVerifier.create(queryBus.query(queryMessage).get()
                                    .getPayload())
                    .expectNext("a", "b", "c", "d")
                    .verifyComplete();

        assertTrue(hasBeenCalled.get());
    }

    @Test
    void testHandlerInterceptor() throws ExecutionException, InterruptedException {
        queryBus.registerHandlerInterceptor((unitOfWork, interceptorChain) ->
                                                    ((Flux) interceptorChain.proceed()).map(it -> "a"));

        QueryMessage<String, Flux<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "fluxQuery", ResponseTypes.fluxOf(String.class));

        StepVerifier.create(queryBus.query(queryMessage).get()
                                    .getPayload())
                    .expectNext("a", "a", "a", "a")
                    .verifyComplete();
    }

    @Test
    void testErrorStream() throws ExecutionException, InterruptedException {
        QueryMessage<String, Flux<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "errorStream", ResponseTypes.fluxOf(String.class));

        QueryResponseMessage<Flux<String>> result = queryBus.query(queryMessage).get();
        StepVerifier.create(result.getPayload())
                    .verifyErrorMatches(t -> t instanceof RuntimeException && t.getMessage().equals("oops"));
    }

    @Test
    void testErrorStreamOnMultipleResponses() throws ExecutionException, InterruptedException {
        QueryMessage<String, List<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "errorStream", ResponseTypes.multipleInstancesOf(String.class));

        QueryResponseMessage<List<String>> result = queryBus.query(queryMessage).get();
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
