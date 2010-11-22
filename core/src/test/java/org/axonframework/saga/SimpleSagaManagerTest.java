/*
 * Copyright (c) 2010. Axon Framework
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

import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.Event;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.eventhandling.EventBus;
import org.junit.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class SimpleSagaManagerTest {

    private SimpleSagaManager testSubject;
    private SagaRepository repository;
    private Event event = new StubDomainEvent();
    private Saga saga1;
    private Saga sagaFromFactory;
    private Set<Saga> sagasFromRepository;
    private EventBus eventBus;

    @Before
    public void setUp() throws Exception {
        repository = mock(SagaRepository.class);
        SagaLookupPropertyResolver lookupPropertyResolver = mock(SagaLookupPropertyResolver.class);
        SagaFactory sagaFactory = mock(SagaFactory.class);
        eventBus = mock(EventBus.class);
        testSubject = new SimpleSagaManager(Saga.class, repository, lookupPropertyResolver, sagaFactory, eventBus);
        sagasFromRepository = new HashSet<Saga>();

        saga1 = mock(Saga.class);
        sagasFromRepository.add(saga1);
        when(lookupPropertyResolver.extractLookupProperty(event)).thenReturn(new SagaLookupProperty("key", "val"));
        when(repository.find(eq(Saga.class), eq(new SagaLookupProperty("key", "val")))).thenReturn(sagasFromRepository);
        sagaFromFactory = mock(Saga.class);
        when(sagaFactory.createSaga(Saga.class)).thenReturn(sagaFromFactory);
    }

    @Test
    public void testSagaAlwaysCreatedOnEvent() {
        testSubject.setEventsToAlwaysCreateNewSagasFor(Arrays.<Class<? extends Event>>asList(DomainEvent.class));
        Set<Saga> actualResult = testSubject.findSagas(event);
        assertEquals(2, actualResult.size());
        assertTrue(actualResult.contains(saga1));
        assertTrue(actualResult.contains(sagaFromFactory));
    }

    @Test
    public void testSagaOptionallyCreatedOnEvent_SagasExist() {
        testSubject.setEventsToOptionallyCreateNewSagasFor(Arrays.<Class<? extends Event>>asList(DomainEvent.class));
        Set<Saga> actualResult = testSubject.findSagas(event);
        assertEquals(1, actualResult.size());
        assertTrue(actualResult.contains(saga1));
        assertFalse(actualResult.contains(sagaFromFactory));
    }

    @Test
    public void testSagaOptionallyCreatedOnEvent_NoSagaFound() {
        sagasFromRepository.clear();
        testSubject.setEventsToOptionallyCreateNewSagasFor(Arrays.<Class<? extends Event>>asList(DomainEvent.class));
        Set<Saga> actualResult = testSubject.findSagas(event);
        assertEquals(1, actualResult.size());
        assertFalse(actualResult.contains(saga1));
        assertTrue(actualResult.contains(sagaFromFactory));
    }

    @Test
    public void testCommit() {
        testSubject.commit(sagasFromRepository);

        verify(repository).commit(saga1);
    }

    @Test
    public void testAllSagasAreInvoked() {
        Event event = new StubDomainEvent();
        Set<Saga> sagas = new HashSet<Saga>();
        final Saga saga1 = mock(Saga.class);
        final Saga saga2 = mock(Saga.class);
        sagas.add(saga1);
        sagas.add(saga2);
        when(testSubject.findSagas(event)).thenReturn(sagas);
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

}
