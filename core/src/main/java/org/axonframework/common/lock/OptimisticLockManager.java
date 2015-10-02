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

import org.axonframework.repository.ConcurrencyException;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of a {@link LockManager} that uses an optimistic locking strategy. If a client asks the manager
 * to obtain a lock for a resource, the manager complies by returning a lock immediately, even if other clients
 * are also in possession of the same lock instance.
 * <p/>
 * However, the lock will only be valid for one client at a time. Once that client releases the lock it will become
 * available for other clients again.
 * <p/>
 * Classes that use a {@link org.axonframework.repository.LockingRepository} with this strategy must implement
 * any retry logic themselves. Use the {@link ConcurrencyException} to detect concurrent access.
 *
 * @author Allard Buijze
 * @see ConcurrencyException
 * @since 0.3
 */
public class OptimisticLockManager implements LockManager {

    private final ConcurrentHashMap<String, OptimisticLock> locks = new ConcurrentHashMap<>();

    /**
     * Obtain a lock for a resource identified by the  given <code>identifier</code>. This method will return a
     * lock immediately, even if other clients are already in possession of the same Lock instance.
     *
     * @param identifier the identifier of the resource to obtain a lock for.
     */
    @Override
    public Lock obtainLock(String identifier) {
        OptimisticLock lock = locks.computeIfAbsent(identifier, o -> new OptimisticLock(identifier));
        if (!lock.registerClient()) {
            return obtainLock(identifier);
        }
        return lock;
    }

    /**
     * Validate a lock for a resource identified by the given <code>identifier</code>.
     * <p/>
     * This method will return <code>true</code> if one of the following is true:
     * <ul>
     *     <li>This is the first time the lock is validated</li>
     *     <li>All other processes that validated this lock have also released it</li>
     *     <li>The lock was last successfully validated by a process running on the current thread</li>
     * </ul>
     *
     * @param identifier the identifier of the resource to validate the lock for.
     * @return <code>true</code> if the current thread holds the lock on the resource, <code>false</code> otherwise.
     */
    @Override
    public boolean validateLock(String identifier) {
        OptimisticLock lock = locks.get(identifier);
        return lock != null && lock.lock();
    }

    private class OptimisticLock implements Lock {

        private final String aggregateIdentifier;
        private final Map<Thread, Integer> threadsHoldingLock = new WeakHashMap<>();
        private boolean removed = false;
        private Thread threadWithLock;

        private OptimisticLock(String aggregateIdentifier) {
            this.aggregateIdentifier = aggregateIdentifier;
        }

        private synchronized boolean registerClient() {
            if (removed) {
                return false;
            }
            Integer lockCount = threadsHoldingLock.get(Thread.currentThread());
            if (lockCount == null) {
                lockCount = 0;
            }
            threadsHoldingLock.put(Thread.currentThread(), lockCount + 1);
            return true;
        }

        private synchronized boolean lock() {
            if (removed) {
                return false;
            }
            if (isLocked()) {
                return Thread.currentThread().equals(threadWithLock);
            } else {
                threadWithLock = Thread.currentThread();
                return true;
            }
        }

        private boolean isLocked() {
            return threadWithLock != null;
        }

        @Override
        public synchronized void release() {
            if (Thread.currentThread().equals(threadWithLock)) {
                threadWithLock = null;
            }
            Integer lockCount = threadsHoldingLock.get(Thread.currentThread());
            if (lockCount == null || lockCount == 1) {
                threadsHoldingLock.remove(Thread.currentThread());
            } else {
                threadsHoldingLock.put(Thread.currentThread(), lockCount - 1);
            }
            if (threadsHoldingLock.isEmpty()) {
                removed = true;
                locks.remove(aggregateIdentifier, this);
            }
        }
    }
}
