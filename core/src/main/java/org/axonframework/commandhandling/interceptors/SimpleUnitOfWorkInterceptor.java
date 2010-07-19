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
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CommandHandlerInterceptor that starts a {@link org.axonframework.unitofwork.DefaultUnitOfWork} when a command is
 * received. If command handling is successful, the UnitOfWork is committed, otherwise, it is rolled back.
 * <p/>
 * The UnitOfWork makes the changes made during command handling more atomic. Events are only dispatched when all
 * changes to the aggregates have been successfully persisted.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public class SimpleUnitOfWorkInterceptor extends CommandInterceptorAdapter {

    private static final String UNIT_OF_WORK_ATTRIBUTE = "SimpleUnitOfWorkInterceptor.UnitOfWork";
    private static final Logger logger = LoggerFactory.getLogger(SimpleUnitOfWorkInterceptor.class);

    @Override
    protected void onIncomingCommand(Object command, CommandContext context, CommandHandler handler) {
        UnitOfWork unitOfWork = createUnitOfWork(context);
        context.setProperty(UNIT_OF_WORK_ATTRIBUTE, unitOfWork);
        CurrentUnitOfWork.set(unitOfWork);
    }

    @Override
    protected void onSuccessfulExecution(Object command, Object result, CommandContext context,
                                         CommandHandler handler) {
        UnitOfWork unitOfWork = getUnitOfWork(context);
        try {
            unitOfWork.commit();
        } catch (RuntimeException e) {
            logger.warn("An error occurred while committing the UnitOfWork. Rolling back the UnitOfWork instead.", e);
            unitOfWork.rollback();
        } finally {
            CurrentUnitOfWork.clear();
            context.removeProperty(UNIT_OF_WORK_ATTRIBUTE);
        }
    }

    @Override
    protected void onFailedExecution(Object command, Exception exception, CommandContext context,
                                     CommandHandler handler) {
        try {
            UnitOfWork unitOfWork = (UnitOfWork) context.getProperty(UNIT_OF_WORK_ATTRIBUTE);
            unitOfWork.rollback();
        } finally {
            CurrentUnitOfWork.clear();
            context.removeProperty(UNIT_OF_WORK_ATTRIBUTE);
        }
    }

    /**
     * Creates a new instance of a UnitOfWork. This implementation creates a new {@link
     * org.axonframework.unitofwork.DefaultUnitOfWork}. Subclasses may override this method to provide another instance
     * instead.
     *
     * @param commandContext
     * @return The UnitOfWork to bind to the current thread.
     */
    protected UnitOfWork createUnitOfWork(CommandContext commandContext) {
        return new DefaultUnitOfWork();
    }

    /**
     * Gets the UnitOfWork from the given execution context.
     *
     * @param context The command context of the current execution
     * @return The UnitOfWork bound to the context, or <code>null</code> if not found.
     */
    protected UnitOfWork getUnitOfWork(CommandContext context) {
        return (UnitOfWork) context.getProperty(UNIT_OF_WORK_ATTRIBUTE);
    }
}
