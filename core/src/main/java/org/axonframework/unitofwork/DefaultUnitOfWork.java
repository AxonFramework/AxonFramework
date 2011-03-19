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

package org.axonframework.unitofwork;

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.AggregateRoot;
import org.axonframework.domain.Event;
import org.axonframework.eventhandling.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * been explicitly save in their aggregates will be saved when the UnitOfWork commits.
 * <p/>
 * This implementation requires a mechanism that explicitly commits or rolls back.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public class DefaultUnitOfWork extends AbstractUnitOfWork {

    private static final Logger logger = LoggerFactory.getLogger(DefaultUnitOfWork.class);

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
    protected void doRollback(Throwable cause) {
        registeredAggregates.clear();
        eventsToPublish.clear();
        notifyListenersRollback(cause);
    }

    @Override
    protected void doCommit() {
        publishEvents();

        commitInnerUnitOfWork();

        notifyListenersAfterCommit();
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public <T extends AggregateRoot> T registerAggregate(T aggregate,
                                                         SaveAggregateCallback<T> callback) {
        T similarAggregate = (T) findSimilarAggregate(aggregate.getClass(), aggregate.getIdentifier());
        if (similarAggregate != null) {
            logger.warn("An aggregate is being registered with this UnitOfWork more than once. "
                                + "Although this is not likely to cause problems, it is improper use of resources. "
                                + "Duplicated aggregate: type [{}], identifier [{}]",
                        aggregate.getClass().getSimpleName(),
                        aggregate.getIdentifier().asString());
            return similarAggregate;
        }
        registeredAggregates.put(aggregate, new AggregateEntry<T>(aggregate, callback));
        return aggregate;
    }

    @SuppressWarnings({"unchecked"})
    private <T extends AggregateRoot> T findSimilarAggregate(Class<T> aggregateType,
                                                             AggregateIdentifier identifier) {
        for (AggregateRoot aggregate : registeredAggregates.keySet()) {
            if (aggregateType.isInstance(aggregate) && identifier.equals(aggregate.getIdentifier())) {
                return (T) aggregate;
            }
        }
        return null;
    }

    @Override
    public void registerListener(UnitOfWorkListener listener) {
        if (logger.isDebugEnabled()) {
            logger.debug("Registering listener: {}", listener.getClass().getName());
        }
        listeners.add(listener);
    }

    @Override
    public void publishEvent(Event event, EventBus eventBus) {
        if (logger.isDebugEnabled()) {
            logger.debug("Staging event for publishing: [{}] on [{}]",
                         event.getClass().getName(),
                         eventBus.getClass().getName());
        }
        eventsToPublish.add(new EventEntry(event, eventBus));
    }

    @Override
    protected void notifyListenersRollback(Throwable cause) {
        logger.debug("Notifying listeners of rollback");
        for (UnitOfWorkListener listener : listeners) {
            if (logger.isDebugEnabled()) {
                logger.debug("Notifying listener [{}] of rollback", listener.getClass().getName());
            }
            listener.onRollback(cause);
        }
    }

    /**
     * Send a {@link UnitOfWorkListener#afterCommit()} notification to all registered listeners.
     */
    protected void notifyListenersAfterCommit() {
        logger.debug("Notifying listeners after commit");
        for (UnitOfWorkListener listener : listeners) {
            if (logger.isDebugEnabled()) {
                logger.debug("Notifying listener [{}] after commit", listener.getClass().getName());
            }
            listener.afterCommit();
        }
    }

    /**
     * Publishes all registered events to their respective event bus.
     */
    protected void publishEvents() {
        logger.debug("Publishing events to the event bus");
        if (dispatcherStatus == Status.DISPATCHING) {
            // this prevents events from overtaking each other
            logger.debug("UnitOfWork is already in the dispatch process. "
                                 + "That process will publish events instead. Aborting...");
            return;
        }
        dispatcherStatus = Status.DISPATCHING;
        while (!eventsToPublish.isEmpty()) {
            EventEntry eventEntry = eventsToPublish.poll();
            if (logger.isDebugEnabled()) {
                logger.debug("Publishing event [{}] to event bus [{}]",
                             eventEntry.event.getClass().getName(),
                             eventEntry.eventBus.getClass().getName());
            }
            eventEntry.publishEvent();
        }
        logger.debug("All events successfully published.");
        dispatcherStatus = Status.READY;
    }

    @Override
    protected void saveAggregates() {
        logger.debug("Persisting changes to aggregates");
        for (AggregateEntry entry : registeredAggregates.values()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Persisting changes to [{}], identifier: [{}]",
                             entry.aggregateRoot.getClass().getName(),
                             entry.aggregateRoot.getIdentifier().asString());
            }
            entry.saveAggregate();
        }
        logger.debug("Aggregates successfully persisted");
        registeredAggregates.clear();
    }

    @Override
    protected void notifyListenersPrepareCommit() {
        logger.debug("Notifying listeners of commit request");
        List<Event> events = eventsToPublish();
        for (UnitOfWorkListener listener : listeners) {
            if (logger.isDebugEnabled()) {
                logger.debug("Notifying listener [{}] of upcoming commit", listener.getClass().getName());
            }
            listener.onPrepareCommit(registeredAggregates.keySet(), events);
        }
        logger.debug("Listeners successfully notified");
    }

    @Override
    protected void notifyListenersCleanup() {
        logger.debug("Notifying listeners of cleanup");
        for (UnitOfWorkListener listener : listeners) {
            try {
                if (logger.isDebugEnabled()) {
                    logger.debug("Notifying listener [{}] of cleanup", listener.getClass().getName());
                }
                listener.onCleanup();
            } catch (RuntimeException e) {
                logger.warn("Listener raised an exception on cleanup. Ignoring...", e);
            }
        }
        logger.debug("Listeners successfully notified");
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
