/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.jupiter.api.*;

import java.util.Arrays;

import static org.mockito.Mockito.*;

/**
 * @author Rene de Waele
 */
class CorrelationDataInterceptorTest {

    private CorrelationDataInterceptor<Message<?>> subject;
    private UnitOfWork<Message<?>> mockUnitOfWork;
    private InterceptorChain mockInterceptorChain;
    private CorrelationDataProvider mockProvider1;
    private CorrelationDataProvider mockProvider2;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockProvider1 = mock(CorrelationDataProvider.class);
        mockProvider2 = mock(CorrelationDataProvider.class);
        subject = new CorrelationDataInterceptor<>(Arrays.asList(mockProvider1, mockProvider2));
        mockUnitOfWork = mock(UnitOfWork.class);
        mockInterceptorChain = mock(InterceptorChain.class);
    }

    @Test
    void attachesCorrelationDataProvidersToUnitOfWork() throws Exception {
        subject.handle(mockUnitOfWork, mockInterceptorChain);
        verify(mockUnitOfWork).registerCorrelationDataProvider(mockProvider1);
        verify(mockUnitOfWork).registerCorrelationDataProvider(mockProvider2);
        verify(mockInterceptorChain).proceedSync();
    }
}
