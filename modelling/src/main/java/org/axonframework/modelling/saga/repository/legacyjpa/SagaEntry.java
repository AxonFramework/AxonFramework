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

package org.axonframework.modelling.saga.repository.legacyjpa;

import org.axonframework.serialization.Serializer;

import javax.persistence.Entity;

/**
 * Java Persistence Entity allowing sagas to be stored in a relational database.
 *
 * @author Allard Buijze
 * @since 0.7
 * @deprecated in favor of using {@link org.axonframework.modelling.saga.repository.jpa.SagaEntry} which moved to
 * jakarta.
 */
@Deprecated
@Entity
public class SagaEntry<T> extends AbstractSagaEntry<byte[]> {

    /**
     * Constructs a new SagaEntry for the given {@code saga}. The given saga must be serializable. The provided saga is
     * not modified by this operation.
     *
     * @param saga           The saga to store
     * @param sagaIdentifier The saga identifier
     * @param serializer     The serialization mechanism to convert the Saga to a byte stream
     */
    public SagaEntry(T saga, String sagaIdentifier, Serializer serializer) {
        super(saga, sagaIdentifier, serializer, byte[].class);
    }

    /**
     * Constructor required by JPA. Do not use.
     *
     * @see #SagaEntry(Object, String, Serializer)
     */
    protected SagaEntry() {
        // required by JPA
    }
}