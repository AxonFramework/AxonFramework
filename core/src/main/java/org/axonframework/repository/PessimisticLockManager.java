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
import org.axonframework.domain.AggregateRoot;
import org.axonframework.util.Assert;

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

    private final ConcurrentHashMap<String, DisposableLock> locks = new ConcurrentHashMap<String, DisposableLock>();

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean validateLock(AggregateRoot aggregate) {
        AggregateIdentifier aggregateIdentifier = aggregate.getIdentifier();

        return isLockAvailableFor(aggregateIdentifier)
                && lockFor(aggregateIdentifier).isHeldByCurrentThread();
    }

    /**
     * Obtain a lock for an aggregate. This method will block until a lock was successfully obtained.
     *
     * @param aggregateIdentifier the identifier of the aggregate to obtains a lock for.
     */
    @Override
    public void obtainLock(AggregateIdentifier aggregateIdentifier) {
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
    public void releaseLock(AggregateIdentifier aggregateIdentifier) {
        Assert.state(locks.containsKey(aggregateIdentifier.asString()), "No lock for this aggregate was ever obtained");
        DisposableLock lock = lockFor(aggregateIdentifier);
        lock.unlock(aggregateIdentifier);
    }

    private void createLockIfAbsent(AggregateIdentifier aggregateIdentifier) {
        if (!locks.contains(aggregateIdentifier)) {
            locks.putIfAbsent(aggregateIdentifier.asString(), new DisposableLock());
        }
    }

    private boolean isLockAvailableFor(AggregateIdentifier aggregateIdentifier) {
        return locks.containsKey(aggregateIdentifier.asString());
    }

    private DisposableLock lockFor(AggregateIdentifier aggregateIdentifier) {
        return locks.get(aggregateIdentifier.asString());
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

        private void unlock(AggregateIdentifier aggregateIdentifier) {
            lock.unlock();
            disposeIfUnused(aggregateIdentifier);
        }

        private synchronized boolean lock() {
            if (isClosed) {
                return false;
            }
            lock.lock();
            return true;
        }

        private synchronized void disposeIfUnused(AggregateIdentifier aggregateIdentifier) {
            if (lock.tryLock()) {
                try {
                    if (lock.getHoldCount() == 1) {
                        // we now have a lock. We can shut it down.
                        isClosed = true;
                        locks.remove(aggregateIdentifier.asString(), this);
                    }
                } finally {
                    lock.unlock();
                }
            }
        }
    }
}
