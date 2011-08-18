/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.eventstore.jpa;

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.eventstore.EventSerializer;
import org.axonframework.eventstore.EventStoreManagement;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.eventstore.EventVisitor;
import org.axonframework.eventstore.SnapshotEventStore;
import org.axonframework.eventstore.XStreamEventSerializer;
import org.axonframework.repository.ConcurrencyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.sql.DataSource;

/**
 * An EventStore implementation that uses JPA to store DomainEvents in a database. The actual DomainEvent is stored as
 * a
 * serialized blob of bytes. Other columns are used to store meta-data that allow quick finding of DomainEvents for a
 * specific aggregate in the correct order.
 * <p/>
 * This EventStore supports snapshots pruning, which can enabled by configuring a {@link #setMaxSnapshotsArchived(int)
 * maximum number of snapshots to archive}. By default snapshot pruning is configured to archive only {@value
 * #DEFAULT_MAX_SNAPSHOTS_ARCHIVED} snapshot per aggregate.
 * <p/>
 * The serializer used to serialize the events is configurable. By default, the {@link XStreamEventSerializer} is used.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class JpaEventStore implements SnapshotEventStore, EventStoreManagement {

    private static final Logger logger = LoggerFactory.getLogger(JpaEventStore.class);

    private EntityManager entityManager;

    private final EventSerializer eventSerializer;
    private static final int DEFAULT_BATCH_SIZE = 100;
    private int batchSize = DEFAULT_BATCH_SIZE;
    private static final int DEFAULT_MAX_SNAPSHOTS_ARCHIVED = 1;
    private int maxSnapshotsArchived = DEFAULT_MAX_SNAPSHOTS_ARCHIVED;
    private final EventEntryStore eventEntryStore;

    private PersistenceExceptionResolver persistenceExceptionResolver;

    /**
     * Initialize a JpaEventStore using an {@link org.axonframework.eventstore.XStreamEventSerializer}, which
     * serializes events as XML and the default Event Entry store.
     * <p/>
     * The JPA Persistence context is required to contain two entities: {@link DomainEventEntry} and {@link
     * SnapshotEventEntry}.
     */
    public JpaEventStore() {
        this(new XStreamEventSerializer(), new DefaultEventEntryStore());
    }

    /**
     * Initialize a JpaEventStore which serializes events using the given <code>eventSerializer</code> and the default
     * Event Entry store.
     * <p/>
     * The JPA Persistence context is required to contain two entities: {@link DomainEventEntry} and {@link
     * SnapshotEventEntry}.
     *
     * @param eventSerializer The serializer to (de)serialize domain events with.
     */
    public JpaEventStore(EventSerializer eventSerializer) {
        this(eventSerializer, new DefaultEventEntryStore());
    }

    /**
     * Initialize a JpaEventStore using the given <code>eventEntryStore</code> and an {@link
     * org.axonframework.eventstore.XStreamEventSerializer}, which serializes events as XML.
     *
     * @param eventEntryStore The instance providing persistence logic for Domain Event entries
     */
    public JpaEventStore(EventEntryStore eventEntryStore) {
        this(new XStreamEventSerializer(), eventEntryStore);
    }

    /**
     * Initialize a JpaEventStore which serializes events using the given <code>eventSerializer</code> and stores the
     * events in the database using the given <code>eventEntryStore</code>.
     *
     * @param eventSerializer The serializer to (de)serialize domain events with.
     * @param eventEntryStore The instance providing persistence logic for Domain Event entries
     */
    public JpaEventStore(EventSerializer eventSerializer, EventEntryStore eventEntryStore) {
        this.eventSerializer = eventSerializer;
        this.eventEntryStore = eventEntryStore;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void appendEvents(String type, DomainEventStream events) {
        DomainEvent event = null;
        try {
            while (events.hasNext()) {
                event = events.next();
                eventEntryStore.persistEvent(type, event, eventSerializer.serialize(event), entityManager);
            }
        } catch (RuntimeException exception) {
            if (persistenceExceptionResolver != null
                    && persistenceExceptionResolver.isDuplicateKeyViolation(exception)) {
                throw new ConcurrencyException(
                        String.format("Concurrent modification detected for Aggregate identifier [%s], sequence: [%s]",
                                      event.getAggregateIdentifier(),
                                      event.getSequenceNumber().toString()),
                        exception);
            }
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DomainEventStream readEvents(String type, AggregateIdentifier identifier) {
        long snapshotSequenceNumber = -1;
        byte[] lastSnapshotEvent = eventEntryStore.loadLastSnapshotEvent(type, identifier, entityManager);
        DomainEvent snapshotEvent = null;
        if (lastSnapshotEvent != null) {
            try {
                snapshotEvent = eventSerializer.deserialize(lastSnapshotEvent);
                snapshotSequenceNumber = snapshotEvent.getSequenceNumber();
            } catch (RuntimeException ex) {
                logger.warn("Error while reading snapshot event entry. "
                                    + "Reconstructing aggregate on entire event stream. Caused by: {} {}",
                            ex.getClass().getName(),
                            ex.getMessage());
            }
        }

        List<DomainEvent> events = fetchBatch(type, identifier, snapshotSequenceNumber + 1, batchSize);
        if (snapshotEvent != null) {
            events.add(0, snapshotEvent);
        }
        if (events.isEmpty()) {
            throw new EventStreamNotFoundException(type, identifier);
        }
        return new BatchingDomainEventStream(events, identifier, type);
    }

    @SuppressWarnings({"unchecked"})
    private List<DomainEvent> fetchBatch(String type, AggregateIdentifier identifier, long firstSequenceNumber,
                                         int batchSize) {
        List<byte[]> entries = eventEntryStore.fetchBatch(type,
                                                          identifier,
                                                          firstSequenceNumber,
                                                          batchSize,
                                                          entityManager);
        List<DomainEvent> events = new ArrayList<DomainEvent>(entries.size());
        for (byte[] entry : entries) {
            events.add(eventSerializer.deserialize(entry));
        }
        return events;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Upon appending a snapshot, this particular EventStore implementation also prunes snapshots which
     * are considered redundant because they fall outside of the range of maximum snapshots to archive.
     */
    @Override
    public void appendSnapshotEvent(String type, DomainEvent snapshotEvent) {
        // Persist snapshot before pruning redundant archived ones, in order to prevent snapshot misses when reloading
        // an aggregate, which may occur when a READ_UNCOMMITTED transaction isolation level is used.
        eventEntryStore.persistSnapshot(type, snapshotEvent, eventSerializer.serialize(snapshotEvent), entityManager);

        if (maxSnapshotsArchived > 0) {
            eventEntryStore.pruneSnapshots(type, snapshotEvent, maxSnapshotsArchived, entityManager);
        }
    }

    @Override
    public void visitEvents(EventVisitor visitor) {
        int first = 0;
        List<byte[]> batch;
        boolean shouldContinue = true;
        while (shouldContinue) {
            batch = eventEntryStore.fetchBatch(first, batchSize, entityManager);
            for (byte[] entry : batch) {
                visitor.doWithEvent(eventSerializer.deserialize(entry));
            }
            shouldContinue = (batch.size() >= batchSize);
            first += batchSize;
        }
    }

    /**
     * Sets the EntityManager for this EventStore to use.
     *
     * @param entityManager the EntityManager to use.
     */
    @PersistenceContext
    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Registers the data source that allows the EventStore to detect the database type and define the error codes that
     * represent concurrent access failures.
     * <p/>
     * Should not be used in combination with {@link #setPersistenceExceptionResolver(PersistenceExceptionResolver)},
     * but rather as a shorthand alternative for most common database types.
     *
     * @param dataSource A data source providing access to the backing database
     * @throws SQLException If an error occurs while accessing the dataSource
     */
    public void setDataSource(DataSource dataSource) throws SQLException {
        if (persistenceExceptionResolver == null) {
            persistenceExceptionResolver = new SQLErrorCodesResolver(dataSource);
        }
    }

    /**
     * Sets the persistenceExceptionResolver that will help detect concurrency exceptions from the backing database.
     *
     * @param persistenceExceptionResolver the persistenceExceptionResolver that will help detect concurrency
     *                                     exceptions
     */
    public void setPersistenceExceptionResolver(PersistenceExceptionResolver persistenceExceptionResolver) {
        this.persistenceExceptionResolver = persistenceExceptionResolver;
    }

    /**
     * Sets the number of events that should be read at each database access. When more than this number of events must
     * be read to rebuild an aggregate's state, the events are read in batches of this size. Defaults to 100.
     * <p/>
     * Tip: if you use a snapshotter, make sure to choose snapshot trigger and batch size such that a single batch will
     * generally retrieve all events required to rebuild an aggregate's state.
     *
     * @param batchSize the number of events to read on each database access. Default to 100.
     */
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    /**
     * Sets the maximum number of snapshots to archive for an aggregate. The EventStore will keep at most this number
     * of snapshots per aggregate.
     * <p/>
     * Defaults to {@value #DEFAULT_MAX_SNAPSHOTS_ARCHIVED}.
     *
     * @param maxSnapshotsArchived The maximum number of snapshots to archive for an aggregate. A value less than 1
     *                             disables pruning of snapshots.
     */
    public void setMaxSnapshotsArchived(int maxSnapshotsArchived) {
        this.maxSnapshotsArchived = maxSnapshotsArchived;
    }

    private final class BatchingDomainEventStream implements DomainEventStream {

        private int currentBatchSize;
        private Iterator<DomainEvent> currentBatch;
        private DomainEvent next;
        private final AggregateIdentifier id;
        private final String typeId;

        private BatchingDomainEventStream(List<DomainEvent> firstBatch, AggregateIdentifier id,
                                          String typeId) {
            this.id = id;
            this.typeId = typeId;
            this.currentBatchSize = firstBatch.size();
            this.currentBatch = firstBatch.iterator();
            if (currentBatch.hasNext()) {
                next = currentBatch.next();
            }
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public DomainEvent next() {
            DomainEvent nextEvent = next;
            if (!currentBatch.hasNext() && currentBatchSize >= batchSize) {
                logger.debug("Fetching new batch for Aggregate [{}]", id.asString());
                currentBatch = fetchBatch(typeId, id, next.getSequenceNumber() + 1, JpaEventStore.this.batchSize)
                        .iterator();
            }
            next = currentBatch.hasNext() ? currentBatch.next() : null;
            return nextEvent;
        }

        @Override
        public DomainEvent peek() {
            return next;
        }
    }
}
