/*
 * Copyright (c) 2010-2012. Axon Framework
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
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.EventRegistrationCallback;
import org.axonframework.eventhandling.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
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
public class DefaultUnitOfWork extends NestableUnitOfWork {

    private static final Logger logger = LoggerFactory.getLogger(DefaultUnitOfWork.class);

    private final Map<AggregateRoot, AggregateEntry> registeredAggregates =
            new LinkedHashMap<AggregateRoot, AggregateEntry>();
    private final Map<EventBus, List<EventMessage<?>>> eventsToPublish = new HashMap<EventBus, List<EventMessage<?>>>();
    private final UnitOfWorkListenerCollection listeners = new UnitOfWorkListenerCollection();
    private Status dispatcherStatus = Status.READY;
    private final TransactionManager transactionManager;
    private Object backingTransaction;

    /**
     * Initializes a Unit of Work (without starting it) that is not bound to any transaction.
     */
    public DefaultUnitOfWork() {
        this(null);
    }

    /**
     * Initializes a Unit of Work (without starting it) that is binds to the transaction created by the given
     * <code>transactionManager</code> when the Unit of Work starts.
     *
     * @param transactionManager The transaction manager to manage the transaction around this Unit of Work
     */
    public DefaultUnitOfWork(TransactionManager<?> transactionManager) {
        this.transactionManager = transactionManager;
    }

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

    /**
     * Starts a new DefaultUnitOfWork instance using the given <code>transactionManager</code> to provide a backing
     * transaction, registering it a CurrentUnitOfWork. This methods returns the started UnitOfWork instance.
     * <p/>
     * Note that this Unit Of Work type is not meant to be shared among different Threads. A single DefaultUnitOfWork
     * instance should be used exclusively by the Thread that created it.
     *
     * @param transactionManager The transaction manager to provide the backing transaction. May be <code>null</code>
     *                           when not using transactions.
     * @return the started UnitOfWork instance
     */
    public static UnitOfWork startAndGet(TransactionManager<?> transactionManager) {
        DefaultUnitOfWork uow = new DefaultUnitOfWork(transactionManager);
        uow.start();
        return uow;
    }

    @Override
    protected void doStart() {
        if (isTransactional()) {
            this.backingTransaction = transactionManager.startTransaction();
        }
    }

    @Override
    public boolean isTransactional() {
        return transactionManager != null;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void doRollback(Throwable cause) {
        registeredAggregates.clear();
        eventsToPublish.clear();
        try {
            if (backingTransaction != null) {
                transactionManager.rollbackTransaction(backingTransaction);
            }
        } finally {
            notifyListenersRollback(cause);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void doCommit() {
        publishEvents();
        commitInnerUnitOfWork();
        if (isTransactional()) {
            notifyListenersPrepareTransactionCommit(backingTransaction);
            transactionManager.commitTransaction(backingTransaction);
        }
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
        EventRegistrationCallback eventRegistrationCallback = new UoWEventRegistrationCallback(eventBus);

        registeredAggregates.put(aggregate, new AggregateEntry<T>(aggregate, saveAggregateCallback));

        // listen for new events registered in the aggregate
        aggregate.addEventRegistrationCallback(eventRegistrationCallback);
        return aggregate;
    }

    private <T> EventMessage<T> invokeEventRegistrationListeners(EventMessage<T> event) {
        return listeners.onEventRegistered(this, event);
    }

    @SuppressWarnings({"unchecked"})
    private <T extends AggregateRoot> T findSimilarAggregate(Class<T> aggregateType, Object identifier) {
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
        listeners.onRollback(this, cause);
    }

    /**
     * Send a {@link UnitOfWorkListener#afterCommit(UnitOfWork)} notification to all registered listeners.
     *
     * @param transaction The object representing the transaction to about to be committed
     */
    protected void notifyListenersPrepareTransactionCommit(Object transaction) {
        listeners.onPrepareTransactionCommit(this, transaction);
    }

    /**
     * Send a {@link UnitOfWorkListener#afterCommit(UnitOfWork)} notification to all registered listeners.
     */
    protected void notifyListenersAfterCommit() {
        listeners.afterCommit(this);
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
            Iterator<Map.Entry<EventBus, List<EventMessage<?>>>> iterator = eventsToPublish.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<EventBus, List<EventMessage<?>>> entry = iterator.next();
                List<EventMessage<?>> messageList = entry.getValue();
                EventMessage<?>[] messages = messageList.toArray(new EventMessage<?>[messageList.size()]);
                if (logger.isDebugEnabled()) {
                    for (EventMessage message : messages) {
                        logger.debug("Publishing event [{}] to event bus [{}]",
                                     message.getPayloadType().getName(),
                                     entry.getKey());
                    }
                }
                // remove this entry before publication in case a new event is registered with the UoW while publishing
                iterator.remove();
                entry.getKey().publish(messages);
            }
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
        listeners.onPrepareCommit(this, registeredAggregates.keySet(), eventsToPublish());
    }

    @Override
    protected void notifyListenersCleanup() {
        listeners.onCleanup(this);
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

        private final EventBus eventBus;

        public UoWEventRegistrationCallback(EventBus eventBus) {
            this.eventBus = eventBus;
        }

        @Override
        public <T> DomainEventMessage<T> onRegisteredEvent(DomainEventMessage<T> event) {
            event = (DomainEventMessage<T>) invokeEventRegistrationListeners(event);
            doPublish(event, eventBus);
            return event;
        }
    }
}
