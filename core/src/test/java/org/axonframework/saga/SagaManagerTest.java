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
import org.axonframework.eventhandling.EventBus;
import org.axonframework.saga.annotation.AssociationValuesImpl;
import org.axonframework.testutils.MockException;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.junit.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.emptySet;
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
    private SagaFactory mockSagaFactory;

    @Before
    public void setUp() throws Exception {
        mockEventBus = mock(EventBus.class);
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

        mockSagaFactory = mock(SagaFactory.class);
        when(mockSagaFactory.supports(isA(Class.class))).thenReturn(true);

        final AssociationValue associationValue = new AssociationValue("association", "value");
        for (Saga saga : setOf(mockSaga1, mockSaga2, mockSaga3)) {
            final AssociationValuesImpl associationValues = new AssociationValuesImpl();
            associationValues.add(associationValue);
            when(saga.getAssociationValues()).thenReturn(associationValues);
        }
        when(mockSagaRepository.find(isA(Class.class), eq(associationValue)))
                .thenReturn(setOf("saga1", "saga2", "saga3"));
        sagaCreationPolicy = SagaCreationPolicy.NONE;
        testSubject = new AbstractSagaManager(mockEventBus, mockSagaRepository, mockSagaFactory, Saga.class) {

            @Override
            public Class<?> getTargetType() {
                return Saga.class;
            }

            @Override
            protected SagaCreationPolicy getSagaCreationPolicy(Class<? extends Saga> sagaType, EventMessage event) {
                return sagaCreationPolicy;
            }

            @Override
            protected AssociationValue extractAssociationValue(Class<? extends Saga> sagaType, EventMessage event) {
                return associationValue;
            }
        };
    }

    @Test
    public void testSubscription() {
        testSubject.subscribe();
        verify(mockEventBus).subscribe(testSubject);
        testSubject.unsubscribe();
        verify(mockEventBus).unsubscribe(testSubject);
    }

    @Test
    public void testSagasLoadedAndCommitted() {
        EventMessage event = new GenericEventMessage<Object>(new Object());
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
        EventMessage event = new GenericEventMessage<Object>(new Object());
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
        EventMessage event = new GenericEventMessage<Object>(new Object());
        doThrow(new MockException()).when(mockSaga1).handle(event);

        testSubject.handle(event);

        verify(mockSaga1).handle(event);
        verify(mockSaga2).handle(event);
        verify(mockSagaRepository).commit(mockSaga1);
        verify(mockSagaRepository).commit(mockSaga2);
    }

    @Test
    public synchronized void testConcurrentAccessToSagaWhileInCreation_WithUnitOfWork() throws InterruptedException {
        when(mockSagaFactory.createSaga(isA(Class.class))).thenReturn(mockSaga1);
        reset(mockSagaRepository);
        final AtomicInteger counter = new AtomicInteger(0);
        when(mockSagaRepository.find(isA(Class.class), isA(AssociationValue.class)))
                .thenAnswer(new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                        return counter.incrementAndGet() == 1 ? emptySet() : singleton("saga1");
                    }
                });
        final AtomicBoolean added = new AtomicBoolean(false);
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                added.set(true);
                return null;
            }
        }).when(mockSagaRepository).add(isA(Saga.class));

        when(mockSagaRepository.load("saga1")).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                if (added.get()) {
                    return mockSaga1;
                }
                return null;
            }
        });
        sagaCreationPolicy = SagaCreationPolicy.IF_NONE_FOUND;
        final AtomicInteger nestingCounter = new AtomicInteger(20);
        final EventMessage event = GenericEventMessage.asEventMessage(new Object());
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                if (nestingCounter.decrementAndGet() > 0) {
                    UnitOfWork uow = DefaultUnitOfWork.startAndGet();
                    try {
                        testSubject.handle(event);
                    } finally {
                        uow.commit();
                    }
                }
                return null;
            }
        }
        ).when(mockSaga1).handle(isA(EventMessage.class));

        testSubject.handle(event);

        verify(mockSagaRepository).add(mockSaga1);
        verify(mockSagaRepository, times(19)).commit(mockSaga1);
    }


    @SuppressWarnings({"unchecked"})
    private <T> Set<T> setOf(T... items) {
        return ListOrderedSet.decorate(Arrays.asList(items));
    }
}