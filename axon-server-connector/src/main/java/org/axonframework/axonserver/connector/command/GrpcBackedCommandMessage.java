/*
 * Copyright (c) 2010-2019. Axon Framework
 *
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
import io.axoniq.axonserver.grpc.command.Command;
import org.axonframework.axonserver.connector.util.GrpcMetaDataConverter;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;

import java.util.Map;

/**
 * Wrapper that allows clients to access a gRPC {@link Command} as a {@link CommandMessage}.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public class GrpcBackedCommandMessage<C> implements CommandMessage<C> {

    private final Command request;
    private final Serializer serializer;
    private MetaData metaData;

    /**
     * Instantiate a {@link GrpcBackedCommandMessage} with the given {@code command} and using the provided
     * {@link Serializer} to be able to retrieve the payload and {@link MetaData} from it.
     *
     * @param command    the {@link Command} which is being wrapped as a {@link CommandMessage}
     * @param serializer the {@link Serializer} used to deserialize the payload and {@link MetaData} from the given
     * {@code command}
     */
    public GrpcBackedCommandMessage(Command command, Serializer serializer) {
        this.request = command;
        this.serializer = serializer;
    }

    @Override
    public String getCommandName() {
        return request.getName();
    }

    @Override
    public String getIdentifier() {
        return request.getMessageIdentifier();
    }

    @Override
    public MetaData getMetaData() {
        if (metaData == null) {
            metaData = deserializeMetaData(request.getMetaDataMap());
        }
        return metaData;
    }

    @Override
    public C getPayload() {
        String revision = request.getPayload().getRevision();
        SerializedObject serializedObject = new SimpleSerializedObject<>(
                request.getPayload().getData().toByteArray(),
                byte[].class,
                request.getPayload().getType(),
                "".equals(revision) ? null : revision
        );
        //noinspection unchecked
        return (C) serializer.deserialize(serializedObject);
    }

    @Override
    public Class<C> getPayloadType() {
        try {
            //noinspection unchecked
            return (Class<C>) Class.forName(request.getPayload().getType());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CommandMessage<C> withMetaData(Map<String, ?> metaData) {
        return this;
    }

    @Override
    public CommandMessage<C> andMetaData(Map<String, ?> metaData) {
        return this;
    }

    private MetaData deserializeMetaData(Map<String, MetaDataValue> metaDataMap) {
        if (metaDataMap.isEmpty()) {
            return MetaData.emptyInstance();
        }
        GrpcMetaDataConverter grpcMetaDataConverter = new GrpcMetaDataConverter(serializer);
        return MetaData.from(grpcMetaDataConverter.convert(metaDataMap));
    }
}
