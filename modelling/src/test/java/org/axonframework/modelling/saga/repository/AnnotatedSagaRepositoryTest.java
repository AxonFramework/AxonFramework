/*
 * Copyright (c) 2010-2025. Axon Framework
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
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.messaging.annotation.MessageHandlerInterceptorMemberChain;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyDefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.saga.AssociationValue;
import org.axonframework.modelling.saga.Saga;
import org.axonframework.modelling.saga.repository.inmemory.InMemorySagaStore;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.annotation.Nonnull;

import static java.util.Collections.singleton;
import static org.axonframework.messaging.unitofwork.LegacyDefaultUnitOfWork.startAndGet;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class AnnotatedSagaRepositoryTest {

    private AnnotatedSagaRepository<Object> testSubject;
    private SagaStore store;

    private LegacyUnitOfWork<?> currentUnitOfWork;

    @BeforeEach
    void setUp() {
        currentUnitOfWork = startAndGet(null);
        this.store = spy(new InMemorySagaStore());
        this.testSubject = AnnotatedSagaRepository.builder().sagaType(Object.class).sagaStore(store).build();
    }

    @AfterEach
    void tearDown() {
        if (currentUnitOfWork.isActive()) {
            currentUnitOfWork.commit();
        }
        CountingInterceptors.counter.set(0);
    }

    @Test
    void loadedFromUnitOfWorkAfterCreate() {
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
    void loadedFromNestedUnitOfWorkAfterCreate() throws Exception {
        Saga<Object> saga =
                testSubject.createInstance(IdentifierFactory.getInstance().generateIdentifier(), Object::new);
        saga.getAssociationValues().add(new AssociationValue("test", "value"));
        Saga<Object> saga2 =
                startAndGet(null).executeWithResult((ctx) -> testSubject.load(saga.getSagaIdentifier()))
                                 .payload();

        assertSame(saga, saga2);
        currentUnitOfWork.commit();
        verify(store, never()).loadSaga(any(), any());
        verify(store, never()).updateSaga(any(), any(), any(), any());
        verify(store).insertSaga(eq(Object.class), any(), any(), anySet());
    }

    @Test
    void loadedFromNestedUnitOfWorkAfterCreateAndStore() {
        Saga<Object> saga =
                testSubject.createInstance(IdentifierFactory.getInstance().generateIdentifier(), Object::new);
        saga.getAssociationValues().add(new AssociationValue("test", "value"));
        currentUnitOfWork.onPrepareCommit(u -> startAndGet(null).execute((ctx) -> {
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
    void loadedFromUnitOfWorkAfterPreviousLoad() {
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
    void sagaAssociationsVisibleInOtherThreadsBeforeSagaIsCommitted() throws Exception {
        String sagaId = "sagaId";
        AssociationValue associationValue = new AssociationValue("test", "value");

        Thread otherProcess = new Thread(() -> {
            LegacyUnitOfWork<?> unitOfWork = LegacyDefaultUnitOfWork.startAndGet(null);
            testSubject.createInstance(sagaId, Object::new).getAssociationValues().add(associationValue);
            CurrentUnitOfWork.clear(unitOfWork);
        });
        otherProcess.start();
        otherProcess.join();

        assertEquals(singleton(sagaId), testSubject.find(associationValue));
    }

    @Test
    void ifInterceptorSetThatOneShouldBeUsed() throws Exception {
        AnnotatedSagaRepository<TestSaga> sagaRepository = AnnotatedSagaRepository.<TestSaga>builder()
                                                                                  .sagaType(TestSaga.class)
                                                                                  .sagaStore(store)
                                                                                  .interceptorMemberChain(
                                                                                          CountingInterceptors.instance())
                                                                                  .build();
        Saga<TestSaga> saga =
                sagaRepository.createInstance(IdentifierFactory.getInstance().generateIdentifier(), TestSaga::new);
        EventMessage<Object> message = EventTestUtils.asEventMessage(new Object());
        saga.handleSync(message, StubProcessingContext.forMessage(message));

        assertEquals(1, CountingInterceptors.counter.get());
    }

    private static class CountingInterceptors implements MessageHandlerInterceptorMemberChain<TestSaga> {

        static AtomicInteger counter = new AtomicInteger(0);

        private static MessageHandlerInterceptorMemberChain<TestSaga> instance() {
            return new CountingInterceptors();
        }

        @Override
        public Object handleSync(@Nonnull Message<?> message,
                                 @Nonnull ProcessingContext context,
                                 @Nonnull TestSaga target,
                                 @Nonnull MessageHandlingMember<? super TestSaga> handler) throws Exception {
            counter.incrementAndGet();
            return handler.handleSync(message, context, target);
        }
    }

    private static class TestSaga {

        @EventHandler
        public void on(Object o) {

        }
    }
}
