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

package org.axonframework.core.command;

/**
 * Workflow interface that allows for customized command handler invocation chains. A CommandHandlerInterceptor can add
 * customized behavior to command handler invocations, both before and after the invocation.
 * <p/>
 * The {@link #beforeCommandHandling(CommandContext, CommandHandler)} method is called before the {@link CommandHandler}
 * is invoked (but after the CommandHandler has been resolved).
 * <p/>
 * The {@link #afterCommandHandling(CommandContext, CommandHandler)} method is called after the command handler
 * invocation.
 * <p/>
 * A CommandHandlerInterceptor may block an incoming command by throwing an Exception. In such case, all
 * CommandHandlerInterceptors that had their {@link #beforeCommandHandling(CommandContext, CommandHandler)} method
 * invoked will also have the {@link #afterCommandHandling(CommandContext, CommandHandler)} method invoked. The {@link
 * CommandContext} parameter provides information about whether the CommandHandler was invoked and what the result of
 * the invocation is.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public interface CommandHandlerInterceptor {

    /**
     * Invoked before the command handler handles the command. Command handling can be prevented by throwing an
     * exception from this method.
     *
     * @param context The context in which the command is executed. It contains both the command and any information
     *                that previous CommandHandlerInterceptors may have added to it.
     * @param handler The handler that will handle the command.
     * @throws RuntimeException when an error occurs that should block command handling.
     */
    void beforeCommandHandling(CommandContext context, CommandHandler handler);

    /**
     * Invoked after the command handler handled the command or when a CommandHandlerInterceptor further down the chain
     * has thrown an Exception. The CommandContext provides information about whether the handler was invoked and
     * whether the execution was successful or resulted in an Exception.
     *
     * @param context The context in which the command is executed. It contains the command, the result of command
     *                handling, if any, and information that previous CommandHandlerInterceptors may have added to it.
     * @param handler The handler that has handled the command.
     * @throws RuntimeException when an error occurs that should block command handling.
     */
    void afterCommandHandling(CommandContext context, CommandHandler handler);

}
