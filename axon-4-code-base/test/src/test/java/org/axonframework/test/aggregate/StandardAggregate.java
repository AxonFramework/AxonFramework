/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.test.aggregate;

import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventsourcing.AbstractAggregateFactory;
import org.axonframework.eventsourcing.EventSourcingHandler;

import java.util.UUID;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.axonframework.modelling.command.AggregateLifecycle.markDeleted;

/**
 * @author Allard Buijze
 */
class StandardAggregate {

    @SuppressWarnings("UnusedDeclaration")
    private transient int counter;
    private Integer lastNumber;
    @AggregateIdentifier
    private String identifier;
    private MyEntity entity;

    public StandardAggregate(Object aggregateIdentifier) {
        identifier = aggregateIdentifier.toString();
    }

    public StandardAggregate(int initialValue, Object aggregateIdentifier) {
        apply(new MyEvent(aggregateIdentifier == null ? UUID.randomUUID() : aggregateIdentifier, initialValue));
    }

    public void delete(boolean withIllegalStateChange) {
        apply(new MyAggregateDeletedEvent(withIllegalStateChange));
        if (withIllegalStateChange) {
            markDeleted();
        }
    }

    public void doSomethingIllegal(Integer newIllegalValue) {
        apply(new MyEvent(identifier, lastNumber + 1));
        lastNumber = newIllegalValue;
    }

    @EventSourcingHandler
    public void handleMyEvent(MyEvent event) {
        identifier = event.getAggregateIdentifier().toString();
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
        // we don't care about events
    }

    public void doSomething() {
        // this state change should be accepted, since it happens on a transient value
        counter++;
        apply(new MyEvent(identifier, lastNumber + 1));
    }

    @Override
    public int hashCode() {
        // This hashCode implementation is EVIL! But it's on purpose, because it shouldn't matter for Axon.
        int result = counter;
        result = 31 * result + (lastNumber != null ? lastNumber.hashCode() : 0);
        result = 31 * result + (identifier != null ? identifier.hashCode() : 0);
        result = 31 * result + (entity != null ? entity.hashCode() : 0);
        return result;
    }

    static class Factory extends AbstractAggregateFactory<StandardAggregate> {

        public Factory() {
            super(StandardAggregate.class);
        }

        @Override
        protected StandardAggregate doCreateAggregate(String aggregateIdentifier, DomainEventMessage firstEvent) {
            return new StandardAggregate(aggregateIdentifier);
        }
    }
}
