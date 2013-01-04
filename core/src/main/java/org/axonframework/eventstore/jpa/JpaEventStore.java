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

package org.axonframework.eventstore.jpa;

import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.eventstore.EventVisitor;
import org.axonframework.eventstore.SnapshotEventStore;
import org.axonframework.eventstore.jpa.criteria.JpaCriteria;
import org.axonframework.eventstore.jpa.criteria.JpaCriteriaBuilder;
import org.axonframework.eventstore.jpa.criteria.ParameterRegistry;
import org.axonframework.eventstore.management.Criteria;
import org.axonframework.eventstore.management.CriteriaBuilder;
import org.axonframework.eventstore.management.EventStoreManagement;
import org.axonframework.repository.ConcurrencyException;
import org.axonframework.serializer.MessageSerializer;
import org.axonframework.serializer.SerializedDomainEventData;
import org.axonframework.serializer.SerializedDomainEventMessage;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.xml.XStreamSerializer;
import org.axonframework.upcasting.SerializedDomainEventUpcastingContext;
import org.axonframework.upcasting.SimpleUpcasterChain;
import org.axonframework.upcasting.UpcastSerializedDomainEventData;
import org.axonframework.upcasting.UpcasterAware;
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
public class JpaEventStore implements SnapshotEventStore, EventStoreManagement, UpcasterAware {

    private static final Logger logger = LoggerFactory.getLogger(JpaEventStore.class);

    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int DEFAULT_MAX_SNAPSHOTS_ARCHIVED = 1;

    private final MessageSerializer serializer;
    private final EventEntryStore eventEntryStore;
    private int batchSize = DEFAULT_BATCH_SIZE;
    private UpcasterChain upcasterChain = SimpleUpcasterChain.EMPTY;
    private final EntityManagerProvider entityManagerProvider;
    private int maxSnapshotsArchived = DEFAULT_MAX_SNAPSHOTS_ARCHIVED;

    private PersistenceExceptionResolver persistenceExceptionResolver;

    /**
     * Initialize a JpaEventStore using an {@link org.axonframework.serializer.xml.XStreamSerializer}, which
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
     * org.axonframework.serializer.xml.XStreamSerializer}, which serializes events as XML.
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
     * @param serializer            The serializer to (de)serialize domain events with.
     */
    public JpaEventStore(EntityManagerProvider entityManagerProvider, Serializer serializer) {
        this(entityManagerProvider, serializer, new DefaultEventEntryStore());
    }

    /**
     * Initialize a JpaEventStore which serializes events using the given <code>eventSerializer</code> and stores the
     * events in the database using the given <code>eventEntryStore</code>.
     *
     * @param entityManagerProvider The EntityManagerProvider providing the EntityManager instance for this EventStore
     * @param serializer            The serializer to (de)serialize domain events with.
     * @param eventEntryStore       The instance providing persistence logic for Domain Event entries
     */
    public JpaEventStore(EntityManagerProvider entityManagerProvider, Serializer serializer,
                         EventEntryStore eventEntryStore) {
        this.entityManagerProvider = entityManagerProvider;
        this.serializer = new MessageSerializer(serializer);
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
                SerializedObject<byte[]> serializedPayload = serializer.serializePayload(event, byte[].class);
                SerializedObject<byte[]> serializedMetaData = serializer.serializeMetaData(event, byte[].class);
                eventEntryStore.persistEvent(type, event, serializedPayload, serializedMetaData, entityManager);
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
                        serializer.deserialize(lastSnapshotEvent.getPayload()),
                        (Map<String, Object>) serializer.deserialize(lastSnapshotEvent.getMetaData()));
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

        EntityManager entityManager1 = entityManagerProvider.getEntityManager();
        Iterator<? extends SerializedDomainEventData> entries = eventEntryStore.fetchAggregateStream(type,
                                                                                                     identifier,
                                                                                                     snapshotSequenceNumber
                                                                                                             + 1,
                                                                                                     batchSize,
                                                                                                     entityManager1);
        if (snapshotEvent == null && !entries.hasNext()) {
            throw new EventStreamNotFoundException(type, identifier);
        }
        return new CursorBackedDomainEventStream(snapshotEvent, entries, identifier);
    }

    @SuppressWarnings("unchecked")
    private List<DomainEventMessage> upcastAndDeserialize(SerializedDomainEventData entry, Object identifier) {
        final SerializedDomainEventUpcastingContext context = new SerializedDomainEventUpcastingContext(entry,
                                                                                                        serializer);
        List<SerializedObject> objects = upcasterChain.upcast(
                entry.getPayload(), context);
        List<DomainEventMessage> events = new ArrayList<DomainEventMessage>(objects.size());
        for (SerializedObject object : objects) {
            DomainEventMessage<Object> message = new SerializedDomainEventMessage<Object>(
                    new UpcastSerializedDomainEventData(entry, identifier, object), serializer);

            // prevents duplicate deserialization of meta data when it has already been access during upcasting
            if (context.getSerializedMetaData().isDeserialized()) {
                message = message.withMetaData(context.getSerializedMetaData().getObject());
            }

            events.add(message);
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
        SerializedObject<byte[]> serializedPayload = serializer.serializePayload(snapshotEvent, byte[].class);
        SerializedObject<byte[]> serializedMetaData = serializer.serializeMetaData(snapshotEvent, byte[].class);
        eventEntryStore.persistSnapshot(type, snapshotEvent, serializedPayload, serializedMetaData, entityManager);

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
        Iterator<? extends SerializedDomainEventData> batch = eventEntryStore.fetchFiltered(whereClause,
                                                                                            parameters,
                                                                                            batchSize,
                                                                                            entityManager);
        DomainEventStream eventStream = new CursorBackedDomainEventStream(null, batch, null);
        while (eventStream.hasNext()) {
            visitor.doWithEvent(eventStream.next());
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

    @Override
    public void setUpcasterChain(UpcasterChain upcasterChain) {
        this.upcasterChain = upcasterChain;
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

    private final class CursorBackedDomainEventStream implements DomainEventStream {

        private Iterator<DomainEventMessage> currentBatch;
        private DomainEventMessage next;
        private final Iterator<? extends SerializedDomainEventData> cursor;
        private final Object aggregateIdentifier;

        public CursorBackedDomainEventStream(DomainEventMessage snapshotEvent,
                                             Iterator<? extends SerializedDomainEventData> cursor,
                                             Object aggregateIdentifier) {
            this.aggregateIdentifier = aggregateIdentifier;
            if (snapshotEvent != null) {
                currentBatch = Collections.singletonList(snapshotEvent).iterator();
            } else {
                currentBatch = Collections.<DomainEventMessage>emptyList().iterator();
            }
            this.cursor = cursor;
            initializeNextItem();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public DomainEventMessage next() {
            DomainEventMessage current = next;
            initializeNextItem();
            return current;
        }

        private void initializeNextItem() {
            while (!currentBatch.hasNext() && cursor.hasNext()) {
                final SerializedDomainEventData entry = cursor.next();
                currentBatch = upcastAndDeserialize(entry, getAggregateIdentifier(entry)).iterator();
            }
            next = currentBatch.hasNext() ? currentBatch.next() : null;
        }

        private Object getAggregateIdentifier(SerializedDomainEventData entry) {
            return aggregateIdentifier != null ? aggregateIdentifier : entry.getAggregateIdentifier();
        }

        @Override
        public DomainEventMessage peek() {
            return next;
        }
    }
}
