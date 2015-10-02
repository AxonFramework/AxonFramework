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

package org.axonframework.common.lock;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class OptimisticLockManagerTest {

    @Test
    public void testLockReferenceCleanedUpAtUnlock() throws NoSuchFieldException, IllegalAccessException {
        OptimisticLockManager manager = new OptimisticLockManager();
        String identifier = UUID.randomUUID().toString();
        Lock lock = manager.obtainLock(identifier);
        lock.release();

        Field locksField = manager.getClass().getDeclaredField("locks");
        locksField.setAccessible(true);
        Map locks = (Map) locksField.get(manager);
        assertEquals("Expected lock to be cleaned up", 0, locks.size());
    }

    @Test
    public void testLockValidationFailsOnSecondThread() {
        String identifier = "id";
        OptimisticLockManager manager = new OptimisticLockManager();
        manager.obtainLock(identifier);

        assertTrue("The first thread should contain the lock", manager.validateLock(identifier));
        assertTrue("The same thread should still hold the lock", manager.validateLock(identifier));

        ExecutorService executor = Executors.newCachedThreadPool();
        executor.execute(() -> assertFalse("Expected this lock to be invalid", manager.validateLock(identifier)));
    }
}
