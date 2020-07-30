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

package org.axonframework.axonserver.connector.util;

import org.axonframework.messaging.Message;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;

import java.util.function.Function;

/**
 * Mapping that translates a {@link Message} into a GRPC {@link io.axoniq.axonserver.grpc.SerializedObject}.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */
public class GrpcPayloadSerializer implements Function<Message<?>, io.axoniq.axonserver.grpc.SerializedObject> {

    private final GrpcObjectSerializer<Message<?>> delegate;

    /**
     * Constructs a {@link GrpcPayloadSerializer} using the given {@code serializer} to serialize messages with
     *
     * @param serializer the {@link Serializer} used to serialize messages with
     */
    public GrpcPayloadSerializer(Serializer serializer) {
        this(new GrpcObjectSerializer.Serializer<Message<?>>() {
            @Override
            public <T> SerializedObject<T> serialize(Message<?> object, Class<T> expectedRepresentation) {
                return object.serializePayload(serializer, expectedRepresentation);
            }
        });
    }

    private GrpcPayloadSerializer(GrpcObjectSerializer.Serializer<Message<?>> messageSerializer) {
        this(new GrpcObjectSerializer<>(messageSerializer));
    }

    private GrpcPayloadSerializer(GrpcObjectSerializer<Message<?>> delegate) {
        this.delegate = delegate;
    }

    @Override
    public io.axoniq.axonserver.grpc.SerializedObject apply(Message message) {
        return delegate.apply(message);
    }
}
