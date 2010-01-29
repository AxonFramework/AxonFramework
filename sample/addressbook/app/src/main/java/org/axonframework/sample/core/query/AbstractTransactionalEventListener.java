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

package org.axonframework.sample.core.query;

import org.axonframework.core.eventhandler.RetryPolicy;
import org.axonframework.core.eventhandler.TransactionAware;
import org.axonframework.core.eventhandler.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * @author Allard Buijze
 */
public abstract class AbstractTransactionalEventListener implements TransactionAware {

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
        try {
            if (transactionStatus.isSuccessful()) {
                transactionManager.commit(underlyingTransaction.get());
            } else {
                logger.error("Found failed transaction. Will retry shortly.", transactionStatus.getException());
                // TODO: Check if the exception is transient. If not log an error, commit the transaction and skip the last event
                transactionManager.rollback(underlyingTransaction.get());
            }
        } finally {
            underlyingTransaction.remove();
        }
    }

    @Autowired
    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }
}
