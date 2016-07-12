/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.common.transaction;

/**
 * Interface towards a mechanism that manages transactions
 * <p/>
 * Typically, this will involve opening database transactions or connecting to external systems.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface TransactionManager {

    /**
     * Starts a transaction with {@link java.sql.Connection#TRANSACTION_READ_COMMITTED} isolation level. The return
     * value is the started transaction that can be committed or rolled back.
     *
     * @return The object representing the transaction
     */
    default Transaction startTransaction() {
        return startTransaction(TransactionIsolationLevel.READ_COMMITTED);
    }

    /**
     * Starts a transaction with given {@code isolationLevel}. The return value is the started transaction that can
     * be committed or rolled back.
     *
     * @return The object representing the transaction
     */
    Transaction startTransaction(TransactionIsolationLevel isolationLevel);

    default void executeInTransaction(TransactionIsolationLevel isolationLevel, Runnable task) {
        Transaction transaction = startTransaction(isolationLevel);
        try {
            task.run();
            transaction.commit();
        } catch (Throwable e) {
            transaction.rollback();
            throw e;
        }
    }

    default void executeInTransaction(Runnable task) {
        executeInTransaction(TransactionIsolationLevel.READ_COMMITTED, task);
    }
}
