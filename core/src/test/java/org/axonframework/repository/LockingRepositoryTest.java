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

package org.axonframework.repository;

import org.axonframework.common.lock.Lock;
import org.axonframework.common.lock.LockManager;
import org.axonframework.common.lock.OptimisticLockManager;
import org.axonframework.common.lock.PessimisticLockManager;
import org.axonframework.domain.StubAggregate;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.DomainEventMessage;
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

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class LockingRepositoryTest {

    private LockingRepository<StubAggregate> testSubject;
    private EventBus mockEventBus;
    private LockManager lockManager;
    private Lock lock;
    private static final Message<?> MESSAGE = new GenericMessage<Object>("test");

    @Before
    public void setUp() {
        mockEventBus = mock(EventBus.class);
        lockManager = spy(new OptimisticLockManager());
        when(lockManager.obtainLock(anyString()))
                        .thenAnswer(invocation -> lock = spy((Lock) invocation.callRealMethod()));
        testSubject = new InMemoryLockingRepository(lockManager);
        testSubject.setEventBus(mockEventBus);
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
    public void testStoreNewAggregate() {
        startAndGetUnitOfWork();
        StubAggregate aggregate = new StubAggregate();
        aggregate.doSomething();
        testSubject.add(aggregate);
        CurrentUnitOfWork.commit();

        verify(lockManager).obtainLock(aggregate.getIdentifier());
        verify(mockEventBus).publish(isA(DomainEventMessage.class));
    }

    @Test
    public void testLoadAndStoreAggregate() {
        startAndGetUnitOfWork();
        StubAggregate aggregate = new StubAggregate();
        aggregate.doSomething();
        testSubject.add(aggregate);
        verify(lockManager).obtainLock(aggregate.getIdentifier());
        CurrentUnitOfWork.commit();
        verify(lock).release();
        reset(lockManager);

        startAndGetUnitOfWork();
        StubAggregate loadedAggregate = testSubject.load(aggregate.getIdentifier(), 0L);
        verify(lockManager).obtainLock(aggregate.getIdentifier());

        loadedAggregate.doSomething();
        CurrentUnitOfWork.commit();

        verify(lockManager, atLeastOnce()).validateLock(loadedAggregate.getIdentifier());
        verify(mockEventBus, times(2)).publish(any(DomainEventMessage.class));
        verify(lock).release();
    }

    @Test
    public void testLoadAndStoreAggregate_LockReleasedOnException() {
        startAndGetUnitOfWork();
        StubAggregate aggregate = new StubAggregate();

        aggregate.doSomething();
        testSubject.add(aggregate);
        verify(lockManager).obtainLock(aggregate.getIdentifier());
        CurrentUnitOfWork.commit();
        verify(lock).release();
        reset(lockManager);

        startAndGetUnitOfWork();
        StubAggregate loadedAggregate = testSubject.load(aggregate.getIdentifier(), 0L);
        verify(lockManager).obtainLock(aggregate.getIdentifier());

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
    public void testLoadAndStoreAggregate_PessimisticLockReleasedOnException() {
        lockManager = spy(new PessimisticLockManager());
        testSubject = new InMemoryLockingRepository(lockManager);
        testSubject.setEventBus(mockEventBus);
        testSubject = spy(testSubject);

        // we do the same test, but with a pessimistic lock, which has a different way of "re-acquiring" a lost lock
        startAndGetUnitOfWork();
        StubAggregate aggregate = new StubAggregate();
        when(lockManager.obtainLock(aggregate.getIdentifier()))
                        .thenAnswer(invocation -> lock = spy((Lock) invocation.callRealMethod()));
        aggregate.doSomething();
        testSubject.add(aggregate);
        verify(lockManager).obtainLock(aggregate.getIdentifier());
        CurrentUnitOfWork.commit();
        verify(lock).release();
        reset(lockManager);

        startAndGetUnitOfWork();
        StubAggregate loadedAggregate = testSubject.load(aggregate.getIdentifier(), 0L);
        verify(lockManager).obtainLock(aggregate.getIdentifier());

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
    public void testSaveAggregate_RefusedDueToLackingLock() {
        lockManager = spy(new PessimisticLockManager());
        testSubject = new InMemoryLockingRepository(lockManager);
        testSubject.setEventBus(mockEventBus);
        testSubject = spy(testSubject);

        startAndGetUnitOfWork();
        StubAggregate aggregate = new StubAggregate();
        aggregate.doSomething();
        testSubject.add(aggregate);
        CurrentUnitOfWork.commit();

        startAndGetUnitOfWork();
        StubAggregate loadedAggregate = testSubject.load(aggregate.getIdentifier(), 0L);
        loadedAggregate.doSomething();
        CurrentUnitOfWork.commit();

        // this tricks the UnitOfWork to save this aggregate, without loading it.
        startAndGetUnitOfWork();
        CurrentUnitOfWork.get().onPrepareCommit(s -> testSubject.doSave(loadedAggregate));
        loadedAggregate.doSomething();
        try {
            CurrentUnitOfWork.commit();
            fail("This should have failed due to lacking lock");
        } catch (ConcurrencyException e) {
            // that's ok
        }
    }

    private UnitOfWork startAndGetUnitOfWork() {
        UnitOfWork uow = DefaultUnitOfWork.startAndGet(MESSAGE);
        uow.resources().put(EventBus.KEY, mockEventBus);
        return uow;
    }

    static class InMemoryLockingRepository extends LockingRepository<StubAggregate> {

        private Map<Object, StubAggregate> store = new HashMap<>();
        private int saveCount;

        public InMemoryLockingRepository(LockManager lockManager) {
            super(StubAggregate.class, lockManager);
        }

        @Override
        protected void doSaveWithLock(StubAggregate aggregate) {
            store.put(aggregate.getIdentifier(), aggregate);
            saveCount++;
        }

        @Override
        protected void doDeleteWithLock(StubAggregate aggregate) {
            store.remove(aggregate.getIdentifier());
            saveCount++;
        }

        @Override
        protected StubAggregate doLoad(String aggregateIdentifier, Long expectedVersion) {
            return store.get(aggregateIdentifier);
        }

        public int getSaveCount() {
            return saveCount;
        }

        public void resetSaveCount() {
            saveCount = 0;
        }
    }
}
