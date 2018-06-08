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

public class TransactionalUnitOfWork {

    private TransactionalUnitOfWork() {
    }

    public static <T extends Message<?>> UnitOfWork<T> startAndGet(T message, TransactionManager transactionManager) {
        UnitOfWork<T> unitOfWork = DefaultUnitOfWork.startAndGet(message);
        makeTransactional(unitOfWork, transactionManager);

        return unitOfWork;
    }

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
