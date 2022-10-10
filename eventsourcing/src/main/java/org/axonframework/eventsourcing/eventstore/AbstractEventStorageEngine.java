/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.eventhandling.DomainEventData;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackedEventData;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.snapshotting.SnapshotFilter;
import org.axonframework.modelling.command.AggregateStreamCreationException;
import org.axonframework.modelling.command.ConcurrencyException;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.NoOpEventUpcaster;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import static java.lang.String.format;
import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.eventhandling.EventUtils.upcastAndDeserializeTrackedEvents;
import static org.axonframework.eventsourcing.EventStreamUtils.upcastAndDeserializeDomainEvents;

/**
 * Abstract {@link EventStorageEngine} implementation that takes care of event serialization and upcasting.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public abstract class AbstractEventStorageEngine implements EventStorageEngine {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Serializer snapshotSerializer;
    protected final EventUpcaster upcasterChain;
    private final PersistenceExceptionResolver persistenceExceptionResolver;
    private final Serializer eventSerializer;
    private final SnapshotFilter snapshotFilter;

    /**
     * Instantiate a {@link AbstractEventStorageEngine} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the event and snapshot {@link Serializer} are not {@code null}, and will throw an {@link
     * AxonConfigurationException} if any of them is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link AbstractEventStorageEngine} instance
     */
    protected AbstractEventStorageEngine(Builder builder) {
        builder.validate();
        this.snapshotSerializer = builder.snapshotSerializer.get();
        this.upcasterChain = builder.upcasterChain;
        this.persistenceExceptionResolver = builder.persistenceExceptionResolver;
        this.eventSerializer = builder.eventSerializer.get();
        this.snapshotFilter = builder.snapshotFilter;
    }

    @Override
    public Stream<? extends TrackedEventMessage<?>> readEvents(TrackingToken trackingToken, boolean mayBlock) {
        Stream<? extends TrackedEventData<?>> input = readEventData(trackingToken, mayBlock);
        return upcastAndDeserializeTrackedEvents(input, getEventSerializer(), upcasterChain);
    }

    @Override
    public DomainEventStream readEvents(@Nonnull String aggregateIdentifier, long firstSequenceNumber) {
        Stream<? extends DomainEventData<?>> input = readEventData(aggregateIdentifier, firstSequenceNumber);
        return upcastAndDeserializeDomainEvents(input, getEventSerializer(), upcasterChain);
    }

    @Override
    public Optional<DomainEventMessage<?>> readSnapshot(@Nonnull String aggregateIdentifier) {
        return readSnapshotData(aggregateIdentifier)
                .filter(snapshotFilter::allow)
                .map(snapshot -> upcastAndDeserializeDomainEvents(Stream.of(snapshot),
                                                                  getSnapshotSerializer(),
                                                                  upcasterChain
                ))
                .flatMap(DomainEventStream::asStream)
                .findFirst()
                .map(event -> (DomainEventMessage<?>) event);
    }

    @Override
    public void appendEvents(@Nonnull List<? extends EventMessage<?>> events) {
        appendEvents(events, getEventSerializer());
    }

    @Override
    public void storeSnapshot(@Nonnull DomainEventMessage<?> snapshot) {
        storeSnapshot(snapshot, getSnapshotSerializer());
    }

    /**
     * Invoke when an Exception is raised while persisting an Event or Snapshot.
     *
     * @param exception   The exception raised while persisting an Event
     * @param failedEvent The EventMessage that could not be persisted
     */
    protected void handlePersistenceException(Exception exception, EventMessage<?> failedEvent) {
        String eventDescription = buildExceptionMessage(failedEvent);
        if (persistenceExceptionResolver != null && persistenceExceptionResolver.isDuplicateKeyViolation(exception)) {
            if (isFirstDomainEvent(failedEvent)) {
                throw new AggregateStreamCreationException(eventDescription, exception);
            }
            throw new ConcurrencyException(eventDescription, exception);
        } else {
            throw new EventStoreException(eventDescription, exception);
        }
    }

    /**
     * Check whether or not this is the first event, which means we tried to create an aggregate through the given
     * {@code failedEvent}.
     *
     * @param failedEvent the event to be checked
     * @return true in case of first event, false otherwise
     */
    private boolean isFirstDomainEvent(EventMessage<?> failedEvent) {
        if (failedEvent instanceof DomainEventMessage<?>) {
            return ((DomainEventMessage<?>) failedEvent).getSequenceNumber() == 0L;
        }
        return false;
    }

    /**
     * Build an exception message based on an EventMessage.
     *
     * @param failedEvent the event to be used for the exception message
     * @return the created exception message
     */
    private String buildExceptionMessage(EventMessage<?> failedEvent) {
        String eventDescription = format("An event with identifier [%s] could not be persisted",
                                         failedEvent.getIdentifier());
        if (isFirstDomainEvent(failedEvent)) {
            DomainEventMessage<?> failedDomainEvent = (DomainEventMessage<?>) failedEvent;
            eventDescription = format(
                    "Cannot reuse aggregate identifier [%s] to create aggregate [%s] since identifiers need to be unique.",
                    failedDomainEvent.getAggregateIdentifier(),
                    failedDomainEvent.getType());
        } else if (failedEvent instanceof DomainEventMessage<?>) {
            DomainEventMessage<?> failedDomainEvent = (DomainEventMessage<?>) failedEvent;
            eventDescription = format("An event for aggregate [%s] at sequence [%d] was already inserted",
                                      failedDomainEvent.getAggregateIdentifier(),
                                      failedDomainEvent.getSequenceNumber());
        }
        return eventDescription;
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
     * Returns a stream of serialized event entries for given {@code aggregateIdentifier} if the backing database
     * contains a snapshot of the aggregate.
     * <p>
     * It is required that specific event storage engines return snapshots in descending order of their sequence
     * number.
     * </p>
     *
     * @param aggregateIdentifier The aggregate identifier to fetch a snapshot for
     * @return A stream of serialized snapshots of the aggregate
     */
    protected abstract Stream<? extends DomainEventData<?>> readSnapshotData(String aggregateIdentifier);

    /**
     * Get the serializer used by this storage engine when storing and retrieving snapshots.
     *
     * @return the serializer used by this storage
     */
    public Serializer getSnapshotSerializer() {
        return snapshotSerializer;
    }

    /**
     * Get the serializer used by this storage engine when storing and retrieving events.
     *
     * @return the serializer used by this storage
     */
    public Serializer getEventSerializer() {
        return eventSerializer;
    }

    /**
     * Abstract Builder class to instantiate an {@link AbstractEventStorageEngine}.
     * <p>
     * The {@link EventUpcaster} defaults to a {@link NoOpEventUpcaster} and the {@code snapshotFilter} defaults to a
     * {@link SnapshotFilter#allowAll()} instance.
     * <p>
     * The event and snapshot {@link Serializer} are <b>hard requirements</b> and as such should be provided.
     */
    public abstract static class Builder {

        private Supplier<Serializer> snapshotSerializer;
        protected EventUpcaster upcasterChain = NoOpEventUpcaster.INSTANCE;
        private PersistenceExceptionResolver persistenceExceptionResolver;
        private Supplier<Serializer> eventSerializer;
        private SnapshotFilter snapshotFilter = SnapshotFilter.allowAll();

        /**
         * Sets the {@link Serializer} used to serialize and deserialize snapshots.
         *
         * @param snapshotSerializer a {@link Serializer} used to serialize and deserialize snapshots
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder snapshotSerializer(Serializer snapshotSerializer) {
            assertNonNull(snapshotSerializer, "The snapshot Serializer may not be null");
            this.snapshotSerializer = () -> snapshotSerializer;
            return this;
        }

        /**
         * Sets the {@link EventUpcaster} used to deserialize events of older revisions. Defaults to a {@link
         * NoOpEventUpcaster}.
         *
         * @param upcasterChain an {@link EventUpcaster} used to deserialize events of older revisions
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder upcasterChain(EventUpcaster upcasterChain) {
            assertNonNull(upcasterChain, "EventUpcaster may not be null");
            this.upcasterChain = upcasterChain;
            return this;
        }

        /**
         * Sets the {@link PersistenceExceptionResolver} used to detect concurrency exceptions from the backing
         * database. If the {@code persistenceExceptionResolver} is not specified, persistence exceptions are not
         * explicitly resolved.
         *
         * @param persistenceExceptionResolver the {@link PersistenceExceptionResolver} used to detect concurrency
         *                                     exceptions from the backing database
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder persistenceExceptionResolver(PersistenceExceptionResolver persistenceExceptionResolver) {
            this.persistenceExceptionResolver = persistenceExceptionResolver;
            return this;
        }

        /**
         * Sets the {@link Serializer} used to serialize and deserialize the Event Message's payload and {@link
         * org.axonframework.messaging.MetaData} with.
         *
         * @param eventSerializer The serializer to serialize the Event Message's payload and {@link
         *                        org.axonframework.messaging.MetaData} with
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder eventSerializer(Serializer eventSerializer) {
            assertNonNull(eventSerializer, "The event Serializer may not be null");
            this.eventSerializer = () -> eventSerializer;
            return this;
        }

        /**
         * Sets the {@code snapshotFilter} deciding whether to take a snapshot into account. Can be set to filter out
         * specific snapshot revisions which should not be applied. Defaults to a {@link Predicate} which returns {@code
         * true} regardless.
         *
         * @param snapshotFilter a {@link Predicate} which decides whether to take a snapshot into account
         * @return the current Builder instance, for fluent interfacing
         * @deprecated in favor of {@link #snapshotFilter(SnapshotFilter)}
         */
        @Deprecated
        public Builder snapshotFilter(Predicate<? super DomainEventData<?>> snapshotFilter) {
            return snapshotFilter(snapshotFilter::test);
        }

        /**
         * Sets the {@code snapshotFilter} deciding whether to take a snapshot into account. Can be set to filter out
         * specific snapshot revisions which should not be applied. Defaults to {@link SnapshotFilter#allowAll()}.
         * <p>
         * Note that {@link SnapshotFilter} instances can be combined and should return {@code true} if they handle a
         * snapshot they wish to ignore.
         *
         * @param snapshotFilter a {@link SnapshotFilter} which decides whether to take a snapshot into account
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder snapshotFilter(SnapshotFilter snapshotFilter) {
            assertNonNull(snapshotFilter, "The snapshotFilter may not be null");
            this.snapshotFilter = snapshotFilter;
            return this;
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            if (snapshotSerializer == null) {
                snapshotSerializer = () -> XStreamSerializer.defaultSerializer();
            }
            if (eventSerializer == null) {
                eventSerializer = () -> XStreamSerializer.defaultSerializer();
            }
        }
    }
}
