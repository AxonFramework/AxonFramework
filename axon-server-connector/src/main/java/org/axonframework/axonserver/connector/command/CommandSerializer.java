/*
 * Copyright (c) 2018. AxonIQ
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.Serializer;

import java.util.UUID;

import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * Converter between Axon CommandMessage and AxonServer GRPC message.
 *
 * @author Marc Gathier
 */
public class CommandSerializer {

    private final AxonServerConfiguration configuration;

    private final Serializer messageSerializer;

    private final GrpcMetadataSerializer metadataSerializer;

    private final GrpcPayloadSerializer payloadSerializer;

    private final GrpcObjectSerializer<Object> objectSerializer;

    public CommandSerializer(Serializer serializer, AxonServerConfiguration configuration) {
        this.configuration = configuration;
        this.messageSerializer = serializer;
        this.metadataSerializer = new GrpcMetadataSerializer(new GrpcMetaDataConverter(this.messageSerializer));
        this.payloadSerializer = new GrpcPayloadSerializer(messageSerializer);
        this.objectSerializer = new GrpcObjectSerializer<>(messageSerializer);
    }

    public Command serialize(CommandMessage<?> commandMessage, String routingKey, int priority) {
        return Command.newBuilder().setName(commandMessage.getCommandName())
                .setMessageIdentifier(commandMessage.getIdentifier())
                .setTimestamp(System.currentTimeMillis())
                .setPayload(payloadSerializer.apply(commandMessage))
                .putAllMetaData(metadataSerializer.apply(commandMessage.getMetaData()))
                .addProcessingInstructions(ProcessingInstruction.newBuilder()
                                .setKey(ProcessingKey.ROUTING_KEY)
                                .setValue(MetaDataValue.newBuilder().setTextValue(routingKey)))
                .addProcessingInstructions(ProcessingInstruction.newBuilder()
                        .setKey(ProcessingKey.PRIORITY)
                        .setValue(MetaDataValue.newBuilder().setNumberValue(priority)))
                .setClientId(configuration.getComponentName())
                .setComponentName(configuration.getComponentName())
                .build();
    }

    public CommandMessage<?> deserialize(Command request) {
        return new GrpcBackedCommandMessage(request, messageSerializer);
    }

    public <R> GenericCommandResultMessage<R> deserialize(CommandResponse response) {
        MetaData metaData = new GrpcMetaDataConverter(messageSerializer).convert(response.getMetaDataMap());

        if (response.hasErrorMessage()) {
            AxonException exception = ErrorCode.getFromCode(response.getErrorCode()).convert(response.getErrorMessage());
            return new GenericCommandResultMessage<>(exception, metaData);
        }

        R payload = response.hasPayload()
                ? messageSerializer.deserialize(new GrpcSerializedObject(response.getPayload()))
                : null;
        return new GenericCommandResultMessage<>(payload, metaData);
    }

    public CommandProviderOutbound serialize(CommandResultMessage<?> commandResultMessage, String requestIdentifier) {
        CommandResponse.Builder responseBuilder =
                CommandResponse.newBuilder()
                               .setMessageIdentifier(
                                       getOrDefault(commandResultMessage.getIdentifier(), UUID.randomUUID().toString())
                               )
                               .putAllMetaData(metadataSerializer.apply(commandResultMessage.getMetaData()))
                               .setRequestIdentifier(requestIdentifier);
        if (commandResultMessage.isExceptional()) {
            Throwable throwable = commandResultMessage.exceptionResult();
            responseBuilder.setErrorCode(ErrorCode.COMMAND_EXECUTION_ERROR.errorCode());
            responseBuilder.setErrorMessage(ExceptionSerializer.serialize(configuration.getClientId(), throwable));
        } else if (commandResultMessage.getPayload() != null) {
            responseBuilder.setPayload(objectSerializer.apply(commandResultMessage.getPayload()));
        }
        return CommandProviderOutbound.newBuilder().setCommandResponse(responseBuilder).build();
    }
}
