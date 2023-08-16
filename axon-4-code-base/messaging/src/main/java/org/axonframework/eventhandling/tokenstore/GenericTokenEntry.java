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

package org.axonframework.eventhandling.tokenstore;

import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.serialization.Serializer;

/**
 * Generic implementation of a token entry.
 *
 * @author Rene de Waele
 * @param <T> The serialized data type of the token
 */
public class GenericTokenEntry<T> extends AbstractTokenEntry<T> {

    private final String processorName;
    private final int segment;
    private final Class<T> contentType;

    /**
     * Initializes a new token entry for given {@code token}, {@code process} and {@code segment}. The given {@code
     * serializer} can be used to serialize the token before it is stored.
     *
     * @param token         The tracking token to store
     * @param serializer    The serializer to use when storing a serialized token
     * @param processorName The name of the processor to which this token belongs
     * @param segment       The segment of the processor to which this token belongs
     * @param contentType   The content type after serialization
     */
    public GenericTokenEntry(TrackingToken token, Serializer serializer, Class<T> contentType, String processorName,
                             int segment) {
        super(token, serializer, contentType);
        this.processorName = processorName;
        this.segment = segment;
        this.contentType = contentType;
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
     * @param contentType   The content type after serialization
     */
    public GenericTokenEntry(T token, String tokenType, String timestamp, String owner, String processorName,
                             int segment, Class<T> contentType) {
        super(token, tokenType, timestamp, owner);
        this.processorName = processorName;
        this.segment = segment;
        this.contentType = contentType;
    }

    @Override
    public String getProcessorName() {
        return processorName;
    }

    @Override
    public int getSegment() {
        return segment;
    }

    @Override
    public void updateToken(TrackingToken token, Serializer serializer) {
        updateToken(token, serializer, contentType);
    }


}
