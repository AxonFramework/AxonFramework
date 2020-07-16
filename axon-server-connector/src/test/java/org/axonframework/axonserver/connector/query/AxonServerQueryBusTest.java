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
import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.ResultStream;
import io.axoniq.axonserver.connector.query.QueryChannel;
import io.axoniq.axonserver.connector.query.QueryDefinition;
import io.axoniq.axonserver.grpc.ErrorMessage;
import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.TargetContextResolver;
import org.axonframework.axonserver.connector.TestTargetContextResolver;
import org.axonframework.axonserver.connector.util.ProcessingInstructionHelper;
import org.axonframework.common.Registration;
import org.axonframework.lifecycle.ShutdownInProgressException;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.responsetypes.InstanceResponseType;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryExecutionException;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SimpleQueryUpdateEmitter;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.axonframework.axonserver.connector.utils.AssertUtils.assertWithin;
import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
import static org.axonframework.messaging.responsetypes.ResponseTypes.optionalInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test suite to verify the {@link AxonServerQueryBus}.
 *
 * @author Marc Gathier
 */
class AxonServerQueryBusTest {

    private static final String TEST_QUERY = "testQuery";
    public static final String CONTEXT = "default-test";

    private AxonServerConnectionManager axonServerConnectionManager;
    private final QueryBus localSegment = mock(QueryBus.class);
    private final Serializer serializer = XStreamSerializer.defaultSerializer();
    private final TargetContextResolver<QueryMessage<?, ?>> targetContextResolver = spy(new TestTargetContextResolver<>());

    private AxonServerQueryBus testSubject;
    private AxonServerConnection mockConnection;
    private QueryChannel mockQueryChannel;

    @BeforeEach
    void setup() throws Exception {
        AxonServerConfiguration configuration = new AxonServerConfiguration();
        configuration.setContext(CONTEXT);
        axonServerConnectionManager = mock(AxonServerConnectionManager.class);

        testSubject = AxonServerQueryBus.builder()
                                        .axonServerConnectionManager(axonServerConnectionManager)
                                        .configuration(configuration)
                                        .localSegment(localSegment)
                                        .updateEmitter(SimpleQueryUpdateEmitter.builder().build())
                                        .messageSerializer(serializer)
                                        .genericSerializer(serializer)
                                        .targetContextResolver(targetContextResolver)
                                        .build();

        mockConnection = mock(AxonServerConnection.class);
        mockQueryChannel = mock(QueryChannel.class);

        when(axonServerConnectionManager.getConnection(anyString())).thenReturn(mockConnection);
        when(axonServerConnectionManager.getConnection()).thenReturn(mockConnection);

        when(mockConnection.queryChannel()).thenReturn(mockQueryChannel);
        when(mockQueryChannel.registerQueryHandler(any(), any())).thenReturn(() -> CompletableFuture.completedFuture(null));

        when(localSegment.subscribe(any(), any(), any())).thenReturn(() -> true);

    }

    @AfterEach
    void tearDown() {
        axonServerConnectionManager.shutdown();
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
    void query() throws Exception {
        when(mockQueryChannel.query(any())).thenReturn(new StubResultStream(stubResponse("<string>test</string>")));
        QueryMessage<String, String> testQuery = new GenericQueryMessage<>("Hello, World", instanceOf(String.class));

        assertEquals("test", testSubject.query(testQuery).get().getPayload());

        verify(targetContextResolver).resolveContext(testQuery);
    }

    @Test
    void testQueryReportsCorrectException() throws ExecutionException, InterruptedException {
        when(mockQueryChannel.query(any())).thenReturn(new StubResultStream(stubErrorResponse(ErrorCode.QUERY_EXECUTION_ERROR.errorCode(),
                                                                                              "Faking exception result")));
        QueryMessage<String, String> testQuery = new GenericQueryMessage<>("Hello, World", instanceOf(String.class));

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
    void subscribeHandler() {

        when(mockQueryChannel.registerQueryHandler(any(), any())).thenReturn(() -> CompletableFuture.completedFuture(null));

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

        result.close();
        verify(registration).cancel();
    }

    @Test
    void scatterGather() {
        QueryMessage<String, String> testQuery = new GenericQueryMessage<>("Hello, World", instanceOf(String.class));

        when(mockQueryChannel.query(any())).thenReturn(new StubResultStream(stubResponse("<string>1</string>"),
                                                                            stubResponse("<string>2</string>"),
                                                                            stubResponse("<string>3</string>")));

        assertEquals(3, testSubject.scatterGather(testQuery, 12, TimeUnit.SECONDS).count());

        verify(targetContextResolver).resolveContext(testQuery);
        verify(mockQueryChannel).query(argThat(
                r -> r.getPayload().getData().toStringUtf8().equals("<string>Hello, World</string>")
                        && -1 == ProcessingInstructionHelper.numberOfResults(r.getProcessingInstructionsList())));
    }

    @Test
    void queryForOptionalWillRequestInstanceOfFromRemoteDestination() {
        QueryMessage<String, Optional<String>> testQuery =
                new GenericQueryMessage<>("Hello, World", optionalInstanceOf(String.class));

        testSubject.scatterGather(testQuery, 12, TimeUnit.SECONDS);

        verify(targetContextResolver).resolveContext(testQuery);
        verify(mockQueryChannel).query(argThat(r -> r.getResponseType().getType().equals(InstanceResponseType.class.getName())));
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
    void handlerInterceptorRegisteredWithLocalSegment() {

        MessageHandlerInterceptor<QueryMessage<?, ?>> interceptor = (unitOfWork, interceptorChain) -> interceptorChain.proceed();
        testSubject.registerHandlerInterceptor(interceptor);

        verify(localSegment).registerHandlerInterceptor(interceptor);
    }

    @Test
    void testLocalSegmentReturnsLocalQueryBus() {
        assertEquals(localSegment, testSubject.localSegment());
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

        assertThrows(ShutdownInProgressException.class,
                     () -> testSubject.subscriptionQuery(testSubscriptionQuery));
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

    private QueryResponse stubErrorResponse(String errorCode, String message) {
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

    private static class StubResultStream implements ResultStream<QueryResponse> {


        private final Iterator<QueryResponse> responses;
        private QueryResponse peeked;
        private boolean closed;

        public StubResultStream(QueryResponse... responses) {
            this.responses = Arrays.asList(responses).iterator();
        }

        @Override
        public QueryResponse peek() {
            if (peeked == null && responses.hasNext()) {
                peeked = responses.next();
            }
            return peeked;
        }

        @Override
        public QueryResponse nextIfAvailable() {
            if (peeked != null) {
                QueryResponse result = peeked;
                peeked = null;
                return result;
            }
            return responses.hasNext() ? responses.next() : null;
        }

        @Override
        public QueryResponse nextIfAvailable(long timeout, TimeUnit unit) {
            return nextIfAvailable();
        }

        @Override
        public QueryResponse next() {
            return nextIfAvailable();
        }

        @Override
        public void onAvailable(Runnable r) {
            if (peeked != null || responses.hasNext()) {
                r.run();
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
    }
}
