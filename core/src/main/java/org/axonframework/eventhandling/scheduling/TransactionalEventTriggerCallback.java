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

package org.axonframework.eventhandling.scheduling;

import org.axonframework.domain.EventMessage;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract implementation of the {@link EventTriggerCallback} interface that ensures a UnitOfWork is used to contain
 * the transaction. This way, proper locking and unlocking ordering is guaranteed in combination with the underlying
 * transaction.
 *
 * @param <T> The type of transaction status object used by the backing transaction manager
 * @author Allard Buijze
 * @since 1.3
 */
public abstract class TransactionalEventTriggerCallback<T> implements EventTriggerCallback {

    private static final Logger logger = LoggerFactory.getLogger(TransactionalEventTriggerCallback.class);

    @Override
    public void beforePublication(EventMessage event) {
        UnitOfWork uow = DefaultUnitOfWork.startAndGet();
        final T tx = startUnderlyingTransaction(event);
        uow.registerListener(new UnitOfWorkListenerAdapter() {
            @Override
            public void onRollback(Throwable failureCause) {
                logger.warn("Rolling back transaction due to exception.", failureCause);
                rollbackUnderlyingTransaction(tx);
            }

            @Override
            public void afterCommit() {
                commitUnderlyingTransaction(tx);
            }
        });
    }

    @Override
    public void afterPublicationSuccess() {
        CurrentUnitOfWork.commit();
    }

    @Override
    public void afterPublicationFailure(RuntimeException cause) {
        CurrentUnitOfWork.get().rollback(cause);
    }

    /**
     * Starts a transaction in the underlying transaction manager. The returned value will be passed as parameter to
     * the {@link #commitUnderlyingTransaction(Object)} or {@link #rollbackUnderlyingTransaction(Object)} method.
     *
     * @param event the event for which a transaction should be started
     * @return an object describing the underlying transaction.
     */
    protected abstract T startUnderlyingTransaction(EventMessage<?> event);

    /**
     * Commits the transaction described by <code>tx</code> in the underlying transaction manager.
     *
     * @param tx The object returned by {@link #startUnderlyingTransaction(org.axonframework.domain.EventMessage)}
     */
    protected abstract void commitUnderlyingTransaction(T tx);

    /**
     * Rolls back the transaction described by <code>tx</code> in the underlying transaction manager.
     *
     * @param tx The object returned by {@link #startUnderlyingTransaction(org.axonframework.domain.EventMessage)}
     */
    protected abstract void rollbackUnderlyingTransaction(T tx);
}
