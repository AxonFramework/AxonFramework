/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.integrationtests.utils;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;

import java.util.UUID;

/**
 * @author Allard Buijze
 */
public class StubAggregate {

    @AggregateIdentifier
    private String identifier;

    public StubAggregate() {
        identifier = UUID.randomUUID().toString();
    }

    public StubAggregate(Object identifier) {
        this.identifier = identifier.toString();
    }

    public void doSomething() {
        AggregateLifecycle.apply(new StubDomainEvent());
    }

    public String getIdentifier() {
        return identifier;
    }

    @EventHandler
    protected void handle(EventMessage event) {
        identifier = ((DomainEventMessage) event).getAggregateIdentifier();
    }

    public void delete() {
        AggregateLifecycle.apply(new StubDomainEvent());
        AggregateLifecycle.markDeleted();
    }
}
