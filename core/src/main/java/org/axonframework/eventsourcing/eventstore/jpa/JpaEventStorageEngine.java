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

package org.axonframework.eventsourcing.eventstore.jpa;

import org.axonframework.common.Assert;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.*;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.xml.XStreamSerializer;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.LongStream;

import static java.util.stream.Collectors.toSet;
import static org.axonframework.common.ObjectUtils.getOrDefault;
import static org.axonframework.eventsourcing.eventstore.EventUtils.asDomainEventMessage;

/**
 * EventStorageEngine implementation that uses JPA to store and fetch events.
 * <p>
 * By default the payload of events is stored as a serialized blob of bytes. Other columns are used to store meta-data
 * that allow quick finding of DomainEvents for a specific aggregate in the correct order.
 *
 * @author Rene de Waele
 */
public class JpaEventStorageEngine extends BatchingEventStorageEngine {
    private static final long DEFAULT_LOWEST_GLOBAL_SEQUENCE = 1;
    private static final int DEFAULT_MAX_GAP_OFFSET = 10000;

    private final EntityManagerProvider entityManagerProvider;
    private final long lowestGlobalSequence;
    private final int maxGapOffset;
    private final TransactionManager transactionManager;
    private final boolean explicitFlush;

    /**
     * Initializes an EventStorageEngine that uses JPA to store and load events. The payload and metadata of events is
     * stored as a serialized blob of bytes using a new {@link XStreamSerializer}.
     * <p>
     * Events are read in batches of 100. No upcasting is performed after the events have been fetched.
     *
     * @param entityManagerProvider Provider for the {@link EntityManager} used by this EventStorageEngine.
     * @param transactionManager    The instance managing transactions around fetching event data. Required by certain
     *                              databases for reading blob data.
     */
    public JpaEventStorageEngine(EntityManagerProvider entityManagerProvider, TransactionManager transactionManager) {
        this(null, null, null, null, entityManagerProvider, transactionManager, null, null, true);
    }

    /**
     * Initializes an EventStorageEngine that uses JPA to store and load events. Events are fetched in batches of 100.
     *
     * @param serializer            Used to serialize and deserialize event payload and metadata.
     * @param upcasterChain         Allows older revisions of serialized objects to be deserialized.
     * @param dataSource            Allows the EventStore to detect the database type and define the error codes that
     *                              represent concurrent access failures for most database types.
     * @param entityManagerProvider Provider for the {@link EntityManager} used by this EventStorageEngine.
     * @param transactionManager    The instance managing transactions around fetching event data. Required by certain
     *                              databases for reading blob data.
     * @throws SQLException If the database product name can not be determined from the given {@code dataSource}
     */
    public JpaEventStorageEngine(Serializer serializer, EventUpcaster upcasterChain, DataSource dataSource,
                                 EntityManagerProvider entityManagerProvider, TransactionManager transactionManager) throws SQLException {
        this(serializer, upcasterChain, new SQLErrorCodesResolver(dataSource), null, entityManagerProvider, transactionManager,
             null, null, true);
    }

    /**
     * Initializes an EventStorageEngine that uses JPA to store and load events.
     *
     * @param serializer                   Used to serialize and deserialize event payload and metadata.
     * @param upcasterChain                Allows older revisions of serialized objects to be deserialized.
     * @param persistenceExceptionResolver Detects concurrency exceptions from the backing database. If {@code null}
     *                                     persistence exceptions are not explicitly resolved.
     * @param batchSize                    The number of events that should be read at each database access. When more
     *                                     than this number of events must be read to rebuild an aggregate's state, the
     *                                     events are read in batches of this size. Tip: if you use a snapshotter, make
     *                                     sure to choose snapshot trigger and batch size such that a single batch will
     *                                     generally retrieve all events required to rebuild an aggregate's state.
     * @param entityManagerProvider        Provider for the {@link EntityManager} used by this EventStorageEngine.
     * @param maxGapOffset                 The maximum distance in sequence numbers between a missing event and the
     *                                     event with the highest known index. If the gap is bigger it is assumed that
     *                                     the missing event will not be committed to the store anymore. This event
     *                                     storage engine will no longer look for those events the next time a batch is
     *                                     fetched.
     * @param explicitFlush                Whether to explicitly call {@link EntityManager#flush()} after inserting the
     *                                     Events published in this Unit of Work. If {@code false}, this instance relies
     *                                     on the transaction manager to flush data. Note that the
     *                                     {@code persistenceExceptionResolver} may not be able to translate exceptions
     *                                     anymore. {@code false} Should only be used to optimize performance for batch
     *                                     operations. In other cases, {@code true} is recommended.
     * @param transactionManager           The instance managing transactions around fetching event data. Required by certain
     *                                     databases for reading blob data.
     * @param lowestGlobalSequence         The first expected auto generated sequence number. For most data stores this
     *                                     is 1 unless the table has contained entries before.
     */
    public JpaEventStorageEngine(Serializer serializer, EventUpcaster upcasterChain,
                                 PersistenceExceptionResolver persistenceExceptionResolver, Integer batchSize,
                                 EntityManagerProvider entityManagerProvider, TransactionManager transactionManager,
                                 Long lowestGlobalSequence, Integer maxGapOffset, boolean explicitFlush) {
        super(serializer, upcasterChain, persistenceExceptionResolver, batchSize);
        this.entityManagerProvider = entityManagerProvider;
        this.lowestGlobalSequence = getOrDefault(lowestGlobalSequence, DEFAULT_LOWEST_GLOBAL_SEQUENCE);
        this.maxGapOffset = getOrDefault(maxGapOffset, DEFAULT_MAX_GAP_OFFSET);
        this.transactionManager = transactionManager;
        this.explicitFlush = explicitFlush;
    }

    @Override
    protected List<? extends TrackedEventData<?>> fetchTrackedEvents(TrackingToken lastToken, int batchSize) {
        Assert.isTrue(lastToken == null || lastToken instanceof GapAwareTrackingToken, () -> String
                .format("Token [%s] is of the wrong type. Expected [%s]", lastToken,
                        GapAwareTrackingToken.class.getSimpleName()));
        final GapAwareTrackingToken previousToken = (GapAwareTrackingToken) lastToken;
        Collection<Long> gaps = previousToken == null ? Collections.emptySet() : previousToken.getGaps();
        List<Object[]> entries = transactionManager.fetchInTransaction(() -> {
            TypedQuery<Object[]> query;
            if (previousToken == null || gaps.isEmpty()) {
                query = entityManager().createQuery(
                        "SELECT e.globalIndex, e.type, e.aggregateIdentifier, e.sequenceNumber, " +
                                "e.eventIdentifier, e.timeStamp, e.payloadType, e.payloadRevision, e.payload, e.metaData " +
                                "FROM " + domainEventEntryEntityName() + " e " +
                                "WHERE e.globalIndex > :token ORDER BY e.globalIndex ASC", Object[].class);
            } else {
                query = entityManager().createQuery(
                        "SELECT e.globalIndex, e.type, e.aggregateIdentifier, e.sequenceNumber, " +
                                "e.eventIdentifier, e.timeStamp, e.payloadType, e.payloadRevision, e.payload, e.metaData " +
                                "FROM " + domainEventEntryEntityName() + " e " +
                                "WHERE e.globalIndex > :token OR e.globalIndex IN :gaps ORDER BY e.globalIndex ASC",
                        Object[].class)
                        .setParameter("gaps", previousToken.getGaps());
            }
            return query
                    .setParameter("token", previousToken == null ? -1L : previousToken.getIndex())
                    .setMaxResults(batchSize)
                    .getResultList();
        });
        List<TrackedEventData<?>> result = new ArrayList<>();
        GapAwareTrackingToken token = previousToken;
        for (Object[] entry : entries) {
            long globalSequence = (Long) entry[0];
            if (token == null) {
                token = GapAwareTrackingToken.newInstance(globalSequence, LongStream
                        .range(Math.min(lowestGlobalSequence, globalSequence), globalSequence).mapToObj(Long::valueOf)
                        .collect(toSet()));
            } else {
                token = token.advanceTo(globalSequence, maxGapOffset);
            }
            result.add(new GenericTrackedDomainEventEntry<>(token, (String) entry[1], (String) entry[2],
                                                            (Long) entry[3], (String) entry[4], entry[5],
                                                            (String) entry[6], (String) entry[7], entry[8], entry[9]));
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<? extends DomainEventData<?>> fetchDomainEvents(String aggregateIdentifier, long firstSequenceNumber,
                                                                   int batchSize) {
        return transactionManager.fetchInTransaction(
                () -> entityManager().createQuery(
                        "SELECT new org.axonframework.eventsourcing.eventstore.GenericTrackedDomainEventEntry(" +
                                "e.globalIndex, e.type, e.aggregateIdentifier, e.sequenceNumber, " +
                                "e.eventIdentifier, e.timeStamp, e.payloadType, " +
                                "e.payloadRevision, e.payload, e.metaData) " + "FROM " + domainEventEntryEntityName() + " e " +
                                "WHERE e.aggregateIdentifier = :id " + "AND e.sequenceNumber >= :seq " +
                                "ORDER BY e.sequenceNumber ASC")
                        .setParameter("id", aggregateIdentifier)
                        .setParameter("seq", firstSequenceNumber)
                        .setMaxResults(batchSize)
                        .getResultList());
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Optional<? extends DomainEventData<?>> readSnapshotData(String aggregateIdentifier) {
        return transactionManager.fetchInTransaction(
                () -> entityManager().createQuery(
                        "SELECT new org.axonframework.eventsourcing.eventstore.GenericDomainEventEntry(" +
                                "e.type, e.aggregateIdentifier, e.sequenceNumber, e.eventIdentifier, " +
                                "e.timeStamp, e.payloadType, e.payloadRevision, e.payload, e.metaData) " + "FROM " +
                                snapshotEventEntryEntityName() + " e " + "WHERE e.aggregateIdentifier = :id " +
                                "ORDER BY e.sequenceNumber DESC")
                        .setParameter("id", aggregateIdentifier)
                        .setMaxResults(1)
                        .getResultList()
                        .stream()
                        .findFirst()
        );
    }

    @Override
    protected void appendEvents(List<? extends EventMessage<?>> events, Serializer serializer) {
        if (events.isEmpty()) {
            return;
        }
        try {
            events.stream().map(event -> createEventEntity(event, serializer)).forEach(entityManager()::persist);
            if (explicitFlush) {
                entityManager().flush();
            }
        } catch (Exception e) {
            handlePersistenceException(e, events.get(0));
        }
    }

    @Override
    protected void storeSnapshot(DomainEventMessage<?> snapshot, Serializer serializer) {
        deleteSnapshots(snapshot.getAggregateIdentifier());
        try {
            entityManager().persist(createSnapshotEntity(snapshot, serializer));
            if (explicitFlush) {
                entityManager().flush();
            }
        } catch (Exception e) {
            handlePersistenceException(e, snapshot);
        }
    }

    /**
     * Deletes all snapshots from the underlying storage with given {@code aggregateIdentifier}.
     *
     * @param aggregateIdentifier the identifier of the aggregate to delete snapshots for
     */
    protected void deleteSnapshots(String aggregateIdentifier) {
        entityManager().createQuery("DELETE FROM " + snapshotEventEntryEntityName() +
                                            " e WHERE e.aggregateIdentifier = :aggregateIdentifier")
                .setParameter("aggregateIdentifier", aggregateIdentifier).executeUpdate();
    }

    /**
     * Returns a Jpa event entity for given {@code eventMessage}. Use the given {@code serializer} to serialize the
     * payload and metadata of the event.
     *
     * @param eventMessage the event message to store
     * @param serializer   the serializer to serialize the payload and metadata
     * @return the Jpa entity to be inserted
     */
    protected Object createEventEntity(EventMessage<?> eventMessage, Serializer serializer) {
        return new DomainEventEntry(asDomainEventMessage(eventMessage), serializer);
    }

    /**
     * Returns a Jpa snapshot entity for given {@code snapshot} of an aggregate. Use the given {@code serializer} to
     * serialize the payload and metadata of the snapshot event.
     *
     * @param snapshot   the domain event message containing a snapshot of the aggregate
     * @param serializer the serializer to serialize the payload and metadata
     * @return the Jpa entity to be inserted
     */
    protected Object createSnapshotEntity(DomainEventMessage<?> snapshot, Serializer serializer) {
        return new SnapshotEventEntry(snapshot, serializer);
    }

    /**
     * Returns the name of the Jpa event entity. Defaults to 'DomainEventEntry'.
     *
     * @return the name of the Jpa event entity
     */
    protected String domainEventEntryEntityName() {
        return DomainEventEntry.class.getSimpleName();
    }

    /**
     * Returns the name of the Snaphot event entity. Defaults to 'SnapshotEventEntry'.
     *
     * @return the name of the Jpa snapshot entity
     */
    protected String snapshotEventEntryEntityName() {
        return SnapshotEventEntry.class.getSimpleName();
    }

    /**
     * Provides an {@link EntityManager} instance for storing and fetching event data.
     *
     * @return a provided entity manager
     */
    protected EntityManager entityManager() {
        return entityManagerProvider.getEntityManager();
    }
}
