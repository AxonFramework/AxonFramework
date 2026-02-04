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
import io.axoniq.axonserver.grpc.command.Command;
import io.axoniq.axonserver.grpc.command.CommandResponse;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.common.Assert;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.lifecycle.Phase;
import org.axonframework.common.lifecycle.ShutdownLatch;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.CommandResultMessage;
import org.axonframework.messaging.commandhandling.distributed.CommandBusConnector;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

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
    private final String clientId;
    private final String componentName;

    private Handler incomingHandler;
    private final Map<QualifiedName, Registration> subscriptions = new ConcurrentHashMap<>();
    private final ShutdownLatch shutdownLatch = new ShutdownLatch();
    private final ConcurrentHashMap<String, CompletableFuture<?>> commandsInProgress = new ConcurrentHashMap<>();

    /**
     * Creates a new {@code AxonServerConnector} that communicate with Axon Server using the provided
     * {@code connection}.
     *
     * @param connection    The {@code AxonServerConnection} to communicate to Axon Server with.
     * @param configuration The Axon Server configuration, used to retrieve (e.g.) the
     *                      {@link AxonServerConfiguration#getClientId()} to be set when
     *                      {@link #dispatch(CommandMessage, ProcessingContext) dispatching} commands.
     */
    public AxonServerCommandBusConnector(@Nonnull AxonServerConnection connection,
                                         @Nonnull AxonServerConfiguration configuration) {
        this.connection = Objects.requireNonNull(connection, "The AxonServerConnection must not be null.");
        Objects.requireNonNull(configuration, "The AxonServerConfiguration must not be null.");
        this.clientId = configuration.getClientId();
        this.componentName = configuration.getComponentName();
    }

    /**
     * Starts the Axon Server {@link CommandBusConnector} implementation.
     */
    public void start() {
        shutdownLatch.initialize();
        logger.trace("The AxonServerCommandBusConnector started.");
    }

    @Nonnull
    @Override
    public CompletableFuture<CommandResultMessage> dispatch(@Nonnull CommandMessage command,
                                                            @Nullable ProcessingContext processingContext) {
        shutdownLatch.ifShuttingDown("Cannot dispatch new commands as this bus is being shutdown");
        try (ShutdownLatch.ActivityHandle commandInTransit = shutdownLatch.registerActivity()) {
            return connection.commandChannel()
                             .sendCommand(CommandConverter.convertCommandMessage(command, clientId, componentName))
                             .thenCompose(CommandConverter::convertCommandResponse)
                             .whenComplete((commandResponse, throwable) -> commandInTransit.end());
        }
    }

    @Override
    public CompletableFuture<Void> subscribe(@Nonnull QualifiedName commandName, int loadFactor) {
        Assert.isTrue(loadFactor >= 0, () -> "Load factor must be greater than 0.");
        logger.debug("Subscribing to command [{}] with load factor [{}]", commandName, loadFactor);
        Registration registration = connection.commandChannel()
                                              .registerCommandHandler(this::handle, loadFactor, commandName.name());

        this.subscriptions.put(commandName, registration);
        CompletableFuture<Void> completion = new CompletableFuture<>();
        registration.onAck(() -> completion.complete(null));
        return completion;
    }

    private CompletableFuture<CommandResponse> handle(Command command) {
        logger.debug("Received incoming command [{}]", command.getName());
        try {
            CompletableFuture<CommandResponse> result = new CompletableFuture<CommandResponse>()
                    .whenComplete((r, e) -> commandsInProgress.remove(command.getMessageIdentifier()));
            commandsInProgress.put(command.getMessageIdentifier(), result);
            incomingHandler.handle(CommandConverter.convertCommand(command),
                                   new FutureResultCallback(result, command));
            return result;
        } catch (Exception e) {
            logger.error("Error processing incoming command: {}", command.getName(), e);
            commandsInProgress.remove(command.getMessageIdentifier());
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
    public void onIncomingCommand(@Nonnull Handler handler) {
        this.incomingHandler = handler;
    }

    /**
     * Disconnect the command bus for receiving commands from Axon Server, by unsubscribing all registered command
     * handlers and waiting for in-flight commands to complete.
     * <p>
     * This shutdown operation is performed in the {@link Phase#INBOUND_COMMAND_CONNECTOR}
     * phase.
     *
     * @return A completable future that completed once the {@link AxonServerConnection#commandChannel()} has prepared
     * disconnected and handled all in-flight incoming messages.
     */
    public CompletableFuture<Void> disconnect() {
        if (!connection.isConnected()) {
            return CompletableFuture.completedFuture(null);
        }
        logger.trace("Disconnecting the AxonServerCommandBusConnector.");
        return connection.commandChannel()
                         .prepareDisconnect()
                         .thenCompose(r -> commandsInProgress.values()
                                                             .stream()
                                                             .reduce(FutureUtils.emptyCompletedFuture(),
                                                                     CompletableFuture::allOf))
                         .thenRun(() -> {
                         });
    }

    /**
     * Shutdown the command bus asynchronously for dispatching commands to Axon Server. This process will wait for
     * dispatched commands which have not received a response yet. This shutdown operation is performed in the
     * {@link Phase#OUTBOUND_COMMAND_CONNECTORS} phase.
     *
     * @return A completable future which is resolved once all command dispatching activities are completed.
     */
    public CompletableFuture<Void> shutdownDispatching() {
        logger.trace("Shutting down dispatching of AxonServerCommandBusConnector.");
        return shutdownLatch.initiateShutdown();
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("connection", connection);
        descriptor.describeProperty("clientId", clientId);
        descriptor.describeProperty("componentName", componentName);
    }

    private record FutureResultCallback(
            @Nonnull CompletableFuture<CommandResponse> result,
            @Nonnull Command command
    ) implements ResultCallback {

        @Override
        public void onSuccess(CommandResultMessage resultMessage) {
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