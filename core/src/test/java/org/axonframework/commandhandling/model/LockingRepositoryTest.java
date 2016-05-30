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

package org.axonframework.commandhandling.model;

import org.axonframework.commandhandling.StubAggregate;
import org.axonframework.commandhandling.model.inspection.AggregateModel;
import org.axonframework.commandhandling.model.inspection.EventSourcedAggregate;
import org.axonframework.commandhandling.model.inspection.ModelInspector;
import org.axonframework.common.lock.Lock;
import org.axonframework.common.lock.LockFactory;
import org.axonframework.common.lock.PessimisticLockFactory;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class LockingRepositoryTest {

    private Repository<StubAggregate> testSubject;
    private EventStore mockEventStore;
    private LockFactory lockFactory;
    private Lock lock;
    private static final Message<?> MESSAGE = new GenericMessage<Object>("test");

    @Before
    public void setUp() {
        mockEventStore = mock(EventStore.class);
        lockFactory = spy(new PessimisticLockFactory());
        when(lockFactory.obtainLock(anyString()))
                        .thenAnswer(invocation -> lock = spy((Lock) invocation.callRealMethod()));
        testSubject = new InMemoryLockingRepository(lockFactory, mockEventStore);
        testSubject = spy(testSubject);
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @After
    public void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    public void testStoreNewAggregate() throws Exception {
        startAndGetUnitOfWork();
        StubAggregate aggregate = new StubAggregate();
        testSubject.newInstance(() -> aggregate).execute(StubAggregate::doSomething);
        CurrentUnitOfWork.commit();

        verify(lockFactory).obtainLock(aggregate.getIdentifier());
        verify(mockEventStore).publish(isA(DomainEventMessage.class));
    }

    @Test
    public void testLoadAndStoreAggregate() throws Exception {
        startAndGetUnitOfWork();
        StubAggregate aggregate = new StubAggregate();
        testSubject.newInstance(() -> aggregate).execute(StubAggregate::doSomething);
        verify(lockFactory).obtainLock(aggregate.getIdentifier());
        CurrentUnitOfWork.commit();
        verify(lock).release();
        reset(lockFactory);

        startAndGetUnitOfWork();
        Aggregate<StubAggregate> loadedAggregate = testSubject.load(aggregate.getIdentifier(), 0L);
        verify(lockFactory).obtainLock(aggregate.getIdentifier());

        loadedAggregate.execute(StubAggregate::doSomething);
        CurrentUnitOfWork.commit();

        verify(mockEventStore, times(2)).publish(any(DomainEventMessage.class));
        verify(lock).release();
    }

    @Test
    public void testLoadAndStoreAggregate_LockReleasedOnException() throws Exception {
        startAndGetUnitOfWork();
        StubAggregate aggregate = new StubAggregate();

        testSubject.newInstance(() -> aggregate).execute(StubAggregate::doSomething);
        verify(lockFactory).obtainLock(aggregate.getIdentifier());
        CurrentUnitOfWork.commit();
        verify(lock).release();
        reset(lockFactory);

        startAndGetUnitOfWork();
        testSubject.load(aggregate.getIdentifier(), 0L);
        verify(lockFactory).obtainLock(aggregate.getIdentifier());

        CurrentUnitOfWork.get().onPrepareCommit(u -> {
            throw new RuntimeException("Mock Exception");
        });
        try {
            CurrentUnitOfWork.commit();
            fail("Expected exception to be thrown");
        } catch (RuntimeException e) {
            assertEquals("Mock Exception", e.getMessage());
        }

        // make sure the lock is released
        verify(lock).release();
    }

    @Test
    public void testLoadAndStoreAggregate_PessimisticLockReleasedOnException() throws Exception {
        lockFactory = spy(new PessimisticLockFactory());
        testSubject = new InMemoryLockingRepository(lockFactory, mockEventStore);
        testSubject = spy(testSubject);

        // we do the same test, but with a pessimistic lock, which has a different way of "re-acquiring" a lost lock
        startAndGetUnitOfWork();
        StubAggregate aggregate = new StubAggregate();
        when(lockFactory.obtainLock(aggregate.getIdentifier()))
                        .thenAnswer(invocation -> lock = spy((Lock) invocation.callRealMethod()));
        testSubject.newInstance(() -> aggregate).execute(StubAggregate::doSomething);
        verify(lockFactory).obtainLock(aggregate.getIdentifier());
        CurrentUnitOfWork.commit();
        verify(lock).release();
        reset(lockFactory);

        startAndGetUnitOfWork();
        testSubject.load(aggregate.getIdentifier(), 0L);
        verify(lockFactory).obtainLock(aggregate.getIdentifier());

        CurrentUnitOfWork.get().onPrepareCommit(u -> {
            throw new RuntimeException("Mock Exception");
        });

        try {
            CurrentUnitOfWork.commit();
            fail("Expected exception to be thrown");
        } catch (RuntimeException e) {
            assertEquals("Mock Exception", e.getMessage());
        }

        // make sure the lock is released
        verify(lock).release();
    }

    private UnitOfWork<?> startAndGetUnitOfWork() {
        UnitOfWork<?> uow = DefaultUnitOfWork.startAndGet(MESSAGE);
        return uow;
    }

    private static class InMemoryLockingRepository extends LockingRepository<StubAggregate, Aggregate<StubAggregate>> {

        private final EventStore eventStore;
        private final AggregateModel<StubAggregate> aggregateModel;
        private Map<Object, Aggregate<StubAggregate>> store = new HashMap<>();
        private int saveCount;

        public InMemoryLockingRepository(LockFactory lockManager, EventStore eventStore) {
            super(StubAggregate.class, lockManager);
            this.eventStore = eventStore;
            aggregateModel = ModelInspector.inspectAggregate(StubAggregate.class);
        }

        @Override
        protected void doSaveWithLock(Aggregate<StubAggregate> aggregate) {
            store.put(aggregate.identifier(), aggregate);
            saveCount++;
        }

        @Override
        protected void doDeleteWithLock(Aggregate<StubAggregate> aggregate) {
            store.remove(aggregate.identifier());
            saveCount++;
        }

        @Override
        protected Aggregate<StubAggregate> doLoadWithLock(String aggregateIdentifier, Long expectedVersion) {
            return store.get(aggregateIdentifier);
        }

        @Override
        protected Aggregate<StubAggregate> doCreateNewForLock(Callable<StubAggregate> factoryMethod) throws Exception {
            return EventSourcedAggregate.initialize(factoryMethod, aggregateModel, eventStore);
        }

        public int getSaveCount() {
            return saveCount;
        }

        public void resetSaveCount() {
            saveCount = 0;
        }
    }
}
