/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.AbstractEventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Abstract implementation of an {@link EventStore} that uses a {@link EventStorageEngine} to store and load events.
 *
 * @author Rene de Waele
 */
public abstract class AbstractEventStore extends AbstractEventBus implements EventStore {

    private static final Logger logger = LoggerFactory.getLogger(AbstractEventStore.class);

    private final EventStorageEngine storageEngine;

    private final SnapshotResolver snapshotResolver;

    /**
     * Initializes an event store with given {@code storageEngine} and {@link NoOpMessageMonitor}.
     *
     * @param storageEngine The storage engine used to store and load events
     */
    protected AbstractEventStore(EventStorageEngine storageEngine) {
        this(storageEngine, NoOpMessageMonitor.INSTANCE);
    }

    /**
     * Initializes an event store with given {@code storageEngine} and {@code messageMonitor}.
     *
     * @param storageEngine  The storage engine used to store and load events
     * @param messageMonitor The monitor used to record event publications
     */
    protected AbstractEventStore(EventStorageEngine storageEngine,
                                 MessageMonitor<? super EventMessage<?>> messageMonitor) {
        this(storageEngine,
             messageMonitor,
             SnapshotResolver.resolveLast());
    }

    /**
     * Initialize an event store with given {@code storageEngine}, {@code messageMonitor} and {@code snapshotResolver}.
     *
     * @param storageEngine    The storage engine used to store and load events
     * @param messageMonitor   The monitor used to record event publications
     * @param snapshotResolver The function which will choose one snapshot from a stream of snapshots
     */
    protected AbstractEventStore(EventStorageEngine storageEngine,
                                 MessageMonitor<? super EventMessage<?>> messageMonitor,
                                 SnapshotResolver snapshotResolver) {
        super(messageMonitor);
        this.storageEngine = storageEngine;
        this.snapshotResolver = snapshotResolver;
    }

    @Override
    protected void prepareCommit(List<? extends EventMessage<?>> events) {
        storageEngine.appendEvents(events);
        super.prepareCommit(events);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns a {@link DomainEventStream} starting with the last stored snapshot of the aggregate
     * followed by subsequent domain events.
     */
    @Override
    public DomainEventStream readEvents(String aggregateIdentifier) {
        Optional<DomainEventMessage<?>> optionalSnapshot;
        try {
            optionalSnapshot = snapshotResolver.resolve(storageEngine.readSnapshots(aggregateIdentifier));
        } catch (Exception | LinkageError e) {
            optionalSnapshot = handleSnapshotReadingError(aggregateIdentifier, e);
        }
        DomainEventStream eventStream;
        if (optionalSnapshot.isPresent()) {
            DomainEventMessage<?> snapshot = optionalSnapshot.get();
            eventStream = DomainEventStream.concat(DomainEventStream.of(snapshot),
                                                   storageEngine.readEvents(aggregateIdentifier,
                                                                            snapshot.getSequenceNumber() + 1));
        } else {
            eventStream = storageEngine.readEvents(aggregateIdentifier);
        }

        Stream<? extends DomainEventMessage<?>> domainEventMessages = stagedDomainEventMessages(aggregateIdentifier);
        return DomainEventStream.concat(eventStream, DomainEventStream.of(domainEventMessages));
    }

    /**
     * Invoked when an error ({@link Exception} or {@link LinkageError}) occurs while attempting to read a snapshot
     * event. This method can be overridden to change the default behavior, which is to log the exception (warn level)
     * and ignore the snapshot.
     * <p>
     * Overriding implementations may choose to return normally, or raise an exception. Exceptions raised from this
     * method are propagated to the caller of the {@link #readEvents(String)} or {@link #readEvents(String, long)}
     * methods.
     * <p>
     * Returning an empty Optional will force the initialization of the aggregate to happen based on the entire event
     * stream of that aggregate.
     *
     * @param aggregateIdentifier The identifier of the aggregate for which an snapshot failed to load
     * @param e                   The exception or error that occurred while loading or deserializing the snapshot
     * @return An optional DomainEventMessage to use as the snapshot for this aggregate
     * @throws RuntimeException any runtimeException to fail loading the
     */
    protected Optional<DomainEventMessage<?>> handleSnapshotReadingError(String aggregateIdentifier, Throwable e) {
        logger.warn("Error reading snapshot for aggregate [{}]. Reconstructing from entire event stream.",
                    aggregateIdentifier, e);
        return Optional.empty();
    }

    /**
     * Returns a Stream of all DomainEventMessages that have been staged for publication by an Aggregate with given
     * {@code aggregateIdentifier}.
     *
     * @param aggregateIdentifier The identifier of the aggregate to get staged events for
     * @return a Stream of DomainEventMessage of the identified aggregate
     */
    protected Stream<? extends DomainEventMessage<?>> stagedDomainEventMessages(String aggregateIdentifier) {
        return queuedMessages().stream()
                               .filter(m -> m instanceof DomainEventMessage)
                               .map(m -> (DomainEventMessage<?>) m)
                               .filter(m -> aggregateIdentifier.equals(m.getAggregateIdentifier()));
    }

    @Override
    public DomainEventStream readEvents(String aggregateIdentifier, long firstSequenceNumber) {
        return DomainEventStream.concat(storageEngine.readEvents(aggregateIdentifier, firstSequenceNumber),
                                        DomainEventStream.of(
                                                stagedDomainEventMessages(aggregateIdentifier)
                                                        .filter(m -> m.getSequenceNumber() >= firstSequenceNumber)));
    }

    @Override
    public void storeSnapshot(DomainEventMessage<?> snapshot) {
        storageEngine.storeSnapshot(snapshot);
    }

    /**
     * Returns the {@link EventStorageEngine} used by the event store.
     *
     * @return The event storage engine used by this event store
     */
    protected EventStorageEngine storageEngine() {
        return storageEngine;
    }

    @Override
    public Optional<Long> lastSequenceNumberFor(String aggregateIdentifier) {
        Optional<Long> highestStaged = stagedDomainEventMessages(aggregateIdentifier)
                .map(DomainEventMessage::getSequenceNumber)
                .max(Long::compareTo);
        if (highestStaged.isPresent()) {
            return highestStaged;
        }
        return storageEngine.lastSequenceNumberFor(aggregateIdentifier);
    }

    @Override
    public TrackingToken createTailToken() {
        return storageEngine.createTailToken();
    }

    @Override
    public TrackingToken createHeadToken() {
        return storageEngine.createHeadToken();
    }

    @Override
    public TrackingToken createTokenAt(Instant dateTime) {
        return storageEngine.createTokenAt(dateTime);
    }
}
