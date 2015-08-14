/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.repository;

import org.axonframework.domain.AbstractJpaAggregateRoot;
import org.axonframework.domain.IdentifierFactory;

import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class JpaAggregate extends AbstractJpaAggregateRoot {

    private static final long serialVersionUID = -7075224524414732603L;

    @Id
    private String id;

    @Basic
    private String message;

    public JpaAggregate(String message) {
        this.id = IdentifierFactory.getInstance().generateIdentifier();
        this.message = message;
    }

    /**
     * Constructor performing very basic initialization, as required by JPA.
     */
    protected JpaAggregate() {
    }

    @Override
    public String getIdentifier() {
        return id;
    }

    public void setMessage(String newMessage) {
        this.message = newMessage;
        registerEvent(new MessageUpdatedEvent(message));
    }

    public void delete() {
        markDeleted();
    }
}
