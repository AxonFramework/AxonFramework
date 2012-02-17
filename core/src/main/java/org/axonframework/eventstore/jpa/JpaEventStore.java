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

import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.eventstore.EventVisitor;
import org.axonframework.eventstore.SerializedDomainEventData;
import org.axonframework.eventstore.SerializedDomainEventMessage;
import org.axonframework.eventstore.SnapshotEventStore;
import org.axonframework.eventstore.jpa.criteria.JpaCriteria;
import org.axonframework.eventstore.jpa.criteria.JpaCriteriaBuilder;
import org.axonframework.eventstore.jpa.criteria.ParameterRegistry;
import org.axonframework.eventstore.management.Criteria;
import org.axonframework.eventstore.management.CriteriaBuilder;
import org.axonframework.eventstore.management.EventStoreManagement;
import org.axonframework.repository.ConcurrencyException;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.XStreamSerializer;
import org.axonframework.upcasting.Upcaster;
import org.axonframework.upcasting.UpcasterChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.persistence.EntityManager;
import javax.sql.DataSource;

import static org.axonframework.common.IdentifierValidator.validateIdentifier;

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
 * The serializer used to serialize the events is configurable. By default, the {@link XStreamSerializer} is used.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class JpaEventStore implements SnapshotEventStore, EventStoreManagement {

    private static final Logger logger = LoggerFactory.getLogger(JpaEventStore.class);

    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int DEFAULT_MAX_SNAPSHOTS_ARCHIVED = 1;

    private final Serializer eventSerializer;
    private final EventEntryStore eventEntryStore;
    private int batchSize = DEFAULT_BATCH_SIZE;
    private UpcasterChain upcasterChain = UpcasterChain.EMPTY;
    private final EntityManagerProvider entityManagerProvider;
    private int maxSnapshotsArchived = DEFAULT_MAX_SNAPSHOTS_ARCHIVED;

    private PersistenceExceptionResolver persistenceExceptionResolver;

    /**
     * Initialize a JpaEventStore using an {@link org.axonframework.serializer.XStreamSerializer}, which
     * serializes events as XML and the default Event Entry store.
     * <p/>
     * The JPA Persistence context is required to contain two entities: {@link DomainEventEntry} and {@link
     * SnapshotEventEntry}.
     *
     * @param entityManagerProvider The EntityManagerProvider providing the EntityManager instance for this EventStore
     */
    public JpaEventStore(EntityManagerProvider entityManagerProvider) {
        this(entityManagerProvider, new XStreamSerializer(), new DefaultEventEntryStore());
    }

    /**
     * Initialize a JpaEventStore using the given <code>eventEntryStore</code> and an {@link
     * org.axonframework.serializer.XStreamSerializer}, which serializes events as XML.
     *
     * @param entityManagerProvider The EntityManagerProvider providing the EntityManager instance for this EventStore
     * @param eventEntryStore       The instance providing persistence logic for Domain Event entries
     */
    public JpaEventStore(EntityManagerProvider entityManagerProvider, EventEntryStore eventEntryStore) {
        this(entityManagerProvider, new XStreamSerializer(), eventEntryStore);
    }

    /**
     * Initialize a JpaEventStore which serializes events using the given <code>eventSerializer</code> and stores the
     * events in the database using the default EventEntryStore.
     *
     * @param entityManagerProvider The EntityManagerProvider providing the EntityManager instance for this EventStore
     * @param eventSerializer       The serializer to (de)serialize domain events with.
     */
    public JpaEventStore(EntityManagerProvider entityManagerProvider,
                         Serializer eventSerializer) {
        this(entityManagerProvider, eventSerializer, new DefaultEventEntryStore());
    }

    /**
     * Initialize a JpaEventStore which serializes events using the given <code>eventSerializer</code> and stores the
     * events in the database using the given <code>eventEntryStore</code>.
     *
     * @param entityManagerProvider The EntityManagerProvider providing the EntityManager instance for this EventStore
     * @param eventSerializer       The serializer to (de)serialize domain events with.
     * @param eventEntryStore       The instance providing persistence logic for Domain Event entries
     */
    public JpaEventStore(EntityManagerProvider entityManagerProvider,
                         Serializer eventSerializer, EventEntryStore eventEntryStore) {
        this.entityManagerProvider = entityManagerProvider;
        this.eventSerializer = eventSerializer;
        this.eventEntryStore = eventEntryStore;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void appendEvents(String type, DomainEventStream events) {
        DomainEventMessage event = null;
        try {
            EntityManager entityManager = entityManagerProvider.getEntityManager();
            while (events.hasNext()) {
                event = events.next();
                validateIdentifier(event.getAggregateIdentifier().getClass());
                eventEntryStore.persistEvent(type, event, eventSerializer.serialize(event.getPayload(), byte[].class),
                                             eventSerializer.serialize(event.getMetaData(), byte[].class),
                                             entityManager);
            }
            entityManager.flush();
        } catch (RuntimeException exception) {
            if (persistenceExceptionResolver != null
                    && persistenceExceptionResolver.isDuplicateKeyViolation(exception)) {
                throw new ConcurrencyException(
                        String.format("Concurrent modification detected for Aggregate identifier [%s], sequence: [%s]",
                                      event.getAggregateIdentifier(),
                                      event.getSequenceNumber()),
                        exception);
            }
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings({"unchecked"})
    @Override
    public DomainEventStream readEvents(String type, Object identifier) {
        long snapshotSequenceNumber = -1;
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        SerializedDomainEventData lastSnapshotEvent = eventEntryStore.loadLastSnapshotEvent(type, identifier,
                                                                                            entityManager);
        DomainEventMessage snapshotEvent = null;
        if (lastSnapshotEvent != null) {
            try {
                snapshotEvent = new GenericDomainEventMessage<Object>(
                        identifier,
                        lastSnapshotEvent.getSequenceNumber(),
                        eventSerializer.deserialize(lastSnapshotEvent.getPayload()),
                        (Map<String, Object>) eventSerializer.deserialize(lastSnapshotEvent.getMetaData()));
                snapshotSequenceNumber = snapshotEvent.getSequenceNumber();
            } catch (RuntimeException ex) {
                logger.warn("Error while reading snapshot event entry. "
                                    + "Reconstructing aggregate on entire event stream. Caused by: {} {}",
                            ex.getClass().getName(),
                            ex.getMessage());
            } catch (LinkageError error) {
                logger.warn("Error while reading snapshot event entry. "
                                    + "Reconstructing aggregate on entire event stream. Caused by: {} {}",
                            error.getClass().getName(),
                            error.getMessage());
            }
        }

        List<DomainEventMessage> events = fetchBatch(type, identifier, snapshotSequenceNumber + 1);
        if (snapshotEvent != null) {
            events.add(0, snapshotEvent);
        }
        if (events.isEmpty()) {
            throw new EventStreamNotFoundException(type, identifier);
        }
        return new BatchingDomainEventStream(events, identifier, type);
    }

    @SuppressWarnings({"unchecked"})
    private List<DomainEventMessage> fetchBatch(String type, Object identifier, long firstSequenceNumber) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        List<? extends SerializedDomainEventData> entries = eventEntryStore.fetchBatch(type,
                                                                                       identifier,
                                                                                       firstSequenceNumber,
                                                                                       batchSize,
                                                                                       entityManager);
        List<DomainEventMessage> events = new ArrayList<DomainEventMessage>(entries.size());
        for (SerializedDomainEventData entry : entries) {
            events.addAll(SerializedDomainEventMessage.createDomainEventMessages(eventSerializer,
                                                                                 entry.getEventIdentifier(),
                                                                                 identifier,
                                                                                 entry.getSequenceNumber(),
                                                                                 entry.getTimestamp(),
                                                                                 entry.getPayload(),
                                                                                 entry.getMetaData(),
                                                                                 upcasterChain));
        }
        return events;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Upon appending a snapshot, this particular EventStore implementation also prunes snapshots which are considered
     * redundant because they fall outside of the range of maximum snapshots to archive.
     */
    @Override
    public void appendSnapshotEvent(String type, DomainEventMessage snapshotEvent) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        // Persist snapshot before pruning redundant archived ones, in order to prevent snapshot misses when reloading
        // an aggregate, which may occur when a READ_UNCOMMITTED transaction isolation level is used.
        eventEntryStore.persistSnapshot(type, snapshotEvent,
                                        eventSerializer.serialize(snapshotEvent.getPayload(), byte[].class),
                                        eventSerializer.serialize(snapshotEvent.getMetaData(), byte[].class),
                                        entityManager);

        if (maxSnapshotsArchived > 0) {
            eventEntryStore.pruneSnapshots(type, snapshotEvent, maxSnapshotsArchived,
                                           entityManagerProvider.getEntityManager());
        }
    }

    @Override
    public void visitEvents(EventVisitor visitor) {
        doVisitEvents(visitor, null, Collections.<String, Object>emptyMap());
    }

    @Override
    public void visitEvents(Criteria criteria, EventVisitor visitor) {
        StringBuilder sb = new StringBuilder();
        ParameterRegistry parameters = new ParameterRegistry();
        ((JpaCriteria) criteria).parse("e", sb, parameters);
        doVisitEvents(visitor, sb.toString(), parameters.getParameters());
    }

    @Override
    public CriteriaBuilder newCriteriaBuilder() {
        return new JpaCriteriaBuilder();
    }

    private void doVisitEvents(EventVisitor visitor, String whereClause, Map<String, Object> parameters) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        // TODO Batch processing is duplicated for JpaEventStore and MongoEventStore
        // provide a more generic way of doing batch processing.
        int first = 0;
        List<? extends SerializedDomainEventData> batch;
        boolean shouldContinue = true;
        while (shouldContinue) {
            batch = eventEntryStore.fetchFilteredBatch(whereClause, parameters, first, batchSize, entityManager);
            for (SerializedDomainEventData entry : batch) {
                List<DomainEventMessage> domainEventMessages =
                        SerializedDomainEventMessage.createDomainEventMessages(entry,
                                                                               eventSerializer,
                                                                               eventSerializer,
                                                                               upcasterChain);
                for (DomainEventMessage domainEventMessage : domainEventMessages) {
                    visitor.doWithEvent(domainEventMessage);
                }
            }
            shouldContinue = (batch.size() >= batchSize);
            first += batchSize;
        }
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
     * Sets the upcasters which allow older revisions of serialized objects to be deserialized. Upcasters are evaluated
     * in the order they are provided in the given List. That means that you should take special precaution when an
     * upcaster expects another upcaster to have processed an event.
     * <p/>
     * Any upcaster that relies on another upcaster doing its work first, should be placed <em>after</em> that other
     * upcaster in the given list. Thus for any <em>upcaster B</em> that relies on <em>upcaster A</em> to do its work
     * first, the following must be true: <code>upcasters.indexOf(B) > upcasters.indexOf(A)</code>.
     *
     * @param upcasters the upcasters for this serializer.
     */
    public void setUpcasters(List<Upcaster> upcasters) {
        this.upcasterChain = new UpcasterChain(upcasters);
    }

    /**
     * Sets the maximum number of snapshots to archive for an aggregate. The EventStore will keep at most this number
     * of
     * snapshots per aggregate.
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
        private Iterator<DomainEventMessage> currentBatch;
        private DomainEventMessage next;
        private final Object id;
        private final String typeId;

        private BatchingDomainEventStream(List<DomainEventMessage> firstBatch, Object id, String typeId) {
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
        public DomainEventMessage next() {
            DomainEventMessage current = next;
            if (next != null && !currentBatch.hasNext() && currentBatchSize >= batchSize) {
                logger.debug("Fetching new batch for Aggregate [{}]", id);
                List<DomainEventMessage> newBatch = fetchBatch(typeId, id, next.getSequenceNumber() + 1);
                currentBatchSize = newBatch.size();
                currentBatch = newBatch.iterator();
            }
            next = currentBatch.hasNext() ? currentBatch.next() : null;
            return current;
        }

        @Override
        public DomainEventMessage peek() {
            return next;
        }
    }
}
