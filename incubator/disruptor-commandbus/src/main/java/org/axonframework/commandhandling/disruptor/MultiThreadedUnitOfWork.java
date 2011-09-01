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

package org.axonframework.commandhandling.disruptor;

import org.axonframework.domain.AggregateRoot;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.Event;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.EventSourcedAggregateRoot;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.SaveAggregateCallback;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Allard Buijze
 */
public class MultiThreadedUnitOfWork implements UnitOfWork {

    private boolean committed;
    private Throwable rollbackReason;

    private final List<DomainEvent> eventsToPublish = new ArrayList<DomainEvent>();
    private final Set<EventSourcedAggregateRoot> aggregates = new HashSet<EventSourcedAggregateRoot>();

    @Override
    public void commit() {
        committed = true;
        for (EventSourcedAggregateRoot aggregate : aggregates) {
            DomainEventStream uncommittedEvents = aggregate.getUncommittedEvents();
            while (uncommittedEvents.hasNext()) {
                eventsToPublish.add(uncommittedEvents.next());
            }
            aggregate.commitEvents();
        }
        CurrentUnitOfWork.clear(this);
    }

    @Override
    public void rollback() {
        rollback(null);
    }

    @Override
    public void rollback(Throwable cause) {
        rollbackReason = cause;
        for (EventSourcedAggregateRoot aggregate : aggregates) {
            aggregate.commitEvents();
        }
        CurrentUnitOfWork.clear(this);
    }

    @Override
    public void start() {
        CurrentUnitOfWork.set(this);
    }

    @Override
    public boolean isStarted() {
        return !committed && rollbackReason == null;
    }

    @Override
    public void registerListener(UnitOfWorkListener listener) {
        throw new UnsupportedOperationException("Not supported yet!");
    }

    @Override
    public <T extends AggregateRoot> T registerAggregate(T aggregateRoot,
                                                         SaveAggregateCallback<T> saveAggregateCallback) {
        aggregates.add((EventSourcedAggregateRoot) aggregateRoot);
        return aggregateRoot;
    }

    @Override
    public void publishEvent(Event event, EventBus eventBus) {
        throw new UnsupportedOperationException("Not supported yet!");
    }

    public List<DomainEvent> getEvents() {
        return eventsToPublish;
    }
}
