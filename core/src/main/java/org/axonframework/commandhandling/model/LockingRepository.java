/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.commandhandling.model;

import org.axonframework.common.Assert;
import org.axonframework.common.lock.Lock;
import org.axonframework.common.lock.LockFactory;
import org.axonframework.common.lock.PessimisticLockFactory;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

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
public abstract class LockingRepository<T, A extends Aggregate<T>> extends AbstractRepository<T, LockAwareAggregate<T, A>> {

    private static final Logger logger = LoggerFactory.getLogger(LockingRepository.class);

    private final LockFactory lockFactory;

    /**
     * Initialize a repository with a pessimistic locking strategy.
     *
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
    protected LockAwareAggregate<T, A> doCreateNew(Callable<T> factoryMethod) throws Exception {
        A aggregate = doCreateNewForLock(factoryMethod);
        final String aggregateIdentifier = aggregate.identifier();
        Lock lock = lockFactory.obtainLock(aggregateIdentifier);
        try {
            CurrentUnitOfWork.get().onCleanup(u -> lock.release());
        } catch (Throwable ex) {
            if (lock != null) {
                logger.debug("Exception occurred while trying to add an aggregate. Releasing lock.", ex);
                lock.release();
            }
            throw ex;
        }
        return new LockAwareAggregate<>(aggregate, lock);
    }

    protected abstract A doCreateNewForLock(Callable<T> factoryMethod) throws Exception;

    /**
     * Perform the actual loading of an aggregate. The necessary locks have been obtained.
     *
     * @param aggregateIdentifier the identifier of the aggregate to load
     * @param expectedVersion     The expected version of the aggregate
     * @return the fully initialized aggregate
     * @throws AggregateNotFoundException if aggregate with given id cannot be found
     */
    @Override
    protected LockAwareAggregate<T, A> doLoad(String aggregateIdentifier, Long expectedVersion) {
        Lock lock = lockFactory.obtainLock(aggregateIdentifier);
        try {
            final A aggregate = doLoadWithLock(aggregateIdentifier, expectedVersion);
            CurrentUnitOfWork.get().onCleanup(u -> lock.release());
            return new LockAwareAggregate<>(aggregate, lock);
        } catch (Throwable ex) {
            logger.debug("Exception occurred while trying to load an aggregate. Releasing lock.", ex);
            lock.release();
            throw ex;
        }
    }

    @Override
    protected void prepareForCommit(LockAwareAggregate<T, A> aggregate) {
        Assert.state(aggregate.isLockHeld(), "An aggregate is being used for which a lock is no longer held");
        super.prepareForCommit(aggregate);
    }

    /**
     * Verifies whether all locks are valid and delegates to
     * {@link #doSaveWithLock(Aggregate)} to perform actual storage.
     *
     * @param aggregate the aggregate to store
     */
    @Override
    protected void doSave(LockAwareAggregate<T, A> aggregate) {
        if (aggregate.version() != null && !aggregate.isLockHeld()) {
            throw new ConcurrencyException(String.format(
                    "The aggregate of type [%s] with identifier [%s] could not be "
                            + "saved, as a valid lock is not held. Either another thread has saved an aggregate, or "
                            + "the current thread had released its lock earlier on.",
                    aggregate.getClass().getSimpleName(),
                    aggregate.identifier()));
        }
        doSaveWithLock(aggregate.getWrappedAggregate());
    }

    /**
     * Verifies whether all locks are valid and delegates to
     * {@link #doDeleteWithLock(Aggregate)} to perform actual deleting.
     *
     * @param aggregate the aggregate to delete
     */
    @Override
    protected final void doDelete(LockAwareAggregate<T, A> aggregate) {
        if (aggregate.version() != null && !aggregate.isLockHeld()) {
            throw new ConcurrencyException(String.format(
                    "The aggregate of type [%s] with identifier [%s] could not be "
                            + "saved, as a valid lock is not held. Either another thread has saved an aggregate, or "
                            + "the current thread had released its lock earlier on.",
                    aggregate.getClass().getSimpleName(),
                    aggregate.identifier()));
        }
        doDeleteWithLock(aggregate.getWrappedAggregate());
    }

    /**
     * Perform the actual saving of the aggregate. All necessary locks have been verified.
     *
     * @param aggregate the aggregate to store
     */
    protected abstract void doSaveWithLock(A aggregate);

    /**
     * Perform the actual deleting of the aggregate. All necessary locks have been verified.
     *
     * @param aggregate the aggregate to delete
     */
    protected abstract void doDeleteWithLock(A aggregate);

    protected abstract A doLoadWithLock(String aggregateIdentifier, Long expectedVersion);
}
