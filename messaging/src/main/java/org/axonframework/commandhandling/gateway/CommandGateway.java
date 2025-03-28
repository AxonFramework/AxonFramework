/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.commandhandling.gateway;

import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Interface towards the Command Handling components of an application. This interface provides a friendlier API toward
 * the {@link org.axonframework.commandhandling.CommandBus}. The {@code CommandGateway} allows for components
 * dispatching commands to wait for the result.
 *
 * @author Allard Buijze
 * @see DefaultCommandGateway
 * @since 2.0
 */
public interface CommandGateway {

    /**
     * Sends the given {@code command} and returns a {@link CompletableFuture} immediately, without waiting for the
     * command to execute. The caller will therefore not receive any immediate feedback on the {@code command}'s
     * execution. Instead, hooks <em>can</em> be added to the returned {@code CompletableFuture} to react on success or
     * failure of command execution. Note that this operation expects the
     * {@link org.axonframework.commandhandling.CommandBus} to use new threads for command execution.
     * <p/>
     * The given {@code command} is wrapped as the payload of the {@link CommandMessage} that is eventually posted on
     * the {@code CommandBus}, unless the {@code command} already implements {@link Message}. In that case, a
     * {@code CommandMessage} is constructed from that message's payload and
     * {@link org.axonframework.messaging.MetaData}.
     *
     * @param command The command to dispatch.
     * @return A {@link CompletableFuture} which will be resolved successfully or exceptionally based on the eventual
     * command execution result.
     */
    default <R> CompletableFuture<R> send(@Nonnull Object command,
                                          @Nullable ProcessingContext processingContext,
                                          Class<R> expectedType) {
        return send(command, processingContext).resultAs(expectedType);
    }

    /**
     * Sends the given {@code command} and returns a {@link CompletableFuture} immediately, without waiting for the
     * command to execute. The caller will therefore not receive any immediate feedback on the {@code command}'s
     * execution. Instead, hooks <em>can</em> be added to the returned {@code CompletableFuture} to react on success or
     * failure of command execution. Note that this operation expects the
     * {@link org.axonframework.commandhandling.CommandBus} to use new threads for command execution.
     * <p/>
     * The given {@code command} is wrapped as the payload of the {@link CommandMessage} that is eventually posted on
     * the {@code CommandBus}, unless the {@code command} already implements {@link Message}. In that case, a
     * {@code CommandMessage} is constructed from that message's payload and
     * {@link org.axonframework.messaging.MetaData}.
     *
     * @param command           The command to dispatch.
     * @param processingContext A processing context, if any, that is currently active
     * @return A {@link CompletableFuture} which will be resolved successfully or exceptionally based on the eventual
     * command execution result.
     */
    CommandResult send(@Nonnull Object command,
                       @Nullable ProcessingContext processingContext);

    /**
     * Sends the given {@code command} with the given {@code metaData} and returns a {@link CompletableFuture}
     * immediately, without waiting for the command to execute. The caller will therefore not receive any immediate
     * feedback on the {@code command}'s execution. Instead, hooks <em>can</em> be added to the returned
     * {@code CompletableFuture} to react on success or failure of command execution.
     * <p>
     * Note that this operation expects the {@link org.axonframework.commandhandling.CommandBus} to use new threads for
     * command execution.
     * <p/>
     * The given {@code command} and {@code metaData} are wrapped as the payload of the {@link CommandMessage} that is
     * eventually posted on the {@link org.axonframework.commandhandling.CommandBus}, unless the {@code command} already
     * implements {@link Message}. In that case, a {@code CommandMessage} is constructed from that message's payload and
     * {@link org.axonframework.messaging.MetaData}. The provided {@code metaData} is attached afterward in this case.
     *
     * @param command           The command to dispatch.
     * @param metaData          Meta-data that must be registered with the {@code command}.
     * @param processingContext A processing context, if any, that is currently active
     * @return A {@link CompletableFuture} which will be resolved successfully or exceptionally based on the eventual
     * command execution result.
     */
    CommandResult send(@Nonnull Object command,
                       @Nonnull MetaData metaData,
                       @Nullable ProcessingContext processingContext);

    /**
     * Send the given command and wait for the result. The payload of the resulting message is returned, or a
     * {@link CommandExecutionException} is thrown when the command completed with an exception.
     * <p>
     * Note that there is no timeout on the waiting for the result. Use {@link #send(Object, ProcessingContext)} for
     * more flexibility around waiting and dealing with response metadata.
     *
     * @param command The payload or Command Message to send
     * @return the payload of the result message
     * @throws CommandExecutionException when an exception occurs while handling the command
     */
    default Object sendAndWait(@Nonnull Object command) {
        try {
            return send(command, ProcessingContext.NONE)
                    .getResultMessage()
                    // TODO #3077 - Find a more elegant solution for this, if applicable
                    .thenApply(message -> message != null ? message.getPayload() : null)
                    .get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CommandExecutionException("Thread interrupted while waiting for result", e);
        } catch (ExecutionException e) {
            throw new CommandExecutionException("Exception while handling command", e);
        }
    }

    /**
     * Send the given command and wait for the result. The payload of the resulting message is returned, or a
     * {@link CommandExecutionException} is thrown when the command completed with an exception.
     * <p>
     * Note that there is no timeout on the waiting for the result. Use {@link #send(Object, ProcessingContext, Class)}
     * for more flexibility around waiting or {@link #send(Object, ProcessingContext)} for more flexibility regarding
     * the return type.
     *
     * @param command    The payload or Command Message to send
     * @param returnType The class representing the expected response type
     * @param <T>        The generic type of the expected response
     * @return the payload of the result message
     * @throws CommandExecutionException when an exception occurs while handling the command
     */
    default <T> T sendAndWait(@Nonnull Object command, Class<T> returnType) {
        try {
            return send(command, ProcessingContext.NONE).resultAs(returnType).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CommandExecutionException("Thread interrupted while waiting for result", e);
        } catch (ExecutionException e) {
            throw new CommandExecutionException("Exception while handling command", e);
        }
    }
}
