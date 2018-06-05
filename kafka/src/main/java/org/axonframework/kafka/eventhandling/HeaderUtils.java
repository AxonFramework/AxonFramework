/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.kafka.eventhandling;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.axonframework.common.Assert;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.serialization.SerializedObject;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

import static org.axonframework.messaging.Headers.MESSAGE_METADATA;
import static org.axonframework.messaging.Headers.defaultHeaders;

/**
 * Utility for dealing with {@link Headers}. Mostly for internal use.
 *
 * @author Nakul Mishra
 * @since 3.0
 */
public class HeaderUtils {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private HeaderUtils() {
        // private ctor
    }

    /**
     * Converts bytes to long.
     *
     * @param value representing long.
     * @return the long.
     */
    public static Long asLong(byte[] value) {
        return value != null ? ByteBuffer.wrap(value).getLong() : null;
    }

    /**
     * Return Long representation of the <code>value</code> stored under a given <code>key</code> inside the {@link
     * Headers}. In case of missing entry <code>null</code> is returned.
     *
     * @param headers the Kafka headers.
     * @param key     the expected key.
     * @return the value.
     */
    public static Long valueAsLong(Headers headers, String key) {
        return asLong(value(headers, key));
    }

    /**
     * Converts bytes to String.
     *
     * @param value bytes representing {@link String}.
     * @return the String.
     */
    public static String asString(byte[] value) {
        return value != null ? new String(value, UTF_8) : null;
    }

    /**
     * Return String representation of the <code>value</code> stored under a given <code>key</code> inside the {@link
     * Headers}. In case of missing entry <code>null</code> is returned.
     *
     * @param headers the Kafka headers.
     * @param key     the expected key.
     * @return the value.
     */
    public static String valueAsString(Headers headers, String key) {
        return asString(value(headers, key));
    }

    /**
     * Return String representation of the <code>value</code> stored under a given <code>key</code> inside the
     * {@link Headers}. In case of missing entry <code>defaultValue</code> is returned.
     *
     * @param headers      the Kafka headers.
     * @param key          the expected key.
     * @param defaultValue the value when key is missing.
     * @return the value
     */
    public static String valueAsString(Headers headers, String key, String defaultValue) {
        return Objects.toString(asString(value(headers, key)), defaultValue);
    }

    /**
     * Return the <code>value</code> stored under a given <code>key</code> inside the {@link
     * Headers}. In case of missing entry <code>null</code> is returned.
     *
     * @param headers the Kafka headers.
     * @param key     the expected key.
     * @return the value.
     */
    public static byte[] value(Headers headers, String key) {
        Assert.isTrue(headers != null, () -> "header may not be null");
        Header header = headers.lastHeader(key);
        return header != null ? header.value() : null;
    }

    /**
     * Converts primitive arithmetic types to byte array.
     *
     * @param value the number.
     * @return the bytes.
     */
    public static byte[] toBytes(Number value) {
        if (value instanceof Short) {
            return toBytes((Short) value);
        } else if (value instanceof Integer) {
            return toBytes((Integer) value);
        } else if (value instanceof Long) {
            return toBytes((Long) value);
        } else if (value instanceof Float) {
            return toBytes((Float) value);
        } else if (value instanceof Double) {
            return toBytes((Double) value);
        }
        throw new IllegalArgumentException("Cannot convert " + value + " to bytes");
    }

    /**
     * Creates a new {@link org.apache.kafka.common.header.internals.RecordHeader} based on <code>key</code> and
     * <code>value</code> and adds it to <code>target</code>. The value is converted to bytes and follows this logic:
     * <ul>
     * <li>Instant - calls {@link Instant#toEpochMilli()}</li>
     * <li>Number - calls {@link HeaderUtils#toBytes} </li>
     * <li>String/custom object - calls {@link String#toString()} </li>
     * <li>null - <code>null</code> </li>
     * </ul>
     *
     * @param target the Kafka headers.
     * @param key    the key you want to add in the header.
     * @param value  the value you want to store in the header.
     */
    public static void addHeader(Headers target, String key, Object value) {
        Assert.notNull(target, () -> "target may not be null");
        if (value instanceof Instant) {
            target.add(key, toBytes((Number) ((Instant) value).toEpochMilli()));
        } else if (value instanceof Number) {
            target.add(key, toBytes((Number) value));
        } else if (value instanceof String) {
            target.add(key, ((String) value).getBytes(UTF_8));
        } else if (value == null) {
            target.add(key, null);
        } else {
            target.add(key, value.toString().getBytes(UTF_8));
        }
    }

    /**
     * Extract keys from {@link Headers}.
     *
     * @param headers the Kafka header.
     * @return the keys.
     */
    public static Set<String> keys(Headers headers) {
        Assert.notNull(headers, () -> "header may not be null");
        Set<String> keys = new HashSet<>();
        headers.forEach(header -> keys.add(header.key()));
        return keys;
    }

    /**
     * Generates <code>key</code>, used to identify Axon metadata in {@link RecordHeader}.
     *
     * @param key the actual key.
     * @return the generated key.
     */
    public static String generateMetadataKey(String key) {
        return MESSAGE_METADATA + "-" + key;
    }

    /**
     * Extracts actual key name used to send Axon metadata.
     * E.g.<code>"axon-metadata-foo"</code> will extract <code>foo</code>
     *
     * @param metadataKey the generated metadata key.
     * @return the actual key name.
     */
    public static String extractKey(String metadataKey) {
        Assert.isTrue(metadataKey != null && metadataKey.startsWith(MESSAGE_METADATA + "-"), () -> "Invalid key");
        return metadataKey.substring((MESSAGE_METADATA + "-").length());
    }

    /**
     * Extract axon metadata(if any) attached with {@link Headers}.
     *
     * @param target kafka header.
     * @return the metadata.
     */
    public static Map<String, Object> extractAxonMetadata(Headers target) {
        Assert.notNull(target, () -> "header may not be null");
        Map<String, Object> metaData = new HashMap<>();
        target.forEach((header) -> {
            String key = header.key();
            if (isValidMetadataKey(key)) {
                metaData.put(extractKey(key), asString(header.value()));
            }
        });
        return metaData;
    }

    /**
     * Generates {@link Headers} based on {@link EventMessage} and {@link SerializedObject}.
     *
     * @param eventMessage      provides event data.
     * @param serializedObject  payload.
     * @param headerValueMapper function for converting <code>value</code> to bytes. Since {@link
     *                          RecordHeader} can handle only bytes this function needs to define the logic how to
     *                          convert a given value to bytes. See {@link HeaderUtils#byteMapper()} for sample
     *                          implementation.
     * @return the Headers.
     */
    public static Headers toHeaders(EventMessage<?> eventMessage,
                                    SerializedObject<byte[]> serializedObject,
                                    BiFunction<String, Object, RecordHeader> headerValueMapper) {
        Assert.notNull(eventMessage, () -> "Event message may not be null");
        Assert.notNull(serializedObject, () -> "Serialized Object may not be null");
        Assert.notNull(headerValueMapper, () -> "function to map header key and value may not be null");
        RecordHeaders headers = new RecordHeaders();
        addAxonHeaders(headers, eventMessage, serializedObject, headerValueMapper);
        return headers;
    }

    /**
     * Handles <code>value</code> conversion to bytes.
     *
     * @return the mapping function.
     */
    public static BiFunction<String, Object, RecordHeader> byteMapper() {
        return (key, value) -> value instanceof byte[] ?
                new RecordHeader(key, (byte[]) value) :
                new RecordHeader(key, value == null ? null : value.toString().getBytes(UTF_8));
    }

    private static boolean isValidMetadataKey(String key) {
        return key.startsWith(MESSAGE_METADATA + "-");
    }

    private static void addAxonHeaders(Headers target, EventMessage<?> eventMessage,
                                       SerializedObject<byte[]> serializedObject,
                                       BiFunction<String, Object, RecordHeader> headerValueMapper) {
        eventMessage.getMetaData()
                    .forEach((k, v) -> target.add(headerValueMapper.apply(generateMetadataKey(k), v)));
        defaultHeaders(eventMessage, serializedObject).forEach((k, v) -> addHeader(target, k, v));
    }

    private static byte[] toBytes(Short value) {
        ByteBuffer buffer = ByteBuffer.allocate(Short.BYTES);
        buffer.putShort(value);
        return buffer.array();
    }

    private static byte[] toBytes(Integer value) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.putInt(value);
        return buffer.array();
    }

    private static byte[] toBytes(Long value) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(value);
        return buffer.array();
    }

    private static byte[] toBytes(Float value) {
        ByteBuffer buffer = ByteBuffer.allocate(Float.BYTES);
        buffer.putFloat(value);
        return buffer.array();
    }

    private static byte[] toBytes(Double value) {
        ByteBuffer buffer = ByteBuffer.allocate(Double.BYTES);
        buffer.putDouble(value);
        return buffer.array();
    }
}
