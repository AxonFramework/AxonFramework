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
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.*;
import org.axonframework.serialization.Serializer;

import javax.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

import static org.axonframework.eventsourcing.eventstore.EventUtils.asDomainEventMessage;

/**
 * @author Rene de Waele
 */
public class JpaEventStorageEngine extends BatchingEventStorageEngine {
    private final EntityManagerProvider entityManagerProvider;

    public JpaEventStorageEngine(EntityManagerProvider entityManagerProvider) {
        this.entityManagerProvider = entityManagerProvider;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<? extends TrackedEventData<?>> fetchBatch(TrackingToken lastToken, int batchSize) {
        Assert.isTrue(lastToken == null || lastToken instanceof GlobalIndexTrackingToken,
                      String.format("Token %s is of the wrong type", lastToken));
        return entityManager().createQuery("SELECT new org.axonframework.eventsourcing.eventstore.GenericTrackedDomainEventEntry(" +
                                                   "e.globalIndex, e.type, e.aggregateIdentifier, e.sequenceNumber, " +
                                                   "e.eventIdentifier, e.timeStamp, e.payloadType, " +
                                                   "e.payloadRevision, e.payload, e.metaData) " +
                                                   "FROM " + domainEventEntryEntityName() + " e " +
                                                   "WHERE e.globalIndex > :token " + "ORDER BY e.globalIndex ASC")
                .setParameter("token", lastToken == null ? -1 : ((GlobalIndexTrackingToken) lastToken).getGlobalIndex())
                .setMaxResults(batchSize).getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<? extends DomainEventData<?>> fetchBatch(String aggregateIdentifier, long firstSequenceNumber,
                                                            int batchSize) {
        return entityManager().createQuery("SELECT new org.axonframework.eventsourcing.eventstore.GenericDomainEventEntry(" +
                                                   "e.type, e.aggregateIdentifier, e.sequenceNumber, " +
                                                   "e.eventIdentifier, e.timeStamp, e.payloadType, " +
                                                   "e.payloadRevision, e.payload, e.metaData) " +
                                                   "FROM " + domainEventEntryEntityName() + " e " +
                                                   "WHERE e.aggregateIdentifier = :id " +
                                                   "AND e.sequenceNumber >= :seq " + "ORDER BY e.sequenceNumber ASC")
                .setParameter("id", aggregateIdentifier).setParameter("seq", firstSequenceNumber)
                .setMaxResults(batchSize).getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Optional<? extends DomainEventData<?>> readSnapshotData(String aggregateIdentifier) {
        return entityManager()
                .createQuery("SELECT new org.axonframework.eventsourcing.eventstore.GenericDomainEventEntry(" +
                                     "e.type, e.aggregateIdentifier, e.sequenceNumber, e.eventIdentifier, " +
                                     "e.timeStamp, e.payloadType, e.payloadRevision, e.payload, e.metaData) " +
                                     "FROM " + snapshotEventEntryEntityName() + " e " +
                                     "WHERE e.aggregateIdentifier = :id " +
                                     "ORDER BY e.sequenceNumber DESC")
                .setParameter("id", aggregateIdentifier)
                .setMaxResults(1).getResultList().stream().findFirst();
    }

    @Override
    protected void appendEvents(List<? extends EventMessage<?>> events, Serializer serializer) {
        if (events.isEmpty()) {
            return;
        }
        try {
            events.stream().map(event -> createEventEntity(event, serializer)).forEach(entityManager()::persist);
            entityManager().flush();
        } catch (Exception e) {
            handlePersistenceException(e, events.get(0));
        }
    }

    @Override
    protected void storeSnapshot(DomainEventMessage<?> snapshot, Serializer serializer) {
        deleteSnapshots(snapshot.getAggregateIdentifier());
        try {
            entityManager().persist(createSnapshotEntity(snapshot, serializer));
            entityManager().flush();
        } catch (Exception e) {
            handlePersistenceException(e, snapshot);
        }
    }

    protected void deleteSnapshots(String aggregateIdentifier) {
        entityManager().createQuery("DELETE FROM " + snapshotEventEntryEntityName() +
                                            " e WHERE e.aggregateIdentifier = :aggregateIdentifier")
                .setParameter("aggregateIdentifier", aggregateIdentifier).executeUpdate();
    }

    protected Object createEventEntity(EventMessage<?> eventMessage, Serializer serializer) {
        return new DomainEventEntry(asDomainEventMessage(eventMessage), serializer);
    }

    protected Object createSnapshotEntity(DomainEventMessage<?> snapshot, Serializer serializer) {
        return new SnapshotEventEntry(snapshot, serializer);
    }

    protected String domainEventEntryEntityName() {
        return DomainEventEntry.class.getSimpleName();
    }

    protected String snapshotEventEntryEntityName() {
        return SnapshotEventEntry.class.getSimpleName();
    }

    protected EntityManager entityManager() {
        return entityManagerProvider.getEntityManager();
    }
}
