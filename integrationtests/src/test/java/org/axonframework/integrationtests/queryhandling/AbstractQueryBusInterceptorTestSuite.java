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

package org.axonframework.integrationtests.queryhandling;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.configuration.AxonConfiguration;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageDispatchInterceptorChain;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptorChain;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.annotations.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotations.MultiParameterResolverFactory;
import org.axonframework.messaging.annotations.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryHandlingComponent;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.annotations.AnnotatedQueryHandlingComponent;
import org.axonframework.queryhandling.annotations.QueryHandler;
import org.axonframework.serialization.PassThroughConverter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract test suite for validating QueryBus interceptor behavior through configuration.
 * <p>
 * This test suite proves that dispatch interceptors are NOT invoked when using configuration to build QueryBus
 * instances, while handler interceptors ARE properly invoked.
 * <p>
 * Concrete implementations provide different QueryBus configurations (SimpleQueryBus, DistributedQueryBus).
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public abstract class AbstractQueryBusInterceptorTestSuite {

    private static final MessageType TEST_QUERY_TYPE = new MessageType("testQuery");
    private static final MessageType TEST_RESPONSE_TYPE = new MessageType(String.class);
    private static final QualifiedName QUERY_NAME = new QualifiedName("testQuery");
    private static final QualifiedName RESPONSE_NAME = new QualifiedName(String.class);

    protected RecordingDispatchInterceptor dispatchInterceptor;
    protected RecordingHandlerInterceptor handlerInterceptor;
    private QueryBus queryBus;
    private SimpleQueryHandler queryHandler;

    @BeforeEach
    void setUp() {
        // given
        dispatchInterceptor = new RecordingDispatchInterceptor();
        handlerInterceptor = new RecordingHandlerInterceptor();

        queryBus = configuration().getComponent(QueryBus.class);
        queryHandler = new SimpleQueryHandler();

        ParameterResolverFactory parameterResolverFactory = MultiParameterResolverFactory.ordered(
                ClasspathParameterResolverFactory.forClass(SimpleQueryHandler.class)
        );
        QueryHandlingComponent annotatedQueryHandlingComponent = new AnnotatedQueryHandlingComponent<>(
                queryHandler, parameterResolverFactory, PassThroughConverter.MESSAGE_INSTANCE
        );
        queryBus.subscribe(annotatedQueryHandlingComponent);
    }

    @AfterEach
    void tearDown() {
        if (configuration() != null) {
            configuration().shutdown();
        }
    }

    /**
     * Returns the {@link AxonConfiguration} used to test QueryBus interceptor behavior.
     * <p>
     * Implementations should register both dispatch and handler interceptors via configuration methods like
     * {@code MessagingConfigurer.registerQueryDispatchInterceptor()} and
     * {@code MessagingConfigurer.registerQueryHandlerInterceptor()}.
     *
     * @return The {@link AxonConfiguration} with registered interceptors.
     */
    protected abstract AxonConfiguration configuration();

    @Nested
    @DisplayName("Dispatch interceptor tests")
    class DispatchInterceptorTests {

        @Test
        void dispatchInterceptorsAreInvokedForPointToPointQueries() {
            // given
            QueryMessage testQuery = new GenericQueryMessage(TEST_QUERY_TYPE, "test", TEST_RESPONSE_TYPE);

            // when
            MessageStream<QueryResponseMessage> result = queryBus.query(testQuery,
                                                                        StubProcessingContext.forMessage(testQuery));
            result.first().asCompletableFuture().join();

            // then
            assertThat(dispatchInterceptor.getInvocationCount())
                    .as("Dispatch interceptor should be invoked for point-to-point queries")
                    .isGreaterThan(0);
        }

        @Test
        void dispatchInterceptorsAreInvokedForSubscriptionQueries() {
            // given
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    TEST_QUERY_TYPE, "test", TEST_RESPONSE_TYPE
            );

            // when
            MessageStream<QueryResponseMessage> result = queryBus.subscriptionQuery(testQuery,
                                                                                    StubProcessingContext.forMessage(
                                                                                            testQuery),
                                                                                    10);
            result.first().asCompletableFuture().join();

            // then
            assertThat(dispatchInterceptor.getInvocationCount())
                    .as("Dispatch interceptor should be invoked for subscription queries")
                    .isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Handler interceptor tests")
    class HandlerInterceptorTests {

        @Test
        void handlerInterceptorsAreInvokedForPointToPointQueries() {
            // given
            QueryMessage testQuery = new GenericQueryMessage(TEST_QUERY_TYPE, "test", TEST_RESPONSE_TYPE);

            // when
            MessageStream<QueryResponseMessage> result = queryBus.query(testQuery,
                                                                        StubProcessingContext.forMessage(testQuery));
            result.first().asCompletableFuture().join();

            // then
            assertThat(handlerInterceptor.getInvocationCount())
                    .as("Handler interceptor should be invoked for point-to-point queries")
                    .isGreaterThan(0);
        }

        @Test
        void handlerInterceptorsAreInvokedForSubscriptionQueries() {
            // given
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    TEST_QUERY_TYPE, "test", TEST_RESPONSE_TYPE
            );

            // when
            MessageStream<QueryResponseMessage> result = queryBus.subscriptionQuery(testQuery,
                                                                                    StubProcessingContext.forMessage(
                                                                                            testQuery),
                                                                                    10);
            result.first().asCompletableFuture().join();

            // then
            assertThat(handlerInterceptor.getInvocationCount())
                    .as("Handler interceptor should be invoked for subscription queries")
                    .isGreaterThan(0);
        }
    }

    /**
     * Recording dispatch interceptor that tracks invocation count.
     */
    protected static class RecordingDispatchInterceptor implements MessageDispatchInterceptor<Message> {

        private final AtomicInteger invocationCount = new AtomicInteger(0);

        @Nonnull
        @Override
        public MessageStream<?> interceptOnDispatch(@Nonnull Message message,
                                                    @Nullable ProcessingContext context,
                                                    @Nonnull MessageDispatchInterceptorChain<Message> interceptorChain) {
            invocationCount.incrementAndGet();
            return interceptorChain.proceed(message, context);
        }

        public int getInvocationCount() {
            return invocationCount.get();
        }
    }

    /**
     * Recording handler interceptor that tracks invocation count.
     */
    protected static class RecordingHandlerInterceptor implements MessageHandlerInterceptor<Message> {

        private final AtomicInteger invocationCount = new AtomicInteger(0);

        @Nonnull
        @Override
        public MessageStream<?> interceptOnHandle(@Nonnull Message message,
                                                  @Nonnull ProcessingContext context,
                                                  @Nonnull MessageHandlerInterceptorChain<Message> interceptorChain) {
            invocationCount.incrementAndGet();
            return interceptorChain.proceed(message, context);
        }

        public int getInvocationCount() {
            return invocationCount.get();
        }
    }

    /**
     * Simple query handler for testing.
     */
    protected static class SimpleQueryHandler {

        @QueryHandler(queryName = "testQuery")
        public String handle(String query) {
            return "response-" + query;
        }
    }
}
