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
import org.axonframework.unitofwork.DefaultUnitOfWork;
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

    @Override
    protected UnitOfWork createUnitOfWork(CommandContext commandContext) {
        TransactionalUnitOfWork unitOfWork = new TransactionalUnitOfWork(commandContext);
        startTransaction(unitOfWork, commandContext);
        return unitOfWork;
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

    private class TransactionalUnitOfWork extends DefaultUnitOfWork {

        private final CommandContext commandContext;

        private TransactionalUnitOfWork(CommandContext commandContext) {
            this.commandContext = commandContext;
        }

        @Override
        protected void notifyListenersAfterCommit() {
            commitTransaction(this, commandContext);
            super.notifyListenersAfterCommit();
        }

        @Override
        protected void notifyListenersRollback() {
            rollbackTransaction(this, commandContext);
            super.notifyListenersRollback();
        }
    }
}
