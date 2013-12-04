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

package org.axonframework.eventsourcing;

import org.axonframework.common.Assert;
import org.axonframework.common.io.IOUtils;
import org.axonframework.domain.AggregateRoot;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.EventMessage;
import org.axonframework.eventstore.EventStore;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.repository.AggregateNotFoundException;
import org.axonframework.repository.LockManager;
import org.axonframework.repository.LockingRepository;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkListenerAdapter;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

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
    private ConflictResolver conflictResolver;
    private Deque<EventStreamDecorator> eventStreamDecorators = new ArrayDeque<EventStreamDecorator>();
    private final AggregateFactory<T> aggregateFactory;

    /**
     * Initializes a repository with the default locking strategy, using a GenericAggregateFactory to create new
     * aggregate instances of given <code>aggregateType</code>.
     *
     * @param aggregateType The type of aggregate stored in this repository
     * @param eventStore    The event store that holds the event streams for this repository
     * @see org.axonframework.repository.LockingRepository#LockingRepository(Class)
     */
    public EventSourcingRepository(final Class<T> aggregateType, EventStore eventStore) {
        this(new GenericAggregateFactory<T>(aggregateType), eventStore);
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
     * @param lockManager      the locking strategy to apply to this repository
     */
    public EventSourcingRepository(AggregateFactory<T> aggregateFactory, EventStore eventStore,
                                   LockManager lockManager) {
        super(aggregateFactory.getAggregateType(), lockManager);
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
     * @param lockManager   the locking strategy to apply to this
     */
    public EventSourcingRepository(final Class<T> aggregateType, EventStore eventStore,
                                   final LockManager lockManager) {
        this(new GenericAggregateFactory<T>(aggregateType), eventStore, lockManager);
    }

    /**
     * Perform the actual saving of the aggregate. All necessary locks have been verified.
     *
     * @param aggregate the aggregate to store
     */
    @Override
    protected void doSaveWithLock(T aggregate) {
        DomainEventStream eventStream = aggregate.getUncommittedEvents();
        try {
            Iterator<EventStreamDecorator> iterator = eventStreamDecorators.descendingIterator();
            while (iterator.hasNext()) {
                eventStream = iterator.next().decorateForAppend(getTypeIdentifier(), aggregate, eventStream);
            }
            eventStore.appendEvents(getTypeIdentifier(), eventStream);
        } finally {
            IOUtils.closeQuietlyIfCloseable(eventStream);
        }
    }

    /**
     * Delegates to {@link #doSaveWithLock(EventSourcedAggregateRoot)}, as Event Sourcing generally doesn't delete
     * aggregates (not their events).
     * <p/>
     * This method may be safely overridden for special cases that do require deleting an Aggregate's Events.
     *
     * @param aggregate the aggregate to delete
     */
    @Override
    protected void doDeleteWithLock(T aggregate) {
        doSaveWithLock(aggregate);
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
    protected T doLoad(Object aggregateIdentifier, final Long expectedVersion) {
        DomainEventStream events = null;
        DomainEventStream originalStream = null;
        try {
            try {
                events = eventStore.readEvents(getTypeIdentifier(), aggregateIdentifier);
            } catch (EventStreamNotFoundException e) {
                throw new AggregateNotFoundException(aggregateIdentifier, "The aggregate was not found", e);
            }
            originalStream = events;
            for (EventStreamDecorator decorator : eventStreamDecorators) {
                events = decorator.decorateForRead(getTypeIdentifier(), aggregateIdentifier, events);
            }

            final T aggregate = aggregateFactory.createAggregate(aggregateIdentifier, events.peek());
            List<DomainEventMessage> unseenEvents = new ArrayList<DomainEventMessage>();
            aggregate.initializeState(new CapturingEventStream(events, unseenEvents, expectedVersion));
            if (aggregate.isDeleted()) {
                throw new AggregateDeletedException(aggregateIdentifier);
            }
            CurrentUnitOfWork.get().registerListener(new ConflictResolvingListener(aggregate, unseenEvents));

            return aggregate;
        } finally {
            IOUtils.closeQuietlyIfCloseable(events);
            // if a decorator doesn't implement closeable, we still want to be sure we close the original stream
            IOUtils.closeQuietlyIfCloseable(originalStream);
        }
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
     * @param unseenEvents The events that have been concurrenly applied
     */
    protected void resolveConflicts(T aggregate, DomainEventStream unseenEvents) {
        CurrentUnitOfWork.get().registerListener(new ConflictResolvingListener(aggregate, asList(unseenEvents)));
    }

    private List<DomainEventMessage> asList(DomainEventStream domainEventStream) {
        List<DomainEventMessage> unseenEvents = new ArrayList<DomainEventMessage>();
        while (domainEventStream.hasNext()) {
            unseenEvents.add(domainEventStream.next());
        }
        return unseenEvents;
    }

    /**
     * Return the type identifier belonging to the AggregateFactory of this repository.
     *
     * @return the type identifier belonging to the AggregateFactory of this repository
     */
    public String getTypeIdentifier() {
        if (aggregateFactory == null) {
            throw new IllegalStateException("Either an aggregate factory must be configured (recommended), "
                                                    + "or the getTypeIdentifier() method must be overridden.");
        }
        return aggregateFactory.getTypeIdentifier();
    }

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation will do nothing if a conflict resolver (See {@link #setConflictResolver(ConflictResolver)}
     * is
     * set. Otherwise, it will call <code>super.validateOnLoad(...)</code>.
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

    private final class ConflictResolvingListener extends UnitOfWorkListenerAdapter {

        private final T aggregate;
        private final List<DomainEventMessage> unseenEvents;

        private ConflictResolvingListener(T aggregate, List<DomainEventMessage> unseenEvents) {
            this.aggregate = aggregate;
            this.unseenEvents = unseenEvents;
        }

        @Override
        public void onPrepareCommit(UnitOfWork unitOfWork, Set<AggregateRoot> aggregateRoots,
                                    List<EventMessage> events) {
            if (hasPotentialConflicts()) {
                conflictResolver.resolveConflicts(asList(aggregate.getUncommittedEvents()), unseenEvents);
            }
        }

        private boolean hasPotentialConflicts() {
            return aggregate.getUncommittedEventCount() > 0
                    && aggregate.getVersion() != null
                    && !unseenEvents.isEmpty();
        }
    }

    /**
     * Wrapper around a DomainEventStream that captures all passing events of which the sequence number is larger than
     * the expected version number.
     */
    private static final class CapturingEventStream implements DomainEventStream, Closeable {

        private final DomainEventStream eventStream;
        private final List<DomainEventMessage> unseenEvents;
        private final Long expectedVersion;

        private CapturingEventStream(DomainEventStream events, List<DomainEventMessage> unseenEvents,
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
