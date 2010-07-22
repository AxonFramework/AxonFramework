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
import org.axonframework.commandhandling.CommandHandlerInterceptor;

/**
 * Simple adapter for Command Handler Interceptors. This class makes a distinction between incoming commands, successful
 * execution and failed execution. Implementations need to implement the methods for each of these scenarios. The
 * default implementations do nothing.
 *
 * @author Allard Buijze
 * @see #onIncomingCommand(Object,org.axonframework.commandhandling.CommandContext,org.axonframework.commandhandling.CommandHandler)
 * @see #onSuccessfulExecution(Object, Object,org.axonframework.commandhandling.CommandContext,org.axonframework.commandhandling.CommandHandler)
 * @see #onFailedExecution(Object, Throwable,org.axonframework.commandhandling.CommandContext,org.axonframework.commandhandling.CommandHandler)
 * @since 0.6
 */
public abstract class CommandInterceptorAdapter implements CommandHandlerInterceptor {

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeCommandHandling(CommandContext context, CommandHandler handler) {
        onIncomingCommand(context.getCommand(), context, handler);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
    @Override
    public void afterCommandHandling(CommandContext context, CommandHandler handler) {
        if (context.isSuccessful()) {
            onSuccessfulExecution(context.getCommand(), context.getResult(), context, handler);
        } else {
            onFailedExecution(context.getCommand(), context.getException(), context, handler);
        }
    }

    /**
     * Called for each incoming command. The command is extracted from the context for convenience.
     *
     * @param command The incoming command
     * @param context The complete command execution context
     * @param handler The handler that will execute the command
     */
    protected void onIncomingCommand(Object command, CommandContext context, CommandHandler handler) {
    }

    /**
     * Called after successful execution of the command by the command handler. The command and result are extracted
     * from the context for convenience.
     *
     * @param command The executed command
     * @param result  The result of the execution. Can be <code>null</code> or <code>void</code>.
     * @param context The complete command execution context
     * @param handler The handler that executed the command
     */
    protected void onSuccessfulExecution(Object command, Object result, CommandContext context,
                                         CommandHandler handler) {
    }

    /**
     * Called after failed execution or dispatching of a command. The command and thrown exception are extracted from
     * the context for convenience.
     * <p/>
     * Note that the exception may be raised by the command handler or by any of the downstream interceptors.
     *
     * @param command   The command of which execution failed
     * @param exception The acutal thrown exception
     * @param context   The complete command execution context
     * @param handler   The handler that executed the command
     */
    protected void onFailedExecution(Object command, Throwable exception, CommandContext context,
                                     CommandHandler handler) {
    }
}
