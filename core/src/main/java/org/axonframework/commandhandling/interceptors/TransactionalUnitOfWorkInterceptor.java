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

package org.axonframework.commandhandling.interceptors;

import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;

/**
 * Abstract implementation of a {@link org.axonframework.commandhandling.CommandHandlerInterceptor} that starts a
 * transaction and binds a {@link UnitOfWork} to the current thread.
 * <p/>
 * Upon successful execution of the command, the transaction is committed. If execution fails, the UnitOfWork and the
 * transaction are rolled back.
 *
 * @author Allard Buijze
 * @param <T> The type of object representing the transaction
 * @since 0.6
 */
public abstract class TransactionalUnitOfWorkInterceptor<T> extends SimpleUnitOfWorkInterceptor {

    @Override
    protected UnitOfWork createUnitOfWork() {
        TransactionalUnitOfWork unitOfWork = new TransactionalUnitOfWork();
        unitOfWork.start();
        return unitOfWork;
    }

    /**
     * Start a new transaction for a command execution described by the given <code>context</code>. The given
     * <code>unitOfWork</code> is the unitOfWork bound to the current thread.
     *
     * @param unitOfWork The UnitOfWork bound to the current thread.
     * @return A reference to the current transaction
     */
    protected abstract T startTransaction(UnitOfWork unitOfWork);

    /**
     * Commits the transaction for the command execution described by the given <code>context</code>. The given
     * <code>unitOfWork</code> is the unitOfWork bound to the current thread.
     *
     * @param unitOfWork  The unitOfWork bound to the current thread.
     * @param transaction The transaction object returned during during {@link #startTransaction(org.axonframework.unitofwork.UnitOfWork)}
     */
    protected abstract void commitTransaction(UnitOfWork unitOfWork, T transaction);

    /**
     * Rolls back a transaction for a command execution described by the given <code>context</code>. The given
     * <code>unitOfWork</code> is the unitOfWork bound to the current thread.
     *
     * @param unitOfWork  The unitOfWork bound to the current thread.
     * @param transaction The transaction object returned during during {@link #startTransaction(org.axonframework.unitofwork.UnitOfWork)}
     */
    protected abstract void rollbackTransaction(UnitOfWork unitOfWork, T transaction);

    private final class TransactionalUnitOfWork extends DefaultUnitOfWork {

        private T transaction;

        protected void start() {
            this.transaction = startTransaction(this);
        }

        @Override
        protected void notifyListenersAfterCommit() {
            commitTransaction(this, transaction);
            super.notifyListenersAfterCommit();
        }

        @Override
        protected void notifyListenersRollback() {
            rollbackTransaction(this, transaction);
            super.notifyListenersRollback();
        }
    }
}
