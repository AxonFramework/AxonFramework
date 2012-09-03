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

package org.axonframework.eventhandling.transactionmanagers;

import org.axonframework.eventhandling.TransactionManager;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkListenerAdapter;

/**
 * Abstract implementation of the {@link TransactionManager} interface that ensures a UnitOfWork is used to contain the
 * transaction. This way, proper locking and unlocking ordering is guaranteed in combination with the underlying
 * transaction.
 *
 * @param <T> The type of transaction status object used by the underlying transaction manager.
 * @author Allard Buijze
 * @since 2.0
 */
public abstract class AbstractTransactionManager<T> implements TransactionManager<UnitOfWork> {

    @Override
    public UnitOfWork startTransaction() {
        UnitOfWork uow = DefaultUnitOfWork.startAndGet();
        final T tx = startUnderlyingTransaction();
        uow.registerListener(new UnitOfWorkListenerAdapter() {
            @Override
            public void afterCommit(UnitOfWork unitOfWork) {
                commitUnderlyingTransaction(tx);
            }

            @Override
            public void onRollback(UnitOfWork unitOfWork, Throwable failureCause) {
                rollbackUnderlyingTransaction(tx);
            }
        });
        return uow;
    }

    @Override
    public void commitTransaction(UnitOfWork transactionStatus) {
        transactionStatus.commit();
    }

    @Override
    public void rollbackTransaction(UnitOfWork transaction) {
        transaction.rollback();
    }

    /**
     * Starts a transaction in the underlying transaction manager. The returned value will be passed as parameter to
     * the {@link #commitUnderlyingTransaction(Object)} or {@link #rollbackUnderlyingTransaction(Object)} method.
     *
     * @return an object describing the underlying transaction.
     */
    protected abstract T startUnderlyingTransaction();

    /**
     * Commits the transaction described by <code>tx</code> in the underlying transaction manager.
     *
     * @param tx The object returned by {@link #startUnderlyingTransaction()}
     */
    protected abstract void commitUnderlyingTransaction(T tx);

    /**
     * Rolls back the transaction described by <code>tx</code> in the underlying transaction manager.
     *
     * @param tx The object returned by {@link #startUnderlyingTransaction()}
     */
    protected abstract void rollbackUnderlyingTransaction(T tx);
}
