/*
 * Copyright (c) 2010-2014. Axon Framework
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

import org.axonframework.common.Assert;
import org.axonframework.common.io.IOUtils;
import org.axonframework.common.lock.LockFactory;
import org.axonframework.eventstore.EventStore;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.repository.AggregateNotFoundException;
import org.axonframework.repository.LockingRepository;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Abstract repository implementation that allows easy implementation of an Event Sourcing mechanism. It will
 * automatically publish new events to the given {@link org.axonframework.eventhandling.EventBus} and delegate event
 * storage to the provided {@link org.axonframework.eventstore.EventStore}.
 *
 * @param <T> The type of aggregate this repository stores
 * @author Allard Buijze
 * @see EventSourcedAggregateRoot
 * @see org.axonframework.eventsourcing.AbstractEventSourcedAggregateRoot
 * @see org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot
 * @see org.axonframework.eventstore.EventStore
 * @since 0.1
 */
public class EventSourcingRepository<T extends EventSourcedAggregateRoot> extends LockingRepository<T> {

    private final EventStore eventStore;
    private final Deque<EventStreamDecorator> eventStreamDecorators = new ArrayDeque<>();
    private final AggregateFactory<T> aggregateFactory;
    private ConflictResolver conflictResolver;

    /**
     * Initializes a repository with the default locking strategy, using a GenericAggregateFactory to create new
     * aggregate instances of given <code>aggregateType</code>.
     *
     * @param aggregateType The type of aggregate stored in this repository
     * @param eventStore    The event store that holds the event streams for this repository
     * @see org.axonframework.repository.LockingRepository#LockingRepository(Class)
     */
    public EventSourcingRepository(final Class<T> aggregateType, EventStore eventStore) {
        this(new GenericAggregateFactory<>(aggregateType), eventStore);
    }

    /**
     * Initializes a repository with the default locking strategy, using the given <code>aggregateFactory</code> to
     * create new aggregate instances.
     *
     * @param aggregateFactory The factory for new aggregate instances
     * @param eventStore       The event store that holds the event streams for this repository
     * @see org.axonframework.repository.LockingRepository#LockingRepository(Class)
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
    public EventSourcingRepository(AggregateFactory<T> aggregateFactory, EventStore eventStore,
                                   LockFactory lockFactory) {
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
    public EventSourcingRepository(final Class<T> aggregateType, EventStore eventStore,
                                   final LockFactory lockFactory) {
        this(new GenericAggregateFactory<>(aggregateType), eventStore, lockFactory);
    }

    /**
     * Perform the actual loading of an aggregate. The necessary locks have been obtained.
     *
     * @param aggregateIdentifier the identifier of the aggregate to load
     * @param expectedVersion     The expected version of the loaded aggregate
     * @return the fully initialized aggregate
     *
     * @throws AggregateDeletedException in case an aggregate existed in the past, but has been deleted
     * @throws AggregateNotFoundException when an aggregate with the given identifier does not exist
     */
    @Override
    protected T doLoad(String aggregateIdentifier, final Long expectedVersion) {
        DomainEventStream events = null;
        DomainEventStream originalStream = null;
        try {
            try {
                events = eventStore.readEvents(aggregateIdentifier);
            } catch (EventStreamNotFoundException e) {
                throw new AggregateNotFoundException(aggregateIdentifier, "The aggregate was not found", e);
            }
            originalStream = events;
            for (EventStreamDecorator decorator : eventStreamDecorators) {
                events = decorator.decorateForRead(aggregateIdentifier, events);
            }

            final T aggregate = aggregateFactory.createAggregate(aggregateIdentifier, events.peek());
            List<DomainEventMessage<?>> unseenEvents = new ArrayList<>();
            aggregate.initializeState(new CapturingEventStream(events, unseenEvents, expectedVersion));
            if (aggregate.isDeleted()) {
                throw new AggregateDeletedException(aggregateIdentifier);
            }
            resolveConflicts(aggregate, unseenEvents);
            return aggregate;
        } finally {
            IOUtils.closeQuietlyIfCloseable(events);
            // if a decorator doesn't implement closeable, we still want to be sure we close the original stream
            IOUtils.closeQuietlyIfCloseable(originalStream);
        }
    }

    @Override
    protected void doSave(T aggregate) {
        // No action required
    }

    @Override
    protected void doDelete(T aggregate) {
        // No action required
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
     * Resolve (potential) conflicts for the given <code>aggregate</code>, where given <code>unseenEvents</code> may
     * have been concurrently applied.
     *
     * @param aggregate    The aggregate containing the potential conflicts
     * @param unseenEvents The events that have been concurrently applied
     */
    protected void resolveConflicts(T aggregate, List<DomainEventMessage<?>> unseenEvents) {
        // TODO: Reinstate conflict resolution
/*
        if (CurrentUnitOfWork.isStarted() && !unseenEvents.isEmpty()) {
            final String key = "Conflicts/" + aggregate.getIdentifier();
            aggregate.addEventRegistrationCallback(new EventRegistrationCallback() {
                @Override
                public <P> DomainEventMessage<P> onRegisteredEvent(DomainEventMessage<P> event) {
                    CurrentUnitOfWork.get().getOrComputeResource(key, k -> new ArrayList<>())
                                     .add(event);
                    return event;
                }
            });
            CurrentUnitOfWork.get().onPrepareCommit(u -> {
                final List<DomainEventMessage<?>> newEvents = u.getOrDefaultResource(key, Collections.emptyList());
                if (!newEvents.isEmpty()) {
                    conflictResolver.resolveConflicts(newEvents, unseenEvents);
                }
            });
        }
*/
    }

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation will do nothing if a conflict resolver (See {@link #setConflictResolver(ConflictResolver)}
     * is set. Otherwise, it will call <code>super.validateOnLoad(...)</code>.
     */
    @Override
    protected void validateOnLoad(T aggregate, Long expectedVersion) {
        if (conflictResolver == null) {
            super.validateOnLoad(aggregate, expectedVersion);
        }
    }

    /**
     * Sets the Event Stream Decorators that will process the event in the DomainEventStream when read, or written to
     * the event store.
     * <p/>
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

    /**
     * Sets the conflict resolver to use for this repository. If not set (or <code>null</code>), the repository will
     * throw an exception if any unexpected changes appear in loaded aggregates.
     *
     * @param conflictResolver The conflict resolver to use for this repository
     */
    public void setConflictResolver(ConflictResolver conflictResolver) {
        this.conflictResolver = conflictResolver;
    }

    /**
     * Wrapper around a DomainEventStream that captures all passing events of which the sequence number is larger than
     * the expected version number.
     */
    private static final class CapturingEventStream implements DomainEventStream, Closeable {

        private final DomainEventStream eventStream;
        private final List<DomainEventMessage<?>> unseenEvents;
        private final Long expectedVersion;

        private CapturingEventStream(DomainEventStream events, List<DomainEventMessage<?>> unseenEvents,
                                     Long expectedVersion) {
            eventStream = events;
            this.unseenEvents = unseenEvents;
            this.expectedVersion = expectedVersion;
        }

        @Override
        public boolean hasNext() {
            return eventStream.hasNext();
        }

        @Override
        public DomainEventMessage next() {
            DomainEventMessage next = eventStream.next();
            if (expectedVersion != null && next.getSequenceNumber() > expectedVersion) {
                unseenEvents.add(next);
            }
            return next;
        }

        @Override
        public DomainEventMessage peek() {
            return eventStream.peek();
        }

        @Override
        public void close() throws IOException {
            IOUtils.closeQuietlyIfCloseable(eventStream);
        }
    }
}
