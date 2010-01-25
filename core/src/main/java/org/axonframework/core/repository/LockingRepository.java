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

import java.util.UUID;

/**
 * Implementation of the Repository interface that takes provides a locking mechanism to prevent concurrent
 * modifications of persisted aggregates. Unless there is a locking mechanism present in the underlying persistence
 * environment, it is recommended to use a LockingRepository (or one of its subclasses).
 * <p/>
 * The LockingRepository can be initialized with two strategies: <ul><li><em>Optimistic Locking</em> strategy (default):
 * This strategy performs better than the pessimistic one, but you will only discover a concurrency issue at the time a
 * thread tries to save an aggregate. If another thread has saved the same aggregate earlier (but after the first thread
 * loaded its copy), an exception is thrown. The only way to recover from this exception is to load the aggregate from
 * the repository again, replay all actions on it and save it. <li><em>Pessimistic Locking</em> strategy: Pessimistic
 * Locking requires an exclusive lock to be handed to a thread loading an aggregate before the aggregate is handed over.
 * This means that, once an aggregate is loaded, it has full exclusive access to it, until it saves the aggregate. With
 * this strategy, it is important that -no matter what- the aggregate is saved to the repository. Any failure to do so
 * will result in threads blocking endlessly, waiting for a lock that might never be released. </ul>
 * <p/>
 * Important: If an exception is thrown during the saving process, any locks held are released. The calling thread may
 * reattempt saving the aggregate again. If the lock is available, the thread automatically takes back the lock. If,
 * however, another thread has obtained the lock first, a ConcurrencyException is thrown.
 *
 * @author Allard Buijze
 * @param <T> The type that this aggregate stores
 * @since 0.3
 */
public abstract class LockingRepository<T extends VersionedAggregateRoot> extends AbstractRepository<T> {

    private final LockManager lockManager;

    /**
     * Initialize a repository with an optimistic locking strategy.
     */
    protected LockingRepository() {
        this(LockingStrategy.OPTIMISTIC);
    }

    /**
     * Initialize the repository with the given <code>lockingStrategy</code>
     *
     * @param lockingStrategy the locking strategy to use
     */
    protected LockingRepository(LockingStrategy lockingStrategy) {
        switch (lockingStrategy) {
            case PESSIMISTIC:
                lockManager = new PessimisticLockManager();
                break;
            case OPTIMISTIC:
                lockManager = new OptimisticLockManager();
                break;
            default:
                throw new IllegalArgumentException(
                        String.format("This repository implementation does not support the [%s] locking strategy",
                                      lockingStrategy.name()));
        }
    }

    /**
     * Utility constructor for testing
     *
     * @param lockManager the lock manager to use
     */
    LockingRepository(LockManager lockManager) {
        this.lockManager = lockManager;
    }

    /**
     * Saves the given aggregate in the repository. Consistency is safeguarded using the chosen {@link
     * org.axonframework.core.repository.LockingStrategy}.
     * <p/>
     * <strong>Important</strong>: If an exception is thrown during the saving process, the lock is released. The
     * calling thread may reattempt saving the aggregate immediately. If the lock is still available, the thread
     * automatically recovers the lock. If, however, another thread has obtained the lock before it is recovered, a
     * ConcurrencyException is thrown.
     *
     * @param aggregate The aggregate to store in the repository
     * @throws ConcurrencyException when another thread was first in saving the aggregate.
     */
    @Override
    public void save(T aggregate) {
        // make sure no events were previously committed
        boolean isNewAggregate = (aggregate.getLastCommittedEventSequenceNumber() == null);
        if (!isNewAggregate && !lockManager.validateLock(aggregate)) {
            throw new ConcurrencyException(String.format(
                    "The aggregate of type [%s] with identifier [%s] could not be "
                            + "saved due to concurrent access to the repository",
                    aggregate.getClass().getSimpleName(),
                    aggregate.getIdentifier()));
        }
        try {
            super.save(aggregate);
        }
        finally {
            if (!isNewAggregate) {
                lockManager.releaseLock(aggregate.getIdentifier());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings({"unchecked"})
    @Override
    public T load(UUID aggregateIdentifier) {
        lockManager.obtainLock(aggregateIdentifier);
        try {
            return super.load(aggregateIdentifier);
        } catch (RuntimeException ex) {
            lockManager.releaseLock(aggregateIdentifier);
            throw ex;
        }
    }

    @Override
    public void delete(UUID aggregateIdentifier) {
        lockManager.obtainLock(aggregateIdentifier);
        try {
            super.delete(aggregateIdentifier);
        } finally {
            lockManager.releaseLock(aggregateIdentifier);
        }
    }

    /**
     * Perform the actual saving of the aggregate. All necessary locks have been verified.
     *
     * @param aggregate the aggregate to store
     */
    @Override
    protected abstract void doSave(T aggregate);

    /**
     * Perform the actual loading of an aggregate. The necessary locks have been obtained.
     *
     * @param aggregateIdentifier the identifier of the aggregate to load
     * @return the fully initialized aggregate
     */
    @Override
    protected abstract T doLoad(UUID aggregateIdentifier);

}
