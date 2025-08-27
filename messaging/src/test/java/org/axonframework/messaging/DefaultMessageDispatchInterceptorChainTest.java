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

package org.axonframework.messaging;

import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.messaging.MessagingTestHelper.message;

/**
 * Test class validating the {@link DefaultMessageDispatchInterceptorChain}.
 */
class DefaultMessageDispatchInterceptorChainTest {

    @Test
    void proceedInvokesRegisteredInterceptorsInOrder() {
        MessageDispatchInterceptor<Message> interceptorOne = (message, context, chain) -> chain.proceed(
                message.andMetaData(Map.of("interceptorOne", "valueOne")), context
        );
        MessageDispatchInterceptor<Message> interceptorTwo = (message, context, chain) -> chain.proceed(
                message.andMetaData(Map.of("interceptorTwo", "valueTwo")), context
        );
        DefaultMessageDispatchInterceptorChain<Message> testSubject =
                new DefaultMessageDispatchInterceptorChain<>(List.of(interceptorOne, interceptorTwo));

        MessageStream<?> resultStream = testSubject.proceed(message("message", "payload"), null);

        assertThat(resultStream.error()).isNotPresent();
        Optional<? extends MessageStream.Entry<?>> messageEntry = resultStream.next();
        assertThat(messageEntry).isPresent();
        MetaData resultMetadata = messageEntry.get().message().metaData();
        assertThat(resultMetadata.size()).isEqualTo(2);
        assertThat(resultMetadata.get("interceptorOne")).isEqualTo("valueOne");
        assertThat(resultMetadata.get("interceptorTwo")).isEqualTo("valueTwo");
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
