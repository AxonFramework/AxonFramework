/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.commandhandling.model.ConcurrencyException;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.NoOpEventUpcaster;
import org.axonframework.serialization.xml.XStreamSerializer;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * Abstract event storage implementation that takes care of event serialization and upcasting.
 *
 * @author Rene de Waele
 */
public abstract class AbstractEventStorageEngine implements EventStorageEngine {

    private final Serializer serializer;
    private final EventUpcaster upcasterChain;
    private final PersistenceExceptionResolver persistenceExceptionResolver;

    /**
     * Initializes an EventStorageEngine with given {@code serializer}, {@code upcasterChain} and {@code
     * persistenceExceptionResolver}.
     *
     * @param serializer                   Used to serialize and deserialize event payload and metadata. If {@code null}
     *                                     a new {@link XStreamSerializer} is used.
     * @param upcasterChain                Allows older revisions of serialized objects to be deserialized. If {@code
     *                                     null} a {@link NoOpEventUpcaster} is used.
     * @param persistenceExceptionResolver Detects concurrency exceptions from the backing database. If {@code null}
     *                                     persistence exceptions are not explicitly resolved.
     */
    protected AbstractEventStorageEngine(Serializer serializer, EventUpcaster upcasterChain,
                                         PersistenceExceptionResolver persistenceExceptionResolver) {
        this.serializer = getOrDefault(serializer, XStreamSerializer::new);
        this.upcasterChain = getOrDefault(upcasterChain, () -> NoOpEventUpcaster.INSTANCE);
        this.persistenceExceptionResolver = persistenceExceptionResolver;
    }

    @Override
    public Stream<? extends TrackedEventMessage<?>> readEvents(TrackingToken trackingToken, boolean mayBlock) {
        Stream<? extends TrackedEventData<?>> input = readEventData(trackingToken, mayBlock);
        return EventUtils.upcastAndDeserializeTrackedEvents(input, serializer, upcasterChain, true);
    }

    @Override
    public DomainEventStream readEvents(String aggregateIdentifier, long firstSequenceNumber) {
        Stream<? extends DomainEventData<?>> input = readEventData(aggregateIdentifier, firstSequenceNumber);
        return EventUtils.upcastAndDeserializeDomainEvents(input, serializer, upcasterChain, false);
    }

    @Override
    public Optional<DomainEventMessage<?>> readSnapshot(String aggregateIdentifier) {
        return readSnapshotData(aggregateIdentifier).map(entry -> {
            DomainEventStream stream =
                    EventUtils.upcastAndDeserializeDomainEvents(Stream.of(entry), serializer, upcasterChain, false);
            return stream.hasNext() ? stream.next() : null;
        });
    }

    @Override
    public void appendEvents(List<? extends EventMessage<?>> events) {
        appendEvents(events, serializer);
    }

    @Override
    public void storeSnapshot(DomainEventMessage<?> snapshot) {
        storeSnapshot(snapshot, serializer);
    }

    /**
     * Invoke when an Exception is raised while persisting an Event or Snapshot.
     *
     * @param exception   The exception raised while persisting an Event
     * @param failedEvent The EventMessage that could not be persisted
     */
    protected void handlePersistenceException(Exception exception, EventMessage<?> failedEvent) {
        String eventDescription;
        if (failedEvent instanceof DomainEventMessage<?>) {
            DomainEventMessage<?> failedDomainEvent = (DomainEventMessage<?>) failedEvent;
            eventDescription =
                    format("An event for aggregate [%s] at sequence [%d]", failedDomainEvent.getAggregateIdentifier(),
                           failedDomainEvent.getSequenceNumber());
        } else {
            eventDescription = format("An event with identifier [%s]", failedEvent.getIdentifier());
        }
        if (persistenceExceptionResolver != null && persistenceExceptionResolver.isDuplicateKeyViolation(exception)) {
            throw new ConcurrencyException(eventDescription + " was already inserted", exception);
        } else {
            throw new EventStoreException(eventDescription + " could not be persisted", exception);
        }
    }

    /**
     * Append given {@code events} to the backing database. Use the given {@code serializer} to serialize the event's
     * payload and metadata.
     *
     * @param events     Events to append to the database
     * @param serializer Serializer used to convert the events to a suitable format for storage
     */
    protected abstract void appendEvents(List<? extends EventMessage<?>> events, Serializer serializer);

    /**
     * Store the given {@code snapshot} of an Aggregate. Implementations may override any existing snapshot of the
     * Aggregate with the given snapshot.
     *
     * @param snapshot   Snapshot Event of the aggregate
     * @param serializer Serializer used to convert the snapshot event to a suitable format for storage
     */
    protected abstract void storeSnapshot(DomainEventMessage<?> snapshot, Serializer serializer);

    /**
     * Returns a {@link Stream} of serialized event data entries for an aggregate with given {@code identifier}. The
     * events should be ordered by aggregate sequence number and have a sequence number starting from the given {@code
     * firstSequenceNumber}.
     *
     * @param identifier          The identifier of the aggregate to open a stream for
     * @param firstSequenceNumber The sequence number of the first excepted event entry
     * @return a Stream of serialized event entries for the given aggregate
     */
    protected abstract Stream<? extends DomainEventData<?>> readEventData(String identifier, long firstSequenceNumber);

    /**
     * Returns a global {@link Stream} containing all serialized event data entries in the event storage that have a
     * {@link TrackingToken} greater than the given {@code trackingToken}. Event entries in the stream should be ordered
     * by tracking token. If the {@code trackingToken} is {@code null} a stream containing all events should be
     * returned.
     * <p>
     * If the end of the stream is reached and {@code mayBlock} is {@code true} the stream may block to wait for new
     * events.
     *
     * @param trackingToken Object describing the global index of the last processed event or {@code null} to create a
     *                      stream of all events in the store
     * @param mayBlock      If {@code true} the storage engine may optionally choose to block to wait for new event
     *                      messages if the end of the stream is reached.
     * @return A stream containing all tracked event messages stored since the given tracking token
     */
    protected abstract Stream<? extends TrackedEventData<?>> readEventData(TrackingToken trackingToken,
                                                                           boolean mayBlock);

    /**
     * Returns an optional serialized event entry for given {@code aggregateIdentifier} if the backing database
     * contains a snapshot of the aggregate.
     *
     * @param aggregateIdentifier The aggregate identifier to fetch a snapshot for
     * @return An optional with a serialized snapshot of the aggregate
     */
    protected abstract Optional<? extends DomainEventData<?>> readSnapshotData(String aggregateIdentifier);

    /**
     * Get the serializer used by this storage engine when storing and retrieving events.
     *
     * @return the serializer used by this storage
     */
    public Serializer getSerializer() {
        return serializer;
    }
}
