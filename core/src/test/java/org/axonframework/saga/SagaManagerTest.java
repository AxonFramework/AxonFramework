/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.saga;

import org.apache.commons.collections.set.ListOrderedSet;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.domain.Message;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.saga.annotation.AssociationValuesImpl;
import org.axonframework.testutils.MockException;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.junit.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.singleton;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 *
 */
public class SagaManagerTest {

    private AbstractSagaManager testSubject;
    private EventBus mockEventBus;
    private SagaRepository mockSagaRepository;
    private Saga mockSaga1;
    private Saga mockSaga2;
    private Saga mockSaga3;
    private SagaCreationPolicy sagaCreationPolicy;
    private AssociationValue associationValue;
    private SagaFactory mockSagaFactory;

    @Before
    public void setUp() throws Exception {
        mockEventBus = mock(EventBus.class);
        mockSagaRepository = mock(SagaRepository.class);
        mockSagaFactory = mock(SagaFactory.class);
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
        associationValue = new AssociationValue("association", "value");
        for (Saga saga : setOf(mockSaga1, mockSaga2, mockSaga3)) {
            final AssociationValuesImpl associationValues = new AssociationValuesImpl();
            associationValues.add(associationValue);
            when(saga.getAssociationValues()).thenReturn(associationValues);
        }
        when(mockSagaRepository.find(isA(Class.class), eq(associationValue)))
                .thenReturn(setOf("saga1", "saga2", "saga3"));
        sagaCreationPolicy = SagaCreationPolicy.NONE;

        testSubject = new TestableAbstractSagaManager(mockSagaRepository, mockSagaFactory, Saga.class);
    }

    @Test
    public void testSagasLoadedAndCommitted() {
        EventMessage event = new GenericEventMessage<>(new Object());
        testSubject.handle(event);
        verify(mockSaga1).handle(event);
        verify(mockSaga2).handle(event);
        verify(mockSaga3, never()).handle(isA(EventMessage.class));
        verify(mockSagaRepository).commit(mockSaga1);
        verify(mockSagaRepository).commit(mockSaga2);
        verify(mockSagaRepository, never()).commit(mockSaga3);
    }

    @Test
    public void testExceptionPropagated() {
        testSubject.setSuppressExceptions(false);
        EventMessage event = new GenericEventMessage<>(new Object());
        doThrow(new MockException()).when(mockSaga1).handle(event);
        try {
            testSubject.handle(event);
            fail("Expected exception to be propagated");
        } catch (RuntimeException e) {
            assertEquals("Mock", e.getMessage());
        }
        verify(mockSaga1).handle(event);
        verify(mockSaga2, never()).handle(event);
        verify(mockSagaRepository).commit(mockSaga1);
        verify(mockSagaRepository, never()).commit(mockSaga2);
    }

    @Test
    public void testExceptionSuppressed() {
        EventMessage event = new GenericEventMessage<>(new Object());
        doThrow(new MockException()).when(mockSaga1).handle(event);

        testSubject.handle(event);

        verify(mockSaga1).handle(event);
        verify(mockSaga2).handle(event);
        verify(mockSagaRepository).commit(mockSaga1);
        verify(mockSagaRepository).commit(mockSaga2);
    }

    @Test
    public synchronized void testAccessToSagaWhileInCreation() throws InterruptedException {
        when(mockSagaFactory.createSaga(isA(Class.class))).thenReturn(mockSaga1);
        reset(mockSagaRepository);
        when(mockSagaRepository.find(isA(Class.class), isA(AssociationValue.class)))
                .thenReturn(Collections.<String>emptySet())
                .thenReturn(singleton("saga1"));

        sagaCreationPolicy = SagaCreationPolicy.IF_NONE_FOUND;
        final AtomicInteger nestingCounter = new AtomicInteger(20);
        final EventMessage event = GenericEventMessage.asEventMessage(new Object());

        doAnswer(invocationOnMock -> {
            if (nestingCounter.decrementAndGet() > 0) {
                UnitOfWork uow = DefaultUnitOfWork.startAndGet((Message<?>) invocationOnMock.getArguments()[0]);
                try {
                    testSubject.handle(event);
                } finally {
                    uow.commit();
                }
            }
            return null;
        }).when(mockSaga1).handle(isA(EventMessage.class));

        testSubject.handle(event);

        verify(mockSagaRepository).add(mockSaga1);
        verify(mockSagaRepository, times(19)).commit(mockSaga1);

        verify(mockSagaRepository, never()).load("saga1");
    }


    @SuppressWarnings({"unchecked"})
    private <T> Set<T> setOf(T... items) {
        return ListOrderedSet.decorate(Arrays.asList(items));
    }

    private class TestableAbstractSagaManager extends AbstractSagaManager {

        @SafeVarargs
        private TestableAbstractSagaManager(SagaRepository sagaRepository, SagaFactory sagaFactory,
                                            Class<? extends Saga>... sagaTypes) {
            super(sagaRepository, sagaFactory, sagaTypes);
        }

        @Override
        public Class<?> getTargetType() {
            return Saga.class;
        }

        @Override
        protected SagaInitializationPolicy getSagaCreationPolicy(Class<? extends Saga> sagaType, EventMessage event) {
            return new SagaInitializationPolicy(sagaCreationPolicy, associationValue);
        }

        @Override
        protected Set<AssociationValue> extractAssociationValues(Class<? extends Saga> sagaType, EventMessage event) {
            return singleton(associationValue);
        }
    }
}
