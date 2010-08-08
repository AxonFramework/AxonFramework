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

package org.axonframework.repository;

import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.StubAggregate;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkListenerAdapter;
import org.junit.*;
import org.mockito.*;

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
    }

    @Test
    public void testStoreNewAggregate() {
        StubAggregate aggregate = new StubAggregate();
        aggregate.doSomething();
        testSubject.save(aggregate);
        verifyZeroInteractions(lockManager);
        verify(mockEventBus).publish(isA(DomainEvent.class));
    }

    @Test
    public void testLoadAndStoreAggregate() {
        StubAggregate aggregate = new StubAggregate();
        aggregate.doSomething();
        testSubject.save(aggregate);

        StubAggregate loadedAggregate = testSubject.load(aggregate.getIdentifier(), 0L);
        verify(lockManager).obtainLock(aggregate.getIdentifier());

        loadedAggregate.doSomething();
        testSubject.save(loadedAggregate);

        InOrder inOrder = inOrder(lockManager);
        inOrder.verify(lockManager, atLeastOnce()).validateLock(loadedAggregate);
        verify(mockEventBus, times(2)).publish(any(DomainEvent.class));
        inOrder.verify(lockManager).releaseLock(loadedAggregate.getIdentifier());
    }

    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    @Test
    public void testLoadAndStoreAggregate_LockReleasedOnException() {
        StubAggregate aggregate = new StubAggregate();
        aggregate.doSomething();
        testSubject.save(aggregate);

        StubAggregate loadedAggregate = testSubject.load(aggregate.getIdentifier(), 0L);
        verify(lockManager).obtainLock(aggregate.getIdentifier());

        CurrentUnitOfWork.get().registerListener(loadedAggregate, new UnitOfWorkListenerAdapter() {
            @Override
            public void onPrepareCommit() {
                throw new RuntimeException("Mock Exception");
            }
        });
        try {
            testSubject.save(loadedAggregate);
            fail("Expected exception to be thrown");
        }
        catch (RuntimeException e) {
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
        StubAggregate aggregate = new StubAggregate();
        aggregate.doSomething();
        testSubject.save(aggregate);

        StubAggregate loadedAggregate = testSubject.load(aggregate.getIdentifier(), 0L);
        verify(lockManager).obtainLock(aggregate.getIdentifier());

        CurrentUnitOfWork.get().registerListener(loadedAggregate, new UnitOfWorkListenerAdapter() {
            @Override
            public void onPrepareCommit() {
                throw new RuntimeException("Mock Exception");
            }
        });

        try {
            testSubject.save(loadedAggregate);
            fail("Expected exception to be thrown");
        }
        catch (RuntimeException e) {
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

        StubAggregate aggregate = new StubAggregate();
        aggregate.doSomething();
        testSubject.save(aggregate);
        StubAggregate loadedAggregate = testSubject.load(aggregate.getIdentifier(), 0L);
        loadedAggregate.doSomething();

        testSubject.save(aggregate);
        loadedAggregate.doSomething();
        try {
            testSubject.save(loadedAggregate);
            fail("This should have failed due to lacking lock");
        } catch (ConcurrencyException e) {
            // that's ok
        }
    }

}
