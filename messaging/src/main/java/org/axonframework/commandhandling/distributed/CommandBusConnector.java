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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptorSupport;
import org.axonframework.messaging.RemoteHandlingException;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

/**
 * Interface describing the component that remotely connects multiple CommandBus instances.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface CommandBusConnector extends MessageHandlerInterceptorSupport<CommandMessage<?>> {

    /**
     * Sends the given {@code command} to the node assigned to handle messages with the given {@code routingKey}. The
     * sender does not expect a reply.
     * <p/>
     * If this method throws an exception, the sender is guaranteed that the destination of the command did not receive
     * it. If the method returns normally, the actual implementation of the connector defines the delivery guarantees.
     * <p/>
     * Connectors route the commands based on the given {@code routingKey}. Using the same {@code routingKey} will
     * result in the command being sent to the same member. Each message must be sent to <em>exactly one member</em>.
     *
     * @param destination The member of the network to send the message to
     * @param command     The command to send to the (remote) member
     * @throws Exception when an error occurs before or during the sending of the message
     */
    <C> void send(@Nonnull Member destination, @Nonnull CommandMessage<? extends C> command) throws Exception;

    /**
     * Sends the given {@code command} to the node assigned to handle messages with the given {@code routingKey}. The
     * sender expect a reply, and will be notified of the result in the given {@code callback}.
     * <p/>
     * If this method throws an exception, the sender is guaranteed that the destination of the command did not receive
     * it. If the method returns normally, the actual implementation of the connector defines the delivery guarantees.
     * Implementations <em>should</em> always invoke the callback with an outcome.
     * <p/>
     * If a member's connection was lost, and the result of the command is unclear, the {@link
     * CommandCallback#onResult(CommandMessage, CommandResultMessage)}} method is invoked with a {@link
     * RemoteHandlingException} describing the failed connection. A client may choose to resend a command.
     * <p/>
     * Connectors route the commands based on the given {@code routingKey}. Using the same {@code routingKey} will
     * result in the command being sent to the same member.
     *
     * @param destination The member of the network to send the message to
     * @param command     The command to send to the (remote) member
     * @param callback    The callback
     * @param <C>         The type of object expected as command
     * @param <R>         The type of object expected as result of the command
     * @throws Exception when an error occurs before or during the sending of the message
     */
    <C, R> void send(@Nonnull Member destination, @Nonnull CommandMessage<C> command,
                     @Nonnull CommandCallback<? super C, R> callback)
            throws Exception;

    /**
     * Subscribes a command message handler for commands with given {@code commandName}.
     *
     * @param commandName the command name. Usually this equals the fully qualified class name of the command.
     * @param handler     the handler to subscribe
     * @return a handle that can be used to end the subscription
     */
    Registration subscribe(@Nonnull String commandName, @Nonnull MessageHandler<? super CommandMessage<?>, ? extends CommandResultMessage<?>> handler);

    /**
     * Return an {@link Optional} containing the {@link CommandBus} which is used by this {@link CommandBusConnector} to
     * dispatch local and incoming {@link CommandMessage}s on. It is <b>highly recommended</b> to implement this method
     * to ensure an actual CommandBus is provided instead of a default {@link Optional#empty()}.
     *
     * @return an {@link Optional} containing the {@link CommandBus} which is used by this {@link CommandBusConnector}
     * to dispatch local and incoming {@link CommandMessage}s on
     */
    default Optional<CommandBus> localSegment() {
        return Optional.empty();
    }

    /**
     * Initiate the shutdown of a {@link CommandBusConnector}. {@link CommandMessage}s should no longer be dispatched
     * after this method has been invoked.
     *
     * @return a {@link CompletableFuture} indicating when all previously sent commands are completed
     */
    default CompletableFuture<Void> initiateShutdown() {
        return CompletableFuture.completedFuture(null);
    }
}
