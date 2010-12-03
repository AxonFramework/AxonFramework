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

import java.io.Serializable;
import javax.persistence.Basic;
import javax.persistence.Embeddable;
import javax.persistence.Lob;

/**
 * @author Allard Buijze
 */
@Embeddable
public class AssociationEntryKey implements Serializable {

    private static final long serialVersionUID = -3465609483433943413L;

    @Basic
    private String sagaId;

    @Basic
    private String associationKey;

    @Lob
    private Serializable associationValue;

    public AssociationEntryKey(String sagaId, String associationKey, Serializable value) {
        this.sagaId = sagaId;
        this.associationKey = associationKey;
        this.associationValue = value;
    }

    protected AssociationEntryKey() {
        // needed by JPA
    }

    public String getSagaId() {
        return sagaId;
    }

    public String getAssociationKey() {
        return associationKey;
    }

    public Serializable getAssociationValue() {
        return associationValue;
    }
}
