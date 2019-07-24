/*
 * Copyright (c) 2010-2019. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.responsetypes.InstanceResponseType;
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
import org.junit.*;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.axonframework.axonserver.connector.TestTargetContextResolver.BOUNDED_CONTEXT;
import static org.axonframework.axonserver.connector.utils.AssertUtils.assertWithin;
import static org.axonframework.common.ObjectUtils.getOrDefault;
import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit test suite to verify the {@link AxonServerQueryBus}.
 *
 * @author Marc Gathier
 */
public class AxonServerQueryBusTest {

    private DummyMessagePlatformServer dummyMessagePlatformServer;

    private AxonServerConnectionManager axonServerConnectionManager;
    private AxonServerConfiguration configuration;
    private QueryBus localSegment = SimpleQueryBus.builder().build();
    private Serializer serializer = XStreamSerializer.defaultSerializer();
    private TargetContextResolver<QueryMessage<?, ?>> targetContextResolver = spy(new TestTargetContextResolver<>());

    private AxonServerQueryBus testSubject;

    @Before
    public void setup() throws Exception {
        dummyMessagePlatformServer = new DummyMessagePlatformServer(4343);
        dummyMessagePlatformServer.start();

        configuration = new AxonServerConfiguration();
        configuration.setServers("localhost:4343");
        configuration.setClientId("JUnit");
        configuration.setComponentName("JUnit");
        configuration.setInitialNrOfPermits(100);
        configuration.setNewPermitsThreshold(10);
        configuration.setNrOfNewPermits(1000);
        configuration.setContext(BOUNDED_CONTEXT);
        axonServerConnectionManager = spy(AxonServerConnectionManager.builder()
                                                                     .axonServerConfiguration(configuration)
                                                                     .build());

        testSubject = new AxonServerQueryBus(
                axonServerConnectionManager, configuration, localSegment.queryUpdateEmitter(), localSegment, serializer,
                serializer, QueryPriorityCalculator.defaultQueryPriorityCalculator(), targetContextResolver
        );
    }

    @After
    public void tearDown() {
        dummyMessagePlatformServer.stop();
        axonServerConnectionManager.shutdown();
        testSubject.disconnect();
    }

    @Test
    public void subscribe() throws Exception {
        Registration result = testSubject.subscribe("testQuery", String.class, q -> "test");

        Thread.sleep(1000);
        assertWithin(
                1000,
                TimeUnit.MILLISECONDS,
                () -> assertNotNull(dummyMessagePlatformServer.subscriptions("testQuery", String.class.getName()))
        );

        result.cancel();
        assertWithin(
                2000,
                TimeUnit.MILLISECONDS,
                () -> assertNull(dummyMessagePlatformServer.subscriptions("testQuery", String.class.getName()))
        );

        //noinspection unchecked
        verify(axonServerConnectionManager).getQueryStream(eq(BOUNDED_CONTEXT), any(StreamObserver.class));
    }

    @Test
    public void query() throws Exception {
        QueryMessage<String, String> testQuery = new GenericQueryMessage<>("Hello, World", instanceOf(String.class));

        assertEquals("test", testSubject.query(testQuery).get().getPayload());

        verify(targetContextResolver).resolveContext(testQuery);
    }

    @Test
    public void queryWhenQueryServiceStubFails() {
        AxonServerConnectionManager axonServerConnectionManager =
                AxonServerConnectionManager.builder()
                                           .axonServerConfiguration(configuration)
                                           .build();
        AxonServerQueryBus testSubject = spy(new AxonServerQueryBus(
                axonServerConnectionManager, configuration, localSegment.queryUpdateEmitter(),
                localSegment, serializer, serializer, QueryPriorityCalculator.defaultQueryPriorityCalculator(),
                targetContextResolver
        ));
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
            AxonServerQueryDispatchException queryDispatchException = (AxonServerQueryDispatchException) actual
                    .getCause();
            assertEquals(ErrorCode.QUERY_DISPATCH_ERROR.errorCode(), queryDispatchException.code());
        }
    }

    @Test
    public void testQueryReportsCorrectException() throws ExecutionException, InterruptedException {
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
    public void processQuery() {
        AxonServerQueryBus testSubject = new AxonServerQueryBus(
                axonServerConnectionManager, configuration, localSegment.queryUpdateEmitter(), localSegment,
                serializer, serializer, QueryPriorityCalculator.defaultQueryPriorityCalculator(), targetContextResolver
        );

        AtomicReference<StreamObserver<QueryProviderInbound>> inboundStreamObserver =
                buildInboundQueryStreamObserverReference();

        Registration result = testSubject.subscribe("testQuery", String.class, q -> "test: " + q.getPayloadType());

        QueryProviderInbound inboundMessage = testQueryMessage();
        inboundStreamObserver.get().onNext(inboundMessage);

        result.close();
    }

    @Test
    public void scatterGather() {
        QueryMessage<String, String> testQuery = new GenericQueryMessage<>("Hello, World", instanceOf(String.class))
                .andMetaData(MetaData.with("repeat", 10).and("interval", 10));

        assertEquals(10, testSubject.scatterGather(testQuery, 12, TimeUnit.SECONDS).count());

        verify(targetContextResolver).resolveContext(testQuery);
    }

    @Test
    public void scatterGatherTimeout() {
        QueryMessage<String, String> testQuery = new GenericQueryMessage<>("Hello, World", instanceOf(String.class))
                .andMetaData(MetaData.with("repeat", 10).and("interval", 100));

        assertTrue(8 > testSubject.scatterGather(testQuery, 550, TimeUnit.MILLISECONDS).count());

        verify(targetContextResolver).resolveContext(testQuery);
    }

    @Test
    public void testSubscriptionQueryIsHandledByDispatchInterceptors() {
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
    public void dispatchInterceptor() {
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
    public void handlerInterceptor() {
        AxonServerQueryBus testSubject = new AxonServerQueryBus(
                axonServerConnectionManager, configuration, localSegment.queryUpdateEmitter(), localSegment, serializer,
                serializer, QueryPriorityCalculator.defaultQueryPriorityCalculator(), targetContextResolver
        );
        AtomicReference<StreamObserver<QueryProviderInbound>> inboundStreamObserver =
                buildInboundQueryStreamObserverReference();

        testSubject.subscribe("testQuery", String.class, q -> "test: " + q.getPayloadType());

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
    public void reconnectAfterConnectionLost() throws InterruptedException {
        testSubject.subscribe("testQuery", String.class, q -> "test");

        Thread.sleep(50);
        assertNotNull(dummyMessagePlatformServer.subscriptions("testQuery", String.class.getName()));

        dummyMessagePlatformServer.onError("testQuery", String.class.getName());
        Thread.sleep(200);
        assertNotNull(dummyMessagePlatformServer.subscriptions("testQuery", String.class.getName()));

        //noinspection unchecked
        verify(axonServerConnectionManager, times(2)).getQueryStream(eq(BOUNDED_CONTEXT), any(StreamObserver.class));
    }

    private QueryProviderInbound testQueryMessage() {
        org.axonframework.serialization.SerializedObject<byte[]> serializedResponseType =
                serializer.serialize(instanceOf(String.class), byte[].class);

        SerializedObject testResponseType =
                SerializedObject.newBuilder()
                                .setData(ByteString.copyFrom(serializedResponseType.getData()))
                                .setType(serializedResponseType.getType().getName())
                                .setRevision(getOrDefault(serializedResponseType.getType().getRevision(), ""))
                                .build();
        SerializedObject testQueryPayload = SerializedObject.newBuilder()
                                                            .setData(ByteString.copyFromUtf8("<string>Hello</string>"))
                                                            .setType(String.class.getName())
                                                            .build();
        return QueryProviderInbound.newBuilder()
                                   .setQuery(QueryRequest.newBuilder()
                                                         .setQuery("testQuery")
                                                         .setResponseType(testResponseType)
                                                         .setPayload(testQueryPayload)
                                   ).build();
    }


    private AtomicReference<StreamObserver<QueryProviderInbound>> buildInboundQueryStreamObserverReference() {
        AtomicReference<StreamObserver<QueryProviderInbound>> inboundStreamObserver = new AtomicReference<>();

        doAnswer(invocationOnMock -> {
            inboundStreamObserver.set(invocationOnMock.getArgument(1));
            return new TestStreamObserver<QueryProviderOutbound>();
        }).when(axonServerConnectionManager)
          .getQueryStream(any(), any());

        return inboundStreamObserver;
    }
}
