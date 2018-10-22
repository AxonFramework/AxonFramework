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

import io.axoniq.axonserver.grpc.MetaDataValue;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.Serializer;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toMap;
import static org.axonframework.messaging.MetaData.emptyInstance;
import static org.axonframework.messaging.MetaData.from;

/**
 * Implementation that enables to access to a map of GRPC {@link MetaDataValue}s in the form of {@link MetaData}.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */
public class GrpcMetadata implements Supplier<MetaData> {

    private final Map<String, MetaDataValue> map;

    private final Serializer messageSerializer;

    private final List<MetaData> metaData = new LinkedList<>();

    public GrpcMetadata(Map<String, MetaDataValue> map, Serializer messageSerializer) {
        this.map = map;
        this.messageSerializer = messageSerializer;
    }

    @Override
    public MetaData get() {
        if (metaData.isEmpty()){
            if (map.isEmpty()) {
                metaData.add(emptyInstance());
            } else {
                metaData.add(from(map.entrySet().stream().collect(toMap(Map.Entry::getKey, e -> convert(e.getValue())))));
            }
        }
        return metaData.get(0);
    }

    private Object convert(MetaDataValue value) {
        switch (value.getDataCase()) {
            case TEXT_VALUE:
                return value.getTextValue();
            case BYTES_VALUE:
                return messageSerializer.deserialize(new GrpcSerializedObject(value.getBytesValue()));
            case DOUBLE_VALUE:
                return value.getDoubleValue();
            case NUMBER_VALUE:
                return value.getNumberValue();
            case BOOLEAN_VALUE:
                return value.getBooleanValue();
            case DATA_NOT_SET:
                return null;
        }
        return null;
    }
}
