/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling.interception;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.eventhandling.EventHandler;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.core.FluxUtils;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageHandlerInterceptor;
import org.axonframework.messaging.core.MessageHandlerInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.messaging.core.MessagingTestUtils.event;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link EventMessageHandlerInterceptorChain}.
 *
 * @author Steven van Beelen
 */
class EventMessageHandlerInterceptorChainTest {

    private EventHandler mockHandler;

    @BeforeEach
    void setUp() {
        mockHandler = mock();
        when(mockHandler.handle(any(), any())).thenReturn(MessageStream.empty());
    }

    @Test
    void chainWithDifferentProceedCalls() {
        EventMessage testEvent = event("message");

        MessageHandlerInterceptor<EventMessage> interceptorOne =
                (message, context, chain) -> chain.proceed(event("testing"), context);
        MessageHandlerInterceptor<EventMessage> interceptorTwo =
                (message, context, chain) -> chain.proceed(message, context);
        MessageHandlerInterceptorChain<EventMessage> testSubject =
                new EventMessageHandlerInterceptorChain(asList(interceptorOne, interceptorTwo), mockHandler);

        Message result = FluxUtils.of(testSubject.proceed(testEvent, StubProcessingContext.forMessage(testEvent)).first())
            .singleOrEmpty()
            .map(MessageStream.Entry::message)
            .block();
        assertNull(result);
        verify(mockHandler).handle(argThat(x -> (x != null) && "testing".equals(x.payload())), any());
    }

    @Test
    void subsequentChainInvocationsSTartFromBeginningAndInvokeInOrder() {
        // given...
        AtomicInteger invocationCount = new AtomicInteger(0);
        EventMessage firstEvent = event("first");
        ProcessingContext firstContext = StubProcessingContext.forMessage(firstEvent);
        EventMessage secondEvent = event("second");
        ProcessingContext secondContext = StubProcessingContext.forMessage(secondEvent);

        //noinspection Convert2Lambda | Required as anonymous class for spying
        MessageHandlerInterceptor<EventMessage> interceptorOne = spy(new MessageHandlerInterceptor<EventMessage>() {
            @Nonnull
            @Override
            public MessageStream<?> interceptOnHandle(@Nonnull EventMessage message, @Nonnull ProcessingContext context,
                                                      @Nonnull MessageHandlerInterceptorChain<EventMessage> chain) {
                invocationCount.incrementAndGet();
                return chain.proceed(message, context);
            }
        });
        //noinspection Convert2Lambda | Required as anonymous class for spying
        MessageHandlerInterceptor<EventMessage> interceptorTwo = spy(new MessageHandlerInterceptor<EventMessage>() {
            @Nonnull
            @Override
            public MessageStream<?> interceptOnHandle(@Nonnull EventMessage message, @Nonnull ProcessingContext context,
                                                      @Nonnull MessageHandlerInterceptorChain<EventMessage> chain) {
                invocationCount.incrementAndGet();
                return chain.proceed(message, context);
            }
        });
        MessageHandlerInterceptorChain<EventMessage> testSubject =
                new EventMessageHandlerInterceptorChain(asList(interceptorOne, interceptorTwo), mockHandler);

        // when first invocation...
        Message firstResult = FluxUtils.of(testSubject.proceed(firstEvent, firstContext).first())
            .singleOrEmpty()
            .map(MessageStream.Entry::message)
            .block();
        // then response is...
        assertNull(firstResult);
        assertThat(invocationCount.get()).isEqualTo(2);
        // and ordering is...
        InOrder firstInterceptorOrder = inOrder(interceptorOne, interceptorTwo);
        firstInterceptorOrder.verify(interceptorOne).interceptOnHandle(eq(firstEvent), eq(firstContext), any());
        firstInterceptorOrder.verify(interceptorTwo).interceptOnHandle(eq(firstEvent), eq(firstContext), any());

        // when second invocation...
        Message secondResult = FluxUtils.of(testSubject.proceed(secondEvent, secondContext).first())
            .singleOrEmpty()
            .map(MessageStream.Entry::message)
            .block();
        // then response is...
        assertNull(secondResult);
        assertThat(invocationCount.get()).isEqualTo(4);
        // and ordering is...
        InOrder secondInterceptorOrder = inOrder(interceptorOne, interceptorTwo);
        secondInterceptorOrder.verify(interceptorOne).interceptOnHandle(eq(secondEvent), eq(secondContext), any());
        secondInterceptorOrder.verify(interceptorTwo).interceptOnHandle(eq(secondEvent), eq(secondContext), any());
    }

    @Test
    void returnsFailedMessageStreamWhenInterceptorThrowsException() {
        EventMessage testEvent = event("message");

        MessageHandlerInterceptor<EventMessage> faultyInterceptor = (message, context, chain) -> {
            throw new RuntimeException("whoops");
        };
        MessageHandlerInterceptorChain<EventMessage> testSubject =
                new EventMessageHandlerInterceptorChain(List.of(faultyInterceptor), mockHandler);

        Optional<Throwable> exceptionalResult =
                testSubject.proceed(testEvent, StubProcessingContext.forMessage(testEvent))
                           .error();
        assertThat(exceptionalResult).isPresent();
        assertThat(exceptionalResult.get()).isInstanceOf(RuntimeException.class);
    }
}