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
import org.axonframework.commandhandling.CommandHandlerInterceptor;
import org.axonframework.commandhandling.InterceptorChain;
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
public class SimpleUnitOfWorkInterceptor implements CommandHandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(SimpleUnitOfWorkInterceptor.class);

    @Override
    public Object handle(CommandContext context, InterceptorChain chain) throws Throwable {
        logger.debug("Incoming command. Creating new UnitOfWork instance");
        UnitOfWork unitOfWork = createUnitOfWork();
        logger.debug("Registering new UnitOfWork instance with CurrentUnitOfWork");
        CurrentUnitOfWork.set(unitOfWork);

        try {
            logger.debug("Proceeding interceptor chain");
            Object returnValue = chain.proceed(context);
            logger.debug("Committing UnitOfWork after successful command execution");
            unitOfWork.commit();
            logger.debug("UnitOfWork successfully committed");
            return returnValue;
        } catch (Throwable t) {
            logger.debug("Rolling back UnitOfWork after execution error");
            unitOfWork.rollback();
            logger.debug("UnitOfWork rolled back");
            throw t;
        } finally {
            logger.debug("Clearing UnitOfWork from CurrentUnitOfWork");
            CurrentUnitOfWork.clear();
        }
    }

    /**
     * Creates a new instance of a UnitOfWork. This implementation creates a new {@link
     * org.axonframework.unitofwork.DefaultUnitOfWork}. Subclasses may override this method to provide another instance
     * instead.
     *
     * @return The UnitOfWork to bind to the current thread.
     */
    protected UnitOfWork createUnitOfWork() {
        return new DefaultUnitOfWork();
    }
}
