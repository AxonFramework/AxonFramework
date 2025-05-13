/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.messaging.unitofwork;

import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.messaging.Context;

/**
 * Factory for creating {@link UnitOfWork} instances that are bound to a transaction.
 * <p>
 * This factory creates units of work that automatically start a transaction before invocation,
 * commit the transaction on successful completion, and roll back the transaction when an error occurs.
 * <p>
 * The transaction is managed by the configured {@link TransactionManager} and is stored as a resource
 * in the unit of work's {@link Context}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class TransactionalUnitOfWorkFactory {
    private final TransactionManager transactionManager;

    /**
     * Initializes a factory with the given {@code transactionManager}.
     *
     * @param transactionManager The transaction manager used to create and manage transactions for the units of work
     */
    public TransactionalUnitOfWorkFactory(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    /**
     * Creates a new {@link UnitOfWork} that is bound to a transaction.
     * <p>
     * The created unit of work will:
     * <ul>
     *     <li>Start a new transaction before invocation using the configured {@link TransactionManager}</li>
     *     <li>Commit the transaction when the unit of work is committed</li>
     *     <li>Roll back the transaction when an error occurs during any phase of the unit of work</li>
     * </ul>
     * The transaction is stored as a resource in the unit of work's context using a resource key with label "transaction".
     *
     * @return A new transactional unit of work
     */
    public UnitOfWork create() {
        var unitOfWork = new UnitOfWork();
        var transactionKey = Context.ResourceKey.<Transaction>withLabel("transaction");
        unitOfWork.runOnPreInvocation(ctx -> {
            var transaction = transactionManager.startTransaction();
            ctx.putResource(transactionKey, transaction);
        });
        unitOfWork.runOnCommit(ctx -> {
            var transaction = ctx.getResource(transactionKey);
            transaction.commit();
        });
        unitOfWork.onError((ctx, phase, error) -> {
            var transaction = ctx.getResource(transactionKey);
            transaction.rollback();
        });
        return unitOfWork;
    }
}
