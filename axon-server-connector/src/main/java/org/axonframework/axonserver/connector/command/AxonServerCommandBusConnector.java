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

package org.axonframework.axonserver.connector.command;

import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.Registration;
import io.axoniq.axonserver.connector.impl.AsyncRegistration;
import io.axoniq.axonserver.grpc.command.Command;
import io.axoniq.axonserver.grpc.command.CommandResponse;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.distributed.CommandBusConnector;
import org.axonframework.common.Assert;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * An implementation of the {@link CommandBusConnector} that connects to an Axon Server instance to send and receive
 * commands. It uses the Axon Server gRPC API to communicate with the server.
 *
 * @author Allard Buijze
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class AxonServerCommandBusConnector implements CommandBusConnector {

    private static final Logger logger = LoggerFactory.getLogger(AxonServerCommandBusConnector.class);

    private final AxonServerConnection connection;
    private CommandBusConnector.Handler incomingHandler;
    private final Map<QualifiedName, Registration> subscriptions = new ConcurrentHashMap<>();

    /**
     * Creates a new {@code AxonServerConnector} that communicate with Axon Server using the provided
     * {@code connection}.
     *
     * @param connection The {@code AxonServerConnection} to communicate to Axon Server with.
     */
    public AxonServerCommandBusConnector(@Nonnull AxonServerConnection connection) {
        this.connection = Objects.requireNonNull(connection, "The AxonServerConnection must not be null.");
    }

    @Nonnull
    @Override
    public CompletableFuture<CommandResultMessage<?>> dispatch(@Nonnull CommandMessage command,
                                                               @Nullable ProcessingContext processingContext) {
        return connection.commandChannel()
                         .sendCommand(CommandConverter.convertCommandMessage(command))
                         .thenCompose(CommandConverter::convertCommandResponse);
    }

    @Override
    public void subscribe(@Nonnull QualifiedName commandName, int loadFactor) {
        Assert.isTrue(loadFactor >= 0, () -> "Load factor must be greater than 0.");
        logger.debug("Subscribing to command [{}] with load factor [{}]", commandName, loadFactor);
        Registration registration = connection.commandChannel()
                                              .registerCommandHandler(this::handle, loadFactor, commandName.name());

        // Make sure that when we subscribe and immediately send a command, it can be handled.
        if (registration instanceof AsyncRegistration asyncRegistration) {
            try {
                // Waiting synchronously for the subscription to be acknowledged, this should be improved
                // TODO https://github.com/AxonFramework/AxonFramework/issues/3544
                asyncRegistration.awaitAck(2000, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                throw new RuntimeException(
                        "Timed out waiting for subscription acknowledgment for command: " + commandName, e
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Thread interrupted while waiting for subscription acknowledgment", e);
            }
        }
        this.subscriptions.put(commandName, registration);
    }

    private CompletableFuture<CommandResponse> handle(Command command) {
        logger.debug("Received incoming command [{}]", command.getName());
        try {
            CompletableFuture<CommandResponse> result = new CompletableFuture<>();
            incomingHandler.handle(CommandConverter.convertCommand(command),
                                   new FutureResultCallback(result, command));
            return result;
        } catch (Exception e) {
            logger.error("Error processing incoming command: {}", command.getName(), e);
            CompletableFuture<CommandResponse> errorResult = new CompletableFuture<>();
            errorResult.completeExceptionally(e);
            return errorResult;
        }
    }

    @Override
    public boolean unsubscribe(@Nonnull QualifiedName commandName) {
        Registration subscription = subscriptions.remove(commandName);
        if (subscription != null) {
            subscription.cancel();
            return true;
        }
        return false;
    }

    @Override
    public void onIncomingCommand(@Nonnull CommandBusConnector.Handler handler) {
        this.incomingHandler = handler;
    }

    private record FutureResultCallback(
            @Nonnull CompletableFuture<CommandResponse> result,
            @Nonnull Command command
    ) implements ResultCallback {

        @Override
        public void onSuccess(CommandResultMessage<?> resultMessage) {
            logger.debug("Command [{}] completed successfully with result [{}]", command.getName(), resultMessage);
            result.complete(CommandConverter.convertResultMessage(resultMessage, command.getMessageIdentifier()));
        }

        @Override
        public void onError(@Nonnull Throwable cause) {
            logger.info("Command [{}] raised an exception [{}]", command.getName(), cause.getMessage());
            result.completeExceptionally(cause);
        }
    }
}