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

package org.axonframework.eventhandling;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptorChain;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Optional;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.messaging.MessagingTestUtils.event;
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

        Message result = testSubject.proceed(testEvent, StubProcessingContext.forMessage(testEvent))
                                    .first()
                                    .asMono()
                                    .map(MessageStream.Entry::message)
                                    .block();
        assertNull(result);
        verify(mockHandler).handle(argThat(x -> (x != null) && "testing".equals(x.payload())), any());
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