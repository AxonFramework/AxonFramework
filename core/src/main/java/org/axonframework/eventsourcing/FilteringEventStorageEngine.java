package org.axonframework.eventsourcing;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.TrackingToken;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of EventStorageEngine that delegates to another implementation, while filtering
 * events as they are appended. This prevents certain events to be stored in the Event Store.
 *
 * @author Allard Buijze
 * @since 3.1
 */
public class FilteringEventStorageEngine implements EventStorageEngine {

    private final EventStorageEngine delegate;
    private final Predicate<? super EventMessage<?>> filter;

    /**
     * Initializes the FilteringEventStorageEngine delegating all event messages matching the given {@code filter} to
     * the given {@code delegate}.
     * <p>
     * Note that this only affects events stored in the StorageEngine. The {@code EventStore} will still publish these
     * events to Subscribed event handlers. Tracking Event Processors take their events from the stored events, and
     * will therefore not receive any events blocked by this instance.
     *
     * @param delegate the EventStorageEngine to store matching messages in
     * @param filter   the predicate that event messages must match against to be stored
     */
    public FilteringEventStorageEngine(EventStorageEngine delegate, Predicate<? super EventMessage<?>> filter) {
        this.delegate = delegate;
        this.filter = filter;
    }

    @Override
    public void appendEvents(EventMessage<?>... events) {
        delegate.appendEvents(Arrays.stream(events).filter(filter).collect(Collectors.toList()));
    }

    @Override
    public void appendEvents(List<? extends EventMessage<?>> events) {
        delegate.appendEvents(events.stream().filter(filter).collect(Collectors.toList()));
    }

    @Override
    public void storeSnapshot(DomainEventMessage<?> snapshot) {
        delegate.storeSnapshot(snapshot);
    }

    @Override
    public Stream<? extends TrackedEventMessage<?>> readEvents(TrackingToken trackingToken, boolean mayBlock) {
        return delegate.readEvents(trackingToken, mayBlock);
    }

    @Override
    public DomainEventStream readEvents(String aggregateIdentifier) {
        return delegate.readEvents(aggregateIdentifier);
    }

    @Override
    public DomainEventStream readEvents(String aggregateIdentifier, long firstSequenceNumber) {
        return delegate.readEvents(aggregateIdentifier, firstSequenceNumber);
    }

    @Override
    public Optional<DomainEventMessage<?>> readSnapshot(String aggregateIdentifier) {
        return delegate.readSnapshot(aggregateIdentifier);
    }
}
