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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link DispatchInterceptors}.
 */
class DefaultMessageDispatchInterceptorChainTest {

    @Test
    void registerInterceptors() {
        List<String> results = new ArrayList<>();
        DefaultMessageDispatchInterceptorChain<Message<?>> chain = new DefaultMessageDispatchInterceptorChain<>(
                List.of(
                        (m, c, ch) -> {
                            results.add("Interceptor One");
                            return ch.proceed(m, c);
                        },
                        (m, c, ch) -> {
                            results.add("Interceptor Two");
                            return ch.proceed(m, c);
                        }
                )
        );

        chain.proceed(new GenericMessage<>(new MessageType("message"), "payload"), null);
        assertEquals("Interceptor One", results.get(0));
        assertEquals("Interceptor Two", results.get(1));
        assertEquals(2, results.size());
    }
}
