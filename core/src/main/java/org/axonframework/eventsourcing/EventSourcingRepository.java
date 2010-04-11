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

package org.axonframework.eventsourcing;

import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.eventstore.EventStore;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.repository.AggregateNotFoundException;
import org.axonframework.repository.LockingRepository;
import org.axonframework.repository.LockingStrategy;
import org.springframework.beans.factory.annotation.Required;

import java.util.UUID;

/**
 * Abstract repository implementation that allows easy implementation of an Event Sourcing mechanism. It will
 * automatically publish new events to the given {@link org.axonframework.eventhandling.EventBus} and delegate event
 * storage to the provided {@link org.axonframework.eventstore.EventStore}.
 *
 * @author Allard Buijze
 * @param <T> The type of aggregate this repository stores
 * @see EventSourcedAggregateRoot
 * @see org.axonframework.eventsourcing.AbstractEventSourcedAggregateRoot
 * @see org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot
 * @see org.axonframework.eventstore.EventStore
 * @see org.axonframework.eventstore.fs.FileSystemEventStore
 * @since 0.1
 */
public abstract class EventSourcingRepository<T extends EventSourcedAggregateRoot>
        extends LockingRepository<T> {

    private EventStore eventStore;

    /**
     * Initializes a repository with the default locking strategy.
     *
     * @see org.axonframework.repository.LockingRepository#LockingRepository()
     */
    protected EventSourcingRepository() {
    }

    /**
     * Initialize a repository with the given locking strategy.
     *
     * @param lockingStrategy the locking strategy to apply to this
     */
    protected EventSourcingRepository(final LockingStrategy lockingStrategy) {
        super(lockingStrategy);
    }

    /**
     * Perform the actual saving of the aggregate. All necessary locks have been verified.
     *
     * @param aggregate the aggregate to store
     */
    @Override
    protected void doSave(T aggregate) {
        eventStore.appendEvents(getTypeIdentifier(), aggregate.getUncommittedEvents());
    }

    /**
     * Perform the actual loading of an aggregate. The necessary locks have been obtained.
     *
     * @param aggregateIdentifier the identifier of the aggregate to load
     * @return the fully initialized aggregate
     *
     * @throws AggregateDeletedException in case an aggregate existed in the past, but has been deleted
     * @throws org.axonframework.repository.AggregateNotFoundException
     *                                   when an aggregate with the given identifier does not exist
     */
    @Override
    protected T doLoad(UUID aggregateIdentifier) {
        DomainEventStream events;
        try {
            events = eventStore.readEvents(getTypeIdentifier(), aggregateIdentifier);
        } catch (EventStreamNotFoundException e) {
            throw new AggregateNotFoundException("The aggregate was not found", e);
        }
        T aggregate = instantiateAggregate(aggregateIdentifier, events.peek());
        aggregate.initializeState(events);
        return aggregate;
    }

    /**
     * Instantiate the aggregate using the given aggregate identifier and first event. The first event of the event
     * stream is passed to allow the repository to identify the actual implementation type of the aggregate to create.
     * The first event can be either the event that created the aggregate or, when using event sourcing, a snapshot
     * event. In either case, the event should be designed, such that these events contain enough information to deduct
     * the actual aggregate type.
     * <p/>
     * Note that aggregate state should <strong>*not*</strong> be initialized by this method. That means, no events
     * should be applied by a call to this method. The first event is passed to allow the implementation to base the
     * exact type of aggregate to instantiate on that event.
     *
     * @param aggregateIdentifier the aggregate identifier of the aggregate to instantiate
     * @param firstEvent          The first event in the event stream. This is either the event generated during
     *                            creation of the aggregate, or a snapshot event
     * @return an aggregate ready for initialization using a DomainEventStream.
     */
    protected abstract T instantiateAggregate(UUID aggregateIdentifier, DomainEvent firstEvent);

    /**
     * Sets the event store that would physically store the events.
     *
     * @param eventStore the event bus to publish events to
     */
    @Required
    public void setEventStore(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    /**
     * Returns the type identifier for this aggregate. The type identifier is used by the EventStore to organize data
     * related to the same type of aggregate.
     * <p/>
     * Tip: in most cases, the simple class name would be a good start.
     *
     * @return the type identifier of the aggregates this repository stores
     */
    protected abstract String getTypeIdentifier();

}
