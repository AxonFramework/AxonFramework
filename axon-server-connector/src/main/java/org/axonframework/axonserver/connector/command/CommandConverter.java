/*
 * Copyright (c) 2010-2026. Axon Framework
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
import io.axoniq.axonserver.grpc.ProcessingKey;
import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.command.Command;
import io.axoniq.axonserver.grpc.command.CommandResponse;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.axonserver.connector.MetadataConverter;
import org.axonframework.axonserver.connector.util.ExceptionConverter;
import org.axonframework.axonserver.connector.util.ProcessingInstructionUtils;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.CommandResultMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandResultMessage;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.core.GenericMessage;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.axonframework.axonserver.connector.MetadataConverter.convertMetadataValuesToGrpc;
import static org.axonframework.axonserver.connector.util.ProcessingInstructionUtils.createProcessingInstruction;
import static org.axonframework.axonserver.connector.util.ProcessingInstructionUtils.priority;
import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * Utility class to convert commands during
 * {@link AxonServerCommandBusConnector#dispatch(CommandMessage, ProcessingContext) dispatching} and handling of
 * {@link AxonServerCommandBusConnector#subscribe(QualifiedName, int) subscribed} command handlers in the
 * {@link AxonServerCommandBusConnector}.
 * <p>
 * The operations {@link #convertCommandMessage(CommandMessage, String, String) convert}
 * {@link CommandMessage CommandMessages} and {@link #convertCommandResponse(CommandResponse) convert}
 * {@link CommandResponse CommandResponses} are used during dispatching. The operations
 * {@link #convertCommand(Command) convert} {@link Command Commands} and
 * {@link #convertResultMessage(CommandResultMessage, String) convert} result messages are used during handling.
 * <p>
 * This utility class is marked as {@link Internal} as it is specific for the {@link AxonServerCommandBusConnector}.
 *
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
public final class CommandConverter {

    /**
     * Converts the given {@code command} into a {@link Command} for
     * {@link AxonServerCommandBusConnector#dispatch(CommandMessage, ProcessingContext) dispatching}.
     * <p>
     * Will set the {@link ProcessingKey#ROUTING_KEY routing key} and {@link ProcessingKey#PRIORITY priority} when
     * present on the given {@code command}.
     *
     * @param command       The command message to convert to a {@link Command}.
     * @param clientId      The identifier of this application, as specific in the
     *                      {@link org.axonframework.axonserver.connector.AxonServerConfiguration}.
     * @param componentName The name of this application, as specific in the
     *                      {@link org.axonframework.axonserver.connector.AxonServerConfiguration}.
     * @return The given {@code command} converted to a {@link Command}.
     */
    public static Command convertCommandMessage(@Nonnull CommandMessage command,
                                                @Nonnull String clientId,
                                                @Nonnull String componentName) {
        Object payload = command.payload();
        if (!(payload instanceof byte[] payloadAsBytes)) {
            throw new IllegalArgumentException(
                    "Payload must be of type byte[] for AxonServerConnector, but was: "
                            + command.payloadType().getName()
                            + ", consider using a Converter-based CommandBusConnector"
            );
        }
        Command.Builder builder = Command.newBuilder();
        addRoutingKey(builder, command);
        addPriority(builder, command);

        return builder.setClientId(clientId)
                      .setComponentName(componentName)
                      .setMessageIdentifier(command.identifier())
                      .setName(command.type().name())
                      .putAllMetaData(MetadataConverter.convertGrpcToMetadataValues(command.metadata()))
                      .setPayload(SerializedObject.newBuilder()
                                                  .setData(ByteString.copyFrom(payloadAsBytes))
                                                  .setType(command.type().name())
                                                  .setRevision(command.type().version())
                                                  .build())
                      .build();
    }

    /**
     * Converts the given {@code commandResponse} to a {@link CommandResultMessage}, wrapped in a
     * {@link CompletableFuture} for convenience when dealing with {@link CommandResponse CommandResponses} during
     * {@link AxonServerCommandBusConnector#dispatch(CommandMessage, ProcessingContext) dispatching}.
     *
     * @param commandResponse The command response to convert to a {@link CommandResultMessage}.
     * @return The {@code commandResponse} converted to a {@link CommandResultMessage}, wrapped in a
     * {@link CompletableFuture} for convenience.
     */
    public static CompletableFuture<CommandResultMessage> convertCommandResponse(
            @Nonnull CommandResponse commandResponse
    ) {
        if (commandResponse.hasErrorMessage()) {
            return CompletableFuture.failedFuture(ExceptionConverter.convertToAxonException(
                    commandResponse.getErrorCode(),
                    commandResponse.getErrorMessage(),
                    commandResponse.getPayload()
            ));
        }

        if (commandResponse.getPayload().getType().isEmpty()) {
            return FutureUtils.emptyCompletedFuture();
        }

        MessageType messageType = new MessageType(commandResponse.getPayload().getType(),
                                                  commandResponse.getPayload().getRevision());
        Map<String, String> metadata = convertMetadataValuesToGrpc(commandResponse.getMetaDataMap());
        return CompletableFuture.completedFuture(new GenericCommandResultMessage(new GenericMessage(
                commandResponse.getMessageIdentifier(),
                messageType,
                commandResponse.getPayload().getData().toByteArray(),
                metadata
        )));
    }


    /**
     * Converts the given {@code command} into a {@link CommandMessage} for handling in
     * {@link AxonServerCommandBusConnector#subscribe(QualifiedName, int) subscribed} command handlers.
     *
     * @param command The command to convert to a {@link CommandMessage}.
     * @return The given {@code command} converted into a {@link CommandMessage}.
     */
    public static CommandMessage convertCommand(@Nonnull Command command) {
        SerializedObject commandPayload = command.getPayload();
        int priority = priority(command.getProcessingInstructionsList());
        String routingKey = ProcessingInstructionUtils.routingKey(command.getProcessingInstructionsList());
        return new GenericCommandMessage(
                new GenericMessage(
                        command.getMessageIdentifier(),
                        new MessageType(commandPayload.getType(), commandPayload.getRevision()),
                        commandPayload.getData().toByteArray(),
                        convertMetadataValuesToGrpc(command.getMetaDataMap())
                ),
                routingKey,
                priority
        );
    }

    /**
     * Converts the given {@code resultMessage}, when present, into a {@link CommandResponse}, using the given
     * {@code requestIdentifier} to correlate the {@link Command} that led to this {@link CommandResponse}.
     * <p>
     * Whenever the {@code resultMessage} is {@code null}, an empty {@code CommandResponse} is constructed instead for
     * returning a result from handling of a
     * {@link AxonServerCommandBusConnector#subscribe(QualifiedName, int) subscribed} command handler.
     *
     * @param resultMessage     The result message to convert to a {@link CommandResponse}, when present.
     * @param requestIdentifier The identifier correlating the {@link CommandResponse} to the {@link Command} that led
     *                          to the response.
     * @return A {@link CommandResponse} based on the given {@code resultMessage} and {@code requestIdentifier}.
     */
    public static CommandResponse convertResultMessage(@Nullable CommandResultMessage resultMessage,
                                                       @Nonnull String requestIdentifier) {
        if (resultMessage == null) {
            return CommandResponse.newBuilder()
                                  .setMessageIdentifier(UUID.randomUUID().toString())
                                  .setRequestIdentifier(requestIdentifier)
                                  .build();
        }
        Object payload = resultMessage.payload();
        String messageId = getOrDefault(resultMessage.identifier(), UUID.randomUUID().toString());
        CommandResponse.Builder responseBuilder =
                CommandResponse.newBuilder()
                               .setMessageIdentifier(messageId)
                               .putAllMetaData(MetadataConverter.convertGrpcToMetadataValues(resultMessage.metadata()))
                               .setRequestIdentifier(requestIdentifier);

        if (payload != null && !(payload instanceof byte[])) {
            throw new IllegalArgumentException(
                    "Payload must be of type byte[] for AxonServerConnector, but was: %s, consider using a Converter-based CommandBusConnector".
                            formatted(resultMessage.payloadType().getName())
            );
        }
        byte[] payloadAsBytes = (byte[]) Objects.requireNonNullElse(payload, new byte[0]);
        return responseBuilder.setPayload(SerializedObject.newBuilder()
                                                          .setType(resultMessage.type().name())
                                                          .setRevision(resultMessage.type().version())
                                                          .setData(ByteString.copyFrom(payloadAsBytes)))
                              .build();
    }

    private static void addRoutingKey(Command.Builder builder, CommandMessage command) {
        command.routingKey().ifPresent(routingKey -> {
            var instruction = createProcessingInstruction(ProcessingKey.ROUTING_KEY, routingKey);
            builder.addProcessingInstructions(instruction);
        });
    }

    private static void addPriority(Command.Builder builder, CommandMessage command) {
        command.priority().ifPresent(priority -> {
            var instruction = createProcessingInstruction(ProcessingKey.PRIORITY, priority);
            builder.addProcessingInstructions(instruction);
        });
    }

    private CommandConverter() {
        // Utility class
    }
}
