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

/**
 * The mechanism that dispatches Command objects to their appropriate CommandHandler. CommandHandlers can subscribe and
 * unsubscribe to specific types of commands on the command bus. Only a single handler may be subscribed for a single
 * type of command at any time.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public interface CommandBus {

    /**
     * Dispatch the given <code>command</code> to the CommandHandler subscribed to that type of <code>command</code>.
     * The return value or exception resulting from the command is thrown
     *
     * @param command The Command to dispatch
     * @return the result of the command handler invocation.
     *
     * @throws NoHandlerForCommandException when no command handler is registered for the given <code>command</code>.
     * @throws CommandHandlerInvocationException
     *                                      if the command handler threw a checked exception
     * @throws RuntimeException             any unchecked exception raised by the command handler
     * @deprecated This method has been deprecated in favor of {@link #dispatch(Object, CommandCallback)}.
     */
    @Deprecated
    Object dispatch(Object command);

    /**
     * Dispatch the given <code>command</code> to the CommandHandler subscribed to that type of <code>command</code>.
     * When the command is processed, on of the callback methods is called, depending on the result of the processing.
     * <p/>
     * When the method returns, the only guarantee provided by the CommandBus implementation, is that the command has
     * been successfully received. Implementations are highly recommended to perform basic validation of the command
     * before returning from this method call.
     *
     * @param command  The Command to dispatch
     * @param callback The callback to invoke when command processing is complete
     * @throws NoHandlerForCommandException when no command handler is registered for the given <code>command</code>.
     */
    <T> void dispatch(Object command, CommandCallback<T> callback);

    /**
     * Subscribe the given <code>handler</code> to commands of type <code>commandType</code>.
     * <p/>
     * If a subscription already exists for the given type, the behavior is undefined. Implementations may throw an
     * Exception to refuse duplicate subscription or alternatively decide whether the existing or new
     * <code>handler</code> gets the subscription.
     *
     * @param commandType The type of command to subscribe the handler to
     * @param handler     The handler instance that handles the given type of command
     * @param <T>         The Type of command
     */
    <T> void subscribe(Class<T> commandType, CommandHandler<? super T> handler);

    /**
     * Unsubscribe the given <code>handler</code> to commands of type <code>commandType</code>. If the handler is not
     * currently assigned to that type of command, no action is taken.
     *
     * @param commandType The type of command the handler is subscribed to
     * @param handler     The handler instance to unsubscribe from the CommandBus
     * @param <T>         The Type of command
     */
    <T> void unsubscribe(Class<T> commandType, CommandHandler<? super T> handler);

}
