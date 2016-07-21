/*
 * Copyright (c) 2010-2016. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging.unitofwork;

import org.axonframework.common.Registration;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Rene de Waele
 */
public class AbstractUnitOfWorkFactoryTest {

    private StubUnitOfWorkFactory subject;

    @Before
    public void setUp() {
        subject = new StubUnitOfWorkFactory();
    }

    @Test
    public void testCorrelationDataProviderRegistration() {
        CorrelationDataProvider mockCorrelationDataProvider = mock(CorrelationDataProvider.class);
        Registration registration = subject.registerCorrelationDataProvider(mockCorrelationDataProvider);
        UnitOfWork<?> mockUnitOfWork = subject.createUnitOfWork(null);
        verify(mockUnitOfWork).registerCorrelationDataProvider(mockCorrelationDataProvider);
        registration.cancel();
        mockUnitOfWork = subject.createUnitOfWork(null);
        verify(mockUnitOfWork, never()).registerCorrelationDataProvider(any());
    }

    private static class StubUnitOfWorkFactory extends AbstractUnitOfWorkFactory<UnitOfWork<?>> {
        @Override
        protected UnitOfWork<?> doCreateUnitOfWork(Message<?> message) {
            return mock(UnitOfWork.class);
        }
    }

}
