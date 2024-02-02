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

package org.axonframework.commandhandling;

import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageDispatchInterceptorSupport;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptorSupport;

import javax.annotation.Nonnull;

/**
 * The mechanism that dispatches Command objects to their appropriate CommandHandler. CommandHandlers can subscribe and
 * unsubscribe to specific commands (identified by their {@link CommandMessage#getCommandName() name}) on the command
 * bus. Only a single handler may be subscribed for a single command name at any time.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public interface CommandBus extends MessageHandlerInterceptorSupport<CommandMessage<?>>,
        MessageDispatchInterceptorSupport<CommandMessage<?>> {

    /**
     * Dispatch the given {@code command} to the CommandHandler subscribed to the given {@code command}'s name. No
     * feedback is given about the status of the dispatching process. Implementations may return immediately after
     * asserting a valid handler is registered for the given command.
     *
     * @param <C>     The payload type of the command to dispatch
     * @param command The Command to dispatch
     * @throws NoHandlerForCommandException when no command handler is registered for the given {@code command}'s name.
     * @see GenericCommandMessage#asCommandMessage(Object)
     */
    <C> void dispatch(@Nonnull CommandMessage<C> command);

    /**
     * Dispatch the given {@code command} to the CommandHandler subscribed to the given {@code command}'s name. When the
     * command is processed, one of the callback's methods is called, depending on the result of the processing.
     * <p/>
     * There are no guarantees about the successful completion of command dispatching or handling after the method
     * returns. Implementations are highly recommended to perform basic validation of the command before returning
     * from this method call.
     * <p/>
     * Implementations must start a UnitOfWork when before dispatching the command, and either commit or rollback after
     * a successful or failed execution, respectively.
     *
     * @param command  The Command to dispatch
     * @param callback The callback to invoke when command processing is complete
     * @param <C>      The payload type of the command to dispatch
     * @param <R>      The type of the expected result
     * @throws NoHandlerForCommandException when no command handler is registered for the given {@code command}.
     * @see GenericCommandMessage#asCommandMessage(Object)
     */
    <C, R> void dispatch(@Nonnull CommandMessage<C> command, @Nonnull CommandCallback<? super C, ? super R> callback);

    /**
     * Subscribe the given {@code handler} to commands with the given {@code commandName}.
     * <p/>
     * If a subscription already exists for the given name, the behavior is undefined. Implementations may throw an
     * Exception to refuse duplicate subscription or alternatively decide whether the existing or new {@code handler}
     * gets the subscription.
     *
     * @param commandName The name of the command to subscribe the handler to
     * @param handler     The handler instance that handles the given type of command
     * @return a handle to unsubscribe the {@code handler}. When unsubscribed it will no longer receive commands.
     */
    Registration subscribe(@Nonnull String commandName, @Nonnull MessageHandler<? super CommandMessage<?>, ? extends CommandResultMessage<?>> handler);
}
