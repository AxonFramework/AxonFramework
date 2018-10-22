/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.modelling.saga.repository;

import org.axonframework.common.IdentifierFactory;
import org.axonframework.modelling.saga.AssociationValue;
import org.axonframework.modelling.saga.Saga;
import org.axonframework.modelling.saga.repository.inmemory.InMemorySagaStore;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.*;
import org.mockito.*;

import java.util.HashSet;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.axonframework.messaging.unitofwork.DefaultUnitOfWork.startAndGet;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
public class AnnotatedSagaRepositoryTest {

    private AnnotatedSagaRepository<Object> testSubject;
    private SagaStore store;

    private UnitOfWork<?> currentUnitOfWork;

    @Before
    public void setUp() {
        currentUnitOfWork = startAndGet(null);
        this.store = spy(new InMemorySagaStore());
        this.testSubject = AnnotatedSagaRepository.builder().sagaType(Object.class).sagaStore(store).build();
    }

    @After
    public void tearDown() {
        if (currentUnitOfWork.isActive()) {
            currentUnitOfWork.commit();
        }
    }

    @Test
    public void testLoadedFromUnitOfWorkAfterCreate() {
        Saga<Object> saga =
                testSubject.createInstance(IdentifierFactory.getInstance().generateIdentifier(), Object::new);
        saga.getAssociationValues().add(new AssociationValue("test", "value"));

        Saga<Object> saga2 = testSubject.load(saga.getSagaIdentifier());

        assertSame(saga, saga2);
        currentUnitOfWork.commit();
        verify(store, never()).loadSaga(any(), any());
        verify(store, never()).updateSaga(any(), any(), any(), any());
        verify(store).insertSaga(eq(Object.class), any(), any(), any());
    }

    @Test
    public void testLoadedFromNestedUnitOfWorkAfterCreate() throws Exception {
        Saga<Object> saga =
                testSubject.createInstance(IdentifierFactory.getInstance().generateIdentifier(), Object::new);
        saga.getAssociationValues().add(new AssociationValue("test", "value"));
        Saga<Object> saga2 =
                startAndGet(null).executeWithResult(() -> testSubject.load(saga.getSagaIdentifier()))
                .getPayload();

        assertSame(saga, saga2);
        currentUnitOfWork.commit();
        verify(store, never()).loadSaga(any(), any());
        verify(store, never()).updateSaga(any(), any(), any(), any());
        verify(store).insertSaga(eq(Object.class), any(), any(), anySet());
    }

    @Test
    public void testLoadedFromNestedUnitOfWorkAfterCreateAndStore() {
        Saga<Object> saga =
                testSubject.createInstance(IdentifierFactory.getInstance().generateIdentifier(), Object::new);
        saga.getAssociationValues().add(new AssociationValue("test", "value"));
        currentUnitOfWork.onPrepareCommit(u -> startAndGet(null).execute(() -> {
            Saga<Object> saga1 = testSubject.load(saga.getSagaIdentifier());
            saga1.getAssociationValues().add(new AssociationValue("second", "value"));
        }));

        currentUnitOfWork.commit();
        InOrder inOrder = inOrder(store);
        Set<AssociationValue> associationValues = new HashSet<>();
        associationValues.add(new AssociationValue("test", "value"));
        associationValues.add(new AssociationValue("second", "value"));
        inOrder.verify(store).insertSaga(eq(Object.class), any(), any(), eq(associationValues));
        inOrder.verify(store).updateSaga(eq(Object.class), any(), any(), any());
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testLoadedFromUnitOfWorkAfterPreviousLoad() {
        Saga<Object> preparedSaga =
                testSubject.createInstance(IdentifierFactory.getInstance().generateIdentifier(), Object::new);
        currentUnitOfWork.commit();
        currentUnitOfWork = startAndGet(null);
        reset(store);

        Saga<Object> saga = testSubject.load(preparedSaga.getSagaIdentifier());
        saga.getAssociationValues().add(new AssociationValue("test", "value"));

        Saga<Object> saga2 = testSubject.load(preparedSaga.getSagaIdentifier());

        assertSame(saga, saga2);
        verify(store).loadSaga(eq(Object.class), any());
        verify(store, never()).updateSaga(eq(Object.class), any(), any(), any());

        currentUnitOfWork.commit();

        verify(store).updateSaga(eq(Object.class), any(), any(), any());
        verify(store, never()).insertSaga(eq(Object.class), any(), any(), any());
    }

    @Test
    public void testSagaAssociationsVisibleInOtherThreadsBeforeSagaIsCommitted() throws Exception {
        String sagaId = "sagaId";
        AssociationValue associationValue = new AssociationValue("test", "value");

        Thread otherProcess = new Thread(() -> {
            UnitOfWork<?> unitOfWork = DefaultUnitOfWork.startAndGet(null);
            testSubject.createInstance(sagaId, Object::new).getAssociationValues().add(associationValue);
            CurrentUnitOfWork.clear(unitOfWork);
        });
        otherProcess.start();
        otherProcess.join();

        assertEquals(singleton(sagaId), testSubject.find(associationValue));
    }
}
