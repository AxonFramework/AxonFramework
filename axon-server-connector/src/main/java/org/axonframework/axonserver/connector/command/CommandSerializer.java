/*
 * Copyright (c) 2010-2020. Axon Framework
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

import io.axoniq.axonserver.grpc.MetaDataValue;
import io.axoniq.axonserver.grpc.ProcessingInstruction;
import io.axoniq.axonserver.grpc.ProcessingKey;
import io.axoniq.axonserver.grpc.command.Command;
import io.axoniq.axonserver.grpc.command.CommandProviderOutbound;
import io.axoniq.axonserver.grpc.command.CommandResponse;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.util.ExceptionSerializer;
import org.axonframework.axonserver.connector.util.GrpcMetaDataConverter;
import org.axonframework.axonserver.connector.util.GrpcMetadataSerializer;
import org.axonframework.axonserver.connector.util.GrpcObjectSerializer;
import org.axonframework.axonserver.connector.util.GrpcPayloadSerializer;
import org.axonframework.axonserver.connector.util.GrpcSerializedObject;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.common.AxonException;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.LazyDeserializingObject;
import org.axonframework.serialization.SerializedMessage;
import org.axonframework.serialization.Serializer;

import java.util.UUID;

import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * Converter between Axon Framework {@link CommandMessage}s and Axon Server gRPC {@link Command} messages.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public class CommandSerializer {

    private final AxonServerConfiguration configuration;
    private final Serializer messageSerializer;
    private final GrpcMetadataSerializer metadataSerializer;
    private final GrpcPayloadSerializer payloadSerializer;
    private final GrpcObjectSerializer<Object> objectSerializer;

    /**
     * Instantiate a serializer used to convert Axon {@link CommandMessage}s and {@link CommandResultMessage}s into Axon
     * Server gRPC messages and vice versa. The provided {@code serializer} is used for both from and to
     * framework-server conversions.
     *
     * @param serializer    a {@link Serializer} used to de-/serialize an Axon Server gRPC message into a {@link
     *                      CommandMessage} or {@link CommandResultMessage} and vice versa
     * @param configuration an {@link AxonServerConfiguration} used to set the configurable component id and name in the
     *                      messages
     */
    public CommandSerializer(Serializer serializer, AxonServerConfiguration configuration) {
        this.configuration = configuration;
        this.messageSerializer = serializer;
        this.metadataSerializer = new GrpcMetadataSerializer(new GrpcMetaDataConverter(this.messageSerializer));
        this.payloadSerializer = new GrpcPayloadSerializer(messageSerializer);
        this.objectSerializer = new GrpcObjectSerializer<>(messageSerializer);
    }

    /**
     * Convert a {@link CommandMessage} into a {@link Command}. The {@code routingKey} and {@code priority} respectively
     * define which service to route the command to and what the command's priority among others is.
     *
     * @param commandMessage the {@link CommandMessage} to convert into a {@link Command}
     * @param routingKey     a {@link String} defining the routing key of the given {@link CommandMessage}
     * @param priority       a {@code int} defining the priority of the given {@link CommandMessage}
     * @return a {@link Command} based on the provided {@code commandMessage}
     */
    public Command serialize(CommandMessage<?> commandMessage, String routingKey, int priority) {
        return Command.newBuilder().setName(commandMessage.getCommandName())
                      .setMessageIdentifier(commandMessage.getIdentifier())
                      .setTimestamp(System.currentTimeMillis())
                      .setPayload(payloadSerializer.apply(commandMessage))
                      .putAllMetaData(metadataSerializer.apply(commandMessage.getMetaData()))
                      .addProcessingInstructions(
                              ProcessingInstruction.newBuilder()
                                                   .setKey(ProcessingKey.ROUTING_KEY)
                                                   .setValue(MetaDataValue.newBuilder().setTextValue(routingKey))
                      )
                      .addProcessingInstructions(
                              ProcessingInstruction.newBuilder()
                                                   .setKey(ProcessingKey.PRIORITY)
                                                   .setValue(MetaDataValue.newBuilder().setNumberValue(priority))
                      )
                      .setClientId(configuration.getClientId())
                      .setComponentName(configuration.getComponentName())
                      .build();
    }

    /**
     * Convert a {@link CommandResultMessage} into a {@link CommandProviderOutbound}.
     *
     * @param commandResultMessage the {@link CommandResultMessage} to convert into a {@link CommandProviderOutbound}
     * @param requestIdentifier    a {@link String} identifying where the original request came from
     * @return a {@link CommandProviderOutbound} based on the provided {@code commandResultMessage}
     */
    public CommandResponse serialize(CommandResultMessage<?> commandResultMessage, String requestIdentifier) {
        CommandResponse.Builder responseBuilder =
                CommandResponse.newBuilder()
                               .setMessageIdentifier(
                                       getOrDefault(commandResultMessage.getIdentifier(), UUID.randomUUID().toString())
                               )
                               .putAllMetaData(metadataSerializer.apply(commandResultMessage.getMetaData()))
                               .setRequestIdentifier(requestIdentifier);

        if (commandResultMessage.isExceptional()) {
            Throwable throwable = commandResultMessage.exceptionResult();
            responseBuilder.setErrorCode(ErrorCode.getCommandExecutionErrorCode(throwable).errorCode());
            responseBuilder.setErrorMessage(ExceptionSerializer.serialize(configuration.getClientId(), throwable));
            commandResultMessage.exceptionDetails()
                                .ifPresent(details -> responseBuilder.setPayload(objectSerializer.apply(details)));
        } else if (commandResultMessage.getPayload() != null) {
            responseBuilder.setPayload(objectSerializer.apply(commandResultMessage.getPayload()));
        }

        return responseBuilder.build();
    }

    /**
     * Convert a {@link Command} into a {@link CommandMessage}.
     *
     * @param command the {@link Command} to convert into a {@link CommandMessage}
     * @return a {@link CommandMessage} based on the provided {@code command}
     */
    public CommandMessage<?> deserialize(Command command) {
        return new GrpcBackedCommandMessage<>(command, messageSerializer);
    }

    /**
     * Convert a {@link CommandResponse} into a {@link CommandResultMessage}.
     *
     * @param commandResponse the {@link CommandResponse} to convert into a {@link CommandResultMessage}
     * @param <R>             a generic specifying the response payload type
     * @return a {@link CommandResultMessage} of generic {@code R} based on the provided {@code commandResponse}
     */
    public <R> CommandResultMessage<R> deserialize(CommandResponse commandResponse) {
        MetaData metaData = new GrpcMetaDataConverter(messageSerializer).convert(commandResponse.getMetaDataMap());

        if (commandResponse.hasErrorMessage()) {
            Object exceptionDetails = commandResponse.hasPayload()
                    ? messageSerializer.deserialize(new GrpcSerializedObject(commandResponse.getPayload()))
                    : null;
            AxonException exception = ErrorCode.getFromCode(commandResponse.getErrorCode())
                                               .convert(commandResponse.getErrorMessage(), () -> exceptionDetails);

            return new GenericCommandResultMessage<>(
                    new GenericMessage<>(commandResponse.getMessageIdentifier(), null, metaData), exception
            );
        }

        //noinspection unchecked
        Message<R> response = commandResponse.hasPayload()
                ? new SerializedMessage<>(commandResponse.getMessageIdentifier(),
                                          new LazyDeserializingObject<>(
                                                  new GrpcSerializedObject(commandResponse.getPayload()),
                                                  messageSerializer
                                          ),
                                          new LazyDeserializingObject<>(metaData))
                : (GenericMessage<R>) new GenericMessage<>(commandResponse.getMessageIdentifier(),
                                                           Void.class,
                                                           null,
                                                           metaData);
        return new GenericCommandResultMessage<>(response);
    }
}
