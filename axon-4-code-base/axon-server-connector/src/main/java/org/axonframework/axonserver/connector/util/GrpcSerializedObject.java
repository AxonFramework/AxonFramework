/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SerializedType;

/**
 * Wrapper that allows clients to access a gRPC {@link io.axoniq.axonserver.grpc.SerializedObject} message as a {@link
 * SerializedObject}.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */
public class GrpcSerializedObject implements SerializedObject<byte[]> {

    private final io.axoniq.axonserver.grpc.SerializedObject payload;

    /**
     * Initialize a {@link GrpcSerializedObject}, wrapping a {@link io.axoniq.axonserver.grpc.SerializedObject} as a
     * {@link SerializedObject}.
     *
     * @param serializedObject a {@link io.axoniq.axonserver.grpc.SerializedObject} which will be wrapped as a {@link
     *                         SerializedObject}
     */
    public GrpcSerializedObject(io.axoniq.axonserver.grpc.SerializedObject serializedObject) {
        this.payload = serializedObject;
    }

    @Override
    public Class<byte[]> getContentType() {
        return byte[].class;
    }

    @Override
    public SerializedType getType() {
        return new SerializedType() {
            @Override
            public String getName() {
                return payload.getType();
            }

            @Override
            public String getRevision() {
                String revision = payload.getRevision();
                return "".equals(revision) ? null : revision;
            }
        };
    }

    @Override
    public byte[] getData() {
        return payload.getData().toByteArray();
    }
}
