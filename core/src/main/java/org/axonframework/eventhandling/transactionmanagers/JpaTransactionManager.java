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

package org.axonframework.eventhandling.transactionmanagers;

import org.axonframework.eventhandling.RetryPolicy;
import org.axonframework.eventhandling.TransactionManager;
import org.axonframework.eventhandling.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;

/**
 * TransactionManager implementation for event listeners that update data in JPA managed data sources.
 * <p/>
 * The transaction manager will commit the transaction when event handling is successful. If a non-transient
 * (non-recoverable) exception occurs, the failing event is discarded and the transaction is committed. If a transient
 * exception occurs, such as a failing connection, the transaction is rolled back and scheduled for a retry.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public abstract class JpaTransactionManager implements TransactionManager {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private PlatformTransactionManager transactionManager;

    private static final ThreadLocal<org.springframework.transaction.TransactionStatus> underlyingTransaction =
            new ThreadLocal<org.springframework.transaction.TransactionStatus>();

    @Override
    public void beforeTransaction(TransactionStatus transactionStatus) {
        transactionStatus.setRetryPolicy(RetryPolicy.RETRY_TRANSACTION);
        transactionStatus.setMaxTransactionSize(25);
        underlyingTransaction.set(transactionManager.getTransaction(new DefaultTransactionDefinition()));
    }

    @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
    @Override
    public void afterTransaction(TransactionStatus transactionStatus) {
        if (transactionStatus.isSuccessful()) {
            transactionManager.commit(underlyingTransaction.get());
        } else {
            logger.warn("Found failed transaction: [{}].", transactionStatus.getException().getClass().getSimpleName());
            if (!isTransient(transactionStatus.getException())) {
                logger.error("ERROR! Exception is not transient or recoverable! Committing transaction and "
                        + "skipping Event processing", transactionStatus.getException());
                transactionStatus.setRetryPolicy(RetryPolicy.SKIP_FAILED_EVENT);
                transactionManager.commit(underlyingTransaction.get());
            } else {
                logger.warn("Performing rollback on transaction due to recoverable exception: [{}]",
                            transactionStatus.getException().getClass().getSimpleName());
                transactionStatus.setRetryPolicy(RetryPolicy.RETRY_TRANSACTION);
                if (underlyingTransaction.get() != null) {
                    transactionManager.rollback(underlyingTransaction.get());
                }
            }
        }
        underlyingTransaction.remove();
    }

    /**
     * The PlatformTransactionManager that manages the transactions with the underlying data source.
     *
     * @param transactionManager the transaction manager that manages transactions with underlying data sources
     */
    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    private boolean isTransient(Throwable exception) {
        if (exception instanceof SQLTransientException ||
                exception instanceof SQLRecoverableException) {
            return true;
        }
        if (exception.getCause() != null && exception.getCause() != exception) {
            return isTransient(exception.getCause());
        }
        return false;
    }
}
