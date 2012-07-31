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
import org.axonframework.common.lock.IdentifierBasedLock;

/**
 * Implementation of the {@link LockManager} that uses a pessimistic locking strategy. Calls to obtainLock will block
 * until a lock could be obtained. If a lock is obtained by a thread, that thread has guaranteed unique access.
 *
 * @author Allard Buijze
 * @since 0.3
 */
public class PessimisticLockManager implements LockManager {

    private final IdentifierBasedLock lock = new IdentifierBasedLock();

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean validateLock(AggregateRoot aggregate) {
        Object aggregateIdentifier = aggregate.getIdentifier();
        return lock.hasLock(aggregateIdentifier.toString());
    }

    /**
     * Obtain a lock for an aggregate. This method will block until a lock was successfully obtained.
     *
     * @param aggregateIdentifier the identifier of the aggregate to obtains a lock for.
     */
    @Override
    public void obtainLock(Object aggregateIdentifier) {
        lock.obtainLock(aggregateIdentifier.toString());
    }

    /**
     * Release the lock held on the aggregate. If no valid lock is held by the current thread, an exception is thrown.
     *
     * @param aggregateIdentifier the identifier of the aggregate to release the lock for.
     * @throws IllegalStateException        if no lock was ever obtained for this aggregate
     * @throws IllegalMonitorStateException if a lock was obtained, but is not currently held by the current thread
     */
    @Override
    public void releaseLock(Object aggregateIdentifier) {
        lock.releaseLock(aggregateIdentifier.toString());
    }
}
