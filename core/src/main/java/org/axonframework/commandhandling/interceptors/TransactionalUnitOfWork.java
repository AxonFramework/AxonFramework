/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.commandhandling.interceptors;

import org.axonframework.domain.AggregateRoot;
import org.axonframework.domain.Event;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.unitofwork.SaveAggregateCallback;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkListener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the UnitOfWork that buffers all published events until it is committed. Aggregates that have not
 * been explicitly save in their aggregates will be saved when the UnitOfWork committs.
 * <p/>
 * This implementation requires a mechanism that explicitly commits or rolls back.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public class TransactionalUnitOfWork implements UnitOfWork {

    private final Map<AggregateRoot, AggregateEntry> registeredAggregates = new LinkedHashMap<AggregateRoot, AggregateEntry>();
    private final List<EventEntry> eventsToPublish = new LinkedList<EventEntry>();
    private final List<UnitOfWorkListener> listeners = new ArrayList<UnitOfWorkListener>();
    private Status status = Status.STARTED;

    private static enum Status {

        STARTED, COMMITTING, ROLLING_BACK, COMMITTED, ROLLED_BACK
    }

    @Override
    public <T extends AggregateRoot> void registerAggregate(T aggregateRoot, SaveAggregateCallback<T> callback) {
        registeredAggregates.put(aggregateRoot, new AggregateEntry<T>(aggregateRoot, callback));
    }

    @Override
    public <T extends AggregateRoot> void reportAggregateSaved(T aggregateRoot) {
        if (status == Status.STARTED) {
            registeredAggregates.remove(aggregateRoot);
        }
    }

    @Override
    public void registerListener(UnitOfWorkListener listener) {
        listeners.add(listener);
    }

    @Override
    public void publishEvent(Event event, EventBus eventBus) {
        eventsToPublish.add(new EventEntry(event, eventBus));
    }

    @Override
    public void rollback() {
        status = Status.ROLLING_BACK;
        registeredAggregates.clear();
        eventsToPublish.clear();
        for (UnitOfWorkListener listener : listeners) {
            listener.onRollback();
        }
        status = Status.ROLLED_BACK;
    }

    @Override
    public void commit() {
        status = Status.COMMITTING;
        for (AggregateEntry entry : registeredAggregates.values()) {
            entry.saveAggregate();
        }
        registeredAggregates.clear();
        for (EventEntry entry : eventsToPublish) {
            entry.publishEvent();
        }
        for (UnitOfWorkListener listener : listeners) {
            listener.afterCommit();
        }
        status = Status.COMMITTED;
    }

    private static class EventEntry {

        private final Event event;
        private final EventBus eventBus;

        public EventEntry(Event event, EventBus eventBus) {
            this.event = event;
            this.eventBus = eventBus;
        }

        public void publishEvent() {
            eventBus.publish(event);
        }
    }

    private static class AggregateEntry<T extends AggregateRoot> {

        private final T aggregateRoot;
        private final SaveAggregateCallback<T> callback;

        public AggregateEntry(T aggregateRoot, SaveAggregateCallback<T> callback) {
            this.aggregateRoot = aggregateRoot;
            this.callback = callback;
        }

        public void saveAggregate() {
            callback.save(aggregateRoot);
        }
    }
}
