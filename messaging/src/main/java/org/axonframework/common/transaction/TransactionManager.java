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

package org.axonframework.common.transaction;

import org.axonframework.messaging.unitofwork.ProcessingLifecycle;
import org.axonframework.messaging.unitofwork.ProcessingLifecycleHandlerRegistrar;

import java.util.function.Supplier;

/**
 * Interface towards a mechanism that manages transactions
 * <p/>
 * Typically, this will involve opening database transactions or connecting to external systems.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface TransactionManager extends ProcessingLifecycleHandlerRegistrar {

    /**
     * Starts a transaction. The return value is the started transaction that can be committed or rolled back.
     *
     * @return The object representing the transaction
     */
    Transaction startTransaction();

    /**
     * Executes the given {@code task} in a new {@link Transaction}. The transaction is committed when the task
     * completes normally, and rolled back when it throws an exception.
     *
     * @param task The task to execute
     */
    default void executeInTransaction(Runnable task) {
        Transaction transaction = startTransaction();
        try {
            task.run();
            transaction.commit();
        } catch (Throwable e) {
            transaction.rollback();
            throw e;
        }
    }

    default void attachToProcessingLifecycle(ProcessingLifecycle processingLifecycle) {
        processingLifecycle.runOnPreInvocation(pc -> {
            Transaction transaction = startTransaction();
            pc.runOnCommit(p -> transaction.commit());
            pc.onError((p, phase, e) -> transaction.rollback());
        });
    }

    @Override
    default void registerHandlers(ProcessingLifecycle processingLifecycle) {
        processingLifecycle.runOnPreInvocation(pc -> {
            Transaction transaction = startTransaction();
            pc.runOnCommit(p -> transaction.commit());
            pc.onError((p, phase, e) -> transaction.rollback());
        });
    }

    /**
     * Invokes the given {@code supplier} in a transaction managed by the current TransactionManager. Upon completion of
     * the call, the transaction will be committed in the case of a regular return value, or rolled back in case an
     * exception occurred.
     * <p>
     * This method is an alternative to {@link #executeInTransaction(Runnable)} in cases where a result needs to be
     * returned from the code to be executed transactionally.
     *
     * @param supplier The supplier of the value to return
     * @param <T>      The type of value to return
     * @return The value returned by the supplier
     */
    default <T> T fetchInTransaction(Supplier<T> supplier) {
        Transaction transaction = startTransaction();
        try {
            T result = supplier.get();
            transaction.commit();
            return result;
        } catch (Throwable e) {
            transaction.rollback();
            throw e;
        }
    }
}
