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

import org.apache.kafka.common.header.Headers;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Utility for dealing with {@link Headers}.
 *
 * @author Nakul Mishra
 * @since 3.0
 */
public class HeaderUtils {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private HeaderUtils() {
    }

    /**
     * Converts bytes to String.
     *
     * @param value bytes representing string.
     * @return the String.
     */
    public static String asString(byte[] value) {
        return value != null ? new String(value, UTF_8) : null;
    }

    /**
     * Converts long to bytes.
     *
     * @param value long.
     * @return the bytes.
     */
    public static byte[] toBytes(long value) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(value);
        return buffer.array();
    }

    /**
     * Converts bytes to long.
     *
     * @param bytes representing long.
     * @return the long.
     */
    public static long asLong(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getLong();
    }

    /**
     * Adds a new {@link org.apache.kafka.common.header.internals.RecordHeader}
     *
     * @param target parent header.
     * @param key    header name.
     * @param value  header value (sent in bytes).
     */
    public static void addBytes(Headers target, String key, Object value) {
        if (value instanceof Instant) {
            target.add(key, toBytes(((Instant) value).toEpochMilli()));
        } else if (value instanceof Long) {
            target.add(key, toBytes((Long) value));
        } else if (value instanceof String) {
            target.add(key, ((String) value).getBytes());
        } else if (value == null) {
            target.add(key, null);
        } else {
            target.add(key, value.toString().getBytes());
        }
    }

    /**
     * Extract keys from {@link Headers}.
     *
     * @param headers Kafka header.
     * @return all keys present in {@link Headers}.
     */
    public static Set<String> keys(Headers headers) {
        Set<String> keys = new HashSet<>();
        headers.forEach(header -> keys.add(header.key()));
        return keys;
    }

    /**
     * Extract axon metadata(if any) attached with {@link Headers}.
     *
     * @param headers Kafka header.
     * @return Map containing  metadata.
     */
    public static Map<String, Object> extractAxonMetadata(Headers headers) {
        Map<String, Object> metaData = new HashMap<>();
        headers.forEach((header) -> {
            String k = header.key();
            byte[] v = header.value();
            if (k.startsWith(org.axonframework.messaging.Headers.MESSAGE_METADATA + "-")) {
                metaData.put(k.substring((org.axonframework.messaging.Headers.MESSAGE_METADATA + "-").length()),
                             asString(v));
            }
        });
        return metaData;
    }
}
