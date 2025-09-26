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

package org.axonframework.axonserver.connector.query;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.axoniq.axonserver.connector.ErrorCategory;
import io.axoniq.axonserver.connector.ReplyChannel;
import io.axoniq.axonserver.grpc.ErrorMessage;
import io.axoniq.axonserver.grpc.MetaDataValue;
import io.axoniq.axonserver.grpc.ProcessingInstruction;
import io.axoniq.axonserver.grpc.ProcessingKey;
import io.axoniq.axonserver.grpc.query.QueryRequest;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.common.TypeReference;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.conversion.MessageConverter;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryBusTestUtils;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.annotations.AnnotatedQueryHandlingComponent;
import org.axonframework.queryhandling.annotations.QueryHandler;
import org.axonframework.queryhandling.tracing.DefaultQueryBusSpanFactory;
import org.axonframework.queryhandling.tracing.QueryBusSpanFactory;
import org.axonframework.serialization.PassThroughConverter;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.tracing.TestSpanFactory;
import org.junit.jupiter.api.*;
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

import static org.axonframework.messaging.responsetypes.ResponseTypes.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests of {@link QueryProcessingTask}.
 */
@Disabled("TODO #3488 - Axon Server Query Bus replacement")
class QueryProcessingTaskIntegrationTest {

    private static final TypeReference<List<String>> LIST_OF_STRINGS = new TypeReference<>() {
    };
    private static final String CLIENT_ID = "clientId";
    private static final String COMPONENT_NAME = "componentName";
    private static final int DIRECT_QUERY_NUMBER_OF_RESULTS = 1;
    private QueryBus localSegment;
    private CachingReplyChannel<QueryResponse> responseHandler;
    private QuerySerializer querySerializer;
    private TestSpanFactory spanFactory;
    private QueryBusSpanFactory queryBusSpanFactory;

    private QueryHandlingComponent1 queryHandlingComponent1;

    @BeforeEach
    void setUp() {
        spanFactory = new TestSpanFactory();
        queryBusSpanFactory = DefaultQueryBusSpanFactory.builder()
                                                        .spanFactory(spanFactory)
                                                        .build();
        localSegment = QueryBusTestUtils.aQueryBus();
        responseHandler = new CachingReplyChannel<>();
        Serializer serializer = JacksonSerializer.defaultSerializer();
        AxonServerConfiguration config = AxonServerConfiguration.builder()
                                                                .clientId(CLIENT_ID)
                                                                .componentName(COMPONENT_NAME)
                                                                .build();
        querySerializer = new QuerySerializer(serializer, serializer, config);
        queryHandlingComponent1 = new QueryHandlingComponent1();
        MessageConverter converter = PassThroughConverter.MESSAGE_INSTANCE;
        localSegment.subscribe(new AnnotatedQueryHandlingComponent<>(queryHandlingComponent1, converter));
        localSegment.subscribe(new AnnotatedQueryHandlingComponent<>(new QueryHandlingComponent2(), converter));
    }

    @Test
    void directQueryWhenRequesterDoesntSupportStreaming() {
        QueryMessage queryMessage =
                new GenericQueryMessage(new MessageType(FluxQuery.class),
                                        new FluxQuery(1000),
                                        publisherOf(String.class));
        QueryRequest request =
                querySerializer.serializeRequest(queryMessage, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1);

        QueryProcessingTask task = new QueryProcessingTask(localSegment,
                                                           request,
                                                           responseHandler,
                                                           querySerializer,
                                                           CLIENT_ID,
                                                           queryBusSpanFactory);

        task.run();
        task.request(10);
        assertEquals(1, responseHandler.sent().size());
        assertOrder(responseHandler.sent().getFirst());
        assertTrue(responseHandler.completed());
    }

    @Test
    void queryProcessingTaskIsTraced() {
        QueryMessage queryMessage =
                new GenericQueryMessage(new MessageType(FluxQuery.class),
                                        new FluxQuery(1000),
                                        publisherOf(String.class));
        QueryRequest request =
                querySerializer.serializeRequest(queryMessage, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1);

        QueryProcessingTask task = new QueryProcessingTask(localSegment,
                                                           request,
                                                           responseHandler,
                                                           querySerializer,
                                                           CLIENT_ID,
                                                           queryBusSpanFactory);

        task.run();
        spanFactory.verifySpanCompleted("QueryBus.processQueryMessage");
    }

    @Test
    void directQueryWhenRequesterDoesntSupportStreamingAndFlowControlMessagesComesBeforeQueryExecution() {
        QueryMessage queryMessage =
                new GenericQueryMessage(new MessageType(FluxQuery.class),
                                        new FluxQuery(1000),
                                        publisherOf(String.class));
        QueryRequest request =
                querySerializer.serializeRequest(queryMessage, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1);

        QueryProcessingTask task = new QueryProcessingTask(localSegment,
                                                           request,
                                                           responseHandler,
                                                           querySerializer,
                                                           CLIENT_ID,
                                                           queryBusSpanFactory);

        task.request(10);
        task.run();
        assertEquals(1, responseHandler.sent().size());
        assertOrder(responseHandler.sent().getFirst());
        assertTrue(responseHandler.completed());
    }

    @Test
    void directQueryWhenRequesterDoesntSupportStreamingAndCancelMessagesComesBeforeQueryExecution() {
        QueryMessage queryMessage =
                new GenericQueryMessage(new MessageType(FluxQuery.class),
                                        new FluxQuery(1000),
                                        publisherOf(String.class));
        QueryRequest request =
                querySerializer.serializeRequest(queryMessage, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1);

        QueryProcessingTask task = new QueryProcessingTask(localSegment,
                                                           request,
                                                           responseHandler,
                                                           querySerializer,
                                                           CLIENT_ID,
                                                           queryBusSpanFactory);

        task.cancel();
        task.run();
        assertTrue(responseHandler.sent().isEmpty());
        assertTrue(responseHandler.completed());
    }

    @Test
    void streamingQuery() {
        QueryMessage queryMessage =
                new GenericQueryMessage(new MessageType(FluxQuery.class),
                                        new FluxQuery(1000),
                                        publisherOf(String.class));
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
                                                           queryBusSpanFactory);

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
    void streamingAList() {
        QueryMessage queryMessage = new GenericQueryMessage(
                new MessageType(ListQuery.class), new ListQuery(1000), multipleInstancesOf(String.class)
        );
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
                                                           queryBusSpanFactory);

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
    void streamingAListWhenReactorIsNotOnClasspath() {
        QueryMessage queryMessage = new GenericQueryMessage(
                new MessageType(ListQuery.class), new ListQuery(1000), multipleInstancesOf(String.class)
        );
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
                                                           () -> false, queryBusSpanFactory);

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
    void streamingAListWhenReactorIsNotOnClasspathWithConcurrentRequests() throws InterruptedException {
        QueryMessage queryMessage = new GenericQueryMessage(
                new MessageType(ListQuery.class), new ListQuery(1000), multipleInstancesOf(String.class)
        );
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
                                                           () -> false, queryBusSpanFactory);

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
    void streamingQueryWithConcurrentRequests() throws InterruptedException {
        QueryMessage queryMessage =
                new GenericQueryMessage(new MessageType(FluxQuery.class),
                                        new FluxQuery(1000),
                                        publisherOf(String.class));
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
                                                           queryBusSpanFactory);

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
    void streamingStringViaDirectQuery() {
        QueryMessage queryMessage =
                new GenericQueryMessage(new MessageType(InstanceQuery.class),
                                        new InstanceQuery(),
                                        instanceOf(String.class));
        QueryRequest request =
                querySerializer.serializeRequest(queryMessage, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1)
                               .toBuilder()
                               .addProcessingInstructions(asSupportsStreaming())
                               .build();

        QueryProcessingTask task = new QueryProcessingTask(localSegment,
                                                           request,
                                                           responseHandler,
                                                           querySerializer,
                                                           CLIENT_ID,
                                                           queryBusSpanFactory);

        task.request(10);
        task.run();
        assertEquals(1, responseHandler.sent().size());
        Object payload = querySerializer.deserializeResponse(responseHandler.sent().getFirst(),
                                                             instanceOf(String.class))
                                        .payload();
        assertEquals("value", payload);
        assertTrue(responseHandler.completed());
    }

    @Test
    void multipleInstanceQueryShouldInvokeFlux() {
        QueryMessage queryMessage = new GenericQueryMessage(
                new MessageType(MultipleInstanceQuery.class),
                new MultipleInstanceQuery(1000),
                publisherOf(String.class)
        );
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
                                                           queryBusSpanFactory);

        task.run();
        task.request(1000);
        assertEquals(1000, responseHandler.sent().size());
        assertTrue(responseHandler.completed());
        String firstPayload =
                querySerializer.deserializeResponse(responseHandler.sent().getFirst(), instanceOf(String.class))
                               .payloadAs(String.class);
        assertTrue(firstPayload.startsWith("flux-"));
    }

    @Test
    void cancellationOfStreamingFluxQuery() {
        QueryMessage queryMessage =
                new GenericQueryMessage(new MessageType(FluxQuery.class),
                                        new FluxQuery(1000),
                                        publisherOf(String.class));
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
                                                           queryBusSpanFactory);
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
    void streamingFluxQueryWhenCancelMessageComesFirst() {
        QueryMessage queryMessage =
                new GenericQueryMessage(new MessageType(FluxQuery.class),
                                        new FluxQuery(1000),
                                        publisherOf(String.class));
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
                                                           queryBusSpanFactory);
        task.cancel();
        task.run();
        assertTrue(responseHandler.sent().isEmpty());
        assertTrue(responseHandler.completed());
    }

    @Test
    void cancellationOfStreamingListQuery() {
        QueryMessage queryMessage = new GenericQueryMessage(
                new MessageType(ListQuery.class), new ListQuery(1000), multipleInstancesOf(String.class)
        );
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
                                                           () -> false, queryBusSpanFactory);
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
    void streamingListQueryWhenCancelMessageComesFirst() {
        QueryMessage queryMessage = new GenericQueryMessage(
                new MessageType(ListQuery.class), new ListQuery(1000), multipleInstancesOf(String.class)
        );
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
                                                           () -> false, queryBusSpanFactory);
        task.cancel();
        task.run();
        assertTrue(responseHandler.sent().isEmpty());
        assertTrue(responseHandler.completed());
    }

    @Test
    void fluxEmittingErrorAfterAWhile() {
        QueryMessage queryMessage = new GenericQueryMessage(
                new MessageType(ErrorAfterAWhileFluxQuery.class),
                new ErrorAfterAWhileFluxQuery(),
                publisherOf(String.class)
        );
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
                                                           queryBusSpanFactory);

        task.run();
        task.request(100);
        assertEquals(4, responseHandler.sent().size());
        assertTrue(responseHandler.completed());
        assertOrder(responseHandler.sent().stream().limit(3).collect(Collectors.toList()));
        QueryResponse queryResponse = responseHandler.sent().get(3);
        assertTrue(queryResponse.hasErrorMessage());
    }

    @Test
    void fluxEmittingErrorRightAway() {
        QueryMessage queryMessage =
                new GenericQueryMessage(new MessageType(ErrorFluxQuery.class),
                                        new ErrorFluxQuery(),
                                        publisherOf(String.class));
        QueryRequest request =
                querySerializer.serializeRequest(queryMessage, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1)
                               .toBuilder()
                               .addProcessingInstructions(asSupportsStreaming())
                               .build();

        QueryProcessingTask task = new QueryProcessingTask(localSegment,
                                                           request,
                                                           responseHandler,
                                                           querySerializer,
                                                           CLIENT_ID,
                                                           queryBusSpanFactory);

        task.run();
        task.request(100);
        assertEquals(1, responseHandler.sent().size());
        assertTrue(responseHandler.completed());
        QueryResponse queryResponse = responseHandler.sent().getFirst();
        assertTrue(queryResponse.hasErrorMessage());
    }

    @Test
    void fluxHandlerThrowingAnException() {
        QueryMessage queryMessage = new GenericQueryMessage(
                new MessageType(ThrowingExceptionFluxQuery.class),
                new ThrowingExceptionFluxQuery(),
                publisherOf(String.class)
        );
        QueryRequest request =
                querySerializer.serializeRequest(queryMessage, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1)
                               .toBuilder()
                               .addProcessingInstructions(asSupportsStreaming())
                               .build();

        QueryProcessingTask task = new QueryProcessingTask(localSegment,
                                                           request,
                                                           responseHandler,
                                                           querySerializer,
                                                           CLIENT_ID,
                                                           queryBusSpanFactory);

        task.run();
        task.request(100);
        assertEquals(1, responseHandler.sent().size());
        assertTrue(responseHandler.completed());
        QueryResponse queryResponse = responseHandler.sent().getFirst();
        assertTrue(queryResponse.hasErrorMessage());
    }

    @Test
    void listHandlerThrowingAnException() {
        QueryMessage queryMessage = new GenericQueryMessage(
                new MessageType(ThrowingExceptionListQuery.class),
                new ThrowingExceptionListQuery(),
                multipleInstancesOf(String.class)
        );
        QueryRequest request =
                querySerializer.serializeRequest(queryMessage, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1)
                               .toBuilder()
                               .addProcessingInstructions(asSupportsStreaming())
                               .build();

        QueryProcessingTask task = new QueryProcessingTask(localSegment,
                                                           request,
                                                           responseHandler,
                                                           querySerializer,
                                                           CLIENT_ID,
                                                           queryBusSpanFactory);

        task.run();
        task.request(100);
        assertEquals(1, responseHandler.sent().size());
        assertTrue(responseHandler.completed());
        QueryResponse queryResponse = responseHandler.sent().getFirst();
        assertTrue(queryResponse.hasErrorMessage());
    }

    @Test
    void fluxStreamingQueryWhenRequestingTooMany() {
        QueryMessage queryMessage =
                new GenericQueryMessage(new MessageType(FluxQuery.class),
                                        new FluxQuery(1000),
                                        publisherOf(String.class));
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
                                                           queryBusSpanFactory);

        task.run();
        task.request(Long.MAX_VALUE);
        task.request(6);
        assertEquals(1000, responseHandler.sent().size());
        assertOrder(responseHandler.sent());
        assertTrue(responseHandler.completed());
    }

    @Test
    @Disabled("TODO #3488")
    void listStreamingQueryWhenRequestingTooMany() {
        QueryMessage queryMessage = new GenericQueryMessage(
                new MessageType("query"), new ListQuery(1000), multipleInstancesOf(String.class)
        );
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
                                                           queryBusSpanFactory);

        task.run();
        task.request(Long.MAX_VALUE);
        task.request(6);
        assertEquals(1000, responseHandler.sent().size());
        assertOrder(responseHandler.sent());
        assertTrue(responseHandler.completed());
    }

    @Test
    void responsePendingReturnsTrueForUncompletedTask() {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(FluxQuery.class),
                new FluxQuery(1000),
                ResponseTypes.publisherOf(String.class)
        );
        QueryRequest testRequest = querySerializer.serializeRequest(testQuery, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1);
        QueryProcessingTask testSubject = new QueryProcessingTask(localSegment,
                                                                  testRequest,
                                                                  responseHandler,
                                                                  querySerializer,
                                                                  CLIENT_ID,
                                                                  queryBusSpanFactory);

        assertTrue(testSubject.resultPending());
    }

    @Test
    void responsePendingReturnsFalseForCompletedTask() {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(FluxQuery.class),
                new FluxQuery(1),
                ResponseTypes.publisherOf(String.class)
        );
        QueryRequest testRequest = querySerializer.serializeRequest(testQuery, DIRECT_QUERY_NUMBER_OF_RESULTS, 1000, 1);
        QueryProcessingTask testSubject = new QueryProcessingTask(localSegment,
                                                                  testRequest,
                                                                  responseHandler,
                                                                  querySerializer,
                                                                  CLIENT_ID,
                                                                  queryBusSpanFactory);

        assertTrue(testSubject.resultPending());
        testSubject.run();
        testSubject.request(1);
        assertEquals(1, responseHandler.sent().size());
        assertOrder(responseHandler.sent().getFirst());
        assertTrue(responseHandler.completed());
        assertFalse(testSubject.resultPending());
    }

    private void assertOrder(List<QueryResponse> responses) {
        for (int i = 0; i < responses.size(); i++) {
            QueryResponseMessage responseMessage =
                    querySerializer.deserializeResponse(responses.get(i), instanceOf(String.class));
            assertEquals(i, Integer.parseInt(responseMessage.payloadAs(String.class)));
        }
    }

    private void assertOrder(QueryResponse response) {
        List<String> responses = querySerializer.deserializeResponse(response, multipleInstancesOf(String.class))
                                                .payloadAs(LIST_OF_STRINGS);
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

    @SuppressWarnings("unused") // Suppressing query handler unused message, as they are used.
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

    @SuppressWarnings("unused") // Suppressing query handler unused message, as they are used.
    private static class QueryHandlingComponent2 {

        @QueryHandler
        public List<String> listMultiInstance(MultipleInstanceQuery query) {
            return IntStream.range(0, query.numberOfResults())
                            .mapToObj(Objects::toString)
                            .map(s -> "list-" + s)
                            .collect(Collectors.toList());
        }

        @QueryHandler
        public Flux<String> errorFluxQuery(ErrorFluxQuery query) {
            return Flux.error(new RuntimeException("oops"));
        }

        @QueryHandler
        public Flux<String> errorFluxQuery(ErrorAfterAWhileFluxQuery query) {
            return Flux.just("0", "1", "2")
                       .concatWith(Flux.error(new RuntimeException("oops")));
        }

        @QueryHandler
        public Flux<String> errorFluxQuery(ThrowingExceptionFluxQuery query) {
            throw new RuntimeException("oops");
        }

        @QueryHandler
        public Flux<String> errorFluxQuery(ThrowingExceptionListQuery query) {
            throw new RuntimeException("oops");
        }
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    private static class MultipleInstanceQuery {

        private final int numberOfResults;

        private MultipleInstanceQuery(@JsonProperty("numberOfResults") int numberOfResults) {
            this.numberOfResults = numberOfResults;
        }

        public int numberOfResults() {
            return numberOfResults;
        }
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    private static class FluxQuery {

        private final int numberOfResults;

        private FluxQuery(@JsonProperty("numberOfResults") int numberOfResults) {
            this.numberOfResults = numberOfResults;
        }

        public int numberOfResults() {
            return numberOfResults;
        }
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    private static class ListQuery {

        private final int numberOfResults;

        private ListQuery(@JsonProperty("numberOfResults") int numberOfResults) {
            this.numberOfResults = numberOfResults;
        }

        public int numberOfResults() {
            return numberOfResults;
        }
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    private static class ErrorFluxQuery {

    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    private static class ErrorAfterAWhileFluxQuery {

    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    private static class ThrowingExceptionFluxQuery {

    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    private static class ThrowingExceptionListQuery {

    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
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
