/*
 * Copyright (c) 2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.saga;

import org.apache.commons.collections.set.ListOrderedSet;
import org.axonframework.domain.Event;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.eventhandling.EventBus;
import org.junit.*;

import java.util.Arrays;
import java.util.Set;

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
        testSubject = new AbstractSagaManager(mockEventBus, mockSagaRepository, mockSagaFactory) {
            @Override
            protected Set<Saga> findSagas(Event event) {
                return setOf(mockSaga1, mockSaga2, mockSaga3);
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
        StubDomainEvent event = new StubDomainEvent();
        testSubject.handle(event);
        verify(mockSaga1).handle(event);
        verify(mockSaga2).handle(event);
        verify(mockSaga3, never()).handle(isA(Event.class));
        verify(mockSagaRepository).commit(mockSaga1);
        verify(mockSagaRepository).commit(mockSaga2);
        verify(mockSagaRepository, never()).commit(mockSaga3);
    }

    @Test
    public void testExceptionPropagated() {
        testSubject.setSuppressExceptions(false);
        StubDomainEvent event = new StubDomainEvent();
        doThrow(new RuntimeException("Mock")).when(mockSaga1).handle(event);
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
        StubDomainEvent event = new StubDomainEvent();
        doThrow(new RuntimeException("Mock")).when(mockSaga1).handle(event);

        testSubject.handle(event);

        verify(mockSaga1).handle(event);
        verify(mockSaga2).handle(event);
        verify(mockSagaRepository).commit(mockSaga1);
        verify(mockSagaRepository).commit(mockSaga2);
    }

    @SuppressWarnings({"unchecked"})
    private <T> Set<T> setOf(T... items) {
        return ListOrderedSet.decorate(Arrays.asList(items));
    }
}
