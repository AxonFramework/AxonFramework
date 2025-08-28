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

package org.axonframework.commandhandling;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptorChain;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.messaging.MessagingTestUtils.command;
import static org.axonframework.messaging.MessagingTestUtils.commandResult;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link CommandMessageHandlerInterceptorChain}.
 *
 * @author Simon Zambrovski
 */
class CommandMessageHandlerInterceptorChainTest {

    private CommandHandler mockHandler;

    @BeforeEach
    void setUp() {
        mockHandler = mock();
        when(mockHandler.handle(any(), any()))
                .thenReturn(MessageStream.just(commandResult("result", "Result")).cast());
    }

    @Test
    void chainWithDifferentProceedCalls() {
        CommandMessage testCommand = command("message", "original");

        MessageHandlerInterceptor<CommandMessage> interceptorOne = (message, context, chain) ->
                chain.proceed(command("message", "testing"), context);
        MessageHandlerInterceptor<CommandMessage> interceptorTwo = (message, context, chain) ->
                chain.proceed(message, context);
        MessageHandlerInterceptorChain<CommandMessage> testSubject =
                new CommandMessageHandlerInterceptorChain(asList(interceptorOne, interceptorTwo), mockHandler);

        Message result = testSubject.proceed(testCommand, StubProcessingContext.forMessage(testCommand))
                                    .first()
                                    .asMono()
                                    .map(MessageStream.Entry::message)
                                    .block();
        assertNotNull(result);
        assertSame("Result", result.payload());
        verify(mockHandler).handle(argThat(x -> (x != null) && "testing".equals(x.payload())), any());
    }

    @Test
    void secondChainInvocationProceedsThroughChainFromBeginning() {
        AtomicInteger invocationCount = new AtomicInteger(0);
        CommandMessage firstCommand = command("message", "first");
        CommandMessage secondCommand = command("message", "second");

        MessageHandlerInterceptor<CommandMessage> interceptorOne = (message, context, chain) -> {
            invocationCount.incrementAndGet();
            return chain.proceed(message, context);
        };
        MessageHandlerInterceptor<CommandMessage> interceptorTwo = (message, context, chain) -> {
            invocationCount.incrementAndGet();
            return chain.proceed(message, context);
        };
        MessageHandlerInterceptorChain<CommandMessage> testSubject =
                new CommandMessageHandlerInterceptorChain(asList(interceptorOne, interceptorTwo), mockHandler);

        Message firstResult = testSubject.proceed(firstCommand, StubProcessingContext.forMessage(firstCommand))
                                         .first()
                                         .asMono()
                                         .map(MessageStream.Entry::message)
                                         .block();
        assertNotNull(firstResult);
        assertSame("Result", firstResult.payload());
        assertThat(invocationCount.get()).isEqualTo(2);

        Message secondResult = testSubject.proceed(secondCommand, StubProcessingContext.forMessage(secondCommand))
                                          .first()
                                          .asMono()
                                          .map(MessageStream.Entry::message)
                                          .block();
        assertNotNull(secondResult);
        assertSame("Result", firstResult.payload());
        assertThat(invocationCount.get()).isEqualTo(4);
    }

    @Test
    void returnsFailedMessageStreamWhenInterceptorThrowsException() {
        CommandMessage testCommand = command("message", "original");

        MessageHandlerInterceptor<CommandMessage> faultyInterceptor = (message, context, chain) -> {
            throw new RuntimeException("whoops");
        };
        MessageHandlerInterceptorChain<CommandMessage> testSubject =
                new CommandMessageHandlerInterceptorChain(List.of(faultyInterceptor), mockHandler);

        Optional<Throwable> exceptionalResult =
                testSubject.proceed(testCommand, StubProcessingContext.forMessage(testCommand))
                           .error();
        assertThat(exceptionalResult).isPresent();
        assertThat(exceptionalResult.get()).isInstanceOf(RuntimeException.class);
    }
}
