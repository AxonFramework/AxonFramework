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

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.configuration.Configuration;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Component that publishes events to a {@link CommandGateway} in a predefined
 * {@link org.axonframework.messaging.unitofwork.ProcessingContext context}. The events will be published in the context
 * this appender was created for. You can construct one through the
 * {@link #forContext(ProcessingContext, Configuration)}.
 * <p>
 * When using annotation-based {@link org.axonframework.messaging.annotation.MessageHandler @MessageHandler-methods} and
 * you have declared an argument of type {@link CommandDispatcher}, the dispatcher will automatically be injected by the
 * {@link org.axonframework.commandhandling.annotation.CommandDispatcherParameterResolverFactory}.
 * <p>
 * As this component is {@link ProcessingContext}-scoped, it is not retrievable from the {@link Configuration}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface CommandDispatcher extends DescribableComponent {

    /**
     * The {@link Context.ResourceKey} used to store the {@link CommandDispatcher} in the {@link ProcessingContext}.
     */
    Context.ResourceKey<ContextAwareCommandDispatcher> RESOURCE_KEY = Context.ResourceKey.withLabel("CommandDispatcher");

    /**
     * Creates a dispatcher for the given {@link ProcessingContext} and {@link Configuration}.
     * <p>
     * You can use this dispatcher <b>only</b> for the context it was created for. There is no harm in using this method
     * more than once with the same {@code context}, as the same dispatcher will be returned.
     *
     * @param context       The {@link ProcessingContext} to create the dispatcher for.
     * @param configuration The {@link Configuration} to use for the dispatcher.
     * @return The command dispatcher specific for the given {@code context}.
     */
    static CommandDispatcher forContext(@Nonnull ProcessingContext context,
                                        @Nonnull Configuration configuration) {
        Objects.requireNonNull(configuration, "The configuration must not be null.");
        return context.computeResourceIfAbsent(
                RESOURCE_KEY,
                () -> new ContextAwareCommandDispatcher(configuration.getComponent(CommandGateway.class), context)
        );
    }

    /**
     * Sends the given {@code command} and returns a {@link CompletableFuture} immediately, without waiting for the
     * command to execute.
     * <p>
     * The caller will therefore not receive any immediate feedback on the {@code command's} execution. Instead, hooks
     * <em>can</em> be added to the returned {@code CompletableFuture} to react on success or failure of command
     * execution.
     * <p>
     * Note that this operation expects the {@link org.axonframework.commandhandling.CommandBus} to use new threads for
     * command execution.
     * <p/>
     * The given {@code command} is wrapped as the payload of the
     * {@link org.axonframework.commandhandling.CommandMessage} that is eventually posted on the {@code CommandBus},
     * unless the {@code command} already implements {@link org.axonframework.messaging.Message}. In that case, a
     * {@code CommandMessage} is constructed from that message's payload and
     * {@link org.axonframework.messaging.MetaData}.
     *
     * @param command      The command payload or {@link org.axonframework.commandhandling.CommandMessage} to send.
     * @param expectedType The expected result type.
     * @param <R>          The generic type of the expected response.
     * @return A {@link CompletableFuture} that will be resolved successfully or exceptionally based on the eventual
     * command execution result.
     */
    default <R> CompletableFuture<R> send(@Nonnull Object command,
                                          @Nonnull Class<R> expectedType) {
        return send(command).resultAs(expectedType);
    }

    /**
     * Send the given {@code command} and waits for completion, disregarding the result.
     * <p>
     * To retrieve the result, use {@link #sendAndWait(Object, Class)} instead, as it allows for type conversion of the
     * result payload.
     *
     * @param command The command payload or {@link org.axonframework.commandhandling.CommandMessage} to send.
     * @throws CommandExecutionException When an exception occurs while handling the {@code command}.
     */
    default void sendAndWait(@Nonnull Object command) {
        try {
            send(command).getResultMessage().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CommandExecutionException("Thread interrupted while waiting for result", e);
        } catch (ExecutionException e) {
            throw new CommandExecutionException("Exception while handling command", e);
        }
    }

    /**
     * Send the given {@code command} and waits for the result.
     * <p>
     * The result will be converted to the specified {@code returnType} if possible. The payload of the resulting
     * message is returned, or a {@link CommandExecutionException} is thrown when the command completed with an
     * exception.
     *
     * @param command    The command payload or {@link org.axonframework.commandhandling.CommandMessage} to send.
     * @param resultType The class representing the type of the expected command result.
     * @param <R>        The generic type of the expected response.
     * @return The payload of the result message of type {@code R}.
     * @throws CommandExecutionException When an exception occurs while handling the {@code command}.
     */
    default <R> R sendAndWait(@Nonnull Object command,
                              @Nonnull Class<R> resultType) {
        try {
            return send(command).resultAs(resultType).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CommandExecutionException("Thread interrupted while waiting for result", e);
        } catch (ExecutionException e) {
            throw new CommandExecutionException("Exception while handling command", e);
        }
    }

    /**
     * Sends the given {@code command} and returns a {@link CommandResult} immediately, without waiting for the command
     * to execute.
     * <p>
     * The caller will therefore not receive any immediate feedback on the {@code command's} execution. Instead, hooks
     * <em>can</em> be added to the returned {@code CommandResult} to react on success or failure of command
     * execution. A shorthand to retrieve a {@link CompletableFuture} is available through the
     * {@link CommandResult#getResultMessage()} operation.
     * <p>
     * Note that this operation expects the {@link org.axonframework.commandhandling.CommandBus} to use new threads for
     * command execution.
     * <p/>
     * The given {@code command} is wrapped as the payload of the
     * {@link org.axonframework.commandhandling.CommandMessage} that is eventually posted on the {@code CommandBus},
     * unless the {@code command} already implements {@link org.axonframework.messaging.Message}. In that case, a
     * {@code CommandMessage} is constructed from that message's payload and
     * {@link org.axonframework.messaging.MetaData}.
     *
     * @param command The command payload or {@link org.axonframework.commandhandling.CommandMessage} to send.
     * @return A command result success and failure hooks can be registered. The
     * {@link CommandResult#getResultMessage()} serves as a shorthand to retrieve the response.
     */
    CommandResult send(@Nonnull Object command);

    /**
     * Sends the given {@code command} with the given {@code metaData} and returns a {@link CommandResult} immediately,
     * without waiting for the command to execute.
     * <p>
     * The caller will therefore not receive any immediate feedback on the {@code command's} execution. Instead, hooks
     * <em>can</em> be added to the returned {@code CommandResult} to react on success or failure of command
     * execution. A shorthand to retrieve a {@link CompletableFuture} is available through the
     * {@link CommandResult#getResultMessage()} operation.
     * <p>
     * Note that this operation expects the {@link org.axonframework.commandhandling.CommandBus} to use new threads for
     * command execution.
     * <p/>
     * The given {@code command} and {@code metaData} are wrapped as the payload of the
     * {@link org.axonframework.commandhandling.CommandMessage} that is eventually posted on the
     * {@link org.axonframework.commandhandling.CommandBus}, unless the {@code command} already implements
     * {@link org.axonframework.messaging.Message}. In that case, a {@code CommandMessage} is constructed from that
     * message's payload and {@link org.axonframework.messaging.MetaData}. The provided {@code metaData} is attached
     * afterward in this case.
     *
     * @param command  The command payload or {@link org.axonframework.commandhandling.CommandMessage} to send.
     * @param metaData Metadata that must be registered with the {@code command}.
     * @return A command result success and failure hooks can be registered. The
     * {@link CommandResult#getResultMessage()} serves as a shorthand to retrieve the response.
     */
    CommandResult send(@Nonnull Object command,
                       @Nonnull MetaData metaData);
}
