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

package org.axonframework.repository;

import org.axonframework.common.Assert;
import org.axonframework.common.lock.Lock;
import org.axonframework.common.lock.LockFactory;
import org.axonframework.common.lock.PessimisticLockFactory;
import org.axonframework.domain.AggregateRoot;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the Repository interface that takes provides a locking mechanism to prevent concurrent
 * modifications of persisted aggregates. Unless there is a locking mechanism present in the underlying persistence
 * environment, it is recommended to use a LockingRepository (or one of its subclasses).
 * <p/>
 * The LockingRepository can be initialized with a locking strategy. <em>Pessimistic Locking</em> is the default
 * strategy. Pessimistic Locking requires an exclusive lock to be handed to a thread loading an aggregate before
 * the aggregate is handed over. This means that, once an aggregate is loaded, it has full exclusive access to it,
 * until it saves the aggregate.
 * <p/>
 * Important: If an exception is thrown during the saving process, any locks held are released. The calling thread may
 * reattempt saving the aggregate again. If the lock is available, the thread automatically takes back the lock. If,
 * however, another thread has obtained the lock first, a ConcurrencyException is thrown.
 *
 * @param <T> The type that this aggregate stores
 * @author Allard Buijze
 * @since 0.3
 */
public abstract class LockingRepository<T extends AggregateRoot> extends AbstractRepository<T> {

    private static final Logger logger = LoggerFactory.getLogger(LockingRepository.class);

    private final LockFactory lockFactory;

    /**
     * Initialize a repository with a pessimistic locking strategy.
     * @param aggregateType The type of aggregate stored in this repository
     */
    protected LockingRepository(Class<T> aggregateType) {
        this(aggregateType, new PessimisticLockFactory());
    }

    /**
     * Initialize the repository with the given <code>LockFactory</code>.
     *
     * @param aggregateType The type of aggregate stored in this repository
     * @param lockFactory the lock factory to use
     */
    protected LockingRepository(Class<T> aggregateType, LockFactory lockFactory) {
        super(aggregateType);
        Assert.notNull(lockFactory, "LockFactory may not be null");
        this.lockFactory = lockFactory;
    }

    @Override
    public void add(T aggregate) {
        final String aggregateIdentifier = aggregate.getIdentifier();
        Lock lock = lockFactory.obtainLock(aggregateIdentifier);
        try {
            super.add(aggregate);
            CurrentUnitOfWork.get().onCleanup(u -> lock.release());
        } catch (RuntimeException ex) {
            logger.debug("Exception occurred while trying to add an aggregate. Releasing lock.", ex);
            lock.release();
            throw ex;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws AggregateNotFoundException if aggregate with given id cannot be found
     * @throws RuntimeException           any exception thrown by implementing classes
     */
    @SuppressWarnings({"unchecked"})
    @Override
    public T load(String aggregateIdentifier, Long expectedVersion) {
        Lock lock = lockFactory.obtainLock(aggregateIdentifier);
        try {
            final T aggregate = super.load(aggregateIdentifier, expectedVersion);
            CurrentUnitOfWork.get().onCleanup(u -> lock.release());
            return aggregate;
        } catch (RuntimeException ex) {
            logger.debug("Exception occurred while trying to load an aggregate. Releasing lock.", ex);
            lock.release();
            throw ex;
        }
    }
}
