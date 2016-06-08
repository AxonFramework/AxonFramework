/*
 * Copyright (c) 2010-2016. Axon Framework
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

import org.axonframework.commandhandling.model.AggregateNotFoundException;
import org.axonframework.commandhandling.model.LockingRepository;
import org.axonframework.commandhandling.model.inspection.EventSourcedAggregate;
import org.axonframework.common.Assert;
import org.axonframework.common.lock.LockFactory;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Abstract repository implementation that allows easy implementation of an Event Sourcing mechanism. It will
 * automatically publish new events to the given {@link org.axonframework.eventhandling.EventBus} and delegate event
 * storage to the provided {@link org.axonframework.eventsourcing.eventstore.EventStore}.
 *
 * @param <T> The type of aggregate this repository stores
 * @author Allard Buijze
 * @see org.axonframework.eventsourcing.eventstore.EventStore
 * @since 0.1
 */
public class EventSourcingRepository<T> extends LockingRepository<T, EventSourcedAggregate<T>> {

    private final EventStore eventStore;
    private final Deque<EventStreamDecorator> eventStreamDecorators = new ArrayDeque<>();
    private final AggregateFactory<T> aggregateFactory;

    /**
     * Initializes a repository with the default locking strategy, using a GenericAggregateFactory to create new
     * aggregate instances of given {@code aggregateType}.
     *
     * @param aggregateType The type of aggregate stored in this repository
     * @param eventStore    The event store that holds the event streams for this repository
     * @see LockingRepository#LockingRepository(Class)
     */
    public EventSourcingRepository(final Class<T> aggregateType, EventStore eventStore) {
        this(new GenericAggregateFactory<>(aggregateType), eventStore);
    }

    /**
     * Initializes a repository with the default locking strategy, using the given {@code aggregateFactory} to
     * create new aggregate instances.
     *
     * @param aggregateFactory The factory for new aggregate instances
     * @param eventStore       The event store that holds the event streams for this repository
     * @see LockingRepository#LockingRepository(Class)
     */
    public EventSourcingRepository(final AggregateFactory<T> aggregateFactory, EventStore eventStore) {
        super(aggregateFactory.getAggregateType());
        Assert.notNull(eventStore, "eventStore may not be null");
        this.aggregateFactory = aggregateFactory;
        this.eventStore = eventStore;
    }

    /**
     * Initialize a repository with the given locking strategy.
     *
     * @param aggregateFactory The factory for new aggregate instances
     * @param eventStore       The event store that holds the event streams for this repository
     * @param lockFactory      the locking strategy to apply to this repository
     */
    public EventSourcingRepository(AggregateFactory<T> aggregateFactory, EventStore eventStore, LockFactory lockFactory) {
        super(aggregateFactory.getAggregateType(), lockFactory);
        Assert.notNull(eventStore, "eventStore may not be null");
        this.eventStore = eventStore;
        this.aggregateFactory = aggregateFactory;
    }

    /**
     * Initialize a repository with the given locking strategy, using a GenericAggregateFactory to create new aggregate
     * instances.
     *
     * @param aggregateType The type of aggregate to store in this repository
     * @param eventStore    The event store that holds the event streams for this repository
     * @param lockFactory   the locking strategy to apply to this
     */
    public EventSourcingRepository(final Class<T> aggregateType, EventStore eventStore, final LockFactory lockFactory) {
        this(new GenericAggregateFactory<>(aggregateType), eventStore, lockFactory);
    }

    /**
     * Perform the actual loading of an aggregate. The necessary locks have been obtained.
     *
     * @param aggregateIdentifier the identifier of the aggregate to load
     * @param expectedVersion     The expected version of the loaded aggregate
     * @return the fully initialized aggregate
     * @throws AggregateDeletedException  in case an aggregate existed in the past, but has been deleted
     * @throws AggregateNotFoundException when an aggregate with the given identifier does not exist
     */
    @Override
    protected EventSourcedAggregate<T> doLoadWithLock(String aggregateIdentifier, Long expectedVersion) {
        DomainEventStream eventStream = eventStore.readEvents(aggregateIdentifier);
        for (EventStreamDecorator decorator : eventStreamDecorators) {
            eventStream = decorator.decorateForRead(aggregateIdentifier, eventStream);
        }
        if (!eventStream.hasNext()) {
            throw new AggregateNotFoundException(aggregateIdentifier,
                                                 "The aggregate was not found in the event store");
        }
        EventSourcedAggregate<T> aggregate = EventSourcedAggregate
                .initialize(aggregateFactory.createAggregateRoot(aggregateIdentifier, eventStream.peek()),
                            aggregateModel(), eventStore);
        aggregate.initializeState(eventStream);
        if (aggregate.isDeleted()) {
            throw new AggregateDeletedException(aggregateIdentifier);
        }
        return aggregate;
    }

    @Override
    protected EventSourcedAggregate<T> doCreateNewForLock(Callable<T> factoryMethod) throws Exception {
        return EventSourcedAggregate.initialize(factoryMethod, aggregateModel(), eventStore);
    }

    @Override
    protected void doSaveWithLock(EventSourcedAggregate<T> aggregate) {
    }

    @Override
    protected void doDeleteWithLock(EventSourcedAggregate<T> aggregate) {
    }

    /**
     * Returns the factory used by this repository.
     *
     * @return the factory used by this repository
     */
    public AggregateFactory<T> getAggregateFactory() {
        return aggregateFactory;
    }

    /**
     * Sets the Event Stream Decorators that will process the event in the DomainEventStream when read, or written to
     * the event store.
     * <p>
     * When appending events to the event store, the processors are invoked in the reverse order, causing the first
     * decorator in this list to receive each event first. When reading from events, the decorators are invoked in the
     * order given.
     *
     * @param eventProcessors The processors to that will process events in the DomainEventStream
     */
    public void setEventStreamDecorators(List<? extends EventStreamDecorator> eventProcessors) {
        this.eventStreamDecorators.addAll(eventProcessors);
    }

    /**
     * Sets the snapshotter trigger for this repository.
     *
     * @param snapshotterTrigger the snapshotter trigger for this repository.
     */
    public void setSnapshotterTrigger(SnapshotterTrigger snapshotterTrigger) {
        this.eventStreamDecorators.add(snapshotterTrigger);
    }
}
