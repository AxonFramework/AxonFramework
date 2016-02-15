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

import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.saga.annotation.AssociationValuesImpl;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.junit.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.mockito.AdditionalMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class SimpleSagaManagerTest {

    private SimpleSagaManager testSubject;
    private SagaRepository repository;
    private EventMessage event = new GenericEventMessage<Object>(new Object());
    private Saga saga1;
    private EventBus eventBus;
    private AssociationValue associationValue;

    @Before
    public void setUp() throws Exception {
        repository = mock(SagaRepository.class);
        AssociationValueResolver associationValueResolver = mock(AssociationValueResolver.class);
        SagaFactory sagaFactory = mock(SagaFactory.class);
        eventBus = mock(EventBus.class);
        testSubject = new SimpleSagaManager(Saga.class, repository, associationValueResolver, sagaFactory, eventBus);
        Set<String> sagasFromRepository = new HashSet<String>();

        saga1 = mock(Saga.class);
        when(saga1.getSagaIdentifier()).thenReturn("saga1");
        sagasFromRepository.add("saga1");
        when(repository.load("saga1")).thenReturn(saga1);
        associationValue = new AssociationValue("key", "val");
        when(associationValueResolver.extractAssociationValues(isA(EventMessage.class)))
                .thenReturn(singleton(associationValue));
        when(repository.find(eq(Saga.class), eq(associationValue))).thenReturn(sagasFromRepository);
        Saga sagaFromFactory = mock(Saga.class);
        when(sagaFromFactory.getSagaIdentifier()).thenReturn("sagaFromFactory");
        final AssociationValuesImpl associationValues = new AssociationValuesImpl();
        when(sagaFromFactory.getAssociationValues()).thenReturn(associationValues);
        when(sagaFactory.createSaga(isA(Class.class))).thenReturn(sagaFromFactory);
    }

    @Test
    public void testSagaAlwaysCreatedOnEvent() {
        testSubject.setEventsToAlwaysCreateNewSagasFor(Arrays.<Class<?>>asList(Object.class));
        testSubject.handle(event);

        verify(repository).add(isA(Saga.class));
        verify(repository, never()).commit(not(eq(saga1)));
    }

    @Test
    public void testSagaOptionallyCreatedOnEvent_SagasExist() {
        testSubject.setEventsToOptionallyCreateNewSagasFor(Arrays.<Class<?>>asList(Object.class));

        activate(saga1);
        testSubject.handle(event);

        verify(repository).load("saga1");
        verify(repository).commit(saga1);
        verify(repository, never()).add(isA(Saga.class));
        verify(repository, never()).commit(not(eq(saga1)));
    }

    private void activate(Saga saga) {
        when(saga.isActive()).thenReturn(true);
        final AssociationValuesImpl value = new AssociationValuesImpl();
        value.add(associationValue);
        when(saga.getAssociationValues()).thenReturn(value);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSagaOptionallyCreatedOnEvent_NoSagaFound() {
        when(repository.find(isA(Class.class), isA(AssociationValue.class))).thenReturn(Collections.<String>emptySet());
        testSubject.setEventsToOptionallyCreateNewSagasFor(Arrays.<Class<?>>asList(Object.class));
        testSubject.handle(event);

        verify(repository, never()).load("saga1");
        verify(repository).add(isA(Saga.class));
        verify(repository, never()).commit(not(eq(saga1)));
    }

    @Test
    public void testCommit() {
        testSubject.commit(saga1);

        verify(repository).commit(saga1);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testAllSagasAreInvoked() {
        EventMessage event = new GenericEventMessage<Object>(new Object());
        final Saga saga1 = mock(Saga.class);
        final Saga saga2 = mock(Saga.class);
        when(saga1.getSagaIdentifier()).thenReturn("saga1");
        when(saga2.getSagaIdentifier()).thenReturn("saga2");
        activate(saga1);
        activate(saga2);
        when(repository.find(isA(Class.class), isA(AssociationValue.class)))
                .thenReturn(new HashSet<String>(Arrays.asList("saga1", "saga2")));
        when(repository.load("saga1")).thenReturn(saga1);
        when(repository.load("saga2")).thenReturn(saga2);

        testSubject.handle(event);
        verify(saga1, times(1)).handle(event);
        verify(saga2, times(1)).handle(event);
        verify(repository).commit(saga1);
        verify(repository).commit(saga2);
    }

    @Test
    public void testSubscribeAndUnsubscribeFromEventBus() {
        testSubject.subscribe();
        verify(eventBus).subscribe(testSubject);
        testSubject.unsubscribe();
        verify(eventBus).unsubscribe(testSubject);
    }

    @Test
    public void testSagaInstanceIsReusedInUnitOfWork() {
        UnitOfWork unitOfWork = DefaultUnitOfWork.startAndGet();
        try {
            activate(saga1);
            testSubject.handle(event);
            testSubject.handle(event);

            verify(repository).load("saga1");
            verify(saga1, times(2)).handle(event);
        } finally {
            unitOfWork.commit();
        }
    }
}
