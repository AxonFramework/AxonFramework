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

import com.google.protobuf.ByteString;
import org.axonframework.serialization.SerializedObject;

import java.util.function.Function;

import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * Mapping that translates an object into a GRPC {@link io.axoniq.axonserver.grpc.SerializedObject}.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */
public class GrpcObjectSerializer<O> implements Function<O, io.axoniq.axonserver.grpc.SerializedObject> {

    /**
     * Contract towards serializing an object of type {@code A}.
     *
     * @param <A> the type of object to serialize
     */
    public interface Serializer<A> {

        /**
         * Serialize the given {@code object} into an object of the {@code expectedRepresentation}.
         *
         * @param object                 the object of type {@code A} to serialize
         * @param expectedRepresentation the type to serialize the given {@code object} to
         * @param <T>                    the type of the {@link SerializedObject} to return
         * @return a {@link SerializedObject} of {@code expectedRepresentation} based on the given {@code object}
         */
        <T> SerializedObject<T> serialize(A object, Class<T> expectedRepresentation);
    }

    private final Serializer<O> serializer;

    /**
     * Constructs a {@link GrpcObjectSerializer} using the given {@code serializer} to serialize the payload and type of
     * given objects with.
     *
     * @param serializer the {@link org.axonframework.serialization.Serializer} used to serialize the payload and type
     *                   of given objects with
     */
    public GrpcObjectSerializer(org.axonframework.serialization.Serializer serializer) {
        this(serializer::serialize);
    }

    GrpcObjectSerializer(Serializer<O> serializer) {
        this.serializer = serializer;
    }

    @Override
    public io.axoniq.axonserver.grpc.SerializedObject apply(O o) {
        SerializedObject<byte[]> serializedPayload = serializer.serialize(o, byte[].class);
        String revision = getOrDefault(serializedPayload.getType().getRevision(), "");
        return io.axoniq.axonserver.grpc.SerializedObject.newBuilder()
                                                         .setData(ByteString.copyFrom(serializedPayload.getData()))
                                                         .setType(serializedPayload.getType().getName())
                                                         .setRevision(revision)
                                                         .build();
    }
}
