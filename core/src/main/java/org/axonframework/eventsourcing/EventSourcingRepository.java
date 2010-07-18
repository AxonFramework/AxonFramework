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
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkListenerAdapter;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
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
public abstract class EventSourcingRepository<T extends EventSourcedAggregateRoot> extends LockingRepository<T>
        implements AggregateFactory<T> {

    private volatile EventStore eventStore;
    private ConflictResolver conflictResolver;

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
     * @param expectedVersion     The expected version of the loaded aggregate
     * @return the fully initialized aggregate
     *
     * @throws AggregateDeletedException in case an aggregate existed in the past, but has been deleted
     * @throws org.axonframework.repository.AggregateNotFoundException
     *                                   when an aggregate with the given identifier does not exist
     */
    @Override
    protected T doLoad(UUID aggregateIdentifier, final Long expectedVersion) {
        DomainEventStream events;
        try {
            events = eventStore.readEvents(getTypeIdentifier(), aggregateIdentifier);
        } catch (EventStreamNotFoundException e) {
            throw new AggregateNotFoundException("The aggregate was not found", e);
        }
        final T aggregate = createAggregate(aggregateIdentifier, events.peek());
        List<DomainEvent> unseenEvents = new ArrayList<DomainEvent>();
        aggregate.initializeState(new CapturingEventStream(events, unseenEvents, expectedVersion));
        CurrentUnitOfWork.get().registerListener(aggregate, new ConflictResolvingListener(aggregate, unseenEvents));
        return aggregate;
    }

    private List<DomainEvent> asList(DomainEventStream domainEventStream) {
        List<DomainEvent> unseenEvents = new ArrayList<DomainEvent>();
        while (domainEventStream.hasNext()) {
            unseenEvents.add(domainEventStream.next());
        }
        return unseenEvents;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation is aware of the AggregateSnapshot type events. When <code>firstEvent</code> is an instance of
     * {@link AggregateSnapshot}, the aggregate is extracted from the event. Otherwise, aggregate creation is delegated
     * to the abstract {@link #instantiateAggregate(java.util.UUID, org.axonframework.domain.DomainEvent)} method.
     */
    @Override
    @SuppressWarnings({"unchecked"})
    public T createAggregate(UUID aggregateIdentifier, DomainEvent firstEvent) {
        T aggregate;
        if (AggregateSnapshot.class.isInstance(firstEvent)) {
            aggregate = (T) ((AggregateSnapshot) firstEvent).getAggregate();
        } else {
            aggregate = instantiateAggregate(aggregateIdentifier, firstEvent);
        }
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
     * {@inheritDoc}
     * <p/>
     * This implementation will do nothing if a conflict resolver (See {@link #setConflictResolver(ConflictResolver)} is
     * set. Otherwise, it will call <code>super.validateOnLoad(...)</code>.
     */
    @Override
    protected void validateOnLoad(T aggregate, Long expectedVersion) {
        if (conflictResolver == null) {
            super.validateOnLoad(aggregate, expectedVersion);
        }
    }

    /**
     * Sets the event store that would physically store the events.
     *
     * @param eventStore the event bus to publish events to
     */
    @Resource
    public void setEventStore(EventStore eventStore) {
        this.eventStore = eventStore;
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
        private final List<DomainEvent> unseenEvents;

        private ConflictResolvingListener(T aggregate, List<DomainEvent> unseenEvents) {
            this.aggregate = aggregate;
            this.unseenEvents = unseenEvents;
        }

        @Override
        public void onPrepareCommit() {
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
    private static final class CapturingEventStream implements DomainEventStream {

        private final DomainEventStream eventStream;
        private final List<DomainEvent> unseenEvents;
        private final Long expectedVersion;

        private CapturingEventStream(DomainEventStream events,
                                     List<DomainEvent> unseenEvents,
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
        public DomainEvent next() {
            DomainEvent next = eventStream.next();
            if (expectedVersion != null && next.getSequenceNumber() > expectedVersion) {
                unseenEvents.add(next);
            }
            return next;
        }

        @Override
        public DomainEvent peek() {
            return eventStream.peek();
        }
    }
}
