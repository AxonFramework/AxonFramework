/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.redis.eventhandling.tokenstore;

import org.axonframework.eventhandling.tokenstore.GenericTokenEntry;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.serialization.Serializer;

/**
 * Implementation of the GenericTokenEntry to have a temporary data representation with reuse of GenericTokenEntry
 * and AbstractTokenEntry functionality. The token is represented as a byte array.
 *
 * @author Michael Willemse
 */
public class RedisTokenEntry extends GenericTokenEntry<byte[]> {

    /**
     * Initializes a new Redis token entry for given {@code token}, {@code process} and {@code segment}. The given {@code
     * serializer} can be used to serialize the token before it is stored.
     *
     * @param token         The tracking token to store
     * @param serializer    The serializer to use when storing a serialized token
     * @param processorName The name of the processor to which this token belongs
     * @param segment       The segment of the processor to which this token belongs
     */
    public RedisTokenEntry(TrackingToken token, Serializer serializer, String processorName, int segment) {
        super(token, serializer, byte[].class, processorName, segment);
    }

    /**
     * Initializes a token entry from existing data.
     *
     * @param token         the serialized token
     * @param tokenType     the serialized type of the token
     * @param timestamp     the timestamp of the token
     * @param owner         the owner of the token
     * @param processorName The name of the processor to which this token belongs
     * @param segment       The segment of the processor to which this token belongs
     */
    public RedisTokenEntry(byte[] token, String tokenType, String timestamp, String owner, String processorName, int segment) {
        super(token, tokenType, timestamp, owner, processorName, segment, byte[].class);
    }
}
