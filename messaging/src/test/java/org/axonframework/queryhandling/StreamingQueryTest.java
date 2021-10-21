package org.axonframework.queryhandling;

import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.annotation.AnnotationQueryHandlerAdapter;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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
    void testStreamingFluxResults() {
        QueryMessage<String, Flux<String>> queryMessage =
                new GenericQueryMessage<>("criteria", "fluxQuery", ResponseTypes.streamOf(String.class));

        StepVerifier.create(queryBus.streamingQuery(queryMessage)
                                    .getPayload())
                    .expectNext("a", "b", "c", "d")
                    .verifyComplete();
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

        @QueryHandler(queryName = "completableFutureQuery")
        public CompletableFuture<String> completableFutureQuery(String criteria) {
            return CompletableFuture.completedFuture("future");
        }

        @QueryHandler(queryName = "streamingAfterHandlerCompletesQuery")
        public Flux<Long> streamingAfterHandlerCompletesQuery(String criteria) {
            return Flux.interval(Duration.ofSeconds(1))
                       .take(5);
        }
    }
}
