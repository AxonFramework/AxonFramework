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

// todo: DOCS
public class TransactionalUnitOfWorkFactory {

    private final TransactionManager transactionManager;

    public TransactionalUnitOfWorkFactory(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

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
