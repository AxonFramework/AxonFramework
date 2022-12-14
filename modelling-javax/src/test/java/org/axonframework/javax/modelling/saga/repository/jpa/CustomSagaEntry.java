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

import org.axonframework.serialization.Serializer;

import javax.persistence.Entity;

/**
 * A custom {@link SagaEntry} to declare a different serializartion type.
 *
 * @author Christophe Bouhier
 */
@Entity
public class CustomSagaEntry extends AbstractSagaEntry<String> {

    public CustomSagaEntry(Object saga, String sagaIdentifier, Serializer serializer) {
        super(saga, sagaIdentifier, serializer, String.class);
    }

    /**
     * Constructor required by JPA. Do not use.
     *
     * @see #CustomSagaEntry(Object, String, Serializer)
     */
    protected CustomSagaEntry() {
        // required by JPA
    }
}
