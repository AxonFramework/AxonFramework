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

package org.axonframework.messaging.queryhandling.interception;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.FluxUtils;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageHandlerInterceptor;
import org.axonframework.messaging.core.MessageHandlerInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.messaging.queryhandling.QueryHandler;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.messaging.core.MessagingTestUtils.query;
import static org.axonframework.messaging.core.MessagingTestUtils.queryResponse;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link QueryMessageHandlerInterceptorChain}.
 *
 * @author Steven van Beelen
 */
class QueryMessageHandlerInterceptorChainTest {

    private QueryHandler mockHandler;

    @BeforeEach
    void setUp() {
        mockHandler = mock();
        when(mockHandler.handle(any(), any()))
                .then(invocation -> MessageStream.just(queryResponse("response")));
    }

    @Test
    void chainWithDifferentProceedCalls() {
        QueryMessage testQuery = query("message");

        MessageHandlerInterceptor<QueryMessage> interceptorOne =
                (message, context, chain) -> chain.proceed(query("testing"), context);
        MessageHandlerInterceptor<QueryMessage> interceptorTwo =
                (message, context, chain) -> chain.proceed(message, context);
        MessageHandlerInterceptorChain<QueryMessage> testSubject =
                new QueryMessageHandlerInterceptorChain(asList(interceptorOne, interceptorTwo), mockHandler);

        Message result = FluxUtils.of(testSubject.proceed(testQuery, StubProcessingContext.forMessage(testQuery)).first())
            .singleOrEmpty()
            .map(MessageStream.Entry::message)
            .block();
        assertNotNull(result);
        assertSame("response", result.payload());
        verify(mockHandler).handle(argThat(x -> (x != null) && "testing".equals(x.payload())), any());
    }

    @Test
    void subsequentChainInvocationsStartFromBeginningAndInvokeInOrder() {
        // given...
        AtomicInteger invocationCount = new AtomicInteger(0);
        QueryMessage firstQuery = query("first");
        QueryMessage secondQuery = query("second");
        ProcessingContext firstContext = StubProcessingContext.forMessage(firstQuery);
        ProcessingContext secondContext = StubProcessingContext.forMessage(secondQuery);

        //noinspection Convert2Lambda | Required as anonymous class for spying
        MessageHandlerInterceptor<QueryMessage> interceptorOne = spy(new MessageHandlerInterceptor<QueryMessage>() {
            @Nonnull
            @Override
            public MessageStream<?> interceptOnHandle(@Nonnull QueryMessage message,
                                                      @Nonnull ProcessingContext context,
                                                      @Nonnull MessageHandlerInterceptorChain<QueryMessage> chain) {
                invocationCount.incrementAndGet();
                return chain.proceed(message, context);
            }
        });
        //noinspection Convert2Lambda | Required as anonymous class for spying
        MessageHandlerInterceptor<QueryMessage> interceptorTwo = spy(new MessageHandlerInterceptor<QueryMessage>() {
            @Nonnull
            @Override
            public MessageStream<?> interceptOnHandle(@Nonnull QueryMessage message,
                                                      @Nonnull ProcessingContext context,
                                                      @Nonnull MessageHandlerInterceptorChain<QueryMessage> chain) {
                invocationCount.incrementAndGet();
                return chain.proceed(message, context);
            }
        });
        MessageHandlerInterceptorChain<QueryMessage> testSubject =
                new QueryMessageHandlerInterceptorChain(asList(interceptorOne, interceptorTwo), mockHandler);

        // when first invocation...
        Message firstResult = FluxUtils.of(testSubject.proceed(firstQuery, firstContext).first())
            .singleOrEmpty()
            .map(MessageStream.Entry::message)
            .block();
        // then response is...
        assertNotNull(firstResult);
        assertSame("response", firstResult.payload());
        assertThat(invocationCount.get()).isEqualTo(2);
        // and ordering is...
        InOrder firstInterceptorOrder = inOrder(interceptorOne, interceptorTwo);
        firstInterceptorOrder.verify(interceptorOne).interceptOnHandle(eq(firstQuery), eq(firstContext), any());
        firstInterceptorOrder.verify(interceptorTwo).interceptOnHandle(eq(firstQuery), eq(firstContext), any());
        // when second invocation...
        Message secondResult = FluxUtils.of(testSubject.proceed(secondQuery, secondContext).first())
            .singleOrEmpty()
            .map(MessageStream.Entry::message)
            .block();
        // then response is...
        assertNotNull(secondResult);
        assertSame("response", firstResult.payload());
        assertThat(invocationCount.get()).isEqualTo(4);
        // and ordering is...
        InOrder secondInterceptorOrder = inOrder(interceptorOne, interceptorTwo);
        secondInterceptorOrder.verify(interceptorOne).interceptOnHandle(eq(secondQuery), eq(secondContext), any());
        secondInterceptorOrder.verify(interceptorTwo).interceptOnHandle(eq(secondQuery), eq(secondContext), any());
    }

    @Test
    void returnsFailedMessageStreamWhenInterceptorThrowsException() {
        QueryMessage testQuery = query("message");

        MessageHandlerInterceptor<QueryMessage> faultyInterceptor = (message, context, chain) -> {
            throw new RuntimeException("whoops");
        };
        MessageHandlerInterceptorChain<QueryMessage> testSubject =
                new QueryMessageHandlerInterceptorChain(List.of(faultyInterceptor), mockHandler);

        Optional<Throwable> exceptionalResult =
                testSubject.proceed(testQuery, StubProcessingContext.forMessage(testQuery))
                           .error();
        assertThat(exceptionalResult).isPresent();
        assertThat(exceptionalResult.get()).isInstanceOf(RuntimeException.class);
    }
}