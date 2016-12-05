/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.tokenstore.jpa;

import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.serialization.*;

import javax.persistence.Basic;
import javax.persistence.Lob;
import javax.persistence.MappedSuperclass;

/**
 * Abstract base class of a JPA entry containing a serialized tracking token belonging to a given process.
 *
 * @author Rene de Waele
 */
@MappedSuperclass
public abstract class AbstractTokenEntry<T> {
    @Lob
    private T token;
    @Basic
    private String tokenType;

    /**
     * Initializes a new token entry for given {@code token}, {@code process} and {@code segment}. The given {@code
     * serializer} can be used to serialize the token before it is stored.
     *
     * @param token       The tracking token to store
     * @param serializer  The serializer to use when storing a serialized token
     * @param contentType The content type after serialization
     */
    protected AbstractTokenEntry(TrackingToken token, Serializer serializer,
                                 Class<T> contentType) {
        if (token != null) {
            SerializedObject<T> serializedToken = serializer.serialize(token, contentType);
            this.token = serializedToken.getData();
            this.tokenType = serializedToken.getType().getName();
        }
    }

    /**
     * Default constructor required for JPA
     */
    protected AbstractTokenEntry() {
    }

    /**
     * Returns the name of the process to which this token belongs.
     *
     * @return the process name
     */
    public abstract String getProcessorName();

    /**
     * Returns the segment index of the process to which this token belongs.
     *
     * @return the segment index
     */
    public abstract int getSegment();

    @SuppressWarnings("unchecked")
    private SerializedObject<T> getSerializedToken() {
        if (token == null) {
            return null;
        }
        return new SimpleSerializedObject<>(token, (Class<T>) token.getClass(), getTokenType());
    }

    /**
     * Returns the token, deserializing it with given {@code serializer}
     * @param serializer The serialize to deserialize the token with
     * @return the deserialized token stored in this entry
     */
    public TrackingToken getToken(Serializer serializer) {
        return token == null ? null : serializer.deserialize(getSerializedToken());
    }

    /**
     * Returns the {@link SerializedType} of the serialized token.
     *
     * @return the serialized type of the token
     */
    protected SerializedType getTokenType() {
        return new SimpleSerializedType(tokenType, null);
    }

    /**
     * Update the token data to the given {@code token}, using given {@code serializer} to serialize it to the given
     * {@code contentType}.
     *
     * @param token       The token representing the state to update to
     * @param serializer  The serializer to update token to
     * @param contentType The type of data to represent the serialized data in
     */
    protected final void updateToken(TrackingToken token, Serializer serializer, Class<T> contentType) {
        SerializedObject<T> serializedToken = serializer.serialize(token, contentType);
        this.token = serializedToken.getData();
        this.tokenType = serializedToken.getType().getName();
    }
}
