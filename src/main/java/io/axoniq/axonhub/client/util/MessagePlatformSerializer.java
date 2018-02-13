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
import io.axoniq.platform.MetaDataValue;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.MessageSerializer;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;

import java.util.HashMap;
import java.util.Map;

import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * Base class for serializing/deserializing messages for AxonHub.
 *
 * @author Marc Gathier
 */
public abstract class MessagePlatformSerializer {

    protected final Serializer serializer;
    protected final GrpcMetaDataConverter metaDataConverter;

    protected MessagePlatformSerializer(Serializer serializer) {
        this.serializer = serializer;
        this.metaDataConverter = new GrpcMetaDataConverter(serializer);
    }

    public Map<String, MetaDataValue> serializeMetaData(MetaData metaData) {
        Map<String, MetaDataValue> metaDataValueMap = new HashMap<>();
        metaData.forEach((key, value)-> metaDataValueMap.put(key, metaDataConverter.convertToMetaDataValue(value)));
        return metaDataValueMap;
    }



    public io.axoniq.platform.SerializedObject serializePayload(Object o) {
        SerializedObject<byte[]> serializedPayload = new MessageSerializer(serializer).serialize(o, byte[].class);
        return io.axoniq.platform.SerializedObject.newBuilder()
                .setData(ByteString.copyFrom(serializedPayload.getData()))
                .setType(serializedPayload.getType().getName())
                .setRevision(getOrDefault(serializedPayload.getType().getRevision(), ""))
                .build();
    }

    public Object deserializePayload(io.axoniq.platform.SerializedObject payload) {
        String revision = payload.getRevision();
        SerializedObject object =  new SimpleSerializedObject<>(payload.getData().toByteArray(),
                byte[].class, payload.getType(),
                "".equals(revision) ? null : revision);
        return new MessageSerializer(serializer).deserialize(object);
    }

}
