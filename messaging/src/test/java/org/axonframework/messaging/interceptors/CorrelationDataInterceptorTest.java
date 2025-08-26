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

import org.axonframework.messaging.MessageHandlerInterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.Map;

import static org.axonframework.messaging.interceptors.CorrelationDataInterceptor.CORRELATION_DATA;
import static org.mockito.Mockito.*;

/**
 * @author Rene de Waele
 */
class CorrelationDataInterceptorTest {

    private CorrelationDataInterceptor<Message> subject;
    private MessageHandlerInterceptorChain<Message> mockInterceptorChain;
    private CorrelationDataProvider mockProvider1;
    private CorrelationDataProvider mockProvider2;

    @BeforeEach
    void setUp() {
        mockProvider1 = mock(CorrelationDataProvider.class);
        mockProvider2 = mock(CorrelationDataProvider.class);
        subject = new CorrelationDataInterceptor<>(Arrays.asList(mockProvider1, mockProvider2));
        mockInterceptorChain = mock();
    }

    @Test
    void attachesCorrelationDataProvidersToProcessingContext() throws Exception {
        ProcessingContext context = new StubProcessingContext();
        Message<?> message = mock(Message.class);

        when(mockProvider1.correlationDataFor(any())).thenReturn(Map.of("key1", "value"));
        when(mockProvider2.correlationDataFor(any())).thenReturn(Map.of("key1", "value2", "key2", "value2"));
        Map<String, Object> expected = Map.of("key1", "value2", "key2", "value2");

        subject.interceptOnHandle(message, context, mockInterceptorChain);
        verify(mockProvider1).correlationDataFor(message);
        verify(mockProvider2).correlationDataFor(message);
        verify(mockInterceptorChain).proceed(message, context.withResource(CORRELATION_DATA, expected));
    }
}
