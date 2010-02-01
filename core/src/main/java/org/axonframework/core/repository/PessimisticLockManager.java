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

package org.axonframework.core.repository;

import org.axonframework.core.VersionedAggregateRoot;
import org.axonframework.core.util.Assert;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementation of the {@link LockManager} that uses a pessimistic locking strategy. Calls to obtainLock will block
 * until a lock could be obtained. If a lock is obtained by a thread, that thread has guaranteed unique access.
 *
 * @author Allard Buijze
 * @since 0.3
 */
class PessimisticLockManager implements LockManager {

    private final ConcurrentHashMap<UUID, DisposableLock> locks = new ConcurrentHashMap<UUID, DisposableLock>();

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean validateLock(VersionedAggregateRoot aggregate) {
        UUID aggregateIdentifier = aggregate.getIdentifier();
        boolean currentThreadHoldsLock = isLockAvailableFor(aggregateIdentifier)
                && lockFor(aggregateIdentifier).isHeldByCurrentThread();
        boolean lockIsAvailable = !isLockAvailableFor(aggregateIdentifier) || !lockFor(aggregateIdentifier).isLocked();

        if (!currentThreadHoldsLock && lockIsAvailable) {
            // if the thread lost the lock due to an exception, it could get it back, if it's lucky.
            createLockIfAbsent(aggregateIdentifier);
            return lockFor(aggregateIdentifier).tryLock();
        } else {
            return currentThreadHoldsLock;
        }
    }

    /**
     * Obtain a lock for an aggregate. This method will block until a lock was successfully obtained.
     *
     * @param aggregateIdentifier the identifier of the aggregate to obtains a lock for.
     */
    @Override
    public void obtainLock(UUID aggregateIdentifier) {
        boolean lockObtained = false;
        while (!lockObtained) {
            createLockIfAbsent(aggregateIdentifier);
            DisposableLock lock = lockFor(aggregateIdentifier);
            lockObtained = lock.lock();
            if (!lockObtained) {
                locks.remove(aggregateIdentifier, lock);
            }
        }
    }

    /**
     * Release the lock held on the aggregate. If no valid lock is held by the current thread, an exception is thrown.
     *
     * @param aggregateIdentifier the identifier of the aggregate to release the lock for.
     * @throws IllegalStateException        if no lock was ever obtained for this aggregate
     * @throws IllegalMonitorStateException if a lock was obtained, but is not currently held by the current thread
     */
    @Override
    public void releaseLock(UUID aggregateIdentifier) {
        Assert.state(locks.containsKey(aggregateIdentifier), "No lock for this aggregate was ever obtained");
        DisposableLock lock = lockFor(aggregateIdentifier);
        lock.unlock(aggregateIdentifier);
    }

    private void createLockIfAbsent(UUID aggregateIdentifier) {
        if (!locks.contains(aggregateIdentifier)) {
            locks.putIfAbsent(aggregateIdentifier, new DisposableLock());
        }
    }

    private boolean isLockAvailableFor(UUID aggregateIdentifier) {
        return locks.containsKey(aggregateIdentifier);
    }

    private DisposableLock lockFor(UUID aggregateIdentifier) {
        return locks.get(aggregateIdentifier);
    }

    private final class DisposableLock {

        private ReentrantLock lock;
        // guarded by "this"
        private boolean isClosed = false;

        private DisposableLock() {
            this.lock = new ReentrantLock();
        }

        private boolean isHeldByCurrentThread() {
            return lock.isHeldByCurrentThread();
        }

        private void unlock(UUID aggregateIdentifier) {
            lock.unlock();
            if (shutDown()) {
                locks.remove(aggregateIdentifier);
            }
        }

        private synchronized boolean lock() {
            if (isClosed) {
                return false;
            }
            lock.lock();
            return true;
        }

        private synchronized boolean tryLock() {
            return !isClosed && lock.tryLock();
        }

        private boolean isLocked() {
            return lock.isLocked();
        }

        private synchronized boolean shutDown() {
            if (lock.tryLock()) {
                // we now have a lock. We can shut it down.
                isClosed = true;
                lock.unlock();
            }
            return isClosed;
        }
    }
}
