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
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Lob;
import javax.persistence.MappedSuperclass;
import java.io.Serializable;
import java.util.Objects;

/**
 * @author Rene de Waele
 */
@MappedSuperclass
@IdClass(AbstractTokenEntry.PK.class)
public abstract class AbstractTokenEntry<T> {
    @Id
    private String processName;
    @Id
    private int segment;
    @Basic(optional = false)
    @Lob
    private T token;
    @Basic(optional = false)
    private String tokenType;

    public AbstractTokenEntry(String process, int segment, TrackingToken token, Serializer serializer,
                              Class<T> contentType) {
        this.processName = process;
        this.segment = segment;
        SerializedObject<T> serializedToken = serializer.serialize(token, contentType);
        this.token = serializedToken.getData();
        this.tokenType = serializedToken.getType().getName();
    }

    protected AbstractTokenEntry() {
    }

    public String getProcessName() {
        return processName;
    }

    public int getSegment() {
        return segment;
    }

    @SuppressWarnings("unchecked")
    public SerializedObject<T> getToken() {
        return new SimpleSerializedObject<>(token, (Class<T>) token.getClass(), getTokenType());
    }

    protected SerializedType getTokenType() {
        return new SimpleSerializedType(tokenType, null);
    }

    @SuppressWarnings("UnusedDeclaration")
    public static class PK implements Serializable {
        private static final long serialVersionUID = 1L;

        private String processName;
        private int segment;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PK pk = (PK) o;
            return segment == pk.segment &&
                    Objects.equals(processName, pk.processName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(processName, segment);
        }
    }
}
