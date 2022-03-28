/*
 * Copyright (c) 2010-2022. Axon Framework
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

import io.axoniq.axonserver.grpc.command.Command;
import org.axonframework.axonserver.connector.util.GrpcMetaData;
import org.axonframework.axonserver.connector.util.GrpcSerializedObject;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.LazyDeserializingObject;
import org.axonframework.serialization.Serializer;

import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

/**
 * Wrapper that allows clients to access a gRPC {@link Command} as a {@link CommandMessage}.
 *
 * @param <C> a generic specifying the payload type of the wrapped {@link CommandMessage}
 * @author Marc Gathier
 * @since 4.0
 */
public class GrpcBackedCommandMessage<C> implements CommandMessage<C> {

    private final Command command;
    private final LazyDeserializingObject<C> serializedPayload;
    private final Supplier<MetaData> metaDataSupplier;

    /**
     * Instantiate a {@link GrpcBackedCommandMessage} with the given {@code command} and using the provided {@link
     * Serializer} to be able to retrieve the payload and {@link MetaData} from it.
     *
     * @param command    the {@link Command} which is being wrapped as a {@link CommandMessage}
     * @param serializer the {@link Serializer} used to deserialize the payload and {@link MetaData} from the given
     *                   {@code command}
     */
    public GrpcBackedCommandMessage(Command command, Serializer serializer) {
        this(command,
             new LazyDeserializingObject<>(new GrpcSerializedObject(command.getPayload()), serializer),
             new GrpcMetaData(command.getMetaDataMap(), serializer));
    }

    private GrpcBackedCommandMessage(Command command,
                                     LazyDeserializingObject<C> serializedPayload,
                                     Supplier<MetaData> metaDataSupplier) {
        this.command = command;
        this.serializedPayload = serializedPayload;
        this.metaDataSupplier = metaDataSupplier;
    }

    @Override
    public String getCommandName() {
        return command.getName();
    }

    @Override
    public String getIdentifier() {
        return command.getMessageIdentifier();
    }

    @Override
    public MetaData getMetaData() {
        return metaDataSupplier.get();
    }

    @Override
    public C getPayload() {
        return serializedPayload.getObject();
    }

    @Override
    public Class<C> getPayloadType() {
        return serializedPayload.getType();
    }

    @Override
    public GrpcBackedCommandMessage<C> withMetaData(@Nonnull Map<String, ?> metaData) {
        return new GrpcBackedCommandMessage<>(command, serializedPayload, () -> MetaData.from(metaData));
    }

    @Override
    public GrpcBackedCommandMessage<C> andMetaData(@Nonnull Map<String, ?> metaData) {
        return withMetaData(getMetaData().mergedWith(metaData));
    }
}
