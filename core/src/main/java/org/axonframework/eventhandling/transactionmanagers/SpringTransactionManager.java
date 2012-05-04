/*
 * Copyright (c) 2010-2011. Axon Framework
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

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * TransactionManager implementation that uses a {@link org.springframework.transaction.PlatformTransactionManager} as
 * underlying transaction manager.
 * <p/>
 * The transaction manager will commit the transaction when event handling is successful. If a non-transient
 * (non-recoverable) exception occurs, the failing event is discarded and the transaction is committed. If a transient
 * exception occurs, such as a failing connection, the transaction is rolled back and scheduled for a retry.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class SpringTransactionManager
        extends AbstractTransactionManager<TransactionStatus> {

    private PlatformTransactionManager transactionManager;

    @Override
    protected TransactionStatus startUnderlyingTransaction(
            org.axonframework.eventhandling.TransactionStatus transactionStatus) {
        return transactionManager.getTransaction(new DefaultTransactionDefinition());
    }

    @Override
    protected void commitUnderlyingTransaction(TransactionStatus tx) {
        if (tx.isNewTransaction()) {
            transactionManager.commit(tx);
        }
    }

    @Override
    protected void rollbackUnderlyingTransaction(TransactionStatus tx) {
        if (tx.isNewTransaction()) {
            transactionManager.rollback(tx);
        }
    }

    /**
     * The PlatformTransactionManager that manages the transactions with the underlying data source.
     *
     * @param transactionManager the transaction manager that manages transactions with underlying data sources
     */
    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }
}
