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

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * A CommandHandlerInterceptor that uses a {@link PlatformTransactionManager} to manage transactions around command
 * handling. If any events are handled synchronously (i.e. in the thread that processes the command), these handlers can
 * use the same transaction.
 *
 * @author Allard Buijze
 * @see org.springframework.transaction.PlatformTransactionManager
 * @since 0.5
 */
public class SpringTransactionalInterceptor extends TransactionInterceptor<TransactionStatus> {

    private PlatformTransactionManager transactionManager;
    private TransactionDefinition transactionDefinition = new DefaultTransactionDefinition();

    @Override
    protected TransactionStatus startTransaction() {
        return transactionManager.getTransaction(transactionDefinition);
    }

    @Override
    protected void commitTransaction(TransactionStatus transaction) {
        if (transaction.isNewTransaction()) {
            transactionManager.commit(transaction);
        }
    }

    @Override
    protected void rollbackTransaction(TransactionStatus transaction) {
        if (transaction.isNewTransaction() && !transaction.isCompleted()) {
            transactionManager.rollback(transaction);
        }
    }

    /**
     * Sets the <code>transactionManager</code> to use to manage transactions.
     *
     * @param transactionManager the <code>transactionManager</code> to use to manage transactions.
     */
    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    /**
     * Sets the definition of the transaction to use.
     * <p/>
     * Defaults to the {@link org.springframework.transaction.support.DefaultTransactionDefinition}, which uses
     * propagation "REQUIRED" and the default isolation level of the underlying database.
     *
     * @param definition The transaction definition to use
     */
    public void setTransactionDefinition(TransactionDefinition definition) {
        this.transactionDefinition = definition;
    }
}
