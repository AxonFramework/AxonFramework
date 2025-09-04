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

package org.axonframework.messaging.interceptors;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.configuration.Configuration;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageDispatchInterceptorChain;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link DefaultDispatchInterceptorRegistry}.
 *
 * @author Steven van Beelen
 */
class DefaultDispatchInterceptorRegistryTest {

    private DefaultDispatchInterceptorRegistry testSubject;

    private Configuration config;

    @BeforeEach
    void setUp() {
        testSubject = new DefaultDispatchInterceptorRegistry();

        config = mock(Configuration.class);
    }

    @Test
    void registeredDispatchInterceptorIsRetrievable() {
        DispatchInterceptorRegistry result = testSubject.registerInterceptor(c -> new GenericMessageDispatchInterceptor());

        List<MessageDispatchInterceptor<? super Message>> dispatchInterceptors = result.interceptors(config);
        assertThat(dispatchInterceptors).size().isEqualTo(1);
    }

    @Test
    void registeredDispatchInterceptorsAreOnlyCreatedOnce() {
        AtomicInteger builderInvocationCount = new AtomicInteger(0);
        DispatchInterceptorRegistry result = testSubject.registerInterceptor(c -> {
            builderInvocationCount.incrementAndGet();
            return new GenericMessageDispatchInterceptor();
        });

        result.interceptors(config);
        result.interceptors(config);
        result.interceptors(config);
        assertThat(builderInvocationCount.get()).isEqualTo(1);
    }

    static class GenericMessageDispatchInterceptor implements MessageDispatchInterceptor<Message> {

        @Nonnull
        @Override
        public MessageStream<?> interceptOnDispatch(@Nonnull Message message,
                                                    @Nullable ProcessingContext context,
                                                    @Nonnull MessageDispatchInterceptorChain<Message> chain) {
            return chain.proceed(message, context);
        }
    }
}