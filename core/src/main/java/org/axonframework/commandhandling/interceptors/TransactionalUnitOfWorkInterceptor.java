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

/**
 * Abstract implementation of a {@link org.axonframework.commandhandling.CommandHandlerInterceptor} that starts a
 * transaction and binds a {@link UnitOfWork} to the current thread.
 * <p/>
 * Upon successful execution of the command, the transaction is committed. If execution fails, the UnitOfWork and the
 * transaction are rolled back.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public abstract class TransactionalUnitOfWorkInterceptor extends SimpleUnitOfWorkInterceptor {

    /**
     * Starts a new transaction. See {@link #startTransaction(org.axonframework.unitofwork.UnitOfWork ,
     * org.axonframework.commandhandling.CommandContext)}
     *
     * @param context    The command context describing the command execution
     * @param unitOfWork The unitOfWork that has been started
     */
    @Override
    protected void afterCreated(CommandContext context, UnitOfWork unitOfWork) {
        startTransaction(getUnitOfWork(context), context);
    }

    /**
     * Rolls back a transaction. See {@link #rollbackTransaction(org.axonframework.unitofwork.UnitOfWork ,
     * org.axonframework.commandhandling.CommandContext)}
     *
     * @param context    The command context describing the command execution
     * @param unitOfWork The unitOfWork that has been started
     */
    @Override
    protected void afterRollback(CommandContext context, UnitOfWork unitOfWork) {
        rollbackTransaction(unitOfWork, context);
    }

    /**
     * Commits a transaction. See {@link #commitTransaction(org.axonframework.unitofwork.UnitOfWork ,
     * org.axonframework.commandhandling.CommandContext)}.
     *
     * @param context    The command context describing the command execution
     * @param unitOfWork The unitOfWork that has been started
     */
    @Override
    protected void afterCommit(CommandContext context, UnitOfWork unitOfWork) {
        commitTransaction(unitOfWork, context);
    }

    /**
     * Rolls back the transaction. See {@link #rollbackTransaction(org.axonframework.unitofwork.UnitOfWork ,
     * org.axonframework.commandhandling.CommandContext)}.
     *
     * @param context    The command context describing the command execution
     * @param unitOfWork The unitOfWork that has been started
     * @param exception  The exception thrown while committing the UnitOfWork
     */
    @Override
    protected void onCommitFailed(CommandContext context, UnitOfWork unitOfWork, RuntimeException exception) {
        rollbackTransaction(unitOfWork, context);
    }

    /**
     * Start a new transaction for a command execution described by the given <code>context</code>. The given
     * <code>unitOfWork</code> is the unitOfWork bound to the current thread.
     *
     * @param unitOfWork The UnitOfWork bound to the current thread.
     * @param context    The command context of the command being executed
     */
    protected abstract void startTransaction(UnitOfWork unitOfWork, CommandContext context);

    /**
     * Commits the transaction for the command execution described by the given <code>context</code>. The given
     * <code>unitOfWork</code> is the unitOfWork bound to the current thread.
     *
     * @param unitOfWork The unitOfWork bound to the current thread.
     * @param context    The command context of the command being executed
     */
    protected abstract void commitTransaction(UnitOfWork unitOfWork, CommandContext context);

    /**
     * Rolls back a transaction for a command execution described by the given <code>context</code>. The given
     * <code>unitOfWork</code> is the unitOfWork bound to the current thread.
     *
     * @param unitOfWork The unitOfWork bound to the current thread.
     * @param context    The command context of the command being executed
     */
    protected abstract void rollbackTransaction(UnitOfWork unitOfWork, CommandContext context);
}
