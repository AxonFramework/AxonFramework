/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.spring.config.annotation;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.messaging.QualifiedNameUtils;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.utils.StubDomainEvent;

import java.util.UUID;

import static org.axonframework.messaging.QualifiedNameUtils.fromDottedName;
import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.axonframework.modelling.command.AggregateLifecycle.markDeleted;

public class StubAggregate {

    private int invocationCount;

    @AggregateIdentifier
    private String identifier;

    @SuppressWarnings("unused")
    public StubAggregate() {
        identifier = UUID.randomUUID().toString();
    }

    public StubAggregate(Object identifier) {
        this.identifier = identifier.toString();
    }

    @SuppressWarnings("unused")
    public void doSomething() {
        apply(new StubDomainEvent());
    }

    @EventSourcingHandler
    protected void handle(EventMessage<?> event) {
        identifier = ((DomainEventMessage<?>) event).getAggregateIdentifier();
        invocationCount++;
    }

    @SuppressWarnings("unused")
    public int getInvocationCount() {
        return invocationCount;
    }

    @SuppressWarnings("unused")
    public DomainEventMessage<?> createSnapshotEvent() {
        return new GenericDomainEventMessage<>(
                "test", identifier, 5L, QualifiedNameUtils.fromDottedName("test.snapshot"), new StubDomainEvent()
        );
    }

    @SuppressWarnings("unused")
    public void delete() {
        apply(new StubDomainEvent());
        markDeleted();
    }
}
