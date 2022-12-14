/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.javax.modelling.saga.repository.jpa;

import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.MappedSuperclass;

/**
 * Java Persistence Entity allowing sagas to be stored in a relational database. The serialized type of the
 * @{@link SagaEntry} is declared in the concrete implementation of this class.
 *
 * @param <T> the serialized content-type of the saga.
 * @author Christophe Bouhier
 * @since 3.0.3
 */
@MappedSuperclass
public abstract class AbstractSagaEntry<T> {

    @SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
    @Id
    protected String sagaId; // NOSONAR
    @Basic
    protected String sagaType;
    @Basic
    protected String revision;
    @Lob
    @Column(length = 10000)
    protected T serializedSaga;


    /**
     * Constructs a new SagaEntry for the given {@code saga}. The given saga must be serializable. The provided saga is
     * not modified by this operation.
     *
     * @param saga           The saga to store
     * @param sagaIdentifier The saga identifier
     * @param serializer     The serialization mechanism to convert the Saga to a byte stream
     * @param contentType    The saga content type to serialize to
     */
    public AbstractSagaEntry(Object saga, String sagaIdentifier, Serializer serializer, Class<T> contentType) {
        this.sagaId = sagaIdentifier;
        SerializedObject<T> serialized = serializer.serialize(saga, contentType);
        this.serializedSaga = serialized.getData();
        this.sagaType = serialized.getType().getName();
        this.revision = serialized.getType().getRevision();
    }

    /**
     * Default constructor required by JPA.
     */
    protected AbstractSagaEntry() {
    }

    /**
     * Returns the serialized form of the Saga.
     *
     * @return the serialized form of the Saga
     */
    public T getSerializedSaga() {
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
