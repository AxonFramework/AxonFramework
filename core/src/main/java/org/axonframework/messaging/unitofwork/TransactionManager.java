/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.messaging.unitofwork;

/**
 * Interface towards a mechanism that manages transactions
 * <p/>
 * Typically, this will involve opening database transactions or connecting to external systems.
 *
 * @param <T> The type of object used to represent the transaction
 * @author Allard Buijze
 * @since 2.0
 */
public interface TransactionManager<T> {

    /**
     * Starts a transaction. The return value is an object representing the transaction status and must be passed as an
     * argument when invoking {@link #commitTransaction(Object)} or {@link #rollbackTransaction(Object)}.
     * <p/>
     * The returned object must never be <code>null</code> if a transaction was successfully created.
     *
     * @return The object representing the transaction status
     */
    T startTransaction();

    /**
     * Commits the transaction identifier by given <code>transactionStatus</code>.
     *
     * @param transactionStatus The status object provided by {@link #startTransaction()}.
     */
    void commitTransaction(T transactionStatus);

    /**
     * Rolls back the transaction identifier by given <code>transactionStatus</code>.
     *
     * @param transactionStatus The status object provided by {@link #startTransaction()}.
     */
    void rollbackTransaction(T transactionStatus);
}
