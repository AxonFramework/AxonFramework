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
import io.axoniq.axonserver.grpc.MetaDataValue;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SerializedType;
import org.axonframework.serialization.Serializer;

import java.util.HashMap;
import java.util.Map;

import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * Utility class for converting gRPC MetaData entries into a Java Map and vice versa.
 * <p>
 * To optimize communication and minimize the loss of data structure, the MetaDataValue entries used in gRPC distinguish
 * between numerical values (double or long), Strings, booleans and arbitrary objects. The latter group is converted to
 * and from a {@code byte[]} using the configured Serializer.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public class GrpcMetaDataConverter {

    private final Serializer serializer;

    /**
     * Initialize the converter, using the given {@code serializer} to serialize Objects.
     *
     * @param serializer the {@link Serializer} to serialize objects with
     */
    public GrpcMetaDataConverter(Serializer serializer) {
        this.serializer = serializer;
    }

    /**
     * Convert the given {@code value} into a {@link MetaDataValue}, attempting to maintain the source type as much as
     * possible in the returned {@link MetaDataValue}.
     * <ul>
     * <li>A CharSequence (such as String) is stored as a 'string'</li>
     * <li>A Float or Double values is represented as a 'double'</li>
     * <li>A Number that is not a Double or Float is represented as a 'sint64'</li>
     * <li>A Boolean is represented as a 'bool'</li>
     * <li>Any other object is serialized and stored as bytes</li>
     * </ul>
     *
     * @param value the {@link Object} to convert into a {@link MetaDataValue}
     * @return a {@link MetaDataValue} representing the given {@code value}
     */
    public MetaDataValue convertToMetaDataValue(Object value) {
        MetaDataValue.Builder builder = MetaDataValue.newBuilder();

        if (value instanceof CharSequence) {
            builder.setTextValue(value.toString());
        } else if (value instanceof Double || value instanceof Float) {
            builder.setDoubleValue(((Number) value).doubleValue());
        } else if (value instanceof Number) {
            builder.setNumberValue(((Number) value).longValue());
        } else if (value instanceof Boolean) {
            builder.setBooleanValue((Boolean) value);
        } else {
            SerializedObject<byte[]> serializedObject = serializer.serialize(value, byte[].class);
            SerializedType serializedType = serializedObject.getType();

            if (!SerializedType.isEmptyType(serializedType)) {
                builder.setBytesValue(io.axoniq.axonserver.grpc.SerializedObject
                                              .newBuilder()
                                              .setType(serializedType.getName())
                                              .setData(ByteString.copyFrom(serializedObject.getData()))
                                              .setRevision(getOrDefault(serializedType.getRevision(), ""))
                                              .build());
            }
        }

        return builder.build();
    }

    /**
     * Convert the given {@link Map} of {@link MetaDataValue}s to a Map containing the Java representations of each of
     * those values.
     * <p>
     * See {@link #convertToMetaDataValue(Object)} for details about the mapping.
     *
     * @param metaDataMap a {@link Map} containing {@link MetaDataValue} representations of each {@link MetaData} key
     * @return a {@link MetaData} map containing the same keys, referencing to the Java representation of each
     * corresponding value in the given {@code metaDataMap}
     */
    public MetaData convert(Map<String, MetaDataValue> metaDataMap) {
        if (metaDataMap.isEmpty()) {
            return MetaData.emptyInstance();
        }

        Map<String, Object> metaData = new HashMap<>(metaDataMap.size());
        metaDataMap.forEach((k, v) -> metaData.put(k, convertFromMetaDataValue(v)));
        return MetaData.from(metaData);
    }

    /**
     * Convert the given {@link MetaDataValue} to its Java representation.
     * <p>
     * See {@link #convertToMetaDataValue(Object)} for details about the mapping.
     *
     * @param value the {@link MetaDataValue} to convert to its Java representation
     * @return an {@link Object} representing the same value
     */
    public Object convertFromMetaDataValue(MetaDataValue value) {
        switch (value.getDataCase()) {
            case TEXT_VALUE:
                return value.getTextValue();
            case BYTES_VALUE:
                return serializer.deserialize(new GrpcSerializedObject(value.getBytesValue()));
            case DOUBLE_VALUE:
                return value.getDoubleValue();
            case NUMBER_VALUE:
                return value.getNumberValue();
            case BOOLEAN_VALUE:
                return value.getBooleanValue();
            case DATA_NOT_SET:
            default:
                return null;
        }
    }
}
