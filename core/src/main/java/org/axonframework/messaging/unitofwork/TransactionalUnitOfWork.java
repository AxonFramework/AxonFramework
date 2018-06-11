/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.messaging.unitofwork;

import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.messaging.Message;

/**
 * This class represents a factory for creating transactional UnitOfWork instances or converting existing UnitOfWork
 * instances to transaction-aware instances.
 *
 * Transaction awareness in this case indicates that a unit of work will be cleaned up properly when its accompanying
 * transaction results in an error (e.g. starting the transaction fails).
 */
public class TransactionalUnitOfWork {

    private TransactionalUnitOfWork() {
    }

    /**
     * Starts a new transaction-aware DefaultUnitOfWork instance (see {@link DefaultUnitOfWork#startAndGet(Message)}).
     * <p>
     * The UnitOfWork will be created transaction-aware. This means that if anything goes wrong while setting up the transaction
     * the UnitOfWork will be rolled back and the error will be thrown up the chain.
     * </p>
     *
     * @param message the message that will be processed in the context of the unit of work
     * @param transactionManager the TransactionManager instance that will be used to start a transaction for the created UnitOfWork
     * @return the started transaction-aware UnitOfWork instance
     */
    public static <T extends Message<?>> UnitOfWork<T> startAndGet(T message, TransactionManager transactionManager) {
        UnitOfWork<T> unitOfWork = DefaultUnitOfWork.startAndGet(message);
        makeTransactional(unitOfWork, transactionManager);

        return unitOfWork;
    }

    /**
     * Transforms the incoming UnitOfWork to a transaction-aware UnitOfWork.
     *
     * @param unitOfWork the UnitOfWork instance that should be made transaction-aware
     * @param transactionManager the TransactionManager instance that will be used to make the incoming UnitOfWork transaction-aware
     */
    public static <T extends Message<?>> void makeTransactional(UnitOfWork<T> unitOfWork, TransactionManager transactionManager) {
        try {
            Transaction transaction = transactionManager.startTransaction();
            unitOfWork.onCommit(u -> transaction.commit());
            unitOfWork.onRollback(u -> transaction.rollback());
        } catch (Throwable t) {
            unitOfWork.rollback(t);
            throw t;
        }
    }
}
