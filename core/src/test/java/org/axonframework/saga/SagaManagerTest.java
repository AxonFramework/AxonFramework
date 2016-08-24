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
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.SimpleCluster;
import org.axonframework.eventhandling.replay.DiscardingIncomingMessageHandler;
import org.axonframework.eventhandling.replay.ReplayAware;
import org.axonframework.eventhandling.replay.ReplayFailedException;
import org.axonframework.eventhandling.replay.ReplayingCluster;
import org.axonframework.eventstore.management.EventStoreManagement;
import org.axonframework.saga.annotation.AssociationValuesImpl;
import org.axonframework.testutils.MockException;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.NoTransactionManager;
import org.axonframework.unitofwork.UnitOfWork;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.singleton;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
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

    @After
    public void cleanupCurrentUnitOfWork() {
        if (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.clear(CurrentUnitOfWork.get());
            cleanupCurrentUnitOfWork();
        }
    }

    @Deprecated
    @Test
    public void testSubscriptionIsIgnoredWithoutEventBus() {
        testSubject.subscribe();
        verify(mockEventBus, never()).subscribe(testSubject);
        testSubject.unsubscribe();
        verify(mockEventBus, never()).unsubscribe(testSubject);
    }

    @Deprecated
    @Test
    public void testSubscription() {
        testSubject = new TestableAbstractSagaManager(mockEventBus, mockSagaRepository, mockSagaFactory, Saga.class);
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
    public void testSagasInCreationShouldBeClearedAfterRollback() throws InterruptedException {
        //given saga manager with with saga throwing exception when being created
        when(mockSagaFactory.createSaga(isA(Class.class))).thenReturn(mockSaga1);
        reset(mockSagaRepository);
        when(mockSagaRepository.find(isA(Class.class), isA(AssociationValue.class)))
                .thenReturn(Collections.<String>emptySet());
        sagaCreationPolicy = SagaCreationPolicy.IF_NONE_FOUND;
        final EventMessage event = GenericEventMessage.asEventMessage(new Object());
        DefaultUnitOfWork.startAndGet();
        doThrow(new RuntimeException()).doNothing().when(mockSaga1).handle(eq(event));

        testSubject.handle(event);

        //we want to clear saga manager sagasInCreation map
        CurrentUnitOfWork.get().rollback();

        //no saga should be created till now and no saga should be created
        sagaCreationPolicy = SagaCreationPolicy.NONE;

        //when handling event for second time but with NONE creation policy and no exception
        doNothing().when(mockSaga1).handle(eq(event));
        testSubject.handle(event);

        //then - second event shouldn't be handled to saga - just the first event should be handled
        verify(mockSaga1, times(1)).handle(eq(event));
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
    public synchronized void testAccessToSagaWhileInCreation() throws InterruptedException {
        when(mockSagaFactory.createSaga(isA(Class.class))).thenReturn(mockSaga1);
        reset(mockSagaRepository);
        when(mockSagaRepository.find(isA(Class.class), isA(AssociationValue.class)))
                .thenReturn(Collections.<String>emptySet())
                .thenReturn(singleton("saga1"));

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
        }).when(mockSaga1).handle(isA(EventMessage.class));

        testSubject.handle(event);

        verify(mockSagaRepository).add(mockSaga1);
        verify(mockSagaRepository, times(19)).commit(mockSaga1);

        verify(mockSagaRepository, never()).load("saga1");
    }

    @Test
    public void testAttemptSagaReplay() {
        ReplayValidator validator = mock(ReplayValidator.class);
        ReplayingCluster cluster = new ReplayingCluster(
                new SimpleCluster("Cluster"), mock(EventStoreManagement.class), new NoTransactionManager(), 10,
                new DiscardingIncomingMessageHandler());
        cluster.subscribe(testSubject);
        cluster.subscribe(validator);
        try {
            cluster.startReplay();
            fail("Replay should have failed");
        } catch (ReplayFailedException ignored) {
        }
        verify(validator).onReplayFailed(any(Throwable.class));
    }

    @Test
    public void testSagaReplayAllowed() {
        testSubject.setReplayable(true);
        ReplayValidator validator = mock(ReplayValidator.class);
        ReplayingCluster cluster = new ReplayingCluster(
                new SimpleCluster("Cluster"), mock(EventStoreManagement.class), new NoTransactionManager(), 10,
                new DiscardingIncomingMessageHandler());
        cluster.subscribe(testSubject);
        cluster.subscribe(validator);
        cluster.startReplay();
        verify(validator, never()).onReplayFailed(any(Throwable.class));
    }

    @SuppressWarnings({"unchecked"})
    private <T> Set<T> setOf(T... items) {
        return ListOrderedSet.decorate(Arrays.asList(items));
    }

    private interface ReplayValidator extends ReplayAware, EventListener {

    }

    private class TestableAbstractSagaManager extends AbstractSagaManager {

        private TestableAbstractSagaManager(SagaRepository sagaRepository, SagaFactory sagaFactory,
                                            Class<? extends Saga>... sagaTypes) {
            super(sagaRepository, sagaFactory, sagaTypes);
        }

        private TestableAbstractSagaManager(EventBus eventBus, SagaRepository sagaRepository,
                                            SagaFactory sagaFactory,
                                            Class<? extends Saga>... sagaTypes) {
            super(eventBus, sagaRepository, sagaFactory, sagaTypes);
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
