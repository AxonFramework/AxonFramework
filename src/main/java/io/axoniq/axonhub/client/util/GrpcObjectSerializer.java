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

package io.axoniq.axonhub.client.util;

import com.google.protobuf.ByteString;
import io.axoniq.platform.SerializedObject;
import org.axonframework.serialization.Serializer;

import java.util.function.Function;

import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * Created by Sara Pellegrini on 11/05/2018.
 * sara.pellegrini@gmail.com
 */
public class GrpcObjectSerializer implements Function<Object, SerializedObject> {

    private final Serializer serializer;

    public GrpcObjectSerializer(Serializer serializer) {
        this.serializer = serializer;
    }

    @Override
    public SerializedObject apply(Object o) {
        org.axonframework.serialization.SerializedObject<byte[]> serializedPayload = serializer.serialize(o, byte[].class);
        return SerializedObject.newBuilder()
                               .setData(ByteString.copyFrom(serializedPayload.getData()))
                               .setType(serializedPayload.getType().getName())
                               .setRevision(getOrDefault(serializedPayload.getType().getRevision(), ""))
                               .build();
    }
}
