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
import org.junit.*;
import org.mockito.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
        lockManager = spy(new PessimisticLockManager());
        testSubject = spy(new InMemoryLockingRepository(lockManager));
        testSubject.setEventBus(mockEventBus);
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
        reset(mockEventBus);

        StubAggregate loadedAggregate = testSubject.load(aggregate.getIdentifier());
        verify(lockManager).obtainLock(aggregate.getIdentifier());

        loadedAggregate.doSomething();
        testSubject.save(loadedAggregate);
        InOrder inOrder = inOrder(lockManager, mockEventBus, testSubject);
        inOrder.verify(lockManager).validateLock(loadedAggregate);
        inOrder.verify(testSubject).doSave(loadedAggregate);
        inOrder.verify(mockEventBus).publish(isA(DomainEvent.class));
        inOrder.verify(lockManager).releaseLock(loadedAggregate.getIdentifier());
    }

    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    @Test
    public void testLoadAndStoreAggregate_LockReleasedOnException() {
        StubAggregate aggregate = new StubAggregate();
        aggregate.doSomething();
        testSubject.save(aggregate);
        reset(mockEventBus);

        StubAggregate loadedAggregate = testSubject.load(aggregate.getIdentifier());
        verify(lockManager).obtainLock(aggregate.getIdentifier());

        doThrow(new RuntimeException("Mock Exception")).when(testSubject).doSave(isA(StubAggregate.class));

        try {
            testSubject.save(loadedAggregate);
            fail("Expected exception to be thrown");
        }
        catch (RuntimeException e) {
            assertEquals("Mock Exception", e.getMessage());
        }

        // make sure the lock is released
        verify(lockManager).releaseLock(loadedAggregate.getIdentifier());

        // but we may reattempt
        reset(lockManager);
        doCallRealMethod().when(testSubject).doSave(isA(StubAggregate.class));
        testSubject.save(loadedAggregate);
        verify(lockManager).validateLock(aggregate);
        verify(lockManager).releaseLock(aggregate.getIdentifier());
    }

    @Test
    public void testLoadAndStoreAggregate_PessimisticLockReleasedOnException() {
        lockManager = spy(new PessimisticLockManager());
        testSubject = spy(new InMemoryLockingRepository(lockManager));
        testSubject.setEventBus(mockEventBus);

        // we do the same test, but with a pessimistic lock, which has a different way of "re-acquiring" a lost lock
        testLoadAndStoreAggregate_LockReleasedOnException();
    }

    private static class InMemoryLockingRepository extends LockingRepository<StubAggregate> {

        private Map<UUID, StubAggregate> store = new HashMap<UUID, StubAggregate>();

        private InMemoryLockingRepository(LockManager lockManager) {
            super(lockManager);
        }

        @Override
        protected void doSave(StubAggregate aggregate) {
            store.put(aggregate.getIdentifier(), aggregate);
        }

        @Override
        protected StubAggregate doLoad(UUID aggregateIdentifier) {
            return store.get(aggregateIdentifier);
        }
    }
}
