/*
 * Copyright (c) 2010-2020. Axon Framework
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
import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.query.QueryProviderInbound;
import io.axoniq.axonserver.grpc.query.QueryProviderOutbound;
import io.axoniq.axonserver.grpc.query.QueryRequest;
import io.grpc.stub.StreamObserver;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.TargetContextResolver;
import org.axonframework.axonserver.connector.TestStreamObserver;
import org.axonframework.axonserver.connector.TestTargetContextResolver;
import org.axonframework.common.Registration;
import org.axonframework.lifecycle.ShutdownInProgressException;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.responsetypes.InstanceResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryExecutionException;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import static org.axonframework.axonserver.connector.ErrorCode.UNSUPPORTED_INSTRUCTION;
import static org.axonframework.axonserver.connector.TestTargetContextResolver.BOUNDED_CONTEXT;
import static org.axonframework.axonserver.connector.utils.AssertUtils.assertWithin;
import static org.axonframework.common.ObjectUtils.getOrDefault;
import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
import static org.axonframework.messaging.responsetypes.ResponseTypes.optionalInstanceOf;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit test suite to verify the {@link AxonServerQueryBus}.
 *
 * @author Marc Gathier
 */
class AxonServerQueryBusTest {

    private static final String TEST_QUERY = "testQuery";

    private DummyMessagePlatformServer dummyMessagePlatformServer;

    private AxonServerConnectionManager axonServerConnectionManager;
    private AxonServerConfiguration configuration;
    private QueryBus localSegment = SimpleQueryBus.builder().build();
    private Serializer serializer = XStreamSerializer.defaultSerializer();
    private TargetContextResolver<QueryMessage<?, ?>> targetContextResolver = spy(new TestTargetContextResolver<>());

    private AxonServerQueryBus testSubject;

    @BeforeEach
    void setup() throws Exception {
        dummyMessagePlatformServer = new DummyMessagePlatformServer();
        dummyMessagePlatformServer.start();

        configuration = new AxonServerConfiguration();
        configuration.setServers(dummyMessagePlatformServer.getAddress());
        configuration.setClientId("JUnit");
        configuration.setComponentName("JUnit");
        configuration.setInitialNrOfPermits(100);
        configuration.setNewPermitsThreshold(10);
        configuration.setNrOfNewPermits(1000);
        configuration.setContext(BOUNDED_CONTEXT);
        axonServerConnectionManager = spy(AxonServerConnectionManager.builder()
                                                                     .axonServerConfiguration(configuration)
                                                                     .build());

        testSubject = AxonServerQueryBus.builder()
                                        .axonServerConnectionManager(axonServerConnectionManager)
                                        .configuration(configuration)
                                        .localSegment(localSegment)
                                        .updateEmitter(localSegment.queryUpdateEmitter())
                                        .messageSerializer(serializer)
                                        .genericSerializer(serializer)
                                        .targetContextResolver(targetContextResolver)
                                        .build();
    }

    @AfterEach
    void tearDown() {
        dummyMessagePlatformServer.stop();
        axonServerConnectionManager.shutdown();
        testSubject.disconnect();
    }

    @Test
    void subscribe() throws Exception {
        Registration result = testSubject.subscribe(TEST_QUERY, String.class, q -> "test");

        Thread.sleep(1000);
        assertWithin(
                1000,
                TimeUnit.MILLISECONDS,
                () -> assertNotNull(dummyMessagePlatformServer.subscriptions(TEST_QUERY, String.class.getName()))
        );

        result.cancel();
        assertWithin(
                2000,
                TimeUnit.MILLISECONDS,
                () -> assertNull(dummyMessagePlatformServer.subscriptions(TEST_QUERY, String.class.getName()))
        );

        //noinspection unchecked
        verify(axonServerConnectionManager).getQueryStream(eq(BOUNDED_CONTEXT), any(StreamObserver.class));
    }

    @Test
    void query() throws Exception {
        QueryMessage<String, String> testQuery = new GenericQueryMessage<>("Hello, World", instanceOf(String.class));

        assertEquals("test", testSubject.query(testQuery).get().getPayload());

        verify(targetContextResolver).resolveContext(testQuery);
    }

    @Test
    void queryWhenQueryServiceStubFails() {
        AxonServerConnectionManager axonServerConnectionManager =
                AxonServerConnectionManager.builder()
                                           .axonServerConfiguration(configuration)
                                           .build();
        AxonServerQueryBus testSubject = spy(AxonServerQueryBus.builder()
                                                               .axonServerConnectionManager(axonServerConnectionManager)
                                                               .configuration(configuration)
                                                               .localSegment(localSegment)
                                                               .updateEmitter(localSegment.queryUpdateEmitter())
                                                               .messageSerializer(serializer)
                                                               .genericSerializer(serializer)
                                                               .targetContextResolver(targetContextResolver)
                                                               .build());
        RuntimeException expectedException = new RuntimeException("oops");
        when(testSubject.queryService(anyString())).thenThrow(expectedException);

        QueryMessage<String, String> testQuery = new GenericQueryMessage<>("Hello, World", instanceOf(String.class));

        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(testQuery);

        assertTrue(result.isDone());
        assertTrue(result.isCompletedExceptionally());
        verify(targetContextResolver).resolveContext(testQuery);

        try {
            result.get();
            fail("Expected an exception here");
        } catch (Exception actual) {
            assertTrue(actual.getCause() instanceof AxonServerQueryDispatchException);
            AxonServerQueryDispatchException queryDispatchException =
                    (AxonServerQueryDispatchException) actual.getCause();
            assertEquals(ErrorCode.QUERY_DISPATCH_ERROR.errorCode(), queryDispatchException.code());
        }
    }

    @Test
    void testQueryReportsCorrectException() throws ExecutionException, InterruptedException {
        QueryMessage<String, String> testQuery = new GenericQueryMessage<>("Hello, World", instanceOf(String.class))
                .andMetaData(Collections.singletonMap("errorCode", ErrorCode.QUERY_EXECUTION_ERROR.errorCode()));

        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(testQuery);

        assertNotNull(result.get());
        assertFalse(result.isCompletedExceptionally());

        assertTrue(result.get().isExceptional());
        Throwable actual = result.get().exceptionResult();
        assertTrue(actual instanceof QueryExecutionException);
        AxonServerRemoteQueryHandlingException queryDispatchException =
                (AxonServerRemoteQueryHandlingException) actual.getCause();
        assertEquals(ErrorCode.QUERY_EXECUTION_ERROR.errorCode(), queryDispatchException.getErrorCode());

        verify(targetContextResolver).resolveContext(testQuery);
    }

    @Test
    void processQuery() {
        AxonServerQueryBus testSubject = AxonServerQueryBus.builder()
                                                           .axonServerConnectionManager(axonServerConnectionManager)
                                                           .configuration(configuration)
                                                           .localSegment(localSegment)
                                                           .updateEmitter(localSegment.queryUpdateEmitter())
                                                           .messageSerializer(serializer)
                                                           .genericSerializer(serializer)
                                                           .targetContextResolver(targetContextResolver)
                                                           .requestStreamFactory(so -> new TestStreamObserver<>())
                                                           .build();

        AtomicReference<StreamObserver<QueryProviderInbound>> inboundStreamObserver =
                buildInboundQueryStreamObserverReference();

        Registration result = testSubject.subscribe(TEST_QUERY, String.class, q -> "test: " + q.getPayloadType());

        QueryProviderInbound inboundMessage = testQueryMessage();
        inboundStreamObserver.get().onNext(inboundMessage);

        result.close();
    }

    @Test
    void unsupportedQueryInstruction() {
        TestStreamObserver<QueryProviderOutbound> requestStream = new TestStreamObserver<>();
        AxonServerQueryBus testSubject = AxonServerQueryBus.builder()
                                                           .axonServerConnectionManager(axonServerConnectionManager)
                                                           .configuration(configuration)
                                                           .localSegment(localSegment)
                                                           .updateEmitter(localSegment.queryUpdateEmitter())
                                                           .messageSerializer(serializer)
                                                           .genericSerializer(serializer)
                                                           .targetContextResolver(targetContextResolver)
                                                           .requestStreamFactory(so -> requestStream)
                                                           .build();

        AtomicReference<StreamObserver<QueryProviderInbound>> inboundStreamObserver =
                buildInboundQueryStreamObserverReference();

        Registration result = testSubject.subscribe(TEST_QUERY, String.class, q -> "test: " + q.getPayloadType());

        String instructionId = "instructionId";
        QueryProviderInbound inboundMessage = QueryProviderInbound.newBuilder()
                                                                  .setInstructionId(instructionId)
                                                                  .build();
        inboundStreamObserver.get().onNext(inboundMessage);

        result.close();

        assertTrue(requestStream.sentMessages()
                                .stream()
                                .anyMatch(outbound -> outbound.getRequestCase()
                                                              .equals(QueryProviderOutbound.RequestCase.ACK)
                                        && !outbound.getAck().getSuccess()
                                        && outbound.getAck().getError().getErrorCode()
                                                   .equals(UNSUPPORTED_INSTRUCTION.errorCode())
                                        && outbound.getAck().getInstructionId().equals(instructionId)));
    }

    @Test
    void unsupportedQueryInstructionWithoutInstructionId() {
        TestStreamObserver<QueryProviderOutbound> requestStream = new TestStreamObserver<>();
        AxonServerQueryBus testSubject = AxonServerQueryBus.builder()
                                                           .axonServerConnectionManager(axonServerConnectionManager)
                                                           .configuration(configuration)
                                                           .localSegment(localSegment)
                                                           .updateEmitter(localSegment.queryUpdateEmitter())
                                                           .messageSerializer(serializer)
                                                           .genericSerializer(serializer)
                                                           .targetContextResolver(targetContextResolver)
                                                           .requestStreamFactory(so -> requestStream)
                                                           .build();

        AtomicReference<StreamObserver<QueryProviderInbound>> inboundStreamObserver =
                buildInboundQueryStreamObserverReference();

        Registration result = testSubject.subscribe(TEST_QUERY, String.class, q -> "test: " + q.getPayloadType());

        QueryProviderInbound inboundMessage = QueryProviderInbound.newBuilder().build();
        inboundStreamObserver.get().onNext(inboundMessage);

        result.close();

        assertEquals(0, requestStream.sentMessages().size());
    }

    @Test
    void scatterGather() {
        QueryMessage<String, String> testQuery = new GenericQueryMessage<>("Hello, World", instanceOf(String.class))
                .andMetaData(MetaData.with("repeat", 10).and("interval", 10));

        assertEquals(10, testSubject.scatterGather(testQuery, 12, TimeUnit.SECONDS).count());

        verify(targetContextResolver).resolveContext(testQuery);
    }

    @Test
    void queryForOptionalWillRequestInstanceOfFromRemoteDestination() {
        QueryMessage<String, Optional<String>> testQuery =
                new GenericQueryMessage<>("Hello, World", optionalInstanceOf(String.class)).andMetaData(
                        MetaData.with("repeat", 10).and("interval", 10)
                );

        assertEquals(10, testSubject.scatterGather(testQuery, 12, TimeUnit.SECONDS)
                                    .filter(i -> Optional.class.isAssignableFrom(i.getPayloadType()))
                                    .filter(i -> i.getPayload().isPresent())
                                    .count());

        verify(targetContextResolver).resolveContext(testQuery);
    }

    @Test
    void scatterGatherTimeout() {
        QueryMessage<String, String> testQuery = new GenericQueryMessage<>("Hello, World", instanceOf(String.class))
                .andMetaData(MetaData.with("repeat", 10).and("interval", 100));

        assertTrue(8 > testSubject.scatterGather(testQuery, 550, TimeUnit.MILLISECONDS).count());

        verify(targetContextResolver).resolveContext(testQuery);
    }

    @Test
    void testSubscriptionQueryIsHandledByDispatchInterceptors() {
        AtomicInteger counter = new AtomicInteger(0);

        // Add a dispatch interceptor which increase the counter, to check whether it was called during a sub.query
        testSubject.registerDispatchInterceptor(messages -> {
            counter.incrementAndGet();
            return (i, m) -> m;
        });

        SubscriptionQueryMessage<String, String, String> testQuery = new GenericSubscriptionQueryMessage<>(
                "query-payload", instanceOf(String.class), instanceOf(String.class)
        );

        testSubject.subscriptionQuery(testQuery);

        assertEquals(1, counter.get());

        verify(targetContextResolver).resolveContext(testQuery);
    }

    @Test
    void dispatchInterceptor() {
        List<Object> results = new LinkedList<>();
        testSubject.registerDispatchInterceptor(messages -> (a, b) -> {
            results.add(b.getPayload());
            return b;
        });

        testSubject.query(new GenericQueryMessage<>("payload", new InstanceResponseType<>(String.class)));
        assertEquals("payload", results.get(0));
        assertEquals(1, results.size());
    }

    @Test
    void handlerInterceptor() {
        AxonServerQueryBus testSubject = AxonServerQueryBus.builder()
                                                           .axonServerConnectionManager(axonServerConnectionManager)
                                                           .configuration(configuration)
                                                           .localSegment(localSegment)
                                                           .updateEmitter(localSegment.queryUpdateEmitter())
                                                           .messageSerializer(serializer)
                                                           .genericSerializer(serializer)
                                                           .targetContextResolver(targetContextResolver)
                                                           .build();
        AtomicReference<StreamObserver<QueryProviderInbound>> inboundStreamObserver =
                buildInboundQueryStreamObserverReference();

        testSubject.subscribe(TEST_QUERY, String.class, q -> "test: " + q.getPayloadType());

        List<Object> results = new LinkedList<>();
        testSubject.registerHandlerInterceptor((unitOfWork, interceptorChain) -> {
            results.add("Interceptor executed");
            return interceptorChain.proceed();
        });

        QueryProviderInbound inboundMessage = testQueryMessage();
        inboundStreamObserver.get().onNext(inboundMessage);
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(1, results.size()));
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals("Interceptor executed", results.get(0)));
    }

    @Test
    void reconnectAfterConnectionLost() throws InterruptedException {
        testSubject.subscribe(TEST_QUERY, String.class, q -> "test");

        Thread.sleep(50);
        assertNotNull(dummyMessagePlatformServer.subscriptions(TEST_QUERY, String.class.getName()));

        dummyMessagePlatformServer.onError(TEST_QUERY, String.class.getName());
        Thread.sleep(200);
        assertNotNull(dummyMessagePlatformServer.subscriptions(TEST_QUERY, String.class.getName()));

        //noinspection unchecked
        verify(axonServerConnectionManager, times(2)).getQueryStream(eq(BOUNDED_CONTEXT), any(StreamObserver.class));
    }

    @Test
    void testLocalSegmentReturnsLocalQueryBus() {
        assertEquals(localSegment, testSubject.localSegment());
    }

    @Test
    void testDisconnectUnsubscribesAllRegisteredQueries() {
        String testQueryOne = "testQueryOne";
        String testQueryTwo = "testQueryTwo";
        testSubject.subscribe(testQueryOne, String.class, query -> "Done");
        testSubject.subscribe(testQueryTwo, String.class, query -> "Done");

        testSubject.disconnect();

        assertWithin(2, TimeUnit.SECONDS, () -> dummyMessagePlatformServer.isUnsubscribed(testQueryOne, String.class));
        assertWithin(2, TimeUnit.SECONDS, () -> dummyMessagePlatformServer.isUnsubscribed(testQueryTwo, String.class));
    }

    @Test
    void testDisconnectFinishesQueriesInTransit() {
        AtomicReference<StreamObserver<QueryProviderInbound>> inboundStreamObserver =
                buildInboundQueryStreamObserverReference();
        QueryProviderInbound testQueryMessage = testQueryMessage();

        AxonServerQueryBus testSubject = AxonServerQueryBus.builder()
                                                           .axonServerConnectionManager(axonServerConnectionManager)
                                                           .configuration(configuration)
                                                           .localSegment(localSegment)
                                                           .updateEmitter(localSegment.queryUpdateEmitter())
                                                           .messageSerializer(serializer)
                                                           .genericSerializer(serializer)
                                                           .targetContextResolver(targetContextResolver)
                                                           .requestStreamFactory(so -> new TestStreamObserver<>())
                                                           .build();


        // Create a lock for the slow handler and lock it immediately, to spoof the handler's slow/long process
        ReentrantLock slowHandlerLock = new ReentrantLock();
        slowHandlerLock.lock();
        AtomicBoolean queryHandled = new AtomicBoolean(false);

        MessageHandler<QueryMessage<?, ?>> testQueryHandler = query -> {
            try {
                slowHandlerLock.lock();
            } finally {
                slowHandlerLock.unlock();
            }
            queryHandled.set(true);
            return "Done";
        };
        testSubject.subscribe(TEST_QUERY, String.class, testQueryHandler);

        CompletableFuture<Void> disconnected;
        try {
            inboundStreamObserver.get().onNext(testQueryMessage);
        } finally {
            disconnected = testSubject.shutdownDispatching();
            slowHandlerLock.unlock();
        }

        // Wait until the disconnect-thread is finished prior to validating
        disconnected.join();
        assertTrue(queryHandled.get());
        assertTrue(disconnected.isDone());
    }

    @Test
    void testAfterShutdownDispatchingAnShutdownInProgressExceptionOnQueryInvocation() {
        QueryMessage<String, String> testQuery = new GenericQueryMessage<>("some-query", instanceOf(String.class));

        testSubject.shutdownDispatching();

        assertWithin(
                50, TimeUnit.MILLISECONDS,
                () -> assertThrows(ShutdownInProgressException.class, () -> testSubject.query(testQuery))
        );
    }

    @Test
    void testAfterShutdownDispatchingAnShutdownInProgressExceptionOnScatterGatherInvocation() {
        QueryMessage<String, String> testQuery = new GenericQueryMessage<>("some-query", instanceOf(String.class));

        testSubject.shutdownDispatching();

        assertWithin(
                50, TimeUnit.MILLISECONDS,
                () -> assertThrows(
                        ShutdownInProgressException.class,
                        () -> testSubject.scatterGather(testQuery, 1, TimeUnit.SECONDS)
                )
        );
    }

    @Test
    void testAfterShutdownDispatchingAnShutdownInProgressExceptionOnSubscriptionQueryInvocation() {
        SubscriptionQueryMessage<String, String, String> testSubscriptionQuery =
                new GenericSubscriptionQueryMessage<>("some-query", instanceOf(String.class), instanceOf(String.class));

        testSubject.shutdownDispatching();

        assertWithin(
                50, TimeUnit.MILLISECONDS,
                () -> assertThrows(
                        ShutdownInProgressException.class,
                        () -> testSubject.subscriptionQuery(testSubscriptionQuery)
                )
        );
    }

    @Test
    void testShutdownDispatchingWaitsForQueriesInTransitToComplete() {
        AtomicBoolean queryHandled = new AtomicBoolean(false);
        // Queries containing "interval" will sleep for the given amount
        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>(
                "some-blocking-query", instanceOf(String.class)
        ).andMetaData(MetaData.with("interval", 500));

        CompletableFuture<Void> queryResponse =
                testSubject.query(testQueryMessage).thenRun(() -> queryHandled.set(true));

        CompletableFuture<Void> dispatchingHasShutdown = testSubject.shutdownDispatching();

        // Wait until the shutdownDispatching-thread and queryResponse-thread have finished prior to validating
        dispatchingHasShutdown.join();

        assertTrue(queryHandled.get());
        assertTrue(dispatchingHasShutdown.isDone());
    }

    @Test
    void testShutdownDispatchingWaitsForScatterGatherQueriesInTransitToComplete() {
        AtomicBoolean queryHandled = new AtomicBoolean(false);
        // Queries containing "interval" will sleep for the given amount
        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>(
                "some-blocking-query", instanceOf(String.class)
        ).andMetaData(MetaData.with("interval", 500));

        Stream<QueryResponseMessage<String>> queryResponses =
                testSubject.scatterGather(testQueryMessage, 1, TimeUnit.SECONDS);
        CompletableFuture<Void> dispatchingHasShutdown = testSubject.shutdownDispatching();

        // Perform a terminal operation to traverse the stream
        queryResponses.forEach(queryResponse -> queryHandled.set(true));

        // Wait on the shutdownDispatching-thread, after which the scatter gather query should have been handled
        dispatchingHasShutdown.join();
        assertTrue(queryHandled.get());
        assertTrue(dispatchingHasShutdown.isDone());
    }

    private AtomicReference<StreamObserver<QueryProviderInbound>> buildInboundQueryStreamObserverReference() {
        AtomicReference<StreamObserver<QueryProviderInbound>> inboundStreamObserver = new AtomicReference<>();

        doAnswer(invocationOnMock -> {
            inboundStreamObserver.set(invocationOnMock.getArgument(1));
            return new TestStreamObserver<QueryProviderOutbound>();
        }).when(axonServerConnectionManager).getQueryStream(any(), any());

        return inboundStreamObserver;
    }

    private QueryProviderInbound testQueryMessage() {
        org.axonframework.serialization.SerializedObject<byte[]> serializedResponseType =
                serializer.serialize(instanceOf(String.class), byte[].class);
        SerializedObject responseType =
                SerializedObject.newBuilder()
                                .setData(ByteString.copyFrom(serializedResponseType.getData()))
                                .setType(serializedResponseType.getType().getName())
                                .setRevision(getOrDefault(serializedResponseType.getType().getRevision(), ""))
                                .build();

        SerializedObject queryPayload = SerializedObject.newBuilder()
                                                        .setData(ByteString.copyFromUtf8("<string>Hello</string>"))
                                                        .setType(String.class.getName())
                                                        .build();

        QueryRequest query = QueryRequest.newBuilder()
                                         .setQuery(TEST_QUERY)
                                         .setResponseType(responseType)
                                         .setPayload(queryPayload)
                                         .build();

        return QueryProviderInbound.newBuilder()
                                   .setInstructionId("instructionId")
                                   .setQuery(query)
                                   .build();
    }
}
