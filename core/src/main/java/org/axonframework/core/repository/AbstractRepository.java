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

package org.axonframework.core.repository;

import org.axonframework.core.AggregateRoot;
import org.axonframework.core.DomainEvent;
import org.axonframework.core.DomainEventStream;
import org.axonframework.core.eventhandler.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;

import java.util.UUID;

/**
 * Abstract implementation of the {@link org.axonframework.core.repository.Repository} that takes care of the
 * dispatching of events when an aggregate is persisted. All uncommitted events on an aggregate are dispatched when the
 * aggregate is saved.
 * <p/>
 * Note that this repository implementation does not take care of any locking. The underlying persistence is expected to
 * deal with concurrency. Alternatively, consider using the {@link org.axonframework.core.repository.LockingRepository}.
 *
 * @author Allard Buijze
 * @param <T> The type of aggregate this repository stores
 * @see #setEventBus(org.axonframework.core.eventhandler.EventBus)
 * @see org.axonframework.core.repository.LockingRepository
 * @since 0.1
 */
public abstract class AbstractRepository<T extends AggregateRoot> implements Repository<T> {

    private EventBus eventBus;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Saves the given aggregate and publishes all uncommitted events to the EventBus.
     *
     * @param aggregate The aggregate root of the aggregate to store.
     * @see #setEventBus(org.axonframework.core.eventhandler.EventBus)
     */
    @Override
    public void save(T aggregate) {
        doSave(aggregate);
        dispatchUncommittedEvents(aggregate);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T load(UUID aggregateIdentifier) {
        return doLoad(aggregateIdentifier);
    }

    /**
     * Performs the actual saving of the aggregate.
     *
     * @param aggregate the aggregate to store
     */
    protected abstract void doSave(T aggregate);

    /**
     * Loads and initialized the aggregate with the given aggregateIdentifier.
     *
     * @param aggregateIdentifier the identifier of the aggregate to load
     * @return a fully initialized aggregate
     *
     * @throws org.axonframework.core.AggregateNotFoundException
     *          if the aggregate with given identifier does not exist
     */
    protected abstract T doLoad(UUID aggregateIdentifier);

    private void dispatchUncommittedEvents(T aggregate) {
        DomainEventStream uncommittedEvents = aggregate.getUncommittedEvents();
        while (uncommittedEvents.hasNext()) {
            DomainEvent event = uncommittedEvents.next();
            logger.debug("Publishing event [{}] to the EventBus", event.getClass().getSimpleName());
            eventBus.publish(event);
        }
        aggregate.commitEvents();
    }

    /**
     * Sets the event bus to which newly stored events should be published. Optional. By default, the repository tries
     * to autowire the event bus.
     *
     * @param eventBus the event bus to publish events to
     */
    @Autowired
    @Required
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }
}
