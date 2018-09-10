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

package io.axoniq.axonhub.client.command;

import io.axoniq.axonhub.Command;
import io.axoniq.axonhub.CommandResponse;
import io.axoniq.axonhub.ProcessingInstruction;
import io.axoniq.axonhub.ProcessingKey;
import io.axoniq.axonhub.client.AxonHubConfiguration;
import io.axoniq.axonhub.client.util.GrpcMetaDataConverter;
import io.axoniq.axonhub.client.util.GrpcMetadataSerializer;
import io.axoniq.axonhub.client.util.GrpcObjectSerializer;
import io.axoniq.axonhub.client.util.GrpcPayloadSerializer;
import io.axoniq.axonhub.client.util.GrpcSerializedObject;
import io.axoniq.axonhub.grpc.CommandProviderOutbound;
import io.axoniq.platform.MetaDataValue;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.serialization.Serializer;

import java.util.UUID;

/**
 * Converter between Axon CommandMessage and AxonHub GRPC message.
 *
 * @author Marc Gathier
 */
public class CommandSerializer {

    private final AxonHubConfiguration configuration;

    private final Serializer messageSerializer;

    private final GrpcMetadataSerializer metadataSerializer;

    private final GrpcPayloadSerializer payloadSerializer;

    private final GrpcObjectSerializer<Object> objectSerializer;

    public CommandSerializer(Serializer serializer, AxonHubConfiguration configuration) {
        this.configuration = configuration;
        this.messageSerializer = serializer;
        this.metadataSerializer = new GrpcMetadataSerializer(new GrpcMetaDataConverter(this.messageSerializer));
        this.payloadSerializer  = new GrpcPayloadSerializer(messageSerializer);
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

    public Object deserialize(CommandResponse response) {
        return messageSerializer.deserialize(new GrpcSerializedObject(response.getPayload()));
    }

    CommandProviderOutbound serialize(Object payload, String requestIdentifier) {
        CommandResponse.Builder responseBuilder = CommandResponse.newBuilder()
                                                                 .setMessageIdentifier(UUID.randomUUID().toString())
                                                                 .setRequestIdentifier(requestIdentifier);
        if (payload != null) {
            responseBuilder.setPayload(objectSerializer.apply(payload));
        }
        return CommandProviderOutbound.newBuilder().setCommandResponse(responseBuilder).build();
    }



}
