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

package org.axonframework.messaging.interceptors;

import jakarta.annotation.Nonnull;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptorChain;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

/**
 * Interceptor that uses a {@link TransactionManager} to start a new transaction before a Message is handled. When the
 * Unit of Work is committed or rolled back this Interceptor also completes the Transaction.
 *
 * @param <M> Type of message to intercept.
 * @author Rene de Waele
 */
@Deprecated(forRemoval = true)
public class TransactionManagingInterceptor<M extends Message> implements MessageHandlerInterceptor<M> {

    private final TransactionManager transactionManager;

    /**
     * Initializes a TransactionManagingInterceptor that uses the given {@code transactionManager}.
     *
     * @param transactionManager the transaction manager that is used set up a new transaction
     */
    public TransactionManagingInterceptor(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    @Nonnull
    public MessageStream<?> interceptOnHandle(@Nonnull M message,
                                              @Nonnull ProcessingContext context,
                                              @Nonnull MessageHandlerInterceptorChain<M> interceptorChain) {
        Transaction transaction = transactionManager.startTransaction();
        context.runOnCommit(u -> transaction.commit());
        context.onError((u, p, e) -> transaction.rollback());

        return interceptorChain.proceed(message, context);
    }
}
