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

package org.axonframework.commandhandling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Mechanism that takes care of interceptor and event handler execution.
 *
 * @author Allard Buijze
 * @since 0.5
 */
class InterceptorChain {

    private static final Logger logger = LoggerFactory.getLogger(InterceptorChain.class);

    private final InterceptorChain next;
    private final CommandHandlerInterceptor current;

    /**
     * Initialize an InterceptorChain for the given <code>interceptors</code>.
     *
     * @param interceptors The list of interceptors to create the chain for. May not be <code>null</code>
     * @throws NullPointerException if the given list of <code>interceptors</code> is <code>null</code>
     */
    public InterceptorChain(List<? extends CommandHandlerInterceptor> interceptors) {
        if (interceptors.size() > 0) {
            current = interceptors.get(0);
            next = new InterceptorChain(interceptors.subList(1, interceptors.size()));
        } else {
            current = null;
            next = null;
        }
    }

    /**
     * Process the given <code>context</code> and pass it on to the next item in the chain. If this instance is the last
     * item in the chain, the command is passed to the <code>handler</code> for execution.
     *
     * @param context The CommandContext describing command execution
     * @param handler The handler assigned to handle the command
     */
    public void handle(CommandContextImpl context, CommandHandler handler) {
        if (current != null) {
            invokeInterceptorMethods(context, handler);
        } else {
            invokeCommandHandler(context, handler);
        }
    }

    @SuppressWarnings({"unchecked"})
    private void invokeInterceptorMethods(CommandContextImpl context, CommandHandler handler) {
        try {
            current.beforeCommandHandling(context, handler);
            next.handle(context, handler);
        }
        catch (RuntimeException ex) {
            context.markFailedInterceptorExecution(ex);
        }
        try {
            current.afterCommandHandling(context, handler);
        }
        catch (RuntimeException ex) {
            logger.error("An interceptor threw an exception in the afterCommandHandling method:", ex);
        }
    }

    @SuppressWarnings({"unchecked"})
    private void invokeCommandHandler(CommandContextImpl context, CommandHandler handler) {
        try {
            Object returnValue = handler.handle(context.getCommand());
            context.markSuccessfulExecution(returnValue);
        }
        catch (RuntimeException ex) {
            context.markFailedHandlerExecution(ex);
        }
    }
}
