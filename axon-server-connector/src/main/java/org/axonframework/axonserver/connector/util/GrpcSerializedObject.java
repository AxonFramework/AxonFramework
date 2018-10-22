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

package org.axonframework.axonserver.connector.util;

import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SerializedType;

/**
 * Wrapper that allows clients to access a GRPC {@link io.axoniq.axonserver.grpc.SerializedObject} Message as a {@link SerializedObject}.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */
public class GrpcSerializedObject implements SerializedObject<byte[]> {

    private final io.axoniq.axonserver.grpc.SerializedObject payload;

    public GrpcSerializedObject(io.axoniq.axonserver.grpc.SerializedObject payload) {
        this.payload = payload;
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
