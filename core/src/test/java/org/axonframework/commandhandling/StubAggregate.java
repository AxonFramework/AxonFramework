/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling;

import org.axonframework.commandhandling.model.AggregateLifecycle;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.*;
import org.axonframework.messaging.metadata.MetaData;

import java.util.UUID;

/**
 * @author Allard Buijze
 */
public class StubAggregate {

    @AggregateIdentifier
    private Object identifier;

    private int invocationCount;

    public StubAggregate() {
        identifier = UUID.randomUUID();
    }

    public StubAggregate(Object identifier) {
        this.identifier = identifier;
    }

    public void doSomething() {
        AggregateLifecycle.apply(new StubDomainEvent());
    }

    public String getIdentifier() {
        return identifier.toString();
    }

    @EventSourcingHandler
    protected void handle(EventMessage event) {
        identifier = ((DomainEventMessage) event).getAggregateIdentifier();
        invocationCount++;
    }

    public int getInvocationCount() {
        return invocationCount;
    }

    public DomainEventMessage createSnapshotEvent() {
        return new GenericDomainEventMessage<>("type", identifier.toString(), (long) 5,
                                               new StubDomainEvent(), MetaData.emptyInstance());
    }

    public void delete() {
        AggregateLifecycle.apply(new StubDomainEvent());
        AggregateLifecycle.markDeleted();
    }
}
