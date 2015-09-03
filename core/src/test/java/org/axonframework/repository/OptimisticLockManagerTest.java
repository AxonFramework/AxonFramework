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

import org.axonframework.domain.AggregateRoot;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.StubAggregate;
import org.axonframework.testutils.MockException;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkListenerAdapter;
import org.junit.*;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class OptimisticLockManagerTest {

    @Test
    public void testLockReferenceCleanedUpAtUnlock() throws NoSuchFieldException, IllegalAccessException {
        OptimisticLockManager manager = new OptimisticLockManager();
        UUID identifier = UUID.randomUUID();
        manager.obtainLock(identifier);
        manager.releaseLock(identifier);

        Field locksField = manager.getClass().getDeclaredField("locks");
        locksField.setAccessible(true);
        Map locks = (Map) locksField.get(manager);
        assertEquals("Expected lock to be cleaned up", 0, locks.size());
    }

    @Test
    public void testLockFailsOnConcurrentModification() {
        UUID identifier = UUID.randomUUID();
        StubAggregate aggregate1 = new StubAggregate(identifier);
        StubAggregate aggregate2 = new StubAggregate(identifier);
        OptimisticLockManager manager = new OptimisticLockManager();
        manager.obtainLock(aggregate1.getIdentifier());
        manager.obtainLock(aggregate2.getIdentifier());

        aggregate1.doSomething();
        aggregate2.doSomething();

        assertTrue("The first one to commit should contain the lock", manager.validateLock(aggregate1));
        assertFalse("Expected this lock to be invalid", manager.validateLock(aggregate2));
    }

    @Test
    public void testLockRolledBackWhenUnitOfWorkFailsToCommit() {
        UnitOfWork unitOfWork = DefaultUnitOfWork.startAndGet();
        unitOfWork.registerListener(new UnitOfWorkListenerAdapter() {
            @Override
            public void onPrepareCommit(UnitOfWork unitOfWork, Set<AggregateRoot> aggregateRoots,
                                        List<EventMessage> events) {
                throw new MockException();
            }
        });

        UUID identifier = UUID.randomUUID();
        StubAggregate aggregate1 = new StubAggregate(identifier);
        OptimisticLockManager manager = new OptimisticLockManager();
        manager.obtainLock(aggregate1.getIdentifier());
        aggregate1.doSomething();

        assertTrue("The first one to commit should contain the lock", manager.validateLock(aggregate1));

        try {
            unitOfWork.commit();
        } catch (MockException ignored) {
        }
        assertFalse(CurrentUnitOfWork.isStarted());

        StubAggregate aggregate2 = new StubAggregate(identifier);
        manager.obtainLock(aggregate2.getIdentifier());
        aggregate2.doSomething();

        assertTrue("After commit fails, the next one should contain the lock", manager.validateLock(aggregate2));

    }
}
