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

package org.axonframework.eventsourcing.eventstore.jpa.legacy;

import org.axonframework.common.Assert;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventData;
import org.axonframework.eventsourcing.eventstore.TrackedEventData;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.legacy.LegacyTrackingToken;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcasterChain;
import org.axonframework.serialization.xml.XStreamSerializer;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.axonframework.eventsourcing.eventstore.EventUtils.asDomainEventMessage;

/**
 * EventStorageEngine implementation that uses JPA to store and fetch events in a way that is compatible with the event
 * store format of Axon version 2.x.
 * <p>
 * By default the payload of events is stored as a serialized blob of bytes. Other columns are used to store meta-data
 * that allow quick finding of DomainEvents for a specific aggregate in the correct order.
 *
 * @author Rene de Waele
 */
public class LegacyJpaEventStorageEngine extends JpaEventStorageEngine {

    /**
     * Initializes an EventStorageEngine that uses JPA to store and load events. The payload and metadata of events is
     * stored as a serialized blob of bytes using a new {@link XStreamSerializer}.
     * <p>
     * Events are read in batches of 100. No upcasting is performed after the events have been fetched.
     *
     * @param transactionManager    The transaction manager used to set the isolation level of the transaction when
     *                              loading events
     * @param entityManagerProvider Provider for the {@link EntityManager} used by this EventStorageEngine.
     */
    public LegacyJpaEventStorageEngine(TransactionManager transactionManager,
                                       EntityManagerProvider entityManagerProvider) {
        super(entityManagerProvider, transactionManager);
    }

    /**
     * Initializes an EventStorageEngine that uses JPA to store and load events. Events are fetched in batches of 100.
     *
     * @param serializer            Used to serialize and deserialize event payload and metadata.
     * @param upcasterChain         Allows older revisions of serialized objects to be deserialized.
     * @param dataSource            Allows the EventStore to detect the database type and define the error codes that
     *                              represent concurrent access failures for most database types.
     * @param transactionManager    The transaction manager used to set the isolation level of the transaction when
     *                              loading events
     * @param entityManagerProvider Provider for the {@link EntityManager} used by this EventStorageEngine.
     * @throws SQLException If the database product name can not be determined from the given {@code dataSource}
     */
    public LegacyJpaEventStorageEngine(Serializer serializer, EventUpcasterChain upcasterChain, DataSource dataSource,
                                       TransactionManager transactionManager,
                                       EntityManagerProvider entityManagerProvider) throws SQLException {
        super(serializer, upcasterChain, dataSource, transactionManager, entityManagerProvider);
    }

    /**
     * Initializes an EventStorageEngine that uses JPA to store and load events.
     *
     * @param serializer                   Used to serialize and deserialize event payload and metadata.
     * @param upcasterChain                Allows older revisions of serialized objects to be deserialized.
     * @param persistenceExceptionResolver Detects concurrency exceptions from the backing database. If {@code null}
     *                                     persistence exceptions are not explicitly resolved.
     * @param transactionManager           The transaction manager used to set the isolation level of the transaction
     *                                     when loading events
     * @param batchSize                    The number of events that should be read at each database access. When more
     *                                     than this number of events must be read to rebuild an aggregate's state, the
     *                                     events are read in batches of this size. Tip: if you use a snapshotter, make
     *                                     sure to choose snapshot trigger and batch size such that a single batch will
     *                                     generally retrieve all events required to rebuild an aggregate's state.
     * @param entityManagerProvider        Provider for the {@link EntityManager} used by this EventStorageEngine.
     */
    public LegacyJpaEventStorageEngine(Serializer serializer, EventUpcasterChain upcasterChain,
                                       PersistenceExceptionResolver persistenceExceptionResolver,
                                       TransactionManager transactionManager, Integer batchSize,
                                       EntityManagerProvider entityManagerProvider) {
        super(serializer, upcasterChain, persistenceExceptionResolver, transactionManager, batchSize,
              entityManagerProvider);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<? extends TrackedEventData<?>> fetchTrackedEvents(TrackingToken lastToken, int batchSize) {
        Assert.isTrue(lastToken == null || lastToken instanceof LegacyTrackingToken,
                      String.format("Token %s is of the wrong type", lastToken));
        Map<String, Object> paramRegistry = new HashMap<>();
        Query query = entityManager().createQuery(String.format(
                "SELECT new org.axonframework.eventsourcing.eventstore.legacy.GenericLegacyDomainEventEntry(" +
                        "e.type, e.aggregateIdentifier, e.sequenceNumber, e.eventIdentifier, " +
                        "e.timeStamp, e.payloadType, e.payloadRevision, e.metaData, e.payload)" + "FROM %s e %s" +
                        "ORDER BY e.timeStamp ASC, e.sequenceNumber ASC, e.aggregateIdentifier ASC",
                domainEventEntryEntityName(), buildWhereClause((LegacyTrackingToken) lastToken, paramRegistry)));
        paramRegistry.forEach(query::setParameter);
        return query.setMaxResults(batchSize).getResultList();
    }

    protected String buildWhereClause(LegacyTrackingToken lastItem, Map<String, Object> paramRegistry) {
        if (lastItem == null) {
            return "";
        }
        paramRegistry.put("timestamp", lastItem.getTimestamp().toString());
        paramRegistry.put("sequenceNumber", lastItem.getSequenceNumber());
        paramRegistry.put("aggregateIdentifier", lastItem.getAggregateIdentifier());

        // though this looks like an inefficient where clause, it is the fastest way to find the next batch of items
        return "WHERE ((e.timeStamp > :timestamp) " +
                "OR (e.timeStamp = :timestamp AND e.sequenceNumber > :sequenceNumber) " +
                "OR (e.timeStamp = :timestamp AND e.sequenceNumber = :sequenceNumber " +
                "AND e.aggregateIdentifier > :aggregateIdentifier))";
    }

    @Override
    protected TrackingToken getTokenForGapDetection(TrackingToken token) {
        if (token == null) {
            return null;
        }
        Assert.isTrue(token instanceof LegacyTrackingToken, String.format("Token %s is of the wrong type", token));
        LegacyTrackingToken legacyToken = (LegacyTrackingToken) token;
        return new LegacyTrackingToken(legacyToken.getTimestamp(), legacyToken.getAggregateIdentifier(),
                                       legacyToken.getSequenceNumber());
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<? extends DomainEventData<?>> fetchDomainEvents(String aggregateIdentifier, long firstSequenceNumber,
                                                                   int batchSize) {
        return entityManager().createQuery(
                "SELECT new org.axonframework.eventsourcing.eventstore.legacy.GenericLegacyDomainEventEntry(" +
                        "e.type, e.aggregateIdentifier, e.sequenceNumber, " +
                        "e.eventIdentifier, e.timeStamp, e.payloadType, " +
                        "e.payloadRevision, e.payload, e.metaData) " + "FROM " + domainEventEntryEntityName() + " e " +
                        "WHERE e.aggregateIdentifier = :id " + "AND e.sequenceNumber >= :seq " +
                        "ORDER BY e.sequenceNumber ASC").setParameter("id", aggregateIdentifier)
                .setParameter("seq", firstSequenceNumber).setMaxResults(batchSize).getResultList();
    }

    @Override
    protected Object createEventEntity(EventMessage<?> eventMessage, Serializer serializer) {
        return new LegacyDomainEventEntry(asDomainEventMessage(eventMessage), serializer);
    }

    @Override
    protected String domainEventEntryEntityName() {
        return LegacyDomainEventEntry.class.getSimpleName();
    }
}
