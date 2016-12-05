/*
 * Copyright (c) 2010-2016. Axon Framework
 *
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

package org.axonframework.eventhandling.saga.repository.jpa;

import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;

import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;

/**
 * Java Persistence Entity allowing sagas to be stored in a relational database.
 *
 * @author Allard Buijze
 * @since 0.7
 */
@Entity
public class SagaEntry<T> {

    @SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
    @Id
    private String sagaId; // NOSONAR

    @Basic
    private String sagaType;
    @Basic
    private String revision;
    @Lob
    private byte[] serializedSaga;

    /**
     * Constructs a new SagaEntry for the given {@code saga}. The given saga must be serializable. The provided
     * saga is not modified by this operation.
     *
     * @param saga           The saga to store
     * @param sagaIdentifier The saga identifier
     * @param serializer     The serialization mechanism to convert the Saga to a byte stream
     */
    public SagaEntry(T saga, String sagaIdentifier, Serializer serializer) {
        this.sagaId = sagaIdentifier;
        SerializedObject<byte[]> serialized = serializer.serialize(saga, byte[].class);
        this.serializedSaga = serialized.getData();
        this.sagaType = serialized.getType().getName();
        this.revision = serialized.getType().getRevision();
    }

    /**
     * Constructor required by JPA. Do not use.
     *
     * @see #SagaEntry(Object, String, Serializer)
     */
    protected SagaEntry() {
        // required by JPA
    }

    /**
     * Returns the serialized form of the Saga.
     *
     * @return the serialized form of the Saga
     */
    public byte[] getSerializedSaga() {
        return serializedSaga; //NOSONAR
    }

    /**
     * Returns the identifier of the saga contained in this entry
     *
     * @return the identifier of the saga contained in this entry
     */
    public String getSagaId() {
        return sagaId;
    }

    /**
     * Returns the revision of the serialized saga
     *
     * @return the revision of the serialized saga
     */
    public String getRevision() {
        return revision;
    }

    /**
     * Returns the type identifier of the serialized saga
     *
     * @return the type identifier of the serialized saga
     */
    public String getSagaType() {
        return sagaType;
    }
}
