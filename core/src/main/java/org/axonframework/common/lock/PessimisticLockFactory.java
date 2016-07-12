/*
 * Copyright (c) 2010-2014. Axon Framework
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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.Collections.newSetFromMap;
import static java.util.Collections.synchronizedMap;

/**
 * Implementation of a {@link LockFactory} that uses a pessimistic locking strategy. Calls to
 * {@link #obtainLock} will block until a lock could be obtained. If a lock is obtained by a thread, that
 * thread has guaranteed unique access.
 * <p/>
 * Each thread can hold the same lock multiple times. The lock will only be released for other threads when the lock
 * has been released as many times as it was obtained.
 * <p/>
 * This lock can be used to ensure thread safe access to a number of objects, such as Aggregates and Sagas.
 *
 * @author Allard Buijze
 * @since 1.3
 */
public class PessimisticLockFactory implements LockFactory {

    private static final Set<PessimisticLockFactory> INSTANCES =
            newSetFromMap(synchronizedMap(new WeakHashMap<>()));

    private static Set<Thread> threadsWaitingForMyLocks(Thread owner) {
        return threadsWaitingForMyLocks(owner, INSTANCES);
    }

    private static Set<Thread> threadsWaitingForMyLocks(Thread owner, Set<PessimisticLockFactory> locksInUse) {
        Set<Thread> waitingThreads = new HashSet<>();
        for (PessimisticLockFactory lock : locksInUse) {
            lock.locks.values().stream()
                    .filter(disposableLock -> disposableLock.isHeldBy(owner))
                    .forEach(disposableLock -> disposableLock.queuedThreads().stream()
                            .filter(waitingThreads::add)
                            .forEach(thread -> waitingThreads.addAll(threadsWaitingForMyLocks(thread, locksInUse))));
        }
        return waitingThreads;
    }

    private final ConcurrentHashMap<String, DisposableLock> locks = new ConcurrentHashMap<>();

    /**
     * Creates a new IdentifierBasedLock instance.
     * <p/>
     * Deadlocks are detected across instances of the IdentifierBasedLock.
     */
    public PessimisticLockFactory() {
        INSTANCES.add(this);
    }

    /**
     * Obtain a lock for a resource identified by the given {@code identifier}. This method will block until a
     * lock was successfully obtained.
     * <p/>
     * Note: when an exception occurs during the locking process, the lock may or may not have been allocated.
     *
     * @param identifier the identifier of the lock to obtain.
     * @return a handle to release the lock. If the thread that releases the lock does not hold the lock
     * {@link IllegalMonitorStateException} is thrown
     */
    @Override
    public Lock obtainLock(String identifier) {
        boolean lockObtained = false;
        DisposableLock lock = null;
        while (!lockObtained) {
            lock = lockFor(identifier);
            lockObtained = lock.lock();
            if (!lockObtained) {
                locks.remove(identifier, lock);
            }
        }
        return lock;
    }

    private DisposableLock lockFor(String identifier) {
        DisposableLock lock = locks.get(identifier);
        while (lock == null) {
            locks.putIfAbsent(identifier, new DisposableLock(identifier));
            lock = locks.get(identifier);
        }
        return lock;
    }

    private class DisposableLock implements Lock {

        private final String identifier;
        private final PubliclyOwnedReentrantLock lock;
        private boolean isClosed = false;

        private DisposableLock(String identifier) {
            this.identifier = identifier;
            this.lock = new PubliclyOwnedReentrantLock();
        }

        @Override
        public void release() {
            try {
                lock.unlock();
            } finally {
                disposeIfUnused();
            }
        }

        @Override
        public boolean isHeld() {
            return lock.isHeldByCurrentThread();
        }

        public boolean lock() {
            try {
                if (!lock.tryLock(0, TimeUnit.NANOSECONDS)) {
                    do {
                        checkForDeadlock();
                    } while (!lock.tryLock(100, TimeUnit.MILLISECONDS));
                }
            } catch (InterruptedException e) {
                throw new LockAcquisitionFailedException("Thread was interrupted", e);
            }
            if (isClosed) {
                lock.unlock();
                return false;
            }
            return true;
        }

        private void checkForDeadlock() {
            if (!lock.isHeldByCurrentThread() && lock.isLocked()) {
                for (Thread thread : threadsWaitingForMyLocks(Thread.currentThread())) {
                    if (lock.isHeldBy(thread)) {
                        throw new DeadlockException(
                                "An imminent deadlock was detected while attempting to acquire a lock");
                    }
                }
            }
        }

        private void disposeIfUnused() {
            if (lock.tryLock()) {
                try {
                    if (lock.getHoldCount() == 1) {
                        // we now have a lock. We can shut it down.
                        isClosed = true;
                        locks.remove(identifier, this);
                    }
                } finally {
                    lock.unlock();
                }
            }
        }

        public Collection<Thread> queuedThreads() {
            return lock.getQueuedThreads();
        }

        public boolean isHeldBy(Thread owner) {
            return lock.isHeldBy(owner);
        }

    }

    private static final class PubliclyOwnedReentrantLock extends ReentrantLock {

        private static final long serialVersionUID = -2259228494514612163L;

        @Override
        public Collection<Thread> getQueuedThreads() { // NOSONAR
            return super.getQueuedThreads();
        }

        public boolean isHeldBy(Thread thread) {
            return thread.equals(getOwner());
        }
    }
}
