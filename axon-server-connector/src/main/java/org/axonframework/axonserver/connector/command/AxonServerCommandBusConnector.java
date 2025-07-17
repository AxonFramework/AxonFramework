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

import com.google.protobuf.ByteString;
import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.Registration;
import io.axoniq.axonserver.connector.impl.AsyncRegistration;
import io.axoniq.axonserver.grpc.MetaDataValue;
import io.axoniq.axonserver.grpc.ProcessingKey;
import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.command.Command;
import io.axoniq.axonserver.grpc.command.CommandResponse;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.axonserver.connector.AxonServerMetadataConverter;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.util.ProcessingInstructionHelper;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.commandhandling.distributed.CommandBusConnector;
import org.axonframework.common.Assert;
import org.axonframework.common.AxonException;
import org.axonframework.common.FutureUtils;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.axonframework.axonserver.connector.AxonServerMetadataConverter.convertFromMetaDataValues;
import static org.axonframework.axonserver.connector.util.ProcessingInstructionHelper.createProcessingInstruction;
import static org.axonframework.axonserver.connector.util.ProcessingInstructionHelper.priority;
import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * An implementation of {@link CommandBusConnector} that connects to an Axon Server instance to send and receive
 * commands. It uses the Axon Server gRPC API to communicate with the server.
 *
 * @author Allard Buijze
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class AxonServerCommandBusConnector implements CommandBusConnector {

    private static final Logger logger = LoggerFactory.getLogger(AxonServerCommandBusConnector.class);

    private final AxonServerConnection connection;
    private final AtomicReference<CommandBusConnector.Handler> incomingHandler = new AtomicReference<>();
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
    public CompletableFuture<CommandResultMessage<?>> dispatch(@Nonnull CommandMessage<?> command,
                                                               @Nullable ProcessingContext processingContext) {
        return connection.commandChannel()
                         .sendCommand(buildOutgoingCommand(command))
                         .thenCompose(this::buildResultMessage);
    }

    private CompletableFuture<CommandResultMessage<?>> buildResultMessage(CommandResponse commandResponse) {
        if (commandResponse.hasErrorMessage()) {
            return CompletableFuture.failedFuture(exceptionFromErrorResponse(commandResponse));
        }

        if (commandResponse.getPayload().getType().isEmpty()) {
            return FutureUtils.emptyCompletedFuture();
        }

        MessageType messageType = new MessageType(commandResponse.getPayload().getType(),
                                                  commandResponse.getPayload().getRevision());
        Map<String, String> metadata = convertFromMetaDataValues(commandResponse.getMetaDataMap());
        return CompletableFuture.completedFuture(new GenericCommandResultMessage<>(new GenericMessage<>(
                commandResponse.getMessageIdentifier(),
                messageType,
                commandResponse.getPayload().getData().toByteArray(),
                metadata
        )));
    }

    private AxonException exceptionFromErrorResponse(CommandResponse commandResponse) {
        return ErrorCode.getFromCode(commandResponse.getErrorCode())
                        .convert(commandResponse.getErrorMessage(),
                                 () -> getErrorMessageFromCommandResponse(commandResponse));
    }

    private Object getErrorMessageFromCommandResponse(CommandResponse commandResponse) {
        if (commandResponse.getPayload().getData().isEmpty()) {
            return null;
        }
        return commandResponse.getPayload().getData().toByteArray();
    }

    private Command buildOutgoingCommand(CommandMessage<?> command) {
        Object payload = command.getPayload();
        if (!(payload instanceof byte[] payloadAsBytes)) {
            throw new IllegalArgumentException(
                    "Payload must be of type byte[] for AxonServerConnector, but was: " + payload.getClass().getName()
            );
        }
        Command.Builder builder = Command.newBuilder();
        addRoutingKey(builder, command);
        addPriority(builder, command);

        return builder
                .setMessageIdentifier(command.getIdentifier())
                .setName(command.type().name())
                .putAllMetaData(AxonServerMetadataConverter.convertToMetaDataValues(command.getMetaData()))
                .setPayload(SerializedObject.newBuilder()
                                            .setData(ByteString.copyFrom(payloadAsBytes))
                                            .setType(command.type().name())
                                            .setRevision(command.type().version())
                                            .build())
                .build();
    }

    private void addPriority(Command.Builder builder, CommandMessage<?> command) {
        Optional<Long> priority = command.priority();
        if (priority.isEmpty()) {
            return;
        }
        var instruction = createProcessingInstruction(
                ProcessingKey.PRIORITY,
                MetaDataValue.newBuilder().setNumberValue(priority.get()));
        builder.addProcessingInstructions(instruction).build();
    }

    private void addRoutingKey(Command.Builder builder, CommandMessage<?> command) {
        Optional<String> routingKey = command.routingKey();
        if (routingKey.isEmpty()) {
            return;
        }
        var instruction = createProcessingInstruction(
                ProcessingKey.ROUTING_KEY,
                MetaDataValue.newBuilder().setTextValue(routingKey.get()));
        builder.addProcessingInstructions(instruction).build();
    }

    @Override
    public void subscribe(@Nonnull QualifiedName commandName, int loadFactor) {
        Assert.isTrue(loadFactor >= 0, () -> "Load factor must be greater than 0.");
        logger.info("Subscribing to command [{}] with load factor [{}]", commandName, loadFactor);
        Registration registration = connection.commandChannel()
                                              .registerCommandHandler(this::incoming, loadFactor, commandName.name());

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

    private CompletableFuture<CommandResponse> incoming(Command command) {
        logger.info("Received incoming command [{}]", command.getName());
        try {
            CompletableFuture<CommandResponse> result = new CompletableFuture<>();
            incomingHandler.get().handle(convertToCommandMessage(command),
                                         new FutureResultCallback(result, command));
            return result;
        } catch (Exception e) {
            logger.error("Error processing incoming command: {}", command.getName(), e);
            CompletableFuture<CommandResponse> errorResult = new CompletableFuture<>();
            errorResult.completeExceptionally(e);
            return errorResult;
        }
    }

    private CommandMessage<?> convertToCommandMessage(Command command) {
        SerializedObject commandPayload = command.getPayload();
        long priority = priority(command.getProcessingInstructionsList());
        String routingKey = ProcessingInstructionHelper.routingKey(command.getProcessingInstructionsList());
        return new GenericCommandMessage<>(
                new GenericMessage<>(
                        command.getMessageIdentifier(),
                        new MessageType(commandPayload.getType(), commandPayload.getRevision()),
                        commandPayload.getData().toByteArray(),
                        convertFromMetaDataValues(command.getMetaDataMap())
                ),
                routingKey,
                priority
        );
    }

    private CommandResponse createResult(Command command, Message<?> resultMessage) {
        if (resultMessage == null) {
            return CommandResponse.newBuilder()
                                  .setMessageIdentifier(UUID.randomUUID().toString())
                                  .build();
        }

        String messageId = getOrDefault(resultMessage.getIdentifier(), UUID.randomUUID().toString());
        CommandResponse.Builder responseBuilder = CommandResponse
                .newBuilder()
                .setMessageIdentifier(messageId)
                .putAllMetaData(AxonServerMetadataConverter.convertToMetaDataValues(resultMessage.getMetaData()))
                .setRequestIdentifier(command.getMessageIdentifier());
        if (resultMessage.getPayload() instanceof byte[] payload) {
            responseBuilder.setPayload(SerializedObject.newBuilder()
                                                       .setType(resultMessage.type().name())
                                                       .setRevision(resultMessage.type().version())
                                                       .setData(ByteString.copyFrom(payload)));
        }

        return responseBuilder.build();
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
        this.incomingHandler.set(handler);
    }

    private class FutureResultCallback implements ResultCallback {

        private final CompletableFuture<CommandResponse> result;
        private final Command command;

        public FutureResultCallback(CompletableFuture<CommandResponse> result, Command command) {
            this.result = result;
            this.command = command;
        }

        @Override
        public void success(Message<?> resultMessage) {
            logger.info("Command [{}] completed successfully with result [{}]", command.getName(), resultMessage);
            result.complete(createResult(command, resultMessage));
        }

        @Override
        public void error(@Nonnull Throwable cause) {

            result.completeExceptionally(cause);
        }
    }
}
