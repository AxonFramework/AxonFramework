/*
 * Copyright (c) 2010-2024. Axon Framework
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

import com.google.protobuf.ByteString;
import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.ErrorCategory;
import io.axoniq.axonserver.connector.ReplyChannel;
import io.axoniq.axonserver.connector.ResultStream;
import io.axoniq.axonserver.connector.query.QueryChannel;
import io.axoniq.axonserver.connector.query.QueryDefinition;
import io.axoniq.axonserver.connector.query.QueryHandler;
import io.axoniq.axonserver.grpc.ErrorMessage;
import io.axoniq.axonserver.grpc.MetaDataValue;
import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.query.QueryRequest;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import io.axoniq.axonserver.grpc.query.QueryUpdate;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.TargetContextResolver;
import org.axonframework.axonserver.connector.TestTargetContextResolver;
import org.axonframework.axonserver.connector.util.ProcessingInstructionHelper;
import org.axonframework.axonserver.connector.utils.TestSerializer;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.Registration;
import org.axonframework.lifecycle.ShutdownInProgressException;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.responsetypes.InstanceResponseType;
import org.axonframework.queryhandling.DefaultQueryBusSpanFactory;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.GenericStreamingQueryMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryExecutionException;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SimpleQueryUpdateEmitter;
import org.axonframework.queryhandling.StreamingQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.serialization.Serializer;
import org.axonframework.tracing.TestSpanFactory;
import org.junit.jupiter.api.*;
import org.mockito.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static org.awaitility.Awaitility.await;
import static org.axonframework.axonserver.connector.utils.AssertUtils.assertWithin;
import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
import static org.axonframework.messaging.responsetypes.ResponseTypes.optionalInstanceOf;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test suite to verify the {@link AxonServerQueryBus}.
 *
 * @author Marc Gathier
 */
class AxonServerQueryBusTest {

    private static final String TEST_QUERY = "testQuery";
    private static final String CONTEXT = "default-test";
    private static final String INSTANCE_RESPONSE_TYPE_XML = "<org.axonframework.messaging.responsetypes.InstanceResponseType><expectedResponseType>java.lang.String</expectedResponseType></org.axonframework.messaging.responsetypes.InstanceResponseType>";

    private final QueryBus localSegment = mock(QueryBus.class);
    private final Serializer serializer = TestSerializer.xStreamSerializer();
    private final TargetContextResolver<QueryMessage<?, ?>> targetContextResolver = spy(new TestTargetContextResolver<>());

    private AxonServerConnectionManager axonServerConnectionManager;
    private QueryChannel mockQueryChannel;
    private TestSpanFactory spanFactory;

    private AxonServerQueryBus testSubject;
    private AxonServerConfiguration configuration;

    @BeforeEach
    void setup() {
        configuration = new AxonServerConfiguration();
        configuration.setContext(CONTEXT);

        spanFactory = new TestSpanFactory();

        axonServerConnectionManager = mock(AxonServerConnectionManager.class);

        testSubject = AxonServerQueryBus.builder()
                                        .axonServerConnectionManager(axonServerConnectionManager)
                                        .configuration(configuration)
                                        .localSegment(localSegment)
                                        .updateEmitter(SimpleQueryUpdateEmitter.builder().build())
                                        .messageSerializer(serializer)
                                        .genericSerializer(serializer)
                                        .targetContextResolver(targetContextResolver)
                                        .spanFactory(
                                                DefaultQueryBusSpanFactory.builder()
                                                                          .spanFactory(spanFactory)
                                                                          .build()
                                        )
                                        .build();

        AxonServerConnection mockConnection = mock(AxonServerConnection.class);
        mockQueryChannel = mock(QueryChannel.class);

        when(axonServerConnectionManager.getConnection(anyString())).thenReturn(mockConnection);
        when(axonServerConnectionManager.getConnection()).thenReturn(mockConnection);

        when(mockConnection.queryChannel()).thenReturn(mockQueryChannel);
        when(mockQueryChannel.registerQueryHandler(any(), any()))
                .thenReturn(FutureUtils::emptyCompletedFuture);

        when(localSegment.subscribe(any(), any(), any())).thenReturn(() -> true);
    }

    @AfterEach
    void tearDown() throws Exception {
        axonServerConnectionManager.shutdown();
        testSubject.shutdownDispatching().get(5, TimeUnit.SECONDS);
        testSubject.disconnect();
    }

    @Test
    void subscribe() {
        Registration result = testSubject.subscribe(TEST_QUERY, String.class, q -> "test");

        assertNotNull(result);
        verify(axonServerConnectionManager).getConnection(CONTEXT);
        verify(mockQueryChannel).registerQueryHandler(any(), eq(new QueryDefinition(TEST_QUERY, String.class)));
    }

    @Test
    void severalSubscribeInvocationsUseSameQueryHandlerInstance() {
        QueryDefinition firstExpectedQueryDefinition = new QueryDefinition(TEST_QUERY, String.class);
        QueryDefinition secondExpectedQueryDefinition = new QueryDefinition("testIntegerQuery", Integer.class);

        ArgumentCaptor<QueryHandler> queryHandlerCaptor = ArgumentCaptor.forClass(QueryHandler.class);

        Registration resultOne = testSubject.subscribe(TEST_QUERY, String.class, q -> "test");
        assertNotNull(resultOne);
        verify(mockQueryChannel).registerQueryHandler(queryHandlerCaptor.capture(), eq(firstExpectedQueryDefinition));

        Registration resultTwo = testSubject.subscribe("testIntegerQuery", Integer.class, q -> 1337);
        assertNotNull(resultTwo);
        verify(mockQueryChannel).registerQueryHandler(queryHandlerCaptor.capture(), eq(secondExpectedQueryDefinition));

        List<QueryHandler> resultQueryHandlers = queryHandlerCaptor.getAllValues();
        assertEquals(2, resultQueryHandlers.size());
        assertEquals(resultQueryHandlers.get(0), resultQueryHandlers.get(1));
    }

    @Test
    void query() throws Exception {
        when(mockQueryChannel.query(any())).thenReturn(new StubResultStream<>(stubResponse("<string>test</string>")));
        QueryMessage<String, String> testQuery = new GenericQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), "Hello, World", instanceOf(String.class)
        );

        assertEquals("test", testSubject.query(testQuery).get().getPayload());

        verify(targetContextResolver).resolveContext(testQuery);
        verify(localSegment, never()).query(testQuery);
        spanFactory.verifySpanCompleted("QueryBus.queryDistributed");
        spanFactory.verifySpanPropagated("QueryBus.queryDistributed", testQuery);
    }

    @Nested
    class LocalSegmentShortCutEnabled {

        private Registration registration;
        private final QueryMessage<String, String> testQuery = new GenericQueryMessage<>("Hello, World",
                                                                                         TEST_QUERY,
                                                                                         instanceOf(String.class));

        private final StreamingQueryMessage<String, String> testStreamingQuery =
                new GenericStreamingQueryMessage<>("Hello, World", TEST_QUERY, String.class);

        @BeforeEach
        void setUp() {
            testSubject = AxonServerQueryBus.builder()
                                            .axonServerConnectionManager(axonServerConnectionManager)
                                            .configuration(configuration)
                                            .localSegment(localSegment)
                                            .updateEmitter(SimpleQueryUpdateEmitter.builder().build())
                                            .messageSerializer(serializer)
                                            .genericSerializer(serializer)
                                            .targetContextResolver(targetContextResolver)
                                            .enabledLocalSegmentShortCut()
                                            .spanFactory(
                                                    DefaultQueryBusSpanFactory.builder()
                                                                              .spanFactory(spanFactory)
                                                                              .build()
                                            )
                                            .build();

            registration = testSubject.subscribe(TEST_QUERY, String.class, q -> "test");
        }

        @Test
        void queryWhenLocalHandlerIsPresent() {
            when(localSegment.query(testQuery))
                    .thenReturn(CompletableFuture.completedFuture(new GenericQueryResponseMessage<>("ok")));

            testSubject.query(testQuery);

            verify(localSegment).query(testQuery);
            verify(mockQueryChannel, never()).query(any());
        }

        @Test
        void queryWhenRegistrationIsCancel() {
            registration.cancel();

            testSubject.query(testQuery);

            verify(localSegment, never()).query(testQuery);
        }

        @Test
        void streamingQueryWhenLocalHandlerIsPresent() {
            when(localSegment.streamingQuery(testStreamingQuery))
                    .thenReturn(Flux.just(new GenericQueryResponseMessage<>("ok")));

            StepVerifier.create(Flux.from(testSubject.streamingQuery(testStreamingQuery))
                                    .map(Message::getPayload))
                        .expectNext("ok")
                        .verifyComplete();

            verify(localSegment).streamingQuery(testStreamingQuery);
            verify(mockQueryChannel, never()).query(any());
        }

        @Test
        void streamingQueryWhenRegistrationIsCancel() {
            registration.cancel();
            testSubject.streamingQuery(testStreamingQuery);

            verify(localSegment, never()).streamingQuery(testStreamingQuery);
        }
    }

    @Test
    void queryReportsDispatchException() throws Exception {
        //noinspection rawtypes
        StubResultStream t = new StubResultStream(new RuntimeException("Faking problems"));
        //noinspection unchecked
        when(mockQueryChannel.query(any())).thenReturn(t);
        QueryMessage<String, String> testQuery = new GenericQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), "Hello, World", instanceOf(String.class)
        );

        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(testQuery);
        try {
            result.get();
            fail("Expected exception");
        } catch (ExecutionException e) {
            assertInstanceOf(AxonServerQueryDispatchException.class, e.getCause());
            assertEquals("Faking problems", e.getCause().getMessage());
        }

        verify(targetContextResolver).resolveContext(testQuery);
        spanFactory.verifySpanCompleted("QueryBus.queryDistributed");
        spanFactory.verifySpanHasException("QueryBus.queryDistributed", AxonServerQueryDispatchException.class);
    }

    @Test
    void queryReportsCorrectException() throws ExecutionException, InterruptedException {
        when(mockQueryChannel.query(any())).thenReturn(new StubResultStream<>(
                stubErrorResponse(ErrorCode.QUERY_EXECUTION_ERROR.errorCode(), "Faking exception result")
        ));
        QueryMessage<String, String> testQuery = new GenericQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), "Hello, World", instanceOf(String.class)
        );

        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(testQuery);

        assertNotNull(result.get());
        assertFalse(result.isCompletedExceptionally());

        assertTrue(result.get().isExceptional());
        Throwable actual = result.get().exceptionResult();
        assertInstanceOf(QueryExecutionException.class, actual);
        AxonServerRemoteQueryHandlingException remoteQueryHandlingException =
                (AxonServerRemoteQueryHandlingException) actual.getCause();
        assertEquals(ErrorCode.QUERY_EXECUTION_ERROR.errorCode(), remoteQueryHandlingException.getErrorCode());

        verify(targetContextResolver).resolveContext(testQuery);
        spanFactory.verifySpanCompleted("QueryBus.queryDistributed");
        spanFactory.verifySpanHasException("QueryBus.queryDistributed", QueryExecutionException.class);
    }

    @Test
    void queryReportsCorrectNonTransientException() throws ExecutionException, InterruptedException {
        spanFactory.reset();
        when(mockQueryChannel.query(any())).thenReturn(new StubResultStream<>(
                stubErrorResponse(ErrorCode.QUERY_EXECUTION_NON_TRANSIENT_ERROR.errorCode(),
                                  "Faking non transient exception result")
        ));
        QueryMessage<String, String> testQuery = new GenericQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), "Hello, World", instanceOf(String.class)
        );

        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(testQuery);

        assertNotNull(result.get());
        assertFalse(result.isCompletedExceptionally());

        assertTrue(result.get().isExceptional());
        Throwable actual = result.get().exceptionResult();
        assertInstanceOf(QueryExecutionException.class, actual);
        AxonServerNonTransientRemoteQueryHandlingException remoteQueryHandlingException =
                (AxonServerNonTransientRemoteQueryHandlingException) actual.getCause();
        assertEquals(ErrorCode.QUERY_EXECUTION_NON_TRANSIENT_ERROR.errorCode(),
                     remoteQueryHandlingException.getErrorCode());

        verify(targetContextResolver).resolveContext(testQuery);
        await().untilAsserted(() -> {
            spanFactory.verifySpanCompleted("QueryBus.queryDistributed");
            spanFactory.verifySpanHasException("QueryBus.queryDistributed", QueryExecutionException.class);
        });
    }

    @Test
    void queryCloseConnectionOnCompletableFutureCancel() {
        //noinspection unchecked
        ResultStream<QueryResponse> resultStream = mock(ResultStream.class);
        when(mockQueryChannel.query(any())).thenReturn(resultStream);
        QueryMessage<String, String> testQuery = new GenericQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), "Hello, World", instanceOf(String.class)
        );
        testSubject.query(testQuery).cancel(true);
        verify(resultStream).close();
    }

    @Test
    void subscribeHandler() {
        when(mockQueryChannel.registerQueryHandler(any(), any()))
                .thenReturn(FutureUtils::emptyCompletedFuture);

        Registration result = testSubject.subscribe(TEST_QUERY, String.class, q -> "test: " + q.getPayloadType());

        assertNotNull(result);
        verify(mockQueryChannel).registerQueryHandler(any(), eq(new QueryDefinition(TEST_QUERY, String.class)));
    }

    @Test
    void unsubscribeHandler() {
        io.axoniq.axonserver.connector.Registration registration = mock(io.axoniq.axonserver.connector.Registration.class);
        when(mockQueryChannel.registerQueryHandler(any(), any())).thenReturn(registration);

        Registration result = testSubject.subscribe(TEST_QUERY, String.class, q -> "test: " + q.getPayloadType());
        assertNotNull(result);
        verify(mockQueryChannel).registerQueryHandler(any(), eq(new QueryDefinition(TEST_QUERY, String.class)));

        result.cancel();
        verify(registration).cancel();
    }

    @Test
    void scatterGather() {
        QueryMessage<String, String> testQuery = new GenericQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), "Hello, World", instanceOf(String.class)
        );

        when(mockQueryChannel.query(any())).thenReturn(new StubResultStream<>(stubResponse("<string>1</string>"),
                                                                              stubResponse("<string>2</string>"),
                                                                              stubResponse("<string>3</string>")));

        assertEquals(3, testSubject.scatterGather(testQuery, 12, TimeUnit.SECONDS).count());

        verify(targetContextResolver).resolveContext(testQuery);
        //noinspection resource
        verify(mockQueryChannel).query(argThat(
                r -> r.getPayload().getData().toStringUtf8().equals("<string>Hello, World</string>")
                        && -1 == ProcessingInstructionHelper.numberOfResults(r.getProcessingInstructionsList())));
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            spanFactory.verifySpanCompleted("QueryBus.scatterGatherQueryDistributed", testQuery);
            spanFactory.verifySpanPropagated("QueryBus.scatterGatherQueryDistributed", testQuery);
        });
    }

    @Test
    void scatterGatherCloseStreamDoesNotThrowExceptionOnCloseMethod() {
        QueryMessage<String, String> testQuery = new GenericQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), "Hello, World", instanceOf(String.class)
        );

        when(mockQueryChannel.query(any())).thenReturn(new StubResultStream<>(stubResponse("<string>1</string>"),
                                                                              stubResponse("<string>2</string>"),
                                                                              stubResponse("<string>3</string>")));

        Stream<QueryResponseMessage<String>> stream = testSubject.scatterGather(testQuery,
                                                                                12,
                                                                                TimeUnit.SECONDS);
        assertEquals(3, stream.count());
        stream.close();
    }

    @Test
    void streamingFluxQuery() {
        StreamingQueryMessage<String, String> testQuery = new GenericStreamingQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), "Hello, World", String.class
        );

        //noinspection rawtypes,unchecked
        StubResultStream stubResultStream = new StubResultStream(stubResponse("<string>1</string>"),
                                                                 stubResponse("<string>2</string>"),
                                                                 stubResponse("<string>3</string>"));
        //noinspection unchecked
        when(mockQueryChannel.query(any())).thenReturn(stubResultStream);

        StepVerifier.create(Flux.from(testSubject.streamingQuery(testQuery))
                                .map(Message::getPayload))
                    .expectNext("1", "2", "3")
                    .verifyComplete();

        verify(targetContextResolver).resolveContext(testQuery);
        verify(localSegment, never()).streamingQuery(testQuery);
        //noinspection resource
        verify(mockQueryChannel).query(argThat(
                r -> r.getPayload().getData().toStringUtf8().equals("<string>Hello, World</string>")
                        && 1 == ProcessingInstructionHelper.numberOfResults(r.getProcessingInstructionsList())));
        await().atMost(Duration.ofSeconds(3))
               .untilAsserted(() -> {
                   spanFactory.verifySpanCompleted("QueryBus.streamingQueryDistributed", testQuery);
                   spanFactory.verifySpanPropagated("QueryBus.streamingQueryDistributed", testQuery);
               });
    }

    @Test
    void streamingQueryReturnsError() {
        StreamingQueryMessage<String, String> testQuery = new GenericStreamingQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), "Hello, World", String.class
        );

        when(mockQueryChannel.query(any())).thenReturn(new StubResultStream<>(new RuntimeException("oops")));

        StepVerifier.create(Flux.from(testSubject.streamingQuery(testQuery))
                                .map(Message::getPayload))
                    .verifyErrorMatches(t -> t instanceof RuntimeException && "oops".equals(t.getMessage()));

        verify(targetContextResolver).resolveContext(testQuery);
        //noinspection resource
        verify(mockQueryChannel).query(argThat(
                r -> r.getPayload().getData().toStringUtf8().equals("<string>Hello, World</string>")
                        && 1 == ProcessingInstructionHelper.numberOfResults(r.getProcessingInstructionsList())));
        await().atMost(Duration.ofSeconds(3))
               .untilAsserted(() -> {
                   spanFactory.verifySpanCompleted("QueryBus.streamingQueryDistributed");
                   spanFactory.verifySpanHasException("QueryBus.streamingQueryDistributed", RuntimeException.class);
               });
    }

    @Test
    void streamingQueryReturnsNoResults() {
        StreamingQueryMessage<String, String> testQuery = new GenericStreamingQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), "Hello, World", String.class
        );

        when(mockQueryChannel.query(any())).thenReturn(new StubResultStream<>());

        StepVerifier.create(testSubject.streamingQuery(testQuery))
                    .verifyComplete();

        verify(targetContextResolver).resolveContext(testQuery);
        //noinspection resource
        verify(mockQueryChannel).query(argThat(
                r -> r.getPayload().getData().toStringUtf8().equals("<string>Hello, World</string>")
                        && 1 == ProcessingInstructionHelper.numberOfResults(r.getProcessingInstructionsList())));
    }

    @Test
    void queryForOptionalWillRequestInstanceOfFromRemoteDestination() {
        QueryMessage<String, Optional<String>> testQuery = new GenericQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), "Hello, World", optionalInstanceOf(String.class)
        );

        Stream<QueryResponseMessage<Optional<String>>> actual =
                testSubject.scatterGather(testQuery, 12, TimeUnit.SECONDS);
        // not really interested in the result
        actual.close();

        verify(targetContextResolver).resolveContext(testQuery);
        //noinspection resource
        verify(mockQueryChannel).query(argThat(
                r -> r.getResponseType().getType().equals(InstanceResponseType.class.getName())
        ));
    }

    @Test
    void dispatchInterceptor() {
        List<Object> results = new LinkedList<>();
        testSubject.registerDispatchInterceptor(messages -> (a, b) -> {
            results.add(b.getPayload());
            return b;
        });
        QueryMessage<String, String> testQuery = new GenericQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), "payload", new InstanceResponseType<>(String.class)
        );

        testSubject.query(testQuery);
        assertEquals("payload", results.getFirst());
        assertEquals(1, results.size());
    }

    @Test
    void handlerInterceptorRegisteredWithLocalSegment() {
        MessageHandlerInterceptor<QueryMessage<?, ?>> interceptor =
                (unitOfWork, interceptorChain) -> interceptorChain.proceedSync();

        testSubject.registerHandlerInterceptor(interceptor);

        verify(localSegment).registerHandlerInterceptor(interceptor);
    }

    @Test
    void localSegmentReturnsLocalQueryBus() {
        assertEquals(localSegment, testSubject.localSegment());
    }

    @Test
    void afterShutdownDispatchingAnShutdownInProgressExceptionOnQueryInvocation() {
        QueryMessage<String, String> testQuery = new GenericQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), "some-query", instanceOf(String.class)
        );

        assertDoesNotThrow(() -> testSubject.shutdownDispatching().get(5, TimeUnit.SECONDS));

        assertWithin(
                50, TimeUnit.MILLISECONDS,
                () -> assertThrows(ShutdownInProgressException.class, () -> testSubject.query(testQuery))
        );
    }

    @Test
    void shutdownTakesFinishedQueriesIntoAccount() {
        when(mockQueryChannel.query(any())).thenReturn(new StubResultStream<>(stubResponse("some-payload")));
        QueryMessage<String, String> testQuery = new GenericQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), "some-query", instanceOf(String.class)
        );

        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(testQuery);
        result.join();

        assertDoesNotThrow(() -> testSubject.shutdownDispatching().get(5, TimeUnit.SECONDS));
    }

    @Test
    void afterShutdownDispatchingAnShutdownInProgressExceptionOnScatterGatherInvocation() {
        QueryMessage<String, String> testQuery = new GenericQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), "some-query", instanceOf(String.class)
        );

        assertDoesNotThrow(() -> testSubject.shutdownDispatching().get(5, TimeUnit.SECONDS));

        assertWithin(
                50, TimeUnit.MILLISECONDS,
                () -> assertThrows(
                        ShutdownInProgressException.class,
                        () -> testSubject.scatterGather(testQuery, 1, TimeUnit.SECONDS)
                )
        );
    }

    @Test
    void subscriptionQueryCompletesWithExceptionOnUpdateDeserializationError() {
        when(mockQueryChannel.subscriptionQuery(any(), any(), anyInt(), anyInt()))
                .thenReturn(new SimpleSubscriptionQueryResult(
                        "<string>Hello world</string>", stubUpdate("Not a valid XML object")
                ));
        GenericSubscriptionQueryMessage<String, String, String> testQuery = new GenericSubscriptionQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), "test", "Say hi",
                instanceOf(String.class), instanceOf(String.class)
        );

        SubscriptionQueryResult<QueryResponseMessage<String>, SubscriptionQueryUpdateMessage<String>> queryResult =
                testSubject.subscriptionQuery(testQuery);

        Mono<QueryResponseMessage<String>> initialResult = queryResult.initialResult();
        Flux<SubscriptionQueryUpdateMessage<String>> updates = queryResult.updates();
        queryResult.cancel();

        StepVerifier.create(initialResult)
                    .expectNextMatches(r -> r.getPayload().equals("Hello world"))
                    .verifyComplete();

        StepVerifier.create(updates.map(Message::getPayload))
                    .verifyError();
    }

    @Test
    void subscriptionQueryCompletesWithExceptionOnInitialResultDeserializationError() {
        when(mockQueryChannel.subscriptionQuery(any(), any(), anyInt(), anyInt()))
                .thenReturn(new SimpleSubscriptionQueryResult(
                        "Not a valid XML object", stubUpdate("<string>Hello world</string>")
                ));
        GenericSubscriptionQueryMessage<String, String, String> testQuery = new GenericSubscriptionQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), "test", "Say hi",
                instanceOf(String.class), instanceOf(String.class)
        );

        SubscriptionQueryResult<QueryResponseMessage<String>, SubscriptionQueryUpdateMessage<String>> queryResult =
                testSubject.subscriptionQuery(testQuery);

        Mono<QueryResponseMessage<String>> initialResult = queryResult.initialResult();
        Flux<SubscriptionQueryUpdateMessage<String>> updates = queryResult.updates();
        queryResult.cancel();

        StepVerifier.create(initialResult.map(Message::getPayload))
                    .verifyError();

        StepVerifier.create(updates.map(Message::getPayload))
                    .expectNextMatches(r -> r.equals("Hello world"))
                    .verifyComplete();
    }

    @Test
    void afterShutdownDispatchingAnShutdownInProgressExceptionOnSubscriptionQueryInvocation() {
        SubscriptionQueryMessage<String, String, String> testSubscriptionQuery = new GenericSubscriptionQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), "some-query",
                instanceOf(String.class), instanceOf(String.class)
        );

        assertDoesNotThrow(() -> testSubject.shutdownDispatching().get(5, TimeUnit.SECONDS));

        assertThrows(ShutdownInProgressException.class,
                     () -> testSubject.subscriptionQuery(testSubscriptionQuery));
    }

    @Test
    void equalPriorityMessagesProcessedInOrder() throws InterruptedException {
        testSubject = AxonServerQueryBus.builder()
                                        .axonServerConnectionManager(axonServerConnectionManager)
                                        .configuration(configuration)
                                        .localSegment(localSegment)
                                        .updateEmitter(SimpleQueryUpdateEmitter.builder().build())
                                        .messageSerializer(serializer)
                                        .genericSerializer(serializer)
                                        .targetContextResolver(targetContextResolver)
                                        .executorServiceBuilder((c, q) -> new ThreadPoolExecutor(
                                                1, 1, 5, TimeUnit.SECONDS, q
                                        ))
                                        .build();

        int queryCount = 1000;

        CountDownLatch startProcessingGate = new CountDownLatch(1);
        CountDownLatch finishProcessingGate = new CountDownLatch(queryCount);

        List<Long> expected = LongStream.range(0, queryCount)
                                        .boxed()
                                        .collect(Collectors.toList());
        List<Long> actual = new CopyOnWriteArrayList<>();

        AtomicReference<QueryHandler> queryHandlerRef = new AtomicReference<>();
        doAnswer(i -> {
            queryHandlerRef.set(i.getArgument(0));
            return (io.axoniq.axonserver.connector.Registration) FutureUtils::emptyCompletedFuture;
        }).when(mockQueryChannel)
          .registerQueryHandler(any(), any());

        when(localSegment.query(any())).thenAnswer(i -> {
            startProcessingGate.await();
            QueryMessage<?, ?> message = i.getArgument(0);
            actual.add((long) message.getMetaData().get("index"));
            finishProcessingGate.countDown();
            return CompletableFuture.completedFuture(
                    new GenericQueryResponseMessage<>(new QualifiedName("test", "query", "0.0.1"), "ok")
            );
        });

        // We create a subscription to force a registration for this type of query.
        // It doesn't get invoked because the localSegment is mocked
        testSubject.subscribe("testQuery",
                              String.class,
                              (MessageHandler<QueryMessage<?, String>, QueryResponseMessage<?>>) message -> "ok");
        assertWithin(1, TimeUnit.SECONDS, () -> assertNotNull(queryHandlerRef.get()));

        QueryHandler queryHandler = queryHandlerRef.get();
        for (int i = 0; i < queryCount; i++) {
            QueryRequest queryRequest =
                    QueryRequest.newBuilder()
                                .setQuery("testQuery")
                                .setMessageIdentifier(UUID.randomUUID().toString())
                                .setPayload(SerializedObject.newBuilder()
                                                            .setType("java.lang.String")
                                                            .setData(ByteString.copyFromUtf8("<string>Hello</string>"))
                                )
                                .setResponseType(SerializedObject.newBuilder()
                                                                 .setData(ByteString.copyFromUtf8(
                                                                         INSTANCE_RESPONSE_TYPE_XML
                                                                 ))
                                                                 .setType(InstanceResponseType.class.getName())
                                                                 .build())
                                .putMetaData("index", MetaDataValue.newBuilder().setNumberValue(i).build())
                                .build();

            queryHandler.handle(queryRequest, new StubReplyChannel());
        }
        startProcessingGate.countDown();
        //noinspection ResultOfMethodCallIgnored
        finishProcessingGate.await(30, TimeUnit.SECONDS);

        assertEquals(queryCount, actual.size());
        assertEquals(expected, actual);
    }

    @Test
    void disconnectCancelsQueriesInProgressIfAwaitDurationIsSurpassed() {
        AxonServerQueryBus queryInProgressTestSubject =
                AxonServerQueryBus.builder()
                                  .axonServerConnectionManager(axonServerConnectionManager)
                                  .configuration(configuration)
                                  .localSegment(localSegment)
                                  .updateEmitter(SimpleQueryUpdateEmitter.builder().build())
                                  .messageSerializer(serializer)
                                  .genericSerializer(serializer)
                                  .targetContextResolver(targetContextResolver)
                                  .executorServiceBuilder((c, q) -> new ThreadPoolExecutor(
                                          1, 1, 5, TimeUnit.SECONDS, q
                                  ))
                                  .queryInProgressAwait(Duration.ofSeconds(1))
                                  .build();
        CountDownLatch handlerLatch = new CountDownLatch(1);
        AtomicReference<QueryResponse> responseReference = new AtomicReference<>();
        queriesInProgressTestSetup(queryInProgressTestSubject, handlerLatch, responseReference);

        // Start disconnecting right away. As a blocking operation, this ensures we surpass the await duration.
        queryInProgressTestSubject.disconnect();
        // Release te latch, to let go of the blocking query handler.
        handlerLatch.countDown();

        await().atMost(Duration.ofSeconds(1))
               .pollDelay(Duration.ofMillis(250))
               .untilAsserted(() -> assertNull(responseReference.get()));
    }

    @Test
    void disconnectReturnsResponseFromQueriesInProgressIfAwaitDurationIsNotExceeded() throws InterruptedException {
        AxonServerQueryBus queryInProgressTestSubject =
                AxonServerQueryBus.builder()
                                  .axonServerConnectionManager(axonServerConnectionManager)
                                  .configuration(configuration)
                                  .localSegment(localSegment)
                                  .updateEmitter(SimpleQueryUpdateEmitter.builder().build())
                                  .messageSerializer(serializer)
                                  .genericSerializer(serializer)
                                  .targetContextResolver(targetContextResolver)
                                  .executorServiceBuilder((c, q) -> new ThreadPoolExecutor(
                                          1, 1, 5, TimeUnit.SECONDS, q
                                  ))
                                  .queryInProgressAwait(Duration.ofSeconds(1))
                                  .build();
        CountDownLatch handlerLatch = new CountDownLatch(1);
        AtomicReference<QueryResponse> responseReference = new AtomicReference<>();
        queriesInProgressTestSetup(queryInProgressTestSubject, handlerLatch, responseReference);

        // Start disconnecting in a separate thread to ensure the response latch is released
        new Thread(queryInProgressTestSubject::disconnect).start();
        // Sleep a little, to ensure there is some space between disconnecting and releasing the query handler latch
        Thread.sleep(250);
        // Release te latch, to let go of the blocking query handler.
        handlerLatch.countDown();

        await().atMost(Duration.ofSeconds(1))
               .pollDelay(Duration.ofMillis(250))
               .untilAsserted(() -> assertNotNull(responseReference.get()));
        assertEquals("Hello", responseReference.get().getMetaDataOrThrow("response").getTextValue());
    }

    private void queriesInProgressTestSetup(AxonServerQueryBus queryInProgressTestSubject,
                                            CountDownLatch responseLatch,
                                            AtomicReference<QueryResponse> responseReference) {
        AtomicReference<QueryHandler> handlerReference = new AtomicReference<>();
        doAnswer(i -> {
            handlerReference.set(i.getArgument(0));
            return (io.axoniq.axonserver.connector.Registration) () -> CompletableFuture.completedFuture(null);
        }).when(mockQueryChannel)
          .registerQueryHandler(any(), any());

        when(localSegment.query(any())).thenAnswer(i -> {
            responseLatch.await();
            QueryMessage<?, ?> message = i.getArgument(0);
            QueryResponseMessage<?> queryResponse = new GenericQueryResponseMessage<>(message.getPayload()).withMetaData(
                    MetaData.with("response", message.getPayload()));
            return CompletableFuture.completedFuture(queryResponse);
        });

        // We create a subscription to force a registration for this type of query.
        // It doesn't get invoked because the localSegment is mocked
        queryInProgressTestSubject.subscribe(
                "testQuery",
                String.class,
                (MessageHandler<QueryMessage<?, String>, QueryResponseMessage<?>>) message -> "ok"
        );
        await().atMost(Duration.ofSeconds(1))
               .pollDelay(Duration.ofMillis(250))
               .untilAsserted(() -> assertNotNull(handlerReference.get()));

        QueryHandler queryHandler = handlerReference.get();
        QueryRequest queryRequest =
                QueryRequest.newBuilder()
                            .setQuery("testQuery")
                            .setMessageIdentifier(UUID.randomUUID().toString())
                            .setPayload(SerializedObject.newBuilder()
                                                        .setType("java.lang.String")
                                                        .setData(ByteString.copyFromUtf8("<string>Hello</string>"))
                            )
                            .setResponseType(SerializedObject.newBuilder()
                                                             .setData(ByteString.copyFromUtf8(
                                                                     INSTANCE_RESPONSE_TYPE_XML
                                                             ))
                                                             .setType(InstanceResponseType.class.getName())
                                                             .build())
                            .putMetaData("response", MetaDataValue.newBuilder().setTextValue("Hello").build())
                            .build();
        queryHandler.handle(queryRequest, new StubReplyChannel(responseReference));
    }

    private QueryResponse stubResponse(String payload) {
        return QueryResponse.newBuilder()
                            .setRequestIdentifier("request")
                            .setMessageIdentifier(UUID.randomUUID().toString())
                            .setPayload(SerializedObject.newBuilder()
                                                        .setData(ByteString.copyFromUtf8(payload))
                                                        .setType(String.class.getName()))
                            .build();
    }

    private QueryUpdate stubUpdate(String payload) {
        return QueryUpdate.newBuilder()
                          .setMessageIdentifier(UUID.randomUUID().toString())
                          .setPayload(SerializedObject.newBuilder()
                                                      .setData(ByteString.copyFromUtf8(payload))
                                                      .setType(String.class.getName()))
                          .build();
    }

    private QueryResponse stubErrorResponse(String errorCode, @SuppressWarnings("SameParameterValue") String message) {
        return QueryResponse.newBuilder()
                            .setRequestIdentifier("request")
                            .setMessageIdentifier(UUID.randomUUID().toString())
                            .setErrorCode(errorCode)
                            .setErrorMessage(ErrorMessage.newBuilder()
                                                         .setMessage(message)
                                                         .setLocation("test")
                                                         .build())
                            .build();
    }

    private static class StubResultStream<T> implements ResultStream<T> {

        private final Iterator<T> responses;
        private final Throwable error;
        private T peeked;
        private volatile boolean closed;
        private final int totalNumberOfElements;

        public StubResultStream(Throwable error) {
            this.error = error;
            this.closed = true;
            this.responses = Collections.emptyIterator();
            this.totalNumberOfElements = 1;
        }

        @SafeVarargs
        public StubResultStream(T... responses) {
            this.error = null;
            List<T> queryResponses = asList(responses);
            this.responses = queryResponses.iterator();
            this.totalNumberOfElements = queryResponses.size();
            this.closed = totalNumberOfElements == 0;
        }

        @Override
        public T peek() {
            if (peeked == null && responses.hasNext()) {
                peeked = responses.next();
            }
            return peeked;
        }

        @Override
        public T nextIfAvailable() {
            if (peeked != null) {
                T result = peeked;
                peeked = null;
                closeIfThereAreNoMoreElements();
                return result;
            }
            if (responses.hasNext()) {
                T next = responses.next();
                closeIfThereAreNoMoreElements();
                return next;
            } else {
                return null;
            }
        }

        private void closeIfThereAreNoMoreElements() {
            if (!responses.hasNext()) {
                close();
            }
        }

        @Override
        public T nextIfAvailable(long timeout, TimeUnit unit) {
            return nextIfAvailable();
        }

        @Override
        public T next() {
            return nextIfAvailable();
        }

        @Override
        public void onAvailable(Runnable r) {
            if (peeked != null || responses.hasNext() || isClosed()) {
                IntStream.rangeClosed(0, totalNumberOfElements)
                         .forEach(i -> r.run());
            }
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public Optional<Throwable> getError() {
            return Optional.ofNullable(error);
        }
    }

    private class SimpleSubscriptionQueryResult
            implements io.axoniq.axonserver.connector.query.SubscriptionQueryResult {

        private final StubResultStream<QueryUpdate> updateStubResultStream;
        private final String payload;

        public SimpleSubscriptionQueryResult(String payload, QueryUpdate... updates) {
            this.updateStubResultStream = new StubResultStream<>(updates);
            this.payload = payload;
        }

        @Override
        public CompletableFuture<QueryResponse> initialResult() {
            return CompletableFuture.completedFuture(stubResponse(payload));
        }

        @Override
        public ResultStream<QueryUpdate> updates() {
            return updateStubResultStream;
        }
    }

    private static class StubReplyChannel implements ReplyChannel<QueryResponse> {

        private final AtomicReference<QueryResponse> responseReference;

        // No-arg constructor acts like a Noop version of this ReplyChannel
        private StubReplyChannel() {
            this(new AtomicReference<>());
        }

        private StubReplyChannel(AtomicReference<QueryResponse> responseReference) {
            this.responseReference = responseReference;
        }

        @Override
        public void send(QueryResponse outboundMessage) {
            responseReference.set(outboundMessage);
        }

        @Override
        public void complete() {
            // Do nothing - not required for testing
        }

        @Override
        public void completeWithError(ErrorMessage errorMessage) {
            // Do nothing - not required for testing
        }

        @Override
        public void completeWithError(ErrorCategory errorCategory, String message) {
            // Do nothing - not required for testing
        }
    }
}
