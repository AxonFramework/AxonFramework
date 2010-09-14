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

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.AggregateIdentifierFactory;
import org.axonframework.domain.StubAggregate;
import org.junit.*;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class OptimisticLockManagerTest {

    @Test
    public void testLockReferenceCleanedUpAtUnlock() throws NoSuchFieldException, IllegalAccessException {
        OptimisticLockManager manager = new OptimisticLockManager();
        AggregateIdentifier identifier = AggregateIdentifierFactory.randomIdentifier();
        manager.obtainLock(identifier);
        manager.releaseLock(identifier);

        Field locksField = manager.getClass().getDeclaredField("locks");
        locksField.setAccessible(true);
        Map locks = (Map) locksField.get(manager);
        assertEquals("Expected lock to be cleaned up", 0, locks.size());
    }

    @Test
    public void testLockFailsOnConcurrentModification() {
        AggregateIdentifier identifier = AggregateIdentifierFactory.randomIdentifier();
        StubAggregate aggregate1 = new StubAggregate(identifier);
        StubAggregate aggregate2 = new StubAggregate(identifier);
        OptimisticLockManager manager = new OptimisticLockManager();
        manager.obtainLock(aggregate1.getIdentifier());
        manager.obtainLock(aggregate2.getIdentifier());

        aggregate1.doSomething();
        aggregate2.doSomething();

        assertTrue("The first on to commit should contain the lock", manager.validateLock(aggregate1));
        assertFalse("Expected this lock to be invalid", manager.validateLock(aggregate2));
    }
}
