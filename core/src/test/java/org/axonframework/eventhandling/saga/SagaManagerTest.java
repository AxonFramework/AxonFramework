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

package org.axonframework.eventhandling.saga;

import org.apache.commons.collections.set.ListOrderedSet;
import org.axonframework.common.MockException;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

/**
 *
 */
public class SagaManagerTest {

    private AbstractSagaManager<Object> testSubject;
    private SagaRepository<Object> mockSagaRepository;
    private Saga<Object> mockSaga1;
    private Saga<Object> mockSaga2;
    private Saga<Object> mockSaga3;
    private SagaCreationPolicy sagaCreationPolicy;
    private AssociationValue associationValue;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {
        mockSagaRepository = mock(SagaRepository.class);
        mockSaga1 = mock(Saga.class);
        mockSaga2 = mock(Saga.class);
        mockSaga3 = mock(Saga.class);
        when(mockSaga1.isActive()).thenReturn(true);
        when(mockSaga2.isActive()).thenReturn(true);
        when(mockSaga3.isActive()).thenReturn(false);
        when(mockSaga1.getSagaIdentifier()).thenReturn("saga1");
        when(mockSaga2.getSagaIdentifier()).thenReturn("saga2");
        when(mockSaga3.getSagaIdentifier()).thenReturn("saga3");
        when(mockSagaRepository.load("saga1")).thenReturn(mockSaga1);
        when(mockSagaRepository.load("saga2")).thenReturn(mockSaga2);
        when(mockSagaRepository.load("saga3")).thenReturn(mockSaga3);
        when(mockSagaRepository.load("noSaga")).thenReturn(null);
        associationValue = new AssociationValue("association", "value");
        final AssociationValuesImpl associationValues = new AssociationValuesImpl(singleton(associationValue));
        when(mockSaga1.getAssociationValues()).thenReturn(associationValues);
        when(mockSaga2.getAssociationValues()).thenReturn(associationValues);
        when(mockSaga3.getAssociationValues()).thenReturn(associationValues);

        when(mockSagaRepository.find(eq(associationValue)))
                .thenReturn(setOf("saga1", "saga2", "saga3", "noSaga"));
        sagaCreationPolicy = SagaCreationPolicy.NONE;

        testSubject = new TestableAbstractSagaManager(mockSagaRepository);
    }

    @Test
    public void testSagasLoaded() throws Exception {
        EventMessage<?> event = new GenericEventMessage<>(new Object());
        testSubject.accept(event);
        verify(mockSagaRepository).find(associationValue);
        verify(mockSaga1).handle(event);
        verify(mockSaga2).handle(event);
        verify(mockSaga3, never()).handle(event);
    }

    @Test
    public void testExceptionPropagated() throws Exception {
        testSubject.setSuppressExceptions(false);
        EventMessage event = new GenericEventMessage<>(new Object());
        doThrow(new MockException()).when(mockSaga1).handle(event);
        try {
            testSubject.accept(event);
            fail("Expected exception to be propagated");
        } catch (RuntimeException e) {
            e.printStackTrace();
            assertEquals("Mock", e.getMessage());
        }
        verify(mockSaga1, times(1)).handle(event);
    }

    @Test
    public void testExceptionSuppressed() throws Exception {
        testSubject.setSuppressExceptions(true);
        EventMessage event = new GenericEventMessage<>(new Object());
        doThrow(new MockException()).when(mockSaga1).handle(event);

        testSubject.accept(event);

        verify(mockSaga1).handle(event);
        verify(mockSaga2).handle(event);
        verify(mockSaga3, never()).handle(event);
    }

    @SuppressWarnings({"unchecked"})
    private <T> Set<T> setOf(T... items) {
        return ListOrderedSet.decorate(Arrays.asList(items));
    }

    private class TestableAbstractSagaManager extends AbstractSagaManager<Object> {

        private TestableAbstractSagaManager(SagaRepository<Object> sagaRepository) {
            super(Object.class, sagaRepository, Object::new, t -> true);
        }

        @Override
        protected boolean hasHandler(EventMessage<?> event) {
            return true;
        }

        @Override
        protected SagaInitializationPolicy getSagaCreationPolicy(EventMessage<?> event) {
            return new SagaInitializationPolicy(sagaCreationPolicy, associationValue);
        }

        @Override
        protected Set<AssociationValue> extractAssociationValues(EventMessage<?> event) {
            return singleton(associationValue);
        }
    }
}
