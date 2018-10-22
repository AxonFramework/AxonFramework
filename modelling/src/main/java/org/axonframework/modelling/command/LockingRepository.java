/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.modelling.command;

import org.axonframework.common.Assert;
import org.axonframework.common.lock.Lock;
import org.axonframework.common.lock.LockFactory;
import org.axonframework.common.lock.PessimisticLockFactory;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.modelling.command.inspection.AggregateModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

import static org.axonframework.common.BuilderUtils.assertNonNull;

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
public abstract class LockingRepository<T, A extends Aggregate<T>> extends
                                                                   AbstractRepository<T, LockAwareAggregate<T, A>> {

    private static final Logger logger = LoggerFactory.getLogger(LockingRepository.class);

    private final LockFactory lockFactory;

    /**
     * Instantiate a {@link LockingRepository} based on the fields contained in the {@link Builder}.
     * <p>
     * A goal of the provided Builder is to create an {@link AggregateModel} specifying generic {@code T} as the
     * aggregate type to be stored. All aggregates in this repository must be {@code instanceOf} this aggregate type.
     * To instantiate this AggregateModel, either an {@link AggregateModel} can be provided directly or an
     * {@code aggregateType} of type {@link Class} can be used. The latter will internally resolve to an
     * AggregateModel. Thus, either the AggregateModel <b>or</b> the {@code aggregateType} should be provided. An
     * {@link org.axonframework.common.AxonConfigurationException} is thrown if this criteria is not met.
     * <p>
     * Additionally will assert that the {@link LockFactory} is not {@code null}, resulting in an
     * AxonConfigurationException if this is the case.
     *
     * @param builder the {@link Builder} used to instantiate a {@link LockingRepository} instance
     */
    protected LockingRepository(Builder<T> builder) {
        super(builder);
        this.lockFactory = builder.lockFactory;
    }

    @Override
    protected LockAwareAggregate<T, A> doCreateNew(Callable<T> factoryMethod) throws Exception {
        A aggregate = doCreateNewForLock(factoryMethod);
        final String aggregateIdentifier = aggregate.identifierAsString();
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

    /**
     * Creates a new aggregate instance using the given {@code factoryMethod}. Implementations should assume that this
     * method is only called if a UnitOfWork is currently active.
     *
     * @param factoryMethod The method to create the aggregate's root instance
     * @return an Aggregate instance describing the aggregate's state
     * @throws Exception when the factoryMethod throws an exception
     */
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
        Assert.state(aggregate.isLockHeld(), () -> "An aggregate is being used for which a lock is no longer held");
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
                    "The aggregate of type [%s] with identifier [%s] could not be " +
                            "saved, as a valid lock is not held. Either another thread has saved an aggregate, or " +
                            "the current thread had released its lock earlier on.",
                    aggregate.getClass().getSimpleName(), aggregate.identifierAsString()));
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
                    "The aggregate of type [%s] with identifier [%s] could not be " +
                            "saved, as a valid lock is not held. Either another thread has saved an aggregate, or " +
                            "the current thread had released its lock earlier on.",
                    aggregate.getClass().getSimpleName(), aggregate.identifierAsString()));
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

    /**
     * Loads the aggregate with the given aggregateIdentifier. All necessary locks have been obtained.
     *
     * @param aggregateIdentifier the identifier of the aggregate to load
     * @param expectedVersion     The expected version of the aggregate to load
     * @return a fully initialized aggregate
     * @throws AggregateNotFoundException if the aggregate with given identifier does not exist
     */
    protected abstract A doLoadWithLock(String aggregateIdentifier, Long expectedVersion);

    /**
     * Abstract Builder class to instantiate {@link LockingRepository} implementations.
     * <p>
     * The {@link LockFactory} is defaulted to a pessimistic locking strategy, implemented in the
     * {@link PessimisticLockFactory}.
     * A goal of this Builder goal is to create an {@link AggregateModel} specifying generic {@code T} as the aggregate
     * type to be stored. All aggregates in this repository must be {@code instanceOf} this aggregate type. To
     * instantiate this AggregateModel, either an {@link AggregateModel} can be provided directly or an
     * {@code aggregateType} of type {@link Class} can be used. The latter will internally resolve to an AggregateModel.
     * Thus, either the AggregateModel <b>or</b> the {@code aggregateType} should be provided.
     *
     * @param <T> a generic specifying the Aggregate type contained in this {@link Repository} implementation
     */
    protected static abstract class Builder<T> extends AbstractRepository.Builder<T> {

        private LockFactory lockFactory = PessimisticLockFactory.usingDefaults();

        /**
         * Creates a builder for a Repository for given {@code aggregateType}.
         *
         * @param aggregateType the {@code aggregateType} specifying the type of aggregate this {@link Repository} will
         *                      store
         */
        protected Builder(Class<T> aggregateType) {
            super(aggregateType);
        }

        @Override
        public Builder<T> parameterResolverFactory(ParameterResolverFactory parameterResolverFactory) {
            super.parameterResolverFactory(parameterResolverFactory);
            return this;
        }

        @Override
        public Builder<T> handlerDefinition(HandlerDefinition handlerDefinition) {
            super.handlerDefinition(handlerDefinition);
            return this;
        }

        @Override
        public Builder<T> aggregateModel(AggregateModel<T> aggregateModel) {
            super.aggregateModel(aggregateModel);
            return this;
        }

        /**
         * Sets the {@link LockFactory} used to lock an aggregate. Defaults to a pessimistic locking strategy,
         * implemented in the {@link PessimisticLockFactory}.
         *
         * @param lockFactory a {@link LockFactory} used to lock an aggregate
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> lockFactory(LockFactory lockFactory) {
            assertNonNull(lockFactory, "LockFactory may not be null");
            this.lockFactory = lockFactory;
            return this;
        }
    }
}
