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

import com.google.protobuf.ByteString;
import io.axoniq.axonhub.client.util.MessagePlatformSerializer;
import io.axoniq.axonhub.Command;
import io.axoniq.platform.MetaDataValue;
import io.axoniq.axonhub.ProcessingInstruction;
import io.axoniq.axonhub.ProcessingKey;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.serialization.MessageSerializer;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;


import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * Converter between Axon CommandMessage and AxonHub GRPC message.
 *
 * @author Marc Gathier
 */
public class CommandSerializer extends MessagePlatformSerializer {


    public CommandSerializer(Serializer serializer) {
        super(serializer);
    }

    public Command serialize(CommandMessage<?> commandMessage, String routingKey, int priority) {
        SerializedObject<byte[]> serializedPayload = MessageSerializer.serializePayload(commandMessage, serializer, byte[].class);
        return Command.newBuilder().setName(commandMessage.getCommandName())
                .setMessageIdentifier(commandMessage.getIdentifier())
                .setTimestamp(System.currentTimeMillis())
                .setPayload(
                        io.axoniq.platform.SerializedObject.newBuilder()
                                .setData(ByteString.copyFrom(serializedPayload.getData()))
                                .setType(serializedPayload.getType().getName())
                                .setRevision(getOrDefault(serializedPayload.getType().getRevision(), ""))
                                .build())
                .putAllMetaData(serializeMetaData(commandMessage.getMetaData()))
                .addProcessingInstructions(ProcessingInstruction.newBuilder()
                                .setKey(ProcessingKey.ROUTING_KEY)
                                .setValue(MetaDataValue.newBuilder().setTextValue(routingKey)))
                .addProcessingInstructions(ProcessingInstruction.newBuilder()
                        .setKey(ProcessingKey.PRIORITY)
                        .setValue(MetaDataValue.newBuilder().setNumberValue(priority)))
                .build();
    }

    public CommandMessage<?> deserialize(Command request) {
        return new GrpcBackedCommandMessage(request, serializer);
    }

}
