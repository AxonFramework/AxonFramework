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

import org.axonframework.domain.AggregateRoot;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.EventRegistrationCallback;
import org.axonframework.eventhandling.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private final Map<AggregateRoot, AggregateEntry> registeredAggregates =
            new LinkedHashMap<AggregateRoot, AggregateEntry>();
    private final Map<EventBus, List<EventMessage<?>>> eventsToPublish = new HashMap<EventBus, List<EventMessage<?>>>();
    private final UnitOfWorkListenerCollection listeners = new UnitOfWorkListenerCollection();
    private Status dispatcherStatus = Status.READY;

    private static enum Status {

        READY, DISPATCHING
    }

    /**
     * Starts a new DefaultUnitOfWork instance, registering it a CurrentUnitOfWork. This methods returns the started
     * UnitOfWork instance.
     * <p/>
     * Note that this Unit Of Work type is not meant to be shared among different Threads. A single DefaultUnitOfWork
     * instance should be used exclusively by the Thread that created it.
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
    public <T extends AggregateRoot> T registerAggregate(final T aggregate, final EventBus eventBus,
                                                         SaveAggregateCallback<T> saveAggregateCallback) {
        T similarAggregate = (T) findSimilarAggregate(aggregate.getClass(), aggregate.getIdentifier());
        if (similarAggregate != null) {
            if (logger.isInfoEnabled()) {
                logger.info("Ignoring aggregate registration. An aggregate of same type and identifier was already"
                                    + "registered in this Unit Of Work: type [{}], identifier [{}]",
                            aggregate.getClass().getSimpleName(),
                            aggregate.getIdentifier());
            }
            return similarAggregate;
        }
        EventRegistrationCallback eventRegistrationCallback = new UoWEventRegistrationCallback(aggregate, eventBus);

        registeredAggregates.put(aggregate, new AggregateEntry<T>(aggregate, saveAggregateCallback
        ));

        // register any events already available as uncommitted in the aggregate
        DomainEventStream uncommittedEvents = aggregate.getUncommittedEvents();
        while (uncommittedEvents != null && uncommittedEvents.hasNext()) {
            publishEvent(uncommittedEvents.next(), eventBus);
        }

        // listen for new events registered in the aggregate
        aggregate.addEventRegistrationCallback(eventRegistrationCallback);
        return aggregate;
    }

    private <T> EventMessage<T> invokeEventRegistrationListeners(EventMessage<T> event) {
        return listeners.onEventRegistered(event);
    }

    @SuppressWarnings({"unchecked"})
    private <T extends AggregateRoot> T findSimilarAggregate(Class<T> aggregateType,
                                                             Object identifier) {
        for (AggregateRoot aggregate : registeredAggregates.keySet()) {
            if (aggregateType.isInstance(aggregate) && identifier.equals(aggregate.getIdentifier())) {
                return (T) aggregate;
            }
        }
        return null;
    }

    @Override
    public void registerListener(UnitOfWorkListener listener) {
        listeners.add(listener);
    }

    private void doPublish(EventMessage event, EventBus eventBus) {
        eventsToPublishOn(eventBus).add(event);
    }

    private List<EventMessage<?>> eventsToPublishOn(EventBus eventBus) {
        if (!eventsToPublish.containsKey(eventBus)) {
            eventsToPublish.put(eventBus, new ArrayList<EventMessage<?>>());
        }
        return eventsToPublish.get(eventBus);
    }

    @Override
    public void publishEvent(EventMessage<?> event, EventBus eventBus) {
        if (logger.isDebugEnabled()) {
            logger.debug("Staging event for publishing: [{}] on [{}]",
                         event.getPayloadType().getName(),
                         eventBus.getClass().getName());
        }
        event = invokeEventRegistrationListeners(event);
        doPublish(event, eventBus);
    }

    @Override
    protected void notifyListenersRollback(Throwable cause) {
        listeners.onRollback(cause);
    }

    /**
     * Send a {@link UnitOfWorkListener#afterCommit()} notification to all registered listeners.
     */
    protected void notifyListenersAfterCommit() {
        listeners.afterCommit();
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
        for (Map.Entry<EventBus, List<EventMessage<?>>> entry : eventsToPublish.entrySet()) {
            EventMessage[] messages = entry.getValue().toArray(new EventMessage[] {});
            if (logger.isDebugEnabled()) {
                for (EventMessage message : messages) {
                    logger.debug("Publishing event [{}] to event bus [{}]",
                                 message.getPayloadType().getName(),
                                 entry.getKey().getClass().getName());
                }
            }
            entry.getKey().publish(messages);
            entry.getValue().clear();
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
                             entry.aggregateRoot.getIdentifier());
            }
            entry.saveAggregate();
        }
        logger.debug("Aggregates successfully persisted");
        registeredAggregates.clear();
    }

    @Override
    protected void notifyListenersPrepareCommit() {
        listeners.onPrepareCommit(registeredAggregates.keySet(), eventsToPublish());
    }

    @Override
    protected void notifyListenersCleanup() {
        listeners.onCleanup();
    }

    private List<EventMessage> eventsToPublish() {
        List<EventMessage> events = new ArrayList<EventMessage>();
        for (Map.Entry<EventBus, List<EventMessage<?>>> entry : eventsToPublish.entrySet()) {
            events.addAll(entry.getValue());
        }
        return Collections.unmodifiableList(events);
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

    private class UoWEventRegistrationCallback implements EventRegistrationCallback {

        private final AggregateRoot aggregate;
        private final EventBus eventBus;

        public UoWEventRegistrationCallback(AggregateRoot aggregate, EventBus eventBus) {
            this.aggregate = aggregate;
            this.eventBus = eventBus;
        }

        @Override
        public <T> DomainEventMessage<T> onRegisteredEvent(DomainEventMessage<T> event) {
            if (registeredAggregates.containsKey(aggregate)) {
                event = (DomainEventMessage<T>) invokeEventRegistrationListeners(event);
                doPublish(event, eventBus);
            }
            return event;
        }
    }
}
