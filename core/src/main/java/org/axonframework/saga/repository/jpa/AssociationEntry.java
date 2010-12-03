/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.saga.repository.jpa;

import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.repository.SagaStorageException;

import java.io.Serializable;
import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;

/**
 * @author Allard Buijze
 */
@Entity
public class AssociationEntry {

    @Id
    @GeneratedValue
    private Long id;

    @Basic
    private String sagaId;

    @Basic
    private String associationKey;

    @Lob
    private Serializable associationValue;

    public AssociationEntry(String sagaIdentifier, AssociationValue associationValue) {
        if (!Serializable.class.isInstance(associationValue.getValue())) {
            throw new SagaStorageException("Could not persist a saga association, since the value is not serializable");
        }
        this.sagaId = sagaIdentifier;
        this.associationKey = associationValue.getKey();
        this.associationValue = (Serializable) associationValue.getValue();
    }

    protected AssociationEntry() {
    }

    public AssociationValue getAssociationValue() {
        return new AssociationValue(associationKey, associationValue);
    }

    public String getSagaIdentifier() {
        return sagaId;
    }
}
