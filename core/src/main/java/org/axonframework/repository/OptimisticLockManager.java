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

import org.axonframework.domain.AggregateRoot;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the {@link LockManager} that uses an optimistic locking strategy. It uses the sequence number of
 * the last committed event to detect concurrent access.
 * <p/>
 * Classes that use a repository with this strategy must implement any retry logic themselves. Use the {@link
 * ConcurrencyException} to detect concurrent access.
 *
 * @author Allard Buijze
 * @see org.axonframework.eventsourcing.EventSourcedAggregateRoot
 * @see ConcurrencyException
 * @since 0.3
 */
class OptimisticLockManager implements LockManager {

    private final ConcurrentHashMap<UUID, OptimisticLock> locks = new ConcurrentHashMap<UUID, OptimisticLock>();

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean validateLock(AggregateRoot aggregate) {
        OptimisticLock lock = locks.get(aggregate.getIdentifier());
        return lock != null && lock.validate(aggregate);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void obtainLock(UUID aggregateIdentifier) {
        boolean obtained = false;
        while (!obtained) {
            locks.putIfAbsent(aggregateIdentifier, new OptimisticLock());
            OptimisticLock lock = locks.get(aggregateIdentifier);
            obtained = lock != null && lock.lock();
            if (!obtained) {
                locks.remove(aggregateIdentifier, lock);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void releaseLock(UUID aggregateIdentifier) {
        OptimisticLock lock = locks.get(aggregateIdentifier);
        if (lock != null) {
            lock.unlock(aggregateIdentifier);
        }
    }

    private final class OptimisticLock {

        private Long versionNumber;
        private Map<Thread, Integer> threadsHoldingLock = new WeakHashMap<Thread, Integer>();
        private boolean closed = false;

        private OptimisticLock() {
        }

        private synchronized boolean validate(AggregateRoot aggregate) {
            Long lastCommittedEventSequenceNumber = aggregate.getVersion();
            if (versionNumber == null || versionNumber.equals(lastCommittedEventSequenceNumber)) {
                long last = lastCommittedEventSequenceNumber == null ? 0 : lastCommittedEventSequenceNumber;
                versionNumber = last + aggregate.getUncommittedEventCount();
                return true;
            }
            return false;
        }

        private synchronized boolean lock() {
            if (closed) {
                return false;
            }
            Integer lockCount = threadsHoldingLock.get(Thread.currentThread());
            if (lockCount == null) {
                lockCount = 0;
            }
            threadsHoldingLock.put(Thread.currentThread(), lockCount + 1);
            return true;
        }

        private synchronized void unlock(UUID aggregateIdentifier) {
            Integer lockCount = threadsHoldingLock.get(Thread.currentThread());
            if (lockCount == null || lockCount == 1) {
                threadsHoldingLock.remove(Thread.currentThread());
            } else {
                threadsHoldingLock.put(Thread.currentThread(), lockCount - 1);
            }
            if (threadsHoldingLock.isEmpty()) {
                closed = true;
                locks.remove(aggregateIdentifier, this);
            }
        }

    }
}
