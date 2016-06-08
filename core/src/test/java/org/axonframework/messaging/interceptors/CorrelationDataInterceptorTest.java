/*
 * Copyright (c) 2010-2015. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging.interceptors;

import org.axonframework.common.Registration;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.metadata.CorrelationDataProvider;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

/**
 * @author Rene de Waele
 */
public class CorrelationDataInterceptorTest {

    private CorrelationDataInterceptor<Message<?>> subject;
    private UnitOfWork<Message<?>> mockUnitOfWork;
    private InterceptorChain mockInterceptorChain;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        subject = new CorrelationDataInterceptor<>();
        mockUnitOfWork = mock(UnitOfWork.class);
        mockInterceptorChain = mock(InterceptorChain.class);
    }

    @Test
    public void testAttachesCorrelationDataProvidersToUnitOfWork() throws Exception {
        CorrelationDataProvider mockProvider1 = mock(CorrelationDataProvider.class);
        CorrelationDataProvider mockProvider2 = mock(CorrelationDataProvider.class);
        subject.registerCorrelationDataProvider(mockProvider1);
        subject.registerCorrelationDataProvider(mockProvider2);
        subject.handle(mockUnitOfWork, mockInterceptorChain);
        verify(mockUnitOfWork).registerCorrelationDataProvider(mockProvider1);
        verify(mockUnitOfWork).registerCorrelationDataProvider(mockProvider2);
        verify(mockInterceptorChain).proceed();
    }

    @Test
    public void testUnregisteredProviderIsNoLongerAttachedToUnitOfWork() throws Exception {
        CorrelationDataProvider mockProvider = mock(CorrelationDataProvider.class);
        Registration registration = subject.registerCorrelationDataProvider(mockProvider);
        subject.handle(mockUnitOfWork, mockInterceptorChain);
        verify(mockUnitOfWork).registerCorrelationDataProvider(mockProvider);
        verify(mockInterceptorChain).proceed();
        registration.cancel();
        reset((Object) mockInterceptorChain);
        subject.handle(mockUnitOfWork, mockInterceptorChain);
        verifyNoMoreInteractions(mockUnitOfWork);
        verify(mockInterceptorChain).proceed();
    }

}