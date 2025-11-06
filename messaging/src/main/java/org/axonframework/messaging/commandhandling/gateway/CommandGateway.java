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

package org.axonframework.messaging.commandhandling.gateway;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.commandhandling.CommandExecutionException;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;

/**
 * Interface towards the Command Handling components of an application.
 * <p>
 * This interface provides a friendlier API toward the {@link CommandBus}. The
 * {@code CommandGateway} allows for components dispatching commands to wait for the result.
 *
 * @author Allard Buijze
 * @see DefaultCommandGateway
 * @since 2.0.0
 */
public interface CommandGateway extends DescribableComponent {

    /**
     * Sends the given {@code command} with the given {@code metadata} in the provided {@code context} (if available)
     * and returns a {@link CommandResult} immediately, without waiting for the command to execute.
     * <p>
     * The caller will therefore not receive any immediate feedback on the {@code command's} execution. Instead, hooks
     * <em>can</em> be added to the returned {@code CommandResult} to react on success or failure of command
     * execution. A shorthand to retrieve a {@link CompletableFuture} is available through the
     * {@link CommandResult#getResultMessage()} operation.
     * <p>
     * Note that this operation expects the {@link CommandBus} to use new threads for
     * command execution.
     * <p/>
     * The given {@code command} and {@code metadata} are wrapped as the payload of the
     * {@link CommandMessage} that is eventually posted on the
     * {@link CommandBus}, unless the {@code command} already implements
     * {@link Message}. In that case, a {@code CommandMessage} is constructed from that
     * message's payload and {@link org.axonframework.messaging.core.Metadata}. The provided {@code metadata} is attached
     * afterward in this case.
     *
     * @param command  The command payload or {@link CommandMessage} to send.
     * @param metadata Meta-data that must be registered with the {@code command}.
     * @param context  The processing context, if any, to dispatch the given {@code command} in.
     * @return A command result success and failure hooks can be registered. The
     * {@link CommandResult#getResultMessage()} serves as a shorthand to retrieve the response.
     */
    @Nonnull
    CommandResult send(@Nonnull Object command, @Nonnull Metadata metadata, @Nullable ProcessingContext context);

    /**
     * Sends the given {@code command} in the provided {@code context} (if available) and returns a
     * {@link CommandResult} immediately, without waiting for the command to execute.
     * <p>
     * The caller will therefore not receive any immediate feedback on the {@code command's} execution. Instead, hooks
     * <em>can</em> be added to the returned {@code CommandResult} to react on success or failure of command
     * execution. A shorthand to retrieve a {@link CompletableFuture} is available through the
     * {@link CommandResult#getResultMessage()} operation.
     * <p>
     * Note that this operation expects the {@link CommandBus} to use new threads for
     * command execution.
     * <p/>
     * The given {@code command} is wrapped as the payload of the
     * {@link CommandMessage} that is eventually posted on the {@code CommandBus},
     * unless the {@code command} already implements {@link org.axonframework.messaging.core.Message}. In that case, a
     * {@code CommandMessage} is constructed from that message's payload and
     * {@link org.axonframework.messaging.core.Metadata}.
     *
     * @param command The command payload or {@link CommandMessage} to send.
     * @return A command result success and failure hooks can be registered. The
     * {@link CommandResult#getResultMessage()} serves as a shorthand to retrieve the response.
     * @see CommandGateway#send(Object, Metadata, ProcessingContext)
     */
    @Nonnull
    default CommandResult send(@Nonnull Object command) {
        return send(command, Metadata.emptyInstance(), null);
    }

    /**
     * Sends the given {@code command} in the provided {@code context} (if available) and returns a
     * {@link CommandResult} immediately, without waiting for the command to execute.
     * <p>
     * The caller will therefore not receive any immediate feedback on the {@code command's} execution. Instead, hooks
     * <em>can</em> be added to the returned {@code CommandResult} to react on success or failure of command
     * execution. A shorthand to retrieve a {@link CompletableFuture} is available through the
     * {@link CommandResult#getResultMessage()} operation.
     * <p>
     * Note that this operation expects the {@link CommandBus} to use new threads for
     * command execution.
     * <p/>
     * The given {@code command} is wrapped as the payload of the
     * {@link CommandMessage} that is eventually posted on the {@code CommandBus},
     * unless the {@code command} already implements {@link Message}. In that case, a
     * {@code CommandMessage} is constructed from that message's payload and
     * {@link Metadata}.
     *
     * @param command The command payload or {@link CommandMessage} to send.
     * @param context The processing context, if any, to dispatch the given {@code command} in.
     * @return A command result success and failure hooks can be registered. The
     * {@link CommandResult#getResultMessage()} serves as a shorthand to retrieve the response.
     * @see CommandGateway#send(Object, Metadata, ProcessingContext)
     */
    @Nonnull
    default CommandResult send(@Nonnull Object command,
                               @Nullable ProcessingContext context) {
        return send(command, Metadata.emptyInstance(), context);
    }

    /**
     * Sends the given {@code command} with the given {@code metadata} in the provided {@code context} (if available)
     * and returns a {@link CommandResult} immediately, without waiting for the command to execute.
     * <p>
     * The caller will therefore not receive any immediate feedback on the {@code command's} execution. Instead, hooks
     * <em>can</em> be added to the returned {@code CommandResult} to react on success or failure of command
     * execution. A shorthand to retrieve a {@link CompletableFuture} is available through the
     * {@link CommandResult#getResultMessage()} operation.
     * <p>
     * Note that this operation expects the {@link CommandBus} to use new threads for
     * command execution.
     * <p/>
     * The given {@code command} and {@code metadata} are wrapped as the payload of the
     * {@link CommandMessage} that is eventually posted on the
     * {@link CommandBus}, unless the {@code command} already implements
     * {@link org.axonframework.messaging.core.Message}. In that case, a {@code CommandMessage} is constructed from that
     * message's payload and {@link org.axonframework.messaging.core.Metadata}. The provided {@code metadata} is attached
     * afterward in this case.
     *
     * @param command  The command payload or {@link CommandMessage} to send.
     * @param metadata Meta-data that must be registered with the {@code command}.
     * @return A command result success and failure hooks can be registered. The
     * {@link CommandResult#getResultMessage()} serves as a shorthand to retrieve the response.
     * @see CommandGateway#send(Object, Metadata, ProcessingContext)
     */
    @Nonnull
    default CommandResult send(@Nonnull Object command,
                               @Nonnull Metadata metadata) {
        return send(command, metadata, null);
    }

    /**
     * Sends the given {@code command} and returns a {@link CompletableFuture} immediately, without waiting for the
     * command to execute.
     * <p>
     * The caller will therefore not receive any immediate feedback on the {@code command's} execution. Instead, hooks
     * <em>can</em> be added to the returned {@code CompletableFuture} to react on success or failure of command
     * execution.
     * <p>
     * Note that this operation expects the {@link CommandBus} to use new threads for
     * command execution.
     * <p/>
     * The given {@code command} is wrapped as the payload of the {@link CommandMessage} that is eventually posted on
     * the {@code CommandBus}, unless the {@code command} already implements {@link Message}. In that case, a
     * {@code CommandMessage} is constructed from that message's payload and
     * {@link Metadata}.
     *
     * @param <R>        The generic type of the expected response.
     * @param command    The command payload or {@link CommandMessage} to send.
     * @param resultType The class representing the type of the expected command result.
     * @return A {@link CompletableFuture} that will be resolved successfully or exceptionally based on the eventual
     * command execution result.
     * @see CommandGateway#send(Object, Class, ProcessingContext)
     */
    @Nonnull
    default <R> CompletableFuture<R> send(@Nonnull Object command,
                                          @Nonnull Class<R> resultType) {
        return send(command, resultType, null);
    }

    /**
     * Sends the given {@code command} and returns a {@link CompletableFuture} immediately, without waiting for the
     * command to execute.
     * <p>
     * The caller will therefore not receive any immediate feedback on the {@code command's} execution. Instead, hooks
     * <em>can</em> be added to the returned {@code CompletableFuture} to react on success or failure of command
     * execution.
     * <p>
     * Note that this operation expects the {@link CommandBus} to use new threads for
     * command execution.
     * <p/>
     * The given {@code command} is wrapped as the payload of the {@link CommandMessage} that is eventually posted on
     * the {@code CommandBus}, unless the {@code command} already implements {@link Message}. In that case, a
     * {@code CommandMessage} is constructed from that message's payload and
     * {@link Metadata}.
     *
     * @param <R>        The generic type of the expected response.
     * @param command    The command payload or {@link CommandMessage} to send.
     * @param resultType The class representing the type of the expected command result.
     * @param context    The processing context, if any, to dispatch the given {@code command} in.
     * @return A {@link CompletableFuture} that will be resolved successfully or exceptionally based on the eventual
     * command execution result.
     * @see CommandGateway#send(Object, ProcessingContext)
     */
    @Nonnull
    default <R> CompletableFuture<R> send(@Nonnull Object command,
                                          @Nonnull Class<R> resultType,
                                          @Nullable ProcessingContext context) {
        return send(command, context).resultAs(resultType);
    }

    /**
     * Send the given {@code command} and waits for completion.
     * <p>
     * If the command was successful, its result (if any) is discarded. If it was unsuccessful an exception is thrown.
     * Any checked exceptions that may occur as the result of running the command will be wrapped in a
     * {@link CommandExecutionException}.
     * <p>
     * If the result is needed, use {@link #sendAndWait(Object, Class)} instead, as it allows for type conversion of the
     * result payload.
     *
     * @param command The command payload or {@link CommandMessage} to send.
     * @return The payload of the result message, or {@code null} when none is present.
     * @throws CommandExecutionException When a checked exception occurs while handling the command.
     * @see CommandGateway#sendAndWait(Object, Class)
     */
    @Nullable
    default Object sendAndWait(@Nonnull Object command) {
        return sendAndWait(command, Object.class);
    }

    /**
     * Send the given {@code command} and waits for completion.
     * <p>
     * If the command was successful, its result (if any) is discarded. If it was unsuccessful an exception is thrown.
     * Any checked exceptions that may occur as the result of running the command will be wrapped in a
     * {@link CommandExecutionException}.
     * <p>
     * If the result is needed, use {@link #sendAndWait(Object, Class)} instead, as it allows for type conversion of the
     * result payload.
     *
     * @param command The command payload or {@link CommandMessage} to send.
     * @param context The processing context, if any, to dispatch the given {@code command} in.
     * @return The payload of the result message, or {@code null} when none is present.
     * @throws CommandExecutionException When a checked exception occurs while handling the command.
     * @see CommandGateway#sendAndWait(Object, Class, ProcessingContext)
     */
    @Nullable
    default Object sendAndWait(@Nonnull Object command,
                               @Nullable ProcessingContext context) {
        return sendAndWait(command, Object.class, context);
    }

    /**
     * Send the given {@code command} and waits for the result converted to the {@code resultType}.
     * <p>
     * If the command was successful, its result will be converted to the specified {@code returnType} and returned. If
     * it was unsuccessful or conversion failed, an exception is thrown. Any checked exceptions that may occur as the
     * result of running the command will be wrapped in a {@link CommandExecutionException}.
     *
     * @param command    The command payload or {@link CommandMessage} to send.
     * @param resultType The class representing the type of the expected command result.
     * @param <R>        The generic type of the expected response.
     * @return The payload of the result message of type {@code R}, or {@code null} when none is present.
     * @throws CommandExecutionException When a checked exception occurs while handling the command.
     * @see CommandGateway#sendAndWait(Object, Class, ProcessingContext)
     */
    @Nullable
    default <R> R sendAndWait(@Nonnull Object command,
                              @Nonnull Class<R> resultType) {
        return sendAndWait(command, resultType, null);
    }

    /**
     * Send the given {@code command} and waits for the result converted to the {@code resultType}.
     * <p>
     * If the command was successful, its result will be converted to the specified {@code returnType} and returned. If
     * it was unsuccessful or conversion failed, an exception is thrown. Any checked exceptions that may occur as the
     * result of running the command will be wrapped in a {@link CommandExecutionException}.
     *
     * @param command    The command payload or {@link CommandMessage} to send.
     * @param resultType The class representing the type of the expected command result.
     * @param context    The processing context, if any, to dispatch the given {@code command} in.
     * @param <R>        The generic type of the expected response.
     * @return The payload of the result message of type {@code R}, or {@code null} when none is present.
     * @throws CommandExecutionException When a checked exception occurs while handling the command.
     */
    @Nullable
    default <R> R sendAndWait(@Nonnull Object command,
                              @Nonnull Class<R> resultType,
                              @Nullable ProcessingContext context) {
        return send(command, context).wait(resultType);
    }
}
