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

package org.axonframework.eventhandling.scheduling;

import org.axonframework.domain.ApplicationEvent;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * EventTriggerCallback implementation that starts a Spring-managed transaction prior to sending an event, and
 * commits or rollbacks the transaction after publication success or failure, respectively.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class SpringTransactionalTriggerCallback implements EventTriggerCallback, InitializingBean {

    private final ThreadLocal<TransactionStatus> transactions = new ThreadLocal<TransactionStatus>();
    private PlatformTransactionManager transactionManager;
    private TransactionDefinition transactionDefinition = new DefaultTransactionDefinition();

    @Override
    public void beforePublication(ApplicationEvent event) {
        transactions.set(transactionManager.getTransaction(transactionDefinition));
    }

    @Override
    public void afterPublicationSuccess() {
        try {
            TransactionStatus status = transactions.get();
            if (status.isNewTransaction()) {
                transactionManager.commit(status);
            }
        } finally {
            transactions.remove();
        }
    }

    @Override
    public void afterPublicationFailure(RuntimeException cause) {
        try {
            TransactionStatus status = transactions.get();
            if (status.isNewTransaction() && !status.isCompleted()) {
                transactionManager.rollback(status);
            }
        } finally {
            transactions.remove();
        }
    }

    /**
     * Sets the PlatformTransactionManager that this callback uses to start and stop a transaction.
     *
     * @param transactionManager the PlatformTransactionManager that this callback uses to start and stop a transaction
     */
    @Autowired
    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    /**
     * Sets the TransactionDefinition to use when starting a transaction. Defaults to a {@link
     * DefaultTransactionDefinition}.
     *
     * @param transactionDefinition the TransactionDefinition to use when starting a transaction
     */
    public void setTransactionDefinition(TransactionDefinition transactionDefinition) {
        this.transactionDefinition = transactionDefinition;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (transactionManager == null) {
            throw new IllegalStateException("The TransactionManager property is mandatory");
        }
    }
}
