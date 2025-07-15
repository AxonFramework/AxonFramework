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

package org.axonframework.commandhandling.distributed;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * The {@code CommandBusConnector} interface defines the contract for connecting multiple {@code CommandBus} instances.
 * It allows for the dispatching of commands across different command bus instances, whether they are local or remote.
 * <p>
 * One connector can be wrapped with another through the {@link WrappedCommandBusConnector}, upon which more
 * functionality can be added, such as payload conversion or serialization.
 *
 * @author Mitchell Herrijgers
 * @author Allard Buijze
 * @since 5.0.0
 */
public interface CommandBusConnector {

    /**
     * Dispatches the given {@code command} to the appropriate command bus, which may be local or remote.
     *
     * @param command           The command message to dispatch.
     * @param processingContext The processing context for the command.
     * @return A {@link CompletableFuture} that will complete with the result of the command handling.
     */
    @Nonnull
    CompletableFuture<CommandResultMessage<?>> dispatch(@Nonnull CommandMessage<?> command,
                                                        @Nullable ProcessingContext processingContext);

    /**
     * Subscribes to a command with the given {@code commandName} and a {@code loadFactor}.
     *
     * @param commandName The name of the command to subscribe to, typically derived from the command's
     *                    {@link org.axonframework.messaging.QualifiedName}.
     * @param loadFactor  The load factor for the command, which can be used to control the distribution of command
     *                    handling across multiple instances.
     */
    void subscribe(@Nonnull String commandName, int loadFactor);

    /**
     * Unsubscribes from a command with the given {@code commandName}.
     *
     * @param commandName The name of the command to unsubscribe from.
     * @return {@code true} if the unsubscription was successful, {@code false} otherwise.
     */
    boolean unsubscribe(@Nonnull String commandName);

    /**
     * Registers a handler that will be called when an incoming command is received. The handler should process the
     * command and call the provided {@code ResultCallback} to indicate success or failure.
     *
     * @param handler A {@link BiConsumer} that takes a {@link CommandMessage} and a {@link ResultCallback}.
     */
    void onIncomingCommand(@Nonnull Handler handler);

    /**
     * A functional interface representing a handler for incoming command messages. The handler processes the command
     * and uses the provided {@link ResultCallback} to report the result.
     */
    @FunctionalInterface
    interface Handler {

        /**
         * Handles the incoming command message.
         *
         * @param commandMessage The command message to handle.
         * @param callback       The callback to invoke with the result of handling the command.
         */
        void handle(@Nonnull CommandMessage<?> commandMessage, @Nonnull ResultCallback callback);
    }

    /**
     * A callback interface for handling the result of command processing. It provides methods to indicate success or
     * failure of command handling.
     */
    interface ResultCallback {

        /**
         * Called when the command processing is successful.
         *
         * @param resultMessage The result message containing the outcome of the command processing. If the message
         *                      handling yielded no result message, a {@code null} should be passed.
         */
        void success(@Nullable Message<?> resultMessage);

        /**
         * Called when an error occurs during command processing.
         *
         * @param cause The exception that caused the error.
         */
        void error(@Nonnull Throwable cause);
    }
}
