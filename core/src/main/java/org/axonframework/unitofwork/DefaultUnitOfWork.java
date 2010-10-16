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

package org.axonframework.unitofwork;

import org.axonframework.domain.AggregateRoot;
import org.axonframework.domain.Event;
import org.axonframework.eventhandling.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Implementation of the UnitOfWork that buffers all published events until it is committed. Aggregates that have not
 * been explicitly save in their aggregates will be saved when the UnitOfWork committs.
 * <p/>
 * This implementation requires a mechanism that explicitly commits or rolls back.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public class DefaultUnitOfWork extends UnitOfWork {

    private final Map<AggregateRoot, AggregateEntry> registeredAggregates = new LinkedHashMap<AggregateRoot, AggregateEntry>();
    private final Queue<EventEntry> eventsToPublish = new LinkedList<EventEntry>();
    private final Set<UnitOfWorkListener> listeners = new HashSet<UnitOfWorkListener>();
    private Status dispatcherStatus = Status.READY;

    private static enum Status {

        READY, DISPATCHING
    }

    /**
     * Starts a new DefaultUnitOfWork instance, registering it a CurrentUnitOfWork. This methods returns the started
     * UnitOfWork instance.
     *
     * @return the started UnitOfWork instance
     */
    public static UnitOfWork startAndGet() {
        DefaultUnitOfWork uow = new DefaultUnitOfWork();
        uow.start();
        return uow;
    }

    @Override
    protected void doRollback() {
        registeredAggregates.clear();
        eventsToPublish.clear();
        notifyListenersRollback();
        listeners.clear();
    }

    @Override
    protected void doCommit() {
        notifyListenersPrepareCommit();
        saveAggregates();
        publishEvents();
        notifyListenersAfterCommit();
    }

    @Override
    public <T extends AggregateRoot> void registerAggregate(T aggregate,
                                                            SaveAggregateCallback<T> callback) {
        registeredAggregates.put(aggregate, new AggregateEntry<T>(aggregate, callback));
    }

    @Override
    public void registerListener(UnitOfWorkListener listener) {
        listeners.add(listener);
    }

    @Override
    public void publishEvent(Event event, EventBus eventBus) {
        eventsToPublish.add(new EventEntry(event, eventBus));
    }

    /**
     * Send a {@link UnitOfWorkListener#onRollback()} notification to all registered listeners.
     */
    protected void notifyListenersRollback() {
        for (UnitOfWorkListener listener : listeners) {
            listener.onRollback();
        }
    }

    /**
     * Send a {@link UnitOfWorkListener#afterCommit()} notification to all registered listeners.
     */
    protected void notifyListenersAfterCommit() {
        for (UnitOfWorkListener listener : listeners) {
            listener.afterCommit();
        }
    }

    /**
     * Publishes all registered events to their respective event bus.
     */
    protected void publishEvents() {
        if (dispatcherStatus == Status.DISPATCHING) {
            // this prevents events from overtaking each other
            return;
        }
        dispatcherStatus = Status.DISPATCHING;
        while (!eventsToPublish.isEmpty()) {
            eventsToPublish.poll().publishEvent();
        }
        dispatcherStatus = Status.READY;
    }

    /**
     * Saves all registered aggregates by calling their respective callbacks.
     */
    protected void saveAggregates() {
        for (AggregateEntry entry : registeredAggregates.values()) {
            entry.saveAggregate();
        }
        registeredAggregates.clear();
    }

    /**
     * Send a {@link UnitOfWorkListener#onPrepareCommit(java.util.Set, java.util.List)} notification to all registered
     * listeners.
     */
    protected void notifyListenersPrepareCommit() {
        List<Event> events = eventsToPublish();
        for (UnitOfWorkListener listener : listeners) {
            listener.onPrepareCommit(registeredAggregates.keySet(), events);
        }
    }

    private List<Event> eventsToPublish() {
        List<Event> events = new ArrayList<Event>(eventsToPublish.size());
        for (EventEntry entry : eventsToPublish) {
            events.add(entry.event);
        }
        return Collections.unmodifiableList(events);
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
