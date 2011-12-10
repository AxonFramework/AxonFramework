/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.eventsourcing;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.StubDomainEvent;

import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.Id;

/**
 * @author Allard Buijze
 */
@Entity
public class JpaEventSourcedAggregate extends AbstractEventSourcedAggregateRoot {

    private static final long serialVersionUID = -7774899863711655258L;
    @Basic
    private long counter;

    @Id
    private String identifier;

    JpaEventSourcedAggregate() {
    }

    public JpaEventSourcedAggregate(String identifier) {
        this.identifier = identifier;
    }

    public void increaseCounter() {
        apply(new StubDomainEvent());
    }

    @Override
    protected void handle(DomainEventMessage event) {
        counter++;
        if (MyAggregateDeletedEvent.class.isInstance(event.getPayload())) {
            markDeleted();
        }
    }

    public void delete() {
        apply(new MyAggregateDeletedEvent());
    }

    @Override
    public Object getIdentifier() {
        return identifier;
    }

    public static class MyAggregateDeletedEvent {

        private static final long serialVersionUID = 2996583157371670395L;
    }
}
