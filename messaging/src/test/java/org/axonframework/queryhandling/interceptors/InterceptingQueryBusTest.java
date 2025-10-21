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

package org.axonframework.queryhandling.interceptors;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageDispatchInterceptorChain;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptorChain;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryHandlerName;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.messaging.MessagingTestUtils.queryResponse;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link InterceptingQueryBus}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class InterceptingQueryBusTest {

    private static final MessageType TEST_QUERY_TYPE = new MessageType("testQuery");
    private static final MessageType TEST_RESPONSE_TYPE = new MessageType(String.class);

    private InterceptingQueryBus testSubject;
    private QueryBus mockQueryBus;
    private MessageHandlerInterceptor<QueryMessage> handlerInterceptor1;
    private MessageHandlerInterceptor<QueryMessage> handlerInterceptor2;
    private MessageDispatchInterceptor<Message> dispatchInterceptor1;
    private MessageDispatchInterceptor<Message> dispatchInterceptor2;

    @BeforeEach
    void setUp() {
        mockQueryBus = mock(QueryBus.class);
        handlerInterceptor1 = spy(new AddMetadataCountInterceptor<>("handler1", "value"));
        handlerInterceptor2 = spy(new AddMetadataCountInterceptor<>("handler2", "value"));
        dispatchInterceptor1 = spy(new AddMetadataCountInterceptor<>("dispatch1", "value"));
        dispatchInterceptor2 = spy(new AddMetadataCountInterceptor<>("dispatch2", "value"));

        testSubject = new InterceptingQueryBus(mockQueryBus,
                                               List.of(handlerInterceptor1, handlerInterceptor2),
                                               List.of(dispatchInterceptor1, dispatchInterceptor2),
                                               List.of());
    }

    @Nested
    @DisplayName("Dispatch interceptor tests")
    class DispatchInterceptorTests {

        // given
        @Test
        void dispatchInterceptorsInvokedOnQuery() {
            when(mockQueryBus.query(any(), any()))
                    .thenReturn(MessageStream.just(queryResponse("ok")));

            QueryMessage testQuery = new GenericQueryMessage(TEST_QUERY_TYPE, "test", TEST_RESPONSE_TYPE);

            // when
            MessageStream<QueryResponseMessage> result = testSubject.query(testQuery, StubProcessingContext.forMessage(testQuery));

            // then
            ArgumentCaptor<QueryMessage> dispatchedMessage = ArgumentCaptor.forClass(QueryMessage.class);
            verify(mockQueryBus).query(dispatchedMessage.capture(), any());

            QueryMessage actualDispatched = dispatchedMessage.getValue();
            assertTrue(actualDispatched.metadata().containsKey("dispatch1"),
                      "Expected dispatch1 interceptor to add metadata");
            assertTrue(actualDispatched.metadata().containsKey("dispatch2"),
                      "Expected dispatch2 interceptor to add metadata");

            QueryResponseMessage response = result.first().asCompletableFuture().join().message();
            assertTrue(response.metadata().containsKey("dispatch1"),
                      "Expected dispatch1 interceptor to modify response metadata");
            assertTrue(response.metadata().containsKey("dispatch2"),
                      "Expected dispatch2 interceptor to modify response metadata");
        }

        @Test
        void dispatchInterceptorsAreInvokedForEveryQuery() {
            // given
            AtomicInteger counter = new AtomicInteger(0);
            MessageDispatchInterceptor<Message> countingInterceptor = (message, context, chain) -> {
                counter.incrementAndGet();
                return chain.proceed(message, context);
            };
            InterceptingQueryBus countingTestSubject =
                    new InterceptingQueryBus(mockQueryBus, List.of(), List.of(countingInterceptor), List.of());

            when(mockQueryBus.query(any(), any()))
                    .thenReturn(MessageStream.just(queryResponse("ok")));

            QueryMessage firstQuery = new GenericQueryMessage(TEST_QUERY_TYPE, "first", TEST_RESPONSE_TYPE);
            QueryMessage secondQuery = new GenericQueryMessage(TEST_QUERY_TYPE, "second", TEST_RESPONSE_TYPE);

            // when
            countingTestSubject.query(firstQuery, StubProcessingContext.forMessage(firstQuery));
            countingTestSubject.query(secondQuery, StubProcessingContext.forMessage(secondQuery));

            // then
            assertThat(counter.get()).isEqualTo(2);
        }

        @Test
        void earlyReturnAvoidsQueryDispatch() {
            // given
            QueryMessage testQuery = new GenericQueryMessage(TEST_QUERY_TYPE, "test", TEST_RESPONSE_TYPE);
            doReturn(MessageStream.failed(new MockException("Simulating early return")))
                    .when(dispatchInterceptor2)
                    .interceptOnDispatch(any(), any(), any());

            // when
            MessageStream<QueryResponseMessage> result = testSubject.query(testQuery, StubProcessingContext.forMessage(testQuery));

            // then
            assertTrue(result.first().asCompletableFuture().isCompletedExceptionally());
            assertInstanceOf(MockException.class, result.first().asCompletableFuture().exceptionNow());
            verify(dispatchInterceptor1).interceptOnDispatch(any(), any(), any());
            verify(dispatchInterceptor2).interceptOnDispatch(any(), any(), any());
            verify(mockQueryBus, never()).query(any(), any());
        }

        @Test
        void exceptionsInDispatchInterceptorReturnFailedStream() {
            // given
            QueryMessage testQuery = new GenericQueryMessage(TEST_QUERY_TYPE, "test", TEST_RESPONSE_TYPE);
            doThrow(new MockException("Simulating failure in interceptor")).when(dispatchInterceptor2)
                                                                           .interceptOnDispatch(any(), any(), any());

            // when
            MessageStream<QueryResponseMessage> result = testSubject.query(testQuery, StubProcessingContext.forMessage(testQuery));

            // then
            assertTrue(result.first().asCompletableFuture().isCompletedExceptionally());
            assertInstanceOf(MockException.class, result.first().asCompletableFuture().exceptionNow());
            verify(dispatchInterceptor1).interceptOnDispatch(any(), any(), any());
            verify(dispatchInterceptor2).interceptOnDispatch(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Handler interceptor tests")
    class HandlerInterceptorTests {

        @Test
        void handlerInterceptorsInvokedOnHandling() {
            // given
            QueryHandler actualHandler = subscribeHandler(
                    (query, context) -> MessageStream.just(queryResponse("ok"))
            );

            QueryMessage testQuery = new GenericQueryMessage(TEST_QUERY_TYPE, "test", TEST_RESPONSE_TYPE);
            ProcessingContext context = StubProcessingContext.forMessage(testQuery);

            // when
            MessageStream<QueryResponseMessage> result = actualHandler.handle(testQuery, context);

            // then
            QueryResponseMessage response = result.first().asCompletableFuture().join().message();
            assertTrue(response.metadata().containsKey("handler1"),
                      "Expected handler1 interceptor to add metadata");
            assertTrue(response.metadata().containsKey("handler2"),
                      "Expected handler2 interceptor to add metadata");

            verify(handlerInterceptor1).interceptOnHandle(any(), eq(context), any());
            verify(handlerInterceptor2).interceptOnHandle(any(), eq(context), any());
        }

        @Test
        void handlerInterceptorsAreInvokedForEveryQuery() {
            // given
            AtomicInteger counter = new AtomicInteger(0);
            MessageHandlerInterceptor<QueryMessage> countingInterceptor = (message, context, chain) -> {
                counter.incrementAndGet();
                return chain.proceed(message, context);
            };
            InterceptingQueryBus countingTestSubject =
                    new InterceptingQueryBus(mockQueryBus, List.of(countingInterceptor), List.of(), List.of());

            QueryHandlerName testHandlerName = new QueryHandlerName(new QualifiedName("query"), new QualifiedName("response"));
            countingTestSubject.subscribe(testHandlerName, (query, context) -> MessageStream.just(queryResponse("ok")));

            ArgumentCaptor<QueryHandler> handlerCaptor = ArgumentCaptor.forClass(QueryHandler.class);
            verify(mockQueryBus).subscribe(eq(testHandlerName), handlerCaptor.capture());

            QueryHandler actualHandler = handlerCaptor.getValue();

            QueryMessage firstQuery = new GenericQueryMessage(TEST_QUERY_TYPE, "first", TEST_RESPONSE_TYPE);
            QueryMessage secondQuery = new GenericQueryMessage(TEST_QUERY_TYPE, "second", TEST_RESPONSE_TYPE);

            // when
            actualHandler.handle(firstQuery, StubProcessingContext.forMessage(firstQuery)).first();
            actualHandler.handle(secondQuery, StubProcessingContext.forMessage(secondQuery)).first();

            // then
            assertThat(counter.get()).isEqualTo(2);
        }

        @Test
        void exceptionsInHandlerInterceptorReturnFailedStream() {
            // given
            QueryMessage testQuery = new GenericQueryMessage(TEST_QUERY_TYPE, "Request", TEST_RESPONSE_TYPE);
            doThrow(new MockException("Simulating failure in interceptor"))
                    .when(handlerInterceptor2).interceptOnHandle(any(), any(), any());

            QueryHandler actualHandler = subscribeHandler(
                    (query, context) -> MessageStream.just(queryResponse("ok"))
            );

            ProcessingContext context = mock(ProcessingContext.class);

            // when
            var result = actualHandler.handle(testQuery, context);

            // then
            assertTrue(result.first().asCompletableFuture().isCompletedExceptionally());
            assertInstanceOf(MockException.class, result.first().asCompletableFuture().exceptionNow());

            verify(handlerInterceptor1).interceptOnHandle(any(), eq(context), any());
            verify(handlerInterceptor2).interceptOnHandle(any(), eq(context), any());
        }
    }

    @Nested
    @DisplayName("Subscribe tests")
    class SubscribeTests {

        @Test
        void subscribeWrapsHandlerWithInterceptors() {
            // given
            QueryHandlerName handlerName = new QueryHandlerName(new QualifiedName("query"), new QualifiedName("response"));
            QueryHandler mockHandler = mock(QueryHandler.class);

            // when
            testSubject.subscribe(handlerName, mockHandler);

            // then
            ArgumentCaptor<QueryHandler> handlerCaptor = ArgumentCaptor.forClass(QueryHandler.class);
            verify(mockQueryBus).subscribe(eq(handlerName), handlerCaptor.capture());

            QueryHandler wrappedHandler = handlerCaptor.getValue();
            assertNotNull(wrappedHandler);
            assertNotEquals(mockHandler, wrappedHandler, "Expected handler to be wrapped");
        }
    }

    @Nested
    @DisplayName("Subscription query tests")
    class SubscriptionQueryTests {

        @Test
        void subscriptionQueryDelegatesToUnderlyingBus() {
            // given
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    TEST_QUERY_TYPE, "test", TEST_RESPONSE_TYPE
            );
            ProcessingContext context = StubProcessingContext.forMessage(testQuery);

            MessageStream<QueryResponseMessage> expectedResponse = MessageStream.just(queryResponse("initial"));
            when(mockQueryBus.subscriptionQuery(any(), any(), anyInt())).thenReturn(expectedResponse);

            // when
            MessageStream<QueryResponseMessage> result = testSubject.subscriptionQuery(testQuery, context, 10);

            // then
            verify(mockQueryBus).subscriptionQuery(eq(testQuery), eq(context), eq(10));
            assertSame(expectedResponse, result, "Expected result to be from delegate bus");
        }

        @Test
        void subscriptionQueryHandlerInterceptorsAppliedViaSubscribe() {
            // given
            QueryHandlerName handlerName = new QueryHandlerName(
                    new QualifiedName("query"),
                    new QualifiedName("response")
            );
            QueryHandler mockHandler = (query, context) -> MessageStream.just(queryResponse("result"));

            testSubject.subscribe(handlerName, mockHandler);

            // Capture the wrapped handler
            ArgumentCaptor<QueryHandler> handlerCaptor = ArgumentCaptor.forClass(QueryHandler.class);
            verify(mockQueryBus).subscribe(eq(handlerName), handlerCaptor.capture());

            QueryMessage testQuery = new GenericQueryMessage(TEST_QUERY_TYPE, "test", TEST_RESPONSE_TYPE);
            ProcessingContext context = StubProcessingContext.forMessage(testQuery);

            // when
            MessageStream<QueryResponseMessage> result = handlerCaptor.getValue().handle(testQuery, context);

            // then
            QueryResponseMessage response = result.first().asCompletableFuture().join().message();
            assertTrue(response.metadata().containsKey("handler1"),
                      "Expected handler1 interceptor to be applied");
            assertTrue(response.metadata().containsKey("handler2"),
                      "Expected handler2 interceptor to be applied");
        }

        @Test
        void subscribeToUpdatesDelegatesToUnderlyingBus() {
            // given
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    TEST_QUERY_TYPE, "test", TEST_RESPONSE_TYPE
            );
            int updateBufferSize = 10;

            GenericSubscriptionQueryUpdateMessage update1 =
                    new GenericSubscriptionQueryUpdateMessage(TEST_RESPONSE_TYPE, "update1");
            GenericSubscriptionQueryUpdateMessage update2 =
                    new GenericSubscriptionQueryUpdateMessage(TEST_RESPONSE_TYPE, "update2");

            MessageStream<SubscriptionQueryUpdateMessage> expectedUpdates =
                    MessageStream.fromItems(update1, update2);

            when(mockQueryBus.subscribeToUpdates(any(), anyInt())).thenReturn(expectedUpdates);

            // when
            MessageStream<SubscriptionQueryUpdateMessage> result =
                    testSubject.subscribeToUpdates(testQuery, updateBufferSize);

            // then
            verify(mockQueryBus).subscribeToUpdates(eq(testQuery), eq(updateBufferSize));
            assertSame(expectedUpdates, result, "Expected result to be from delegate bus");
        }
    }

    @Nested
    @DisplayName("Subscription update interception tests")
    class SubscriptionUpdateInterceptionTests {

        @Test
        void updateDispatchInterceptorsInvokedOnEmitUpdate() {
            // given
            MessageDispatchInterceptor<SubscriptionQueryUpdateMessage> updateInterceptor1 =
                    spy(new AddMetadataCountInterceptor<>("update1", "value"));
            MessageDispatchInterceptor<SubscriptionQueryUpdateMessage> updateInterceptor2 =
                    spy(new AddMetadataCountInterceptor<>("update2", "value"));

            InterceptingQueryBus testSubjectWithUpdateInterceptors = new InterceptingQueryBus(
                    mockQueryBus,
                    List.of(),
                    List.of(),
                    List.of(updateInterceptor1, updateInterceptor2)
            );

            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    TEST_QUERY_TYPE, "test", TEST_RESPONSE_TYPE
            );
            GenericSubscriptionQueryUpdateMessage updateMessage =
                    new GenericSubscriptionQueryUpdateMessage(TEST_RESPONSE_TYPE, "update");

            when(mockQueryBus.emitUpdate(any(), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            // when
            testSubjectWithUpdateInterceptors.emitUpdate(
                    query -> query.equals(testQuery),
                    () -> updateMessage,
                    null
            ).join();

            // then
            verify(updateInterceptor1).interceptOnDispatch(any(), any(), any());
            verify(updateInterceptor2).interceptOnDispatch(any(), any(), any());

            ArgumentCaptor<Supplier<SubscriptionQueryUpdateMessage>> supplierCaptor =
                    ArgumentCaptor.forClass(Supplier.class);
            verify(mockQueryBus).emitUpdate(any(), supplierCaptor.capture(), any());

            SubscriptionQueryUpdateMessage actualUpdate = supplierCaptor.getValue().get();
            assertTrue(actualUpdate.metadata().containsKey("update1"),
                      "Expected update1 interceptor to add metadata");
            assertTrue(actualUpdate.metadata().containsKey("update2"),
                      "Expected update2 interceptor to add metadata");
        }

        @Test
        void emptyUpdateInterceptorListBypassesInterception() {
            // given
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    TEST_QUERY_TYPE, "test", TEST_RESPONSE_TYPE
            );
            GenericSubscriptionQueryUpdateMessage updateMessage =
                    new GenericSubscriptionQueryUpdateMessage(TEST_RESPONSE_TYPE, "update");

            when(mockQueryBus.emitUpdate(any(), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            // when
            testSubject.emitUpdate(
                    query -> query.equals(testQuery),
                    () -> updateMessage,
                    null
            ).join();

            // then
            ArgumentCaptor<Supplier<SubscriptionQueryUpdateMessage>> supplierCaptor =
                    ArgumentCaptor.forClass(Supplier.class);
            verify(mockQueryBus).emitUpdate(any(), supplierCaptor.capture(), any());

            SubscriptionQueryUpdateMessage actualUpdate = supplierCaptor.getValue().get();
            assertSame(updateMessage, actualUpdate,
                      "Expected update message to be passed through without interception");
        }

        @Test
        void exceptionsInUpdateInterceptorReturnFailedFuture() {
            // given
            MessageDispatchInterceptor<SubscriptionQueryUpdateMessage> failingInterceptor =
                    (message, context, chain) -> {
                        throw new MockException("Simulating failure in update interceptor");
                    };

            InterceptingQueryBus testSubjectWithFailingInterceptor = new InterceptingQueryBus(
                    mockQueryBus,
                    List.of(),
                    List.of(),
                    List.of(failingInterceptor)
            );

            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    TEST_QUERY_TYPE, "test", TEST_RESPONSE_TYPE
            );
            GenericSubscriptionQueryUpdateMessage updateMessage =
                    new GenericSubscriptionQueryUpdateMessage(TEST_RESPONSE_TYPE, "update");

            // when
            CompletableFuture<Void> result = testSubjectWithFailingInterceptor.emitUpdate(
                    query -> query.equals(testQuery),
                    () -> updateMessage,
                    null
            );

            // then
            assertTrue(result.isCompletedExceptionally(),
                      "Expected result to be completed exceptionally");
            assertInstanceOf(MockException.class, result.exceptionNow());
            verify(mockQueryBus, never()).emitUpdate(any(), any(), any());
        }
    }

    @Test
    void describeIncludesAllRelevantProperties() {
        // given
        ComponentDescriptor mockComponentDescriptor = mock(ComponentDescriptor.class);

        // when
        testSubject.describeTo(mockComponentDescriptor);

        // then
        verify(mockComponentDescriptor).describeWrapperOf(eq(mockQueryBus));
        verify(mockComponentDescriptor).describeProperty(argThat(i -> i.contains("dispatch")),
                                                         eq(List.of(dispatchInterceptor1, dispatchInterceptor2)));
        verify(mockComponentDescriptor).describeProperty(argThat(i -> i.contains("handler")),
                                                         eq(List.of(handlerInterceptor1, handlerInterceptor2)));
    }

    /**
     * Subscribes the given handler with the query bus and returns the handler as it is subscribed with its delegate.
     *
     * @param handler The handling logic for the query.
     * @return the handler as wrapped by the surrounding query bus.
     */
    private QueryHandler subscribeHandler(QueryHandler handler) {
        QueryHandlerName name = new QueryHandlerName(new QualifiedName("query"), new QualifiedName("response"));
        testSubject.subscribe(name, handler);

        ArgumentCaptor<QueryHandler> handlerCaptor = ArgumentCaptor.forClass(QueryHandler.class);
        verify(mockQueryBus).subscribe(eq(name), handlerCaptor.capture());
        return handlerCaptor.getValue();
    }

    @SuppressWarnings("unchecked")
    private static class AddMetadataCountInterceptor<M extends Message>
            implements MessageHandlerInterceptor<M>, MessageDispatchInterceptor<M> {

        private final String key;
        private final String value;

        public AddMetadataCountInterceptor(String key, String prefix) {
            this.key = key;
            this.value = prefix;
        }

        @Override
        @Nonnull
        public MessageStream<?> interceptOnDispatch(@Nonnull M message,
                                                    @Nullable ProcessingContext context,
                                                    @Nonnull MessageDispatchInterceptorChain<M> interceptorChain) {
            var intercepted = (M) message.andMetadata(Map.of(key, buildValue(message)));
            return interceptorChain
                    .proceed(intercepted, context)
                    .mapMessage(m -> m.andMetadata(Map.of(key, buildValue(m))));
        }

        @Override
        @Nonnull
        public MessageStream<?> interceptOnHandle(@Nonnull M message,
                                                  @Nonnull ProcessingContext context,
                                                  @Nonnull MessageHandlerInterceptorChain<M> interceptorChain) {
            var intercepted = (M) message.andMetadata(Map.of(key, buildValue(message)));
            return interceptorChain
                    .proceed(intercepted, context)
                    .mapMessage(m -> m.andMetadata(Map.of(key, buildValue(m))));
        }

        private String buildValue(Message message) {
            int count = message.metadata().containsKey(key)
                    ? Integer.parseInt(message.metadata().get(key).split("-")[1])
                    : -1;
            return value + "-" + (count + 1);
        }
    }
}
