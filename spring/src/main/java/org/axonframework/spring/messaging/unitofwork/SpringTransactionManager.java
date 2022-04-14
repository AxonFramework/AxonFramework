/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.spring.messaging.unitofwork;

import org.axonframework.common.Assert;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.annotation.Nonnull;

/**
 * TransactionManager implementation that uses a {@link org.springframework.transaction.PlatformTransactionManager} as
 * underlying transaction manager.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class SpringTransactionManager implements TransactionManager {

    private final PlatformTransactionManager transactionManager;
    private final TransactionDefinition transactionDefinition;

    /**
     * @param transactionManager    The transaction manager to use
     * @param transactionDefinition The definition for transactions to create
     */
    public SpringTransactionManager(PlatformTransactionManager transactionManager,
                                    TransactionDefinition transactionDefinition) {
        Assert.notNull(transactionManager, () -> "transactionManager may not be null");
        this.transactionManager = transactionManager;
        this.transactionDefinition = transactionDefinition;
    }

    /**
     * Initializes the SpringTransactionManager with the given {@code transactionManager} and the default
     * transaction definition.
     *
     * @param transactionManager the transaction manager to use
     */
    public SpringTransactionManager(PlatformTransactionManager transactionManager) {
        this(transactionManager, new DefaultTransactionDefinition());
    }

    @Nonnull
    @Override
    public Transaction startTransaction() {
        TransactionStatus status = transactionManager.getTransaction(transactionDefinition);
        return new Transaction() {
            @Override
            public void commit() {
                commitTransaction(status);
            }

            @Override
            public void rollback() {
                rollbackTransaction(status);
            }
        };
    }

    /**
     * Commits the transaction with given {@code status} if the transaction is new and not completed.
     *
     * @param status The status of the transaction to commit
     */
    protected void commitTransaction(TransactionStatus status) {
        if (status.isNewTransaction() && !status.isCompleted()) {
            transactionManager.commit(status);
        }
    }

    /**
     * Rolls back the transaction with given {@code status} if the transaction is new and not completed.
     *
     * @param status The status of the transaction to roll back
     */
    protected void rollbackTransaction(TransactionStatus status) {
        if (status.isNewTransaction() && !status.isCompleted()) {
            transactionManager.rollback(status);
        }
    }
}
