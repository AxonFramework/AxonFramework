/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.eventhandling.saga.repository;

import org.axonframework.common.lock.Lock;
import org.axonframework.common.lock.LockFactory;
import org.axonframework.eventhandling.saga.Saga;
import org.axonframework.eventhandling.saga.SagaRepository;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;

import java.util.function.Supplier;

/**
 * Abstract implementation of a saga repository that locks access to a saga while the saga is being operated on.
 *
 * @author Rene de Waele
 */
public abstract class LockingSagaRepository<T> implements SagaRepository<T> {

    private final LockFactory lockFactory;

    /**
     * Initializes a saga repository that locks access to a saga while the saga is being operated on.
     *
     * @param lockFactory the factory of locks used by this repository
     */
    protected LockingSagaRepository(LockFactory lockFactory) {
        this.lockFactory = lockFactory;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation locks access to sagas with the given {@code sagaIdentifier} and releases the lock in the
     * clean-up phase of the current {@link UnitOfWork}.
     */
    @Override
    public Saga<T> load(String sagaIdentifier) {
        lockSagaAccess(sagaIdentifier);
        return doLoad(sagaIdentifier);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation locks access to sagas with the given {@code sagaIdentifier} and releases the lock in the
     * clean-up phase of the current {@link UnitOfWork}.
     */
    @Override
    public Saga<T> createInstance(String sagaIdentifier, Supplier<T> factoryMethod) {
        lockSagaAccess(sagaIdentifier);
        return doCreateInstance(sagaIdentifier, factoryMethod);
    }

    private void lockSagaAccess(String sagaIdentifier) {
        UnitOfWork<?> unitOfWork = CurrentUnitOfWork.get();
        Lock lock = lockFactory.obtainLock(sagaIdentifier);
        unitOfWork.root().onCleanup(u -> lock.release());
    }

    /**
     * Loads a known Saga instance by its unique identifier.
     * Due to the concurrent nature of Sagas, it is not unlikely for a Saga to have ceased to exist after it has been
     * found based on associations. Therefore, a repository should return {@code null} in case a Saga doesn't
     * exists, as opposed to throwing an exception.
     *
     * @param sagaIdentifier The unique identifier of the Saga to load
     * @return The Saga instance, or {@code null} if no such saga exists
     */
    protected abstract Saga<T> doLoad(String sagaIdentifier);

    /**
     * Creates a new Saga instance. The returned Saga will delegate event handling to the instance supplied by the given
     * {@code factoryMethod}.
     *
     * @param sagaIdentifier the identifier to use for the new saga instance
     * @param factoryMethod Used to create a new Saga delegate
     * @return a new Saga instance wrapping an instance of type {@code T}
     */
    protected abstract Saga<T> doCreateInstance(String sagaIdentifier, Supplier<T> factoryMethod);

}
