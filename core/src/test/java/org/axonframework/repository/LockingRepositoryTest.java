/*
 * Copyright (c) 2010-2011. Axon Framework
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

import org.axonframework.domain.AggregateRoot;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.StubAggregate;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.SaveAggregateCallback;
import org.axonframework.unitofwork.UnitOfWorkListenerAdapter;
import org.junit.*;
import org.mockito.*;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class LockingRepositoryTest {

    private LockingRepository<StubAggregate> testSubject;
    private EventBus mockEventBus;
    private LockManager lockManager;

    @Before
    public void setUp() {
        mockEventBus = mock(EventBus.class);
        lockManager = spy(new OptimisticLockManager());
        testSubject = new InMemoryLockingRepository(lockManager);
        testSubject.setEventBus(mockEventBus);
        testSubject = spy(testSubject);

        // some UoW is started somewhere, but not shutdown in the same test.
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    public void testStoreNewAggregate() {
        DefaultUnitOfWork.startAndGet();
        StubAggregate aggregate = new StubAggregate();
        aggregate.doSomething();
        testSubject.add(aggregate);
        CurrentUnitOfWork.commit();

        verify(lockManager).obtainLock(aggregate.getIdentifier());
        verify(mockEventBus).publish(isA(DomainEventMessage.class));
    }

    @Test
    public void testLoadAndStoreAggregate() {
        DefaultUnitOfWork.startAndGet();
        StubAggregate aggregate = new StubAggregate();
        aggregate.doSomething();
        testSubject.add(aggregate);
        verify(lockManager).obtainLock(aggregate.getIdentifier());
        CurrentUnitOfWork.commit();
        verify(lockManager).releaseLock(aggregate.getIdentifier());
        reset(lockManager);

        DefaultUnitOfWork.startAndGet();
        StubAggregate loadedAggregate = testSubject.load(aggregate.getIdentifier(), 0L);
        verify(lockManager).obtainLock(aggregate.getIdentifier());

        loadedAggregate.doSomething();
        CurrentUnitOfWork.commit();

        InOrder inOrder = inOrder(lockManager);
        inOrder.verify(lockManager, atLeastOnce()).validateLock(loadedAggregate);
        verify(mockEventBus, times(2)).publish(any(DomainEventMessage.class));
        inOrder.verify(lockManager).releaseLock(loadedAggregate.getIdentifier());
    }

    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    @Test
    public void testLoadAndStoreAggregate_LockReleasedOnException() {
        DefaultUnitOfWork.startAndGet();
        StubAggregate aggregate = new StubAggregate();
        aggregate.doSomething();
        testSubject.add(aggregate);
        verify(lockManager).obtainLock(aggregate.getIdentifier());
        CurrentUnitOfWork.commit();
        verify(lockManager).releaseLock(aggregate.getIdentifier());
        reset(lockManager);

        DefaultUnitOfWork.startAndGet();
        StubAggregate loadedAggregate = testSubject.load(aggregate.getIdentifier(), 0L);
        verify(lockManager).obtainLock(aggregate.getIdentifier());

        CurrentUnitOfWork.get().registerListener(new UnitOfWorkListenerAdapter() {
            @Override
            public void onPrepareCommit(Set<AggregateRoot> aggregateRoots, List<EventMessage> events) {
                throw new RuntimeException("Mock Exception");
            }
        });
        try {
            CurrentUnitOfWork.commit();
            fail("Expected exception to be thrown");
        } catch (RuntimeException e) {
            assertEquals("Mock Exception", e.getMessage());
        }

        // make sure the lock is released
        verify(lockManager).releaseLock(loadedAggregate.getIdentifier());
    }

    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    @Test
    public void testLoadAndStoreAggregate_PessimisticLockReleasedOnException() {
        lockManager = spy(new PessimisticLockManager());
        testSubject = new InMemoryLockingRepository(lockManager);
        testSubject.setEventBus(mockEventBus);
        testSubject = spy(testSubject);

        // we do the same test, but with a pessimistic lock, which has a different way of "re-acquiring" a lost lock
        DefaultUnitOfWork.startAndGet();
        StubAggregate aggregate = new StubAggregate();
        aggregate.doSomething();
        testSubject.add(aggregate);
        verify(lockManager).obtainLock(aggregate.getIdentifier());
        CurrentUnitOfWork.commit();
        verify(lockManager).releaseLock(aggregate.getIdentifier());
        reset(lockManager);

        DefaultUnitOfWork.startAndGet();
        StubAggregate loadedAggregate = testSubject.load(aggregate.getIdentifier(), 0L);
        verify(lockManager).obtainLock(aggregate.getIdentifier());

        CurrentUnitOfWork.get().registerListener(new UnitOfWorkListenerAdapter() {
            @Override
            public void onPrepareCommit(Set<AggregateRoot> aggregateRoots, List<EventMessage> events) {
                throw new RuntimeException("Mock Exception");
            }
        });

        try {
            CurrentUnitOfWork.commit();
            fail("Expected exception to be thrown");
        } catch (RuntimeException e) {
            assertEquals("Mock Exception", e.getMessage());
        }

        // make sure the lock is released
        verify(lockManager).releaseLock(loadedAggregate.getIdentifier());
    }

    @Test
    public void testSaveAggregate_RefusedDueToLackingLock() {
        lockManager = spy(new PessimisticLockManager());
        testSubject = new InMemoryLockingRepository(lockManager);
        testSubject.setEventBus(mockEventBus);
        testSubject = spy(testSubject);
        EventBus eventBus = mock(EventBus.class);

        DefaultUnitOfWork.startAndGet();
        StubAggregate aggregate = new StubAggregate();
        aggregate.doSomething();
        testSubject.add(aggregate);
        CurrentUnitOfWork.commit();

        DefaultUnitOfWork.startAndGet();
        StubAggregate loadedAggregate = testSubject.load(aggregate.getIdentifier(), 0L);
        loadedAggregate.doSomething();
        CurrentUnitOfWork.commit();

        // this tricks the UnitOfWork to save this aggregate, without loading it.
        DefaultUnitOfWork.startAndGet();
        CurrentUnitOfWork.get().registerAggregate(loadedAggregate,
                                                  eventBus,
                                                  new SaveAggregateCallback<StubAggregate>() {
                                                      @Override
                                                      public void save(StubAggregate aggregate) {
                                                          testSubject.doSave(aggregate);
                                                      }
                                                  });
        loadedAggregate.doSomething();
        try {
            CurrentUnitOfWork.commit();
            fail("This should have failed due to lacking lock");
        } catch (ConcurrencyException e) {
            // that's ok
        }
    }
}
