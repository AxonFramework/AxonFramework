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

package org.axonframework.core.command.interceptors;

import org.axonframework.core.command.CommandContext;
import org.axonframework.core.command.CommandHandler;
import org.axonframework.core.command.CommandHandlerInterceptor;
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
public class SpringTransactionalInterceptor implements CommandHandlerInterceptor {

    private static final String TRANSACTION_ATTRIBUTE = "SpringTransactionalInterceptor.Transaction";

    private PlatformTransactionManager transactionManager;

    /**
     * Starts a transaction and registers it with the CommandContext.
     *
     * @param context The context in which the command is executed. It contains both the command and any information
     *                that previous CommandHandlerInterceptors may have added to it.
     * @param handler The handler that will handle the command.
     */
    @Override
    public void beforeCommandHandling(CommandContext context, CommandHandler handler) {
        TransactionStatus transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());
        context.setProperty(TRANSACTION_ATTRIBUTE, transaction);
    }

    /**
     * Will commit the transaction upon successful execution of the command handler. If an exception occurs, the
     * transaction will be rolled back.
     *
     * @param context The context in which the command is executed. It contains the command, the result of command
     *                handling, if any, and information that previous CommandHandlerInterceptors may have added to it.
     * @param handler The handler that has handled the command.
     */
    @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
    @Override
    public void afterCommandHandling(CommandContext context, CommandHandler handler) {
        TransactionStatus transaction = (TransactionStatus) context.getProperty(TRANSACTION_ATTRIBUTE);
        if (context.isSuccessful()) {
            transactionManager.commit(transaction);
        } else {
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
}
