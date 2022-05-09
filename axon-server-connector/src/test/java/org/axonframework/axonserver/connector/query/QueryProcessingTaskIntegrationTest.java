/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.axonserver.connector.query;

import com.thoughtworks.xstream.XStream;
import io.axoniq.axonserver.connector.ErrorCategory;
import io.axoniq.axonserver.connector.ReplyChannel;
import io.axoniq.axonserver.grpc.ErrorMessage;
import io.axoniq.axonserver.grpc.MetaDataValue;
import io.axoniq.axonserver.grpc.ProcessingInstruction;
import io.axoniq.axonserver.grpc.ProcessingKey;
import io.axoniq.axonserver.grpc.query.QueryRequest;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.queryhandling.annotation.AnnotationQueryHandlerAdapter;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.jupiter.api.*;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests of {@link QueryProcessingTask}.
 */
class QueryProcessingTaskIntegrationTest {

    private static final String CLIENT_ID = "clientId";
    private static final String COMPONENT_NAME = "componentName";
    private static final int DIRECT_QUERY_NUMBER_OF_RESULTS = 1;
    private QueryBus localSegment;
    private CachingReplyChannel<QueryResponse> responseHandler;
    private QuerySerializer querySerializer;

    private QueryHandlingComponent1 queryHandlingComponent1;

    @BeforeEach
    void setUp() {
        localSegment = SimpleQueryBus.builder().build();
        responseHandler = new CachingReplyChannel<>();
        Serializer serializer = XStreamSerializer.builder()
                                                 .xStream(new XStream())
                                                 .build();
        AxonServerConfiguration config = AxonServerConfiguration.builder()
                                                                .clientId(CLIENT_ID)
                                                                .componentName(COMPONENT_NAME)
                                                                .build();
        querySerializer = new QuerySerializer(serializer, serializer, config);
        queryHandlingComponent1 = new QueryHandlingComponent1();
        new AnnotationQueryHandlerAdapter<>(queryHandlingComponent1).subscribe(localSegment);
        new AnnotationQueryHandlerAdapter<>(new QueryHandlingComponent2()).subscribe(localSegment);
    }

    @Test
    void testDirectQueryWhenRequesterDoesntSupportStreaming() {
        QueryMessage<FluxQuery, Publisher<String>> queryMessage = new GenericQueryMessage<>(new FluxQuery(1000),
                                                                                            ResponseTypes.publisherOf(String.class));

        QueryRequest request =
                querySerializer.serializeRequest(queryMessage, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1);
        QueryProcessingTask task = new QueryProcessingTask(localSegment,
                                                           request,
                                                           responseHandler,
                                                           querySerializer,
                                                           CLIENT_ID);

        task.run();
        task.request(10);
        assertEquals(1, responseHandler.sent().size());
        assertOrder(responseHandler.sent().get(0));
        assertTrue(responseHandler.completed());
    }

    @Test
    void testDirectQueryWhenRequesterDoesntSupportStreamingAndFlowControlMessagesComesBeforeQueryExecution() {
        QueryMessage<FluxQuery, Publisher<String>> queryMessage = new GenericQueryMessage<>(new FluxQuery(1000),
                                                                                            ResponseTypes.publisherOf(String.class));

        QueryRequest request =
                querySerializer.serializeRequest(queryMessage, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1);
        QueryProcessingTask task = new QueryProcessingTask(localSegment,
                                                           request,
                                                           responseHandler,
                                                           querySerializer,
                                                           CLIENT_ID);

        task.request(10);
        task.run();
        assertEquals(1, responseHandler.sent().size());
        assertOrder(responseHandler.sent().get(0));
        assertTrue(responseHandler.completed());
    }

    @Test
    void testDirectQueryWhenRequesterDoesntSupportStreamingAndCancelMessagesComesBeforeQueryExecution() {
        QueryMessage<FluxQuery, Publisher<String>> queryMessage = new GenericQueryMessage<>(new FluxQuery(1000),
                                                                                            ResponseTypes.publisherOf(String.class));

        QueryRequest request =
                querySerializer.serializeRequest(queryMessage, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1);
        QueryProcessingTask task = new QueryProcessingTask(localSegment,
                                                           request,
                                                           responseHandler,
                                                           querySerializer,
                                                           CLIENT_ID);

        task.cancel();
        task.run();
        assertTrue(responseHandler.sent().isEmpty());
        assertTrue(responseHandler.completed());
    }

    @Test
    void testStreamingQuery() {
        QueryMessage<FluxQuery, Publisher<String>> queryMessage = new GenericQueryMessage<>(new FluxQuery(1000),
                                                                                            ResponseTypes.publisherOf(String.class));

        QueryRequest request =
                querySerializer.serializeRequest(queryMessage, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1, true)
                               .toBuilder()
                               .addProcessingInstructions(asSupportsStreaming())
                               .build();
        QueryProcessingTask task = new QueryProcessingTask(localSegment,
                                                           request,
                                                           responseHandler,
                                                           querySerializer,
                                                           CLIENT_ID);

        task.request(10);
        task.run();
        assertEquals(10, responseHandler.sent().size());
        assertFalse(responseHandler.completed());

        task.request(50);
        assertEquals(60, responseHandler.sent().size());
        assertFalse(responseHandler.completed());

        task.request(1000);
        assertEquals(1000, responseHandler.sent().size());
        assertOrder(responseHandler.sent());
        assertTrue(responseHandler.completed());
    }

    @Test
    void testStreamingAList() {
        QueryMessage<ListQuery, List<String>> queryMessage =
                new GenericQueryMessage<>(new ListQuery(1000),
                                          ResponseTypes.multipleInstancesOf(String.class));

        QueryRequest request =
                querySerializer.serializeRequest(queryMessage, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1, true)
                               .toBuilder()
                               .addProcessingInstructions(asSupportsStreaming())
                               .build();
        QueryProcessingTask task = new QueryProcessingTask(localSegment,
                                                           request,
                                                           responseHandler,
                                                           querySerializer,
                                                           CLIENT_ID);

        task.request(10);
        task.run();
        assertEquals(10, responseHandler.sent().size());
        assertFalse(responseHandler.completed());

        task.request(50);
        assertEquals(60, responseHandler.sent().size());
        assertFalse(responseHandler.completed());

        task.request(1000);
        assertEquals(1000, responseHandler.sent().size());
        assertOrder(responseHandler.sent());
        assertTrue(responseHandler.completed());
    }

    @Test
    void testStreamingAListWhenReactorIsNotOnClasspath() {
        QueryMessage<ListQuery, List<String>> queryMessage =
                new GenericQueryMessage<>(new ListQuery(1000),
                                          ResponseTypes.multipleInstancesOf(String.class));

        QueryRequest request =
                querySerializer.serializeRequest(queryMessage, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1, true)
                               .toBuilder()
                               .addProcessingInstructions(asSupportsStreaming())
                               .build();
        QueryProcessingTask task = new QueryProcessingTask(localSegment,
                                                           request,
                                                           responseHandler,
                                                           querySerializer,
                                                           CLIENT_ID,
                                                           () -> false);

        task.request(10);
        task.run();
        assertEquals(10, responseHandler.sent().size());
        assertFalse(responseHandler.completed());

        task.request(50);
        assertEquals(60, responseHandler.sent().size());
        assertFalse(responseHandler.completed());

        task.request(1000);
        assertEquals(1000, responseHandler.sent().size());
        assertOrder(responseHandler.sent());
        assertTrue(responseHandler.completed());
    }

    @Test
    void testStreamingAListWhenReactorIsNotOnClasspathWithConcurrentRequests()
            throws InterruptedException {
        QueryMessage<ListQuery, List<String>> queryMessage =
                new GenericQueryMessage<>(new ListQuery(1000),
                                          ResponseTypes.multipleInstancesOf(String.class));

        QueryRequest request =
                querySerializer.serializeRequest(queryMessage, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1, true)
                               .toBuilder()
                               .addProcessingInstructions(asSupportsStreaming())
                               .build();
        QueryProcessingTask task = new QueryProcessingTask(localSegment,
                                                           request,
                                                           responseHandler,
                                                           querySerializer,
                                                           CLIENT_ID,
                                                           () -> false);

        CountDownLatch latch = new CountDownLatch(101);
        Runnable queryExecutor = () -> {
            task.run();
            latch.countDown();
        };
        ThreadLocalRandom localRandom = ThreadLocalRandom.current();
        Runnable requester = () -> {
            try {
                Thread.sleep(localRandom.nextInt(50));
            } catch (InterruptedException e) {
                // who cares
            }
            task.request(10);
            latch.countDown();
        };

        int queryExecutorPosition = localRandom.nextInt(0, 101);
        ExecutorService service = Executors.newFixedThreadPool(101);
        for (int i = 0; i < 101; i++) {
            if (i == queryExecutorPosition) {
                service.submit(queryExecutor);
            } else {
                service.submit(requester);
            }
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(1000, responseHandler.sent().size());
        assertOrder(responseHandler.sent());
        assertTrue(responseHandler.completed());
    }

    @Test
    void testStreamingQueryWithConcurrentRequests() throws InterruptedException {
        QueryMessage<FluxQuery, Publisher<String>> queryMessage =
                new GenericQueryMessage<>(new FluxQuery(1000),
                                          ResponseTypes.publisherOf(String.class));

        QueryRequest request =
                querySerializer.serializeRequest(queryMessage, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1, true)
                               .toBuilder()
                               .addProcessingInstructions(asSupportsStreaming())
                               .build();
        QueryProcessingTask task = new QueryProcessingTask(localSegment,
                                                           request,
                                                           responseHandler,
                                                           querySerializer,
                                                           CLIENT_ID);

        CountDownLatch latch = new CountDownLatch(101);
        Runnable queryExecutor = () -> {
            task.run();
            latch.countDown();
        };
        ThreadLocalRandom localRandom = ThreadLocalRandom.current();
        Runnable requester = () -> {
            try {
                Thread.sleep(localRandom.nextInt(50));
            } catch (InterruptedException e) {
                // who cares
            }
            task.request(10);
            latch.countDown();
        };

        int queryExecutorPosition = localRandom.nextInt(0, 101);
        ExecutorService service = Executors.newFixedThreadPool(101);
        for (int i = 0; i < 101; i++) {
            if (i == queryExecutorPosition) {
                service.submit(queryExecutor);
            } else {
                service.submit(requester);
            }
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(1000, responseHandler.sent().size());
        assertOrder(responseHandler.sent());
        assertTrue(responseHandler.completed());
    }

    @Test
    void testStreamingStringViaDirectQuery() {
        QueryMessage<InstanceQuery, String> queryMessage =
                new GenericQueryMessage<>(new InstanceQuery(), ResponseTypes.instanceOf(String.class));

        QueryRequest request =
                querySerializer.serializeRequest(queryMessage, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1)
                               .toBuilder()
                               .addProcessingInstructions(asSupportsStreaming())
                               .build();
        QueryProcessingTask task = new QueryProcessingTask(localSegment,
                                                           request,
                                                           responseHandler,
                                                           querySerializer,
                                                           CLIENT_ID);

        task.request(10);
        task.run();
        assertEquals(1, responseHandler.sent().size());
        String payload = querySerializer.deserializeResponse(responseHandler.sent().get(0),
                                                             ResponseTypes.instanceOf(String.class))
                                        .getPayload();
        assertEquals("value", payload);
        assertTrue(responseHandler.completed());
    }

    @Test
    void testMultipleInstanceQueryShouldInvokeFlux() {
        QueryMessage<MultipleInstanceQuery, Publisher<String>> queryMessage =
                new GenericQueryMessage<>(new MultipleInstanceQuery(1000), ResponseTypes.publisherOf(String.class));

        QueryRequest request =
                querySerializer.serializeRequest(queryMessage, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1, true)
                               .toBuilder()
                               .addProcessingInstructions(asSupportsStreaming())
                               .build();

        QueryProcessingTask task = new QueryProcessingTask(localSegment,
                                                           request,
                                                           responseHandler,
                                                           querySerializer,
                                                           CLIENT_ID);

        task.run();
        task.request(1000);
        assertEquals(1000, responseHandler.sent().size());
        assertTrue(responseHandler.completed());
        String firstPayload =
                querySerializer.deserializeResponse(responseHandler.sent().get(0),
                                                    ResponseTypes.instanceOf(String.class))
                               .getPayload();
        assertTrue(firstPayload.startsWith("flux-"));
    }

    @Test
    void testCancellationOfStreamingFluxQuery() {
        QueryMessage<FluxQuery, Publisher<String>> queryMessage =
                new GenericQueryMessage<>(new FluxQuery(1000), ResponseTypes.publisherOf(String.class));

        QueryRequest request =
                querySerializer.serializeRequest(queryMessage, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1, true)
                               .toBuilder()
                               .addProcessingInstructions(asSupportsStreaming())
                               .build();

        QueryProcessingTask task = new QueryProcessingTask(localSegment,
                                                           request,
                                                           responseHandler,
                                                           querySerializer,
                                                           CLIENT_ID);
        task.run();
        assertTrue(responseHandler.sent().isEmpty());
        task.request(100);
        assertEquals(100, responseHandler.sent().size());
        assertOrder(responseHandler.sent());
        task.cancel();
        assertEquals(100, responseHandler.sent().size());
        assertOrder(responseHandler.sent());
        assertTrue(queryHandlingComponent1.fluxQueryCancelled());
        assertTrue(responseHandler.completed());
    }

    @Test
    void testStreamingFluxQueryWhenCancelMessageComesFirst() {
        QueryMessage<FluxQuery, Publisher<String>> queryMessage =
                new GenericQueryMessage<>(new FluxQuery(1000), ResponseTypes.publisherOf(String.class));

        QueryRequest request =
                querySerializer.serializeRequest(queryMessage, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1, true)
                               .toBuilder()
                               .addProcessingInstructions(asSupportsStreaming())
                               .build();

        QueryProcessingTask task = new QueryProcessingTask(localSegment,
                                                           request,
                                                           responseHandler,
                                                           querySerializer,
                                                           CLIENT_ID);
        task.cancel();
        task.run();
        assertTrue(responseHandler.sent().isEmpty());
        assertTrue(responseHandler.completed());
    }

    @Test
    void testCancellationOfStreamingListQuery() {
        QueryMessage<ListQuery, List<String>> queryMessage =
                new GenericQueryMessage<>(new ListQuery(1000), ResponseTypes.multipleInstancesOf(String.class));

        QueryRequest request =
                querySerializer.serializeRequest(queryMessage, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1, true)
                               .toBuilder()
                               .addProcessingInstructions(asSupportsStreaming())
                               .build();

        QueryProcessingTask task = new QueryProcessingTask(localSegment,
                                                           request,
                                                           responseHandler,
                                                           querySerializer,
                                                           CLIENT_ID,
                                                           () -> false);
        task.run();
        assertTrue(responseHandler.sent().isEmpty());
        task.request(100);
        assertEquals(100, responseHandler.sent().size());
        assertOrder(responseHandler.sent());
        task.cancel();
        assertEquals(100, responseHandler.sent().size());
        assertOrder(responseHandler.sent());
        assertTrue(responseHandler.completed());
    }

    @Test
    void testStreamingListQueryWhenCancelMessageComesFirst() {
        QueryMessage<ListQuery, List<String>> queryMessage =
                new GenericQueryMessage<>(new ListQuery(1000), ResponseTypes.multipleInstancesOf(String.class));

        QueryRequest request =
                querySerializer.serializeRequest(queryMessage, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1, true)
                               .toBuilder()
                               .addProcessingInstructions(asSupportsStreaming())
                               .build();

        QueryProcessingTask task = new QueryProcessingTask(localSegment,
                                                           request,
                                                           responseHandler,
                                                           querySerializer,
                                                           CLIENT_ID,
                                                           () -> false);
        task.cancel();
        task.run();
        assertTrue(responseHandler.sent().isEmpty());
        assertTrue(responseHandler.completed());
    }

    @Test
    void testFluxEmittingErrorAfterAWhile() {
        QueryMessage<ErroringAfterAWhileFluxQuery, Publisher<String>> queryMessage =
                new GenericQueryMessage<>(new ErroringAfterAWhileFluxQuery(), ResponseTypes.publisherOf(String.class));

        QueryRequest request =
                querySerializer.serializeRequest(queryMessage, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1, true)
                               .toBuilder()
                               .addProcessingInstructions(asSupportsStreaming())
                               .build();

        QueryProcessingTask task = new QueryProcessingTask(localSegment,
                                                           request,
                                                           responseHandler,
                                                           querySerializer,
                                                           CLIENT_ID);

        task.run();
        task.request(100);
        assertEquals(4, responseHandler.sent().size());
        assertTrue(responseHandler.completed());
        assertOrder(responseHandler.sent().stream().limit(3).collect(Collectors.toList()));
        QueryResponse queryResponse = responseHandler.sent().get(3);
        assertTrue(queryResponse.hasErrorMessage());
    }

    @Test
    void testFluxEmittingErrorRightAway() {
        QueryMessage<ErroringFluxQuery, Publisher<String>> queryMessage =
                new GenericQueryMessage<>(new ErroringFluxQuery(), ResponseTypes.publisherOf(String.class));

        QueryRequest request =
                querySerializer.serializeRequest(queryMessage, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1)
                               .toBuilder()
                               .addProcessingInstructions(asSupportsStreaming())
                               .build();

        QueryProcessingTask task = new QueryProcessingTask(localSegment,
                                                           request,
                                                           responseHandler,
                                                           querySerializer,
                                                           CLIENT_ID);

        task.run();
        task.request(100);
        assertEquals(1, responseHandler.sent().size());
        assertTrue(responseHandler.completed());
        QueryResponse queryResponse = responseHandler.sent().get(0);
        assertTrue(queryResponse.hasErrorMessage());
    }

    @Test
    void testFluxHandlerThrowingAnException() {
        QueryMessage<ThrowingExceptionFluxQuery, Publisher<String>> queryMessage =
                new GenericQueryMessage<>(new ThrowingExceptionFluxQuery(), ResponseTypes.publisherOf(String.class));

        QueryRequest request =
                querySerializer.serializeRequest(queryMessage, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1)
                               .toBuilder()
                               .addProcessingInstructions(asSupportsStreaming())
                               .build();

        QueryProcessingTask task = new QueryProcessingTask(localSegment,
                                                           request,
                                                           responseHandler,
                                                           querySerializer,
                                                           CLIENT_ID);

        task.run();
        task.request(100);
        assertEquals(1, responseHandler.sent().size());
        assertTrue(responseHandler.completed());
        QueryResponse queryResponse = responseHandler.sent().get(0);
        assertTrue(queryResponse.hasErrorMessage());
    }

    @Test
    void testListHandlerThrowingAnException() {
        QueryMessage<ThrowingExceptionListQuery, List<String>> queryMessage =
                new GenericQueryMessage<>(new ThrowingExceptionListQuery(),
                                          ResponseTypes.multipleInstancesOf(String.class));

        QueryRequest request =
                querySerializer.serializeRequest(queryMessage, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1)
                               .toBuilder()
                               .addProcessingInstructions(asSupportsStreaming())
                               .build();

        QueryProcessingTask task = new QueryProcessingTask(localSegment,
                                                           request,
                                                           responseHandler,
                                                           querySerializer,
                                                           CLIENT_ID);

        task.run();
        task.request(100);
        assertEquals(1, responseHandler.sent().size());
        assertTrue(responseHandler.completed());
        QueryResponse queryResponse = responseHandler.sent().get(0);
        assertTrue(queryResponse.hasErrorMessage());
    }

    @Test
    void testFluxStreamingQueryWhenRequestingTooMany() {
        QueryMessage<FluxQuery, Publisher<String>> queryMessage =
                new GenericQueryMessage<>(new FluxQuery(1000), ResponseTypes.publisherOf(String.class));

        QueryRequest request =
                querySerializer.serializeRequest(queryMessage, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1, true)
                               .toBuilder()
                               .addProcessingInstructions(asSupportsStreaming())
                               .build();

        QueryProcessingTask task = new QueryProcessingTask(localSegment,
                                                           request,
                                                           responseHandler,
                                                           querySerializer,
                                                           CLIENT_ID);

        task.run();
        task.request(Long.MAX_VALUE);
        task.request(6);
        assertEquals(1000, responseHandler.sent().size());
        assertOrder(responseHandler.sent());
        assertTrue(responseHandler.completed());
    }

    @Test
    void testListStreamingQueryWhenRequestingTooMany() {
        QueryMessage<ListQuery, List<String>> queryMessage =
                new GenericQueryMessage<>(new ListQuery(1000), ResponseTypes.multipleInstancesOf(String.class));

        QueryRequest request =
                querySerializer.serializeRequest(queryMessage, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1, true)
                               .toBuilder()
                               .addProcessingInstructions(asSupportsStreaming())
                               .build();

        QueryProcessingTask task = new QueryProcessingTask(localSegment,
                                                           request,
                                                           responseHandler,
                                                           querySerializer,
                                                           CLIENT_ID);

        task.run();
        task.request(Long.MAX_VALUE);
        task.request(6);
        assertEquals(1000, responseHandler.sent().size());
        assertOrder(responseHandler.sent());
        assertTrue(responseHandler.completed());
    }

    private void assertOrder(List<QueryResponse> responses) {
        for (int i = 0; i < responses.size(); i++) {
            QueryResponseMessage<String> responseMessage =
                    querySerializer.deserializeResponse(responses.get(i), ResponseTypes.instanceOf(String.class));
            assertEquals(i, Integer.parseInt(responseMessage.getPayload()));
        }
    }

    private void assertOrder(QueryResponse response) {
        List<String> responses = querySerializer.deserializeResponse(response,
                                                                     ResponseTypes.multipleInstancesOf(String.class))
                                                .getPayload();
        for (int i = 0; i < responses.size(); i++) {
            assertEquals(i, Integer.parseInt(responses.get(i)));
        }
    }

    private ProcessingInstruction asSupportsStreaming() {
        return ProcessingInstruction.newBuilder()
                                    .setKey(ProcessingKey.SERVER_SUPPORTS_STREAMING)
                                    .setValue(MetaDataValue.newBuilder()
                                                           .setBooleanValue(true)
                                                           .build())
                                    .build();
    }

    private static class QueryHandlingComponent1 {

        private final AtomicBoolean fluxQueryCancelled = new AtomicBoolean();

        public boolean fluxQueryCancelled() {
            return fluxQueryCancelled.get();
        }

        @QueryHandler
        public Flux<String> fluxQuery(FluxQuery query) {
            return Flux.range(0, query.numberOfResults())
                       .doOnCancel(() -> fluxQueryCancelled.set(true))
                       .map(Objects::toString);
        }

        @QueryHandler
        public List<String> listQuery(ListQuery query) {
            return IntStream.range(0, query.numberOfResults())
                            .mapToObj(Objects::toString)
                            .collect(Collectors.toList());
        }

        @QueryHandler
        public String instanceQuery(InstanceQuery query) {
            return "value";
        }

        @QueryHandler
        public Flux<String> fluxMultiInstance(MultipleInstanceQuery query) {
            return Flux.range(0, query.numberOfResults())
                       .map(Objects::toString)
                       .map(s -> "flux-" + s);
        }
    }

    private static class QueryHandlingComponent2 {

        @QueryHandler
        public List<String> listMultiInstance(MultipleInstanceQuery query) {
            return IntStream.range(0, query.numberOfResults())
                            .mapToObj(Objects::toString)
                            .map(s -> "list-" + s)
                            .collect(Collectors.toList());
        }

        @QueryHandler
        public Flux<String> erroringFluxQuery(ErroringFluxQuery query) {
            return Flux.error(new RuntimeException("oops"));
        }

        @QueryHandler
        public Flux<String> erroringFluxQuery(ErroringAfterAWhileFluxQuery query) {
            return Flux.just("0", "1", "2")
                       .concatWith(Flux.error(new RuntimeException("oops")));
        }

        @QueryHandler
        public Flux<String> erroringFluxQuery(ThrowingExceptionFluxQuery query) {
            throw new RuntimeException("oops");
        }

        @QueryHandler
        public Flux<String> erroringFluxQuery(ThrowingExceptionListQuery query) {
            throw new RuntimeException("oops");
        }
    }

    private static class MultipleInstanceQuery {

        private final int numberOfResults;

        private MultipleInstanceQuery(int numberOfResults) {
            this.numberOfResults = numberOfResults;
        }

        public int numberOfResults() {
            return numberOfResults;
        }
    }

    private static class FluxQuery {

        private final int numberOfResults;

        private FluxQuery(int numberOfResults) {
            this.numberOfResults = numberOfResults;
        }

        public int numberOfResults() {
            return numberOfResults;
        }
    }

    private static class ListQuery {

        private final int numberOfResults;

        private ListQuery(int numberOfResults) {
            this.numberOfResults = numberOfResults;
        }

        public int numberOfResults() {
            return numberOfResults;
        }
    }

    private static class ErroringFluxQuery {

    }

    private static class ErroringAfterAWhileFluxQuery {

    }

    private static class ThrowingExceptionFluxQuery {

    }

    private static class ThrowingExceptionListQuery {

    }

    private static class InstanceQuery {

    }

    private static class CachingReplyChannel<T> implements ReplyChannel<T> {

        private final List<T> cache = new CopyOnWriteArrayList<>();
        private volatile boolean completed = false;

        @Override
        public void send(T outboundMessage) {
            cache.add(outboundMessage);
        }

        @Override
        public void sendAck() {
            // noop
        }

        @Override
        public void sendNack(ErrorMessage errorMessage) {
            // noop
        }

        @Override
        public void complete() {
            completed = true;
        }

        @Override
        public void completeWithError(ErrorMessage errorMessage) {
            // noop
        }

        @Override
        public void completeWithError(ErrorCategory errorCategory, String message) {
            // noop
        }

        public List<T> sent() {
            return Collections.unmodifiableList(cache);
        }

        public boolean completed() {
            return completed;
        }
    }
}
