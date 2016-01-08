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

package org.axonframework.integrationtests.jpa;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;

import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author Allard Buijze
 */
@Entity
public class SimpleJpaEventSourcedAggregate extends AbstractAnnotatedAggregateRoot {

    private transient List<DomainEventMessage<?>> registeredEvents;

    @Basic
    private long counter;
    @Id
    private final String identifier;

    public SimpleJpaEventSourcedAggregate() {
        identifier = UUID.randomUUID().toString();
    }

    public SimpleJpaEventSourcedAggregate(String identifier) {
        this.identifier = identifier;
    }

    public void doSomething() {
        apply(new SomeEvent());
    }

    @Override
    protected <T> void registerEventMessage(EventMessage<T> message) {
        super.registerEventMessage(message);
        getRegisteredEvents().add((DomainEventMessage<?>) message);
    }

    public List<DomainEventMessage<?>> getRegisteredEvents() {
        if (registeredEvents == null) {
            registeredEvents = new ArrayList<>();
        }
        return registeredEvents;
    }

    public int getRegisteredEventCount() {
        return registeredEvents == null ? 0 : registeredEvents.size();
    }

    public void reset() {
        registeredEvents.clear();
    }

    @EventSourcingHandler
    private void onSomeEvent(SomeEvent event) {
        counter++;
    }

    public long getInvocationCount() {
        return counter;
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }
}


