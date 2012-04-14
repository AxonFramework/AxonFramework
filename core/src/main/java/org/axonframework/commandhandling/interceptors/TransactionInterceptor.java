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

import org.axonframework.commandhandling.CommandHandlerInterceptor;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.InterceptorChain;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkListenerAdapter;

/**
 * Abstract implementation of a {@link org.axonframework.commandhandling.CommandHandlerInterceptor} that starts a
 * transaction and binds a {@link UnitOfWork} to the current thread.
 * <p/>
 * Upon successful execution of the command, the transaction is committed. If execution fails, the UnitOfWork and the
 * transaction are rolled back.
 *
 * @param <T> The type of object representing the transaction
 * @author Allard Buijze
 * @since 0.6
 */
public abstract class TransactionInterceptor<T> implements CommandHandlerInterceptor {

    @Override
    public Object handle(CommandMessage<?> command, UnitOfWork unitOfWork, InterceptorChain interceptorChain)
            throws Throwable {
        T transaction = startTransaction();
        unitOfWork.registerListener(new TransactionalUnitOfWork(transaction));
        return interceptorChain.proceed();
    }

    /**
     * Start a new transaction for a command execution described by the given <code>context</code>. The given
     * <code>unitOfWork</code> is the unitOfWork bound to the current thread.
     *
     * @return A reference to the current transaction
     */
    protected abstract T startTransaction();

    /**
     * Commits the transaction for the command execution described by the given <code>context</code>. The given
     * <code>unitOfWork</code> is the unitOfWork bound to the current thread.
     *
     * @param transaction The transaction object returned during during {@link #startTransaction()}
     */
    protected abstract void commitTransaction(T transaction);

    /**
     * Rolls back a transaction for a command execution described by the given <code>context</code>. The given
     * <code>unitOfWork</code> is the unitOfWork bound to the current thread.
     *
     * @param transaction The transaction object returned during during {@link #startTransaction()}
     */
    protected abstract void rollbackTransaction(T transaction);

    private final class TransactionalUnitOfWork extends UnitOfWorkListenerAdapter {

        private final T transaction;

        /**
         * Creates an instance of the listener, tied to the given <code>transaction</code>.
         *
         * @param transaction the transaction assigned to the Unit Of Work.
         */
        private TransactionalUnitOfWork(T transaction) {
            this.transaction = transaction;
        }

        /**
         * This method tries to roll back the transaction assigned to this Unit Of Work.
         *
         * @param failureCause The cause of the rollback
         */
        @Override
        public void onRollback(Throwable failureCause) {
            rollbackTransaction(transaction);
        }

        /**
         * This method commits the transaction assigned to this Unit Of Work.
         */
        @Override
        public void afterCommit() {
            commitTransaction(transaction);
        }
    }
}
