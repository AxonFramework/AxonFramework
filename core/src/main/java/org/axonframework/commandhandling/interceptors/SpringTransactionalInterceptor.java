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

import org.axonframework.commandhandling.CommandContext;
import org.axonframework.unitofwork.UnitOfWork;
import org.springframework.transaction.PlatformTransactionManager;
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
public class SpringTransactionalInterceptor extends TransactionalUnitOfWorkInterceptor {

    private static final String TRANSACTION_ATTRIBUTE = "SpringTransactionalInterceptor.Transaction";

    private PlatformTransactionManager transactionManager;

    @Override
    protected void startTransaction(UnitOfWork unitOfWork, CommandContext context) {
        TransactionStatus transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());
        context.setProperty(TRANSACTION_ATTRIBUTE, transaction);
    }

    @Override
    protected void commitTransaction(UnitOfWork unitOfWork, CommandContext context) {
        TransactionStatus transaction = (TransactionStatus) context.getProperty(TRANSACTION_ATTRIBUTE);
        transactionManager.commit(transaction);
    }

    @Override
    protected void rollbackTransaction(UnitOfWork unitOfWork, CommandContext context) {
        TransactionStatus transaction = (TransactionStatus) context.getProperty(TRANSACTION_ATTRIBUTE);
        transactionManager.rollback(transaction);
    }

    /**
     * Sets the <code>transactionManager</code> to use to manage transactions.
     *
     * @param transactionManager the <code>transactionManager</code> to use to manage transactions.
     */
    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }
}
