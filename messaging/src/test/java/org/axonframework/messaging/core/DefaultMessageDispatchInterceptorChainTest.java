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

package org.axonframework.messaging.core;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.messaging.core.MessagingTestUtils.message;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link DefaultMessageDispatchInterceptorChain}.
 */
class DefaultMessageDispatchInterceptorChainTest {

    @Test
    void proceedInvokesRegisteredInterceptorsInOrder() {
        MessageDispatchInterceptor<Message> interceptorOne = (message, context, chain) -> chain.proceed(
                message.andMetadata(Map.of("interceptorOne", "valueOne")), context
        );
        MessageDispatchInterceptor<Message> interceptorTwo = (message, context, chain) -> chain.proceed(
                message.andMetadata(Map.of("interceptorTwo", "valueTwo")), context
        );
        DefaultMessageDispatchInterceptorChain<Message> testSubject =
                new DefaultMessageDispatchInterceptorChain<>(List.of(interceptorOne, interceptorTwo));

        MessageStream<?> resultStream = testSubject.proceed(message("message", "payload"), null);

        assertThat(resultStream.error()).isNotPresent();
        Optional<? extends MessageStream.Entry<?>> messageEntry = resultStream.next();
        assertThat(messageEntry).isPresent();
        Metadata resultMetadata = messageEntry.get().message().metadata();
        assertThat(resultMetadata.size()).isEqualTo(2);
        assertThat(resultMetadata.get("interceptorOne")).isEqualTo("valueOne");
        assertThat(resultMetadata.get("interceptorTwo")).isEqualTo("valueTwo");
    }

    @Test
    void eachChainInvocationStartsFromTheBeginningAndInvokesInOrder() {
        // given...
        AtomicInteger invocationCount = new AtomicInteger(0);
        Message firstMessage = message("message", "payload");
        Message secondMessage = message("message", "second");

        //noinspection Convert2Lambda | Required as anonymous class for spying
        MessageDispatchInterceptor<Message> interceptorOne = spy(new MessageDispatchInterceptor<Message>() {
            @Nonnull
            @Override
            public MessageStream<?> interceptOnDispatch(@Nonnull Message message,
                                                        @Nullable ProcessingContext context,
                                                        @Nonnull MessageDispatchInterceptorChain<Message> chain) {
                invocationCount.incrementAndGet();
                return chain.proceed(message, context);
            }
        });
        //noinspection Convert2Lambda | Required as anonymous class for spying
        MessageDispatchInterceptor<Message> interceptorTwo = spy(new MessageDispatchInterceptor<Message>() {
            @Nonnull
            @Override
            public MessageStream<?> interceptOnDispatch(@Nonnull Message message, @Nullable ProcessingContext context,
                                                        @Nonnull MessageDispatchInterceptorChain<Message> chain) {
                invocationCount.incrementAndGet();
                return chain.proceed(message, context);
            }
        });
        MessageDispatchInterceptorChain<Message> testSubject =
                new DefaultMessageDispatchInterceptorChain<>(asList(interceptorOne, interceptorTwo));

        // when first invocation...
        MessageStream<?> firstResult = testSubject.proceed(firstMessage, null);
        // then response is...
        assertThat(firstResult.error()).isNotPresent();
        Optional<? extends MessageStream.Entry<?>> firstEntry = firstResult.next();
        assertThat(firstEntry).isPresent();
        assertThat(invocationCount.get()).isEqualTo(2);
        // and ordering is...
        InOrder firstInterceptorOrder = inOrder(interceptorOne, interceptorTwo);
        firstInterceptorOrder.verify(interceptorOne).interceptOnDispatch(eq(firstMessage), eq(null), any());
        firstInterceptorOrder.verify(interceptorTwo).interceptOnDispatch(eq(firstMessage), eq(null), any());
        // when second invocation...
        MessageStream<?> secondResult = testSubject.proceed(secondMessage, null);
        // then response is...
        assertThat(secondResult.error()).isNotPresent();
        Optional<? extends MessageStream.Entry<?>> secondEntry = secondResult.next();
        assertThat(secondEntry).isPresent();
        assertThat(invocationCount.get()).isEqualTo(4);
        // and ordering is...
        InOrder secondInterceptorOrder = inOrder(interceptorOne, interceptorTwo);
        secondInterceptorOrder.verify(interceptorOne).interceptOnDispatch(eq(secondMessage), eq(null), any());
        secondInterceptorOrder.verify(interceptorTwo).interceptOnDispatch(eq(secondMessage), eq(null), any());
    }

    @Test
    void proceedReturnsFaultyMessageStreamWhenInterceptorThrowsException() {
        MessageDispatchInterceptor<Message> faultyInterceptor = (message, context, chain) -> {
            throw new RuntimeException("whoops");
        };
        DefaultMessageDispatchInterceptorChain<Message> testSubject =
                new DefaultMessageDispatchInterceptorChain<>(List.of(faultyInterceptor));

        MessageStream<?> resultStream = testSubject.proceed(message("message", "payload"), null);
        Optional<Throwable> streamError = resultStream.error();

        assertThat(streamError).isPresent();
        assertThat(streamError.get()).isInstanceOf(RuntimeException.class);
    }
}
