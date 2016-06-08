/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.test;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.AggregateIdentifier;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.EventSourcingHandler;

import java.util.UUID;

import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;
import static org.axonframework.commandhandling.model.AggregateLifecycle.markDeleted;
import static org.junit.Assert.assertNotNull;

/**
 * @author Allard Buijze
 */
class AnnotatedAggregate implements AnnotatedAggregateInterface {

    @SuppressWarnings("UnusedDeclaration")
    private transient int counter;
    private Integer lastNumber;
    @AggregateIdentifier
    private Object identifier;
    private MyEntity entity;

    public AnnotatedAggregate(Object identifier) {
        this.identifier = identifier;
    }

    public AnnotatedAggregate() {
    }

    @CommandHandler
    public AnnotatedAggregate(CreateAggregateCommand command, EventBus eventBus, HardToCreateResource resource) {
        assertNotNull("resource should not be null", resource);
        assertNotNull("Expected EventBus to be injected as resource", eventBus);
        apply(new MyEvent(command.getAggregateIdentifier() == null ?
                                  UUID.randomUUID() : command.getAggregateIdentifier(), 0));
    }

    @CommandHandler
    public void delete(DeleteCommand command) {
        apply(new MyAggregateDeletedEvent(command.isAsIllegalChange()));
        if (command.isAsIllegalChange()) {
            markDeleted();
        }
    }

    @CommandHandler
    public void doSomethingIllegal(IllegalStateChangeCommand command) {
        apply(new MyEvent(command.getAggregateIdentifier(), lastNumber + 1));
        lastNumber = command.getNewIllegalValue();
    }

    @EventSourcingHandler
    public void handleMyEvent(MyEvent event) {
        identifier = event.getAggregateIdentifier();
        lastNumber = event.getSomeValue();
        if (entity == null) {
            entity = new MyEntity();
        }
    }

    @EventSourcingHandler
    public void deleted(MyAggregateDeletedEvent event) {
        if (!event.isWithIllegalStateChange()) {
            markDeleted();
        }
    }

    @EventSourcingHandler
    public void handleAll(DomainEventMessage event) {
        System.out.println("Invoked with payload: " + event.getPayloadType().getName());
        // we don't care about events
    }

    @Override
    public void doSomething(TestCommand command) {
        // this state change should be accepted, since it happens on a transient value
        counter++;
        apply(new MyEvent(command.getAggregateIdentifier(), lastNumber + 1));
    }
}
