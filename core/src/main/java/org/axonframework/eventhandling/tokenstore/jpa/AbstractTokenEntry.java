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

import javax.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * Abstract base class of a JPA entry containing a serialized tracking token belonging to a given process.
 *
 * @author Rene de Waele
 */
@MappedSuperclass
public abstract class AbstractTokenEntry<T> {
    @Basic(optional = false)
    @Lob
    private T token;
    @Basic(optional = false)
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
        SerializedObject<T> serializedToken = serializer.serialize(token, contentType);
        this.token = serializedToken.getData();
        this.tokenType = serializedToken.getType().getName();
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
    public abstract String getProcessName();

    /**
     * Returns the segment index of the process to which this token belongs.
     *
     * @return the segment index
     */
    public abstract int getSegment();

    /**
     * Returns a serialized version of the token.
     *
     * @return the serialized token
     */
    @SuppressWarnings("unchecked")
    public SerializedObject<T> getToken() {
        return new SimpleSerializedObject<>(token, (Class<T>) token.getClass(), getTokenType());
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
     * Primary key for token entries used by JPA
     */
    @SuppressWarnings("UnusedDeclaration")
    public static class PK implements Serializable {
        private static final long serialVersionUID = 1L;

        private String processName;
        private int segment;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PK pk = (PK) o;
            return segment == pk.segment && Objects.equals(processName, pk.processName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(processName, segment);
        }
    }
}
