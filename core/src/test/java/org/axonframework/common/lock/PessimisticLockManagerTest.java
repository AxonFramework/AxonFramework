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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class PessimisticLockManagerTest {

    private String identifier = "mockId";

    @Test
    public void testValidateLocks() {
        PessimisticLockManager manager = new PessimisticLockManager();
        String identifier = UUID.randomUUID().toString();
        assertFalse(manager.validateLock(identifier));
        Lock lock1 = manager.obtainLock(identifier);
        assertTrue(manager.validateLock(identifier));
        Lock lock2 = manager.obtainLock(identifier);
        assertTrue(manager.validateLock(identifier));
        lock1.release();
        assertTrue(manager.validateLock(identifier));
        lock2.release();
        assertFalse(manager.validateLock(identifier));
    }

    @Test
    public void testLockReferenceCleanedUpAtUnlock() throws NoSuchFieldException, IllegalAccessException {
        PessimisticLockManager manager = new PessimisticLockManager();
        Lock lock = manager.obtainLock(identifier);
        lock.release();

        Field locksField = manager.getClass().getDeclaredField("locks");
        locksField.setAccessible(true);
        Map locks = (Map) locksField.get(manager);
        assertEquals("Expected lock to be cleaned up", 0, locks.size());
    }

    @Test
    public void testLockOnlyCleanedUpIfNoLocksAreHeld() {
        PessimisticLockManager manager = new PessimisticLockManager();

        assertFalse(manager.validateLock(identifier));

        Lock lock1 = manager.obtainLock(identifier);
        assertTrue(manager.validateLock(identifier));

        Lock lock2 = manager.obtainLock(identifier);
        assertTrue(manager.validateLock(identifier));

        lock1.release();
        assertTrue(manager.validateLock(identifier));

        lock2.release();
        assertFalse(manager.validateLock(identifier));
    }

    @Test(timeout = 5000)
    public void testDeadlockDetected_TwoThreadsInVector() throws InterruptedException {
        final PessimisticLockManager lock = new PessimisticLockManager();
        final CountDownLatch starter = new CountDownLatch(1);
        final CountDownLatch cdl = new CountDownLatch(1);
        final AtomicBoolean deadlockInThread = new AtomicBoolean(false);
        Thread t1 = createThread(starter, cdl, deadlockInThread, lock, "id1", lock, "id2");
        t1.start();
        lock.obtainLock("id2");
        starter.await();
        cdl.countDown();
        try {
            lock.obtainLock("id1");
            assertTrue(deadlockInThread.get());
        } catch (DeadlockException e) {
            // this is ok!
        }
    }

    @Test(timeout = 5000)
    public void testDeadlockDetected_TwoDifferentLockInstances() throws InterruptedException {
        final PessimisticLockManager lock1 = new PessimisticLockManager();
        final PessimisticLockManager lock2 = new PessimisticLockManager();
        final CountDownLatch starter = new CountDownLatch(1);
        final CountDownLatch cdl = new CountDownLatch(1);
        final AtomicBoolean deadlockInThread = new AtomicBoolean(false);
        Thread t1 = createThread(starter, cdl, deadlockInThread, lock1, "id1", lock2, "id1");
        t1.start();
        lock2.obtainLock("id1");
        starter.await();
        cdl.countDown();
        try {
            lock1.obtainLock("id1");
            assertTrue(deadlockInThread.get());
        } catch (DeadlockException e) {
            // this is ok!
        }
    }

    @Test(timeout = 5000)
    public void testDeadlockDetected_ThreeThreadsInVector() throws InterruptedException {
        final PessimisticLockManager lock = new PessimisticLockManager();
        final CountDownLatch starter = new CountDownLatch(3);
        final CountDownLatch cdl = new CountDownLatch(1);
        final AtomicBoolean deadlockInThread = new AtomicBoolean(false);
        Thread t1 = createThread(starter, cdl, deadlockInThread, lock, "id1", lock, "id2");
        Thread t2 = createThread(starter, cdl, deadlockInThread, lock, "id2", lock, "id3");
        Thread t3 = createThread(starter, cdl, deadlockInThread, lock, "id3", lock, "id4");
        t1.start();
        t2.start();
        t3.start();
        lock.obtainLock("id4");
        starter.await();
        cdl.countDown();
        try {
            lock.obtainLock("id1");
            assertTrue(deadlockInThread.get());
        } catch (DeadlockException e) {
            // this is ok!
        }
    }

    private Thread createThread(final CountDownLatch starter, final CountDownLatch cdl,
                                final AtomicBoolean deadlockInThread, final PessimisticLockManager lockManager1,
                                final String firstId, final PessimisticLockManager lockManager2, final String secondId) {
        return new Thread(() -> {
            Lock lock1 = lockManager1.obtainLock(firstId);
            starter.countDown();
            try {
                cdl.await();
                Lock lock2 = lockManager2.obtainLock(secondId);
                lock2.release();
            } catch (InterruptedException e) {
                System.out.println("Thread 1 interrupted");
            } catch (DeadlockException e) {
                deadlockInThread.set(true);
            } finally {
                lock1.release();
            }
        });
    }
}
