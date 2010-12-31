/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.saga.repository.jpa;

import org.axonframework.saga.Saga;
import org.axonframework.saga.repository.SagaSerializer;
import org.axonframework.util.Assert;

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
public class SagaEntry {

    @Id
    private String sagaId;

    @Lob
    private byte[] serializedSaga;

    /**
     * Constructs a new SagaEntry for the given <code>saga</code>. The given saga must be serializable. The provided
     * saga is not modified by this operation.
     *
     * @param saga       The saga to store
     * @param serializer The serialization mechanism to convert the Saga to a byte stream
     */
    public SagaEntry(Saga saga, SagaSerializer serializer) {
        this.sagaId = saga.getSagaIdentifier();
        this.serializedSaga = serializer.serialize(saga);
    }

    /**
     * Returns the identifier of the saga stored in this entry.
     *
     * @return the identifier of the saga stored in this entry
     */
    public String getSagaId() {
        return sagaId;
    }

    /**
     * Returns the Saga instance stored in this entry.
     *
     * @param serializer The serializer to decode the Saga
     * @return the Saga instance stored in this entry
     */
    public Saga getSaga(SagaSerializer serializer) {
        return serializer.deserialize(serializedSaga);
    }

    /**
     * Updates the saga instance in this entry. The given saga must be serializable.
     *
     * @param saga       the saga to update
     * @param serializer The serializer that can serialize the given Saga
     */
    public void updateSaga(Saga saga, SagaSerializer serializer) {
        Assert.isTrue(sagaId.equals(saga.getSagaIdentifier()),
                      "Cannot update an entry with another saga. Make sure Identifiers have not been altered.");
        this.serializedSaga = serializer.serialize(saga);
    }

    /**
     * Constructor required by JPA. Do not use.
     *
     * @see #SagaEntry(org.axonframework.saga.Saga, org.axonframework.saga.repository.SagaSerializer)
     */
    protected SagaEntry() {
        // required by JPA
    }
}
