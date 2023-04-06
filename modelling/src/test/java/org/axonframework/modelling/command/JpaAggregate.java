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

package org.axonframework.modelling.command;

import jakarta.persistence.Basic;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.modelling.utils.StubDomainEvent;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

@AggregateRoot(type = JpaAggregate.JPA_AGGREGATE_CUSTOM_TYPE_NAME)
@Entity
public class JpaAggregate {

    public static final String JPA_AGGREGATE_CUSTOM_TYPE_NAME = "MyJpaAggregate";

    @AggregateIdentifier
    @Id
    private String id;

    @AggregateVersion
    @Version
    private Long version;

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

    public void setMessage(String newMessage) {
        this.message = newMessage;
        apply(new StubDomainEvent(message));
    }

    public void delete() {
        AggregateLifecycle.markDeleted();
    }

    public String getIdentifier() {
        return id;
    }

    public Long getVersion() {
        return version;
    }
}
