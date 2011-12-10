/*
 * Copyright (c) 2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.repository;

import org.axonframework.domain.AggregateRoot;
import org.junit.*;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class PessimisticLockManagerTest {

    @Test
    public void testLockReferenceCleanedUpAtUnlock() throws NoSuchFieldException, IllegalAccessException {
        PessimisticLockManager manager = new PessimisticLockManager();
        UUID identifier = UUID.randomUUID();
        manager.obtainLock(identifier);
        manager.releaseLock(identifier);

        Field locksField = manager.getClass().getDeclaredField("locks");
        locksField.setAccessible(true);
        Map locks = (Map) locksField.get(manager);
        assertEquals("Expected lock to be cleaned up", 0, locks.size());
    }

    @Test
    public void testLockOnlyCleanedUpIfNoLocksAreHeld() {
        PessimisticLockManager manager = new PessimisticLockManager();
        UUID identifier = UUID.randomUUID();
        AggregateRoot aggregateRoot = mock(AggregateRoot.class);
        when(aggregateRoot.getIdentifier()).thenReturn(identifier);

        assertFalse(manager.validateLock(aggregateRoot));

        manager.obtainLock(identifier);
        assertTrue(manager.validateLock(aggregateRoot));

        manager.obtainLock(identifier);
        assertTrue(manager.validateLock(aggregateRoot));

        manager.releaseLock(identifier);
        assertTrue(manager.validateLock(aggregateRoot));

        manager.releaseLock(identifier);
        assertFalse(manager.validateLock(aggregateRoot));
    }
}
