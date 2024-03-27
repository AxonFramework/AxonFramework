/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.messaging.interceptors;

import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.UnitOfWork;

import javax.annotation.Nonnull;

/**
 * Interceptor that uses a {@link TransactionManager} to start a new transaction before a Message is handled. When the
 * Unit of Work is committed or rolled back this Interceptor also completes the Transaction.
 *
 * @author Rene de Waele
 */
public class TransactionManagingInterceptor<T extends Message<?>> implements MessageHandlerInterceptor<T> {

    private final TransactionManager transactionManager;

    /**
     * Initializes a {@link TransactionManagingInterceptor} that uses the given {@code transactionManager}.
     *
     * @param transactionManager the transaction manager that is used set up a new transaction
     */
    public TransactionManagingInterceptor(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public Object handle(@Nonnull UnitOfWork<? extends T> unitOfWork, @Nonnull InterceptorChain interceptorChain)
            throws Exception {
        Transaction transaction = transactionManager.startTransaction();
        unitOfWork.onCommit(u -> transaction.commit());
        unitOfWork.onRollback(u -> transaction.rollback());
        return interceptorChain.proceedSync();
    }

    @Override
    public <M extends T, R extends Message<?>> MessageStream<? extends R> interceptOnHandle(@Nonnull M message,
                                                                                            @Nonnull ProcessingContext context,
                                                                                            @Nonnull InterceptorChain<M, R> interceptorChain) {
        Transaction transaction = transactionManager.startTransaction();
        context.runOnCommit(u -> transaction.commit());
        context.onError((u, p, e) -> transaction.rollback());

        return interceptorChain.proceed(message, context);
    }
}
