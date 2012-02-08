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
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.EventRegistrationCallback;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.EventSourcedAggregateRoot;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.SaveAggregateCallback;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Specialized UnitOfWork instance for the DisruptorCommandBus. It expects the executing command to target a single
 * aggregate instance.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DisruptorUnitOfWork implements UnitOfWork, EventRegistrationCallback {

    private static final DomainEventStream EMPTY_DOMAIN_EVENT_STREAM = new SimpleDomainEventStream();

    private boolean committed;
    private Throwable rollbackReason;
    private DomainEventStream eventsToStore = EMPTY_DOMAIN_EVENT_STREAM;

    private final List<EventMessage> eventsToPublish = new ArrayList<EventMessage>();
    private final EventSourcedAggregateRoot aggregate;
    private List<UnitOfWorkListener> listeners = new ArrayList<UnitOfWorkListener>();

    /**
     * Initializes a UnitOfWork for execution of a command on the given <code>aggregate</code>.
     *
     * @param aggregate The aggregate on which a command is to be executed.
     */
    public DisruptorUnitOfWork(EventSourcedAggregateRoot aggregate) {
        this.aggregate = aggregate;
    }

    @Override
    public void commit() {
        committed = true;
        eventsToStore = aggregate.getUncommittedEvents();
        aggregate.commitEvents();
        CurrentUnitOfWork.clear(this);
    }

    /**
     * Invokes this UnitOfWork's on-prepare-commit cycle. Typically, this is run after the actual aggregates have been
     * committed, but before any of the changes are made public.
     */
    public void onPrepareCommit() {
        for (UnitOfWorkListener listener : listeners) {
            listener.onPrepareCommit(Collections.<AggregateRoot>singleton(aggregate), eventsToPublish);
        }
    }

    /**
     * Invokes this UnitOfWork's on-after-commit cycle. Typically, this is run after all the events have been stored
     * and
     * published.
     */
    public void onAfterCommit() {
        for (UnitOfWorkListener listener : listeners) {
            listener.afterCommit();
        }
    }

    /**
     * Invokes this UnitOfWork's on-cleanup cycle. Typically, this is run after all the events have been stored and
     * published and the after-commit cycle has been executed.
     */
    public void onCleanup() {
        for (UnitOfWorkListener listener : listeners) {
            listener.onCleanup();
        }
    }

    @Override
    public void rollback() {
        rollback(null);
    }

    @Override
    public void rollback(Throwable cause) {
        rollbackReason = cause;
        aggregate.commitEvents();
        CurrentUnitOfWork.clear(this);
        onCleanup();
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
        this.listeners.add(listener);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends AggregateRoot> T registerAggregate(T aggregateRoot, EventBus eventBus,
                                                         SaveAggregateCallback<T> saveAggregateCallback) {
        if (!aggregateRoot.getClass().isInstance(aggregate) || !aggregate.getIdentifier().equals(aggregateRoot
                                                                                                         .getIdentifier())) {
            throw new IllegalArgumentException("Cannot register an aggregate other than the preloaded aggregate. "
                                                       + "This error typically occurs when an aggregate is loaded "
                                                       + "which is not the aggregate targeted by the command");
        }
        aggregate.addEventRegistrationCallback(this);
        return (T) aggregate;
    }

    @Override
    public void publishEvent(EventMessage event, EventBus eventBus) {
        eventsToPublish.add(event);
    }

    /**
     * Returns the events that need to be stored as part of this Unit of Work.
     *
     * @return the events that need to be stored as part of this Unit of Work
     */
    public DomainEventStream getEventsToStore() {
        return eventsToStore;
    }

    /**
     * Returns the events that need to be published as part of this Unit of Work.
     *
     * @return the events that need to be published as part of this Unit of Work
     */
    public List<EventMessage> getEventsToPublish() {
        return eventsToPublish;
    }

    @Override
    public <T> DomainEventMessage<T> onRegisteredEvent(DomainEventMessage<T> event) {
        DomainEventMessage<T> message = (DomainEventMessage<T>) processListeners(event);
        eventsToPublish.add(message);
        return message;
    }

    private <T> EventMessage<T> processListeners(DomainEventMessage<T> event) {
        EventMessage<T> message = event;
        for (UnitOfWorkListener listener : listeners) {
            message = listener.onEventRegistered(message);
        }
        return message;
    }
}
