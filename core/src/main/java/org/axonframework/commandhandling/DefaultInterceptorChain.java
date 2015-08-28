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

package org.axonframework.commandhandling;

import org.axonframework.messaging.unitofwork.UnitOfWork;

import java.util.Iterator;

/**
 * Mechanism that takes care of interceptor and event handler execution.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class DefaultInterceptorChain implements InterceptorChain {

    private CommandMessage<?> command;
    private final CommandHandler handler;
    private final Iterator<? extends CommandHandlerInterceptor> chain;
    private final UnitOfWork unitOfWork;

    /**
     * Initialize the default interceptor chain to dispatch the given <code>command</code>, through the
     * <code>chain</code>, to the <code>handler</code>.
     *
     * @param command    The command to dispatch through the interceptor chain
     * @param unitOfWork The UnitOfWork the command is executed in
     * @param handler    The handler for the command
     * @param chain      The interceptor composing the chain
     */
    public DefaultInterceptorChain(CommandMessage<?> command, UnitOfWork unitOfWork, CommandHandler<?> handler,
                                   Iterable<? extends CommandHandlerInterceptor> chain) {
        this.command = command;
        this.handler = handler;
        this.chain = chain.iterator();
        this.unitOfWork = unitOfWork;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings({"unchecked"})
    @Override
    public Object proceed(CommandMessage<?> commandProceedWith) throws Throwable {
        command = commandProceedWith;
        if (chain.hasNext()) {
            return chain.next().handle(commandProceedWith, unitOfWork, this);
        } else {
            return handler.handle(commandProceedWith, unitOfWork);
        }
    }

    @Override
    public Object proceed() throws Throwable {
        return proceed(command);
    }
}
