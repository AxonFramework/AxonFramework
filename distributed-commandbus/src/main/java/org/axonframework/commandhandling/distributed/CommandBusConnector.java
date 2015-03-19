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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;

/**
 * Interface describing the component that remotely connects multiple CommandBus instances.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface CommandBusConnector {

    /**
     * Sends the given <code>command</code> to the node assigned to handle messages with the given
     * <code>routingKey</code>. The sender does not expect a reply.
     * <p/>
     * If this method throws an exception, the sender is guaranteed that the destination of the command did not receive
     * it. If the method returns normally, the actual implementation of the connector defines the delivery guarantees.
     * <p/>
     * Connectors route the commands based on the given <code>routingKey</code>. Using the same <code>routingKey</code>
     * will result in the command being sent to the same member. Each message must be sent to <em>exactly one
     * member</em>.
     *
     * @param routingKey The key describing the routing requirements of this command. Generally, commands with the same
     *                   routingKey will be sent to the same destination.
     * @param command    The command to send to the (remote) member
     * @throws Exception when an error occurs before or during the sending of the message
     */
    <C> void send(String routingKey, CommandMessage<C> command) throws Exception;

    /**
     * Sends the given <code>command</code> to the node assigned to handle messages with the given
     * <code>routingKey</code>. The sender expect a reply, and will be notified of the result in the given
     * <code>callback</code>.
     * <p/>
     * If this method throws an exception, the sender is guaranteed that the destination of the command did not receive
     * it. If the method returns normally, the actual implementation of the connector defines the delivery guarantees.
     * Implementations <em>should</em> always invoke the callback with an outcome.
     * <p/>
     * If a member's connection was lost, and the result of the command is unclear, the {@link
     * CommandCallback#onFailure(org.axonframework.commandhandling.CommandMessage, Throwable)} method is invoked with a
     * {@link RemoteCommandHandlingException} describing
     * the failed connection. A client may choose to resend a command.
     * <p/>
     * Connectors route the commands based on the given <code>routingKey</code>. Using the same <code>routingKey</code>
     * will result in the command being sent to the same member.
     *
     * @param routingKey The key describing the routing requirements of this command. Generally, commands with the same
     *                   routingKey will be sent to the same destination.
     * @param command    The command to send to the (remote) member
     * @param callback   The callback on which result notifications are sent
     * @param <R>        The type of object expected as return value in the callback
     * @throws Exception when an error occurs before or during the sending of the message
     */
    <C, R> void send(String routingKey, CommandMessage<C> command, CommandCallback<? super C, R> callback)
            throws Exception;

    /**
     * Subscribe the given <code>handler</code> to commands of type <code>commandType</code> to the local segment of
     * the
     * command bus.
     * <p/>
     * If a subscription already exists for the given type, the behavior is undefined. Implementations may throw an
     * Exception to refuse duplicate subscription or alternatively decide whether the existing or new
     * <code>handler</code> gets the subscription.
     *
     * @param commandName The name of the command to subscribe the handler to
     * @param handler     The handler instance that handles the given type of command
     * @param <C>         The Type of command
     */
    <C> void subscribe(String commandName, CommandHandler<? super C> handler);

    /**
     * Unsubscribe the given <code>handler</code> to commands of type <code>commandType</code>. If the handler is not
     * currently assigned to that type of command, no action is taken.
     *
     * @param commandName The name of the command the handler is subscribed to
     * @param handler     The handler instance to unsubscribe from the CommandBus
     * @param <C>         The Type of command
     * @return <code>true</code> of this handler is successfully unsubscribed, <code>false</code> of the given
     * <code>handler</code> was not the current handler for given <code>commandType</code>.
     */
    <C> boolean unsubscribe(String commandName, CommandHandler<? super C> handler);
}
