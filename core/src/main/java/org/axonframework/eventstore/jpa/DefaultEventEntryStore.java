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

import java.util.Iterator;
import java.util.List;
import javax.persistence.EntityManager;

/**
 * Implementation of the EventEntryStore that stores events in DomainEventEntry entities and snapshot events in
 * SnapshotEventEntry entities.
 * <p/>
 * This implementation requires that the aforementioned instances are available in the current persistence context.
 *
 * @author Allard Buijze
 * @since 1.2
 */
class DefaultEventEntryStore implements EventEntryStore {

    @Override
    public void persistEvent(String aggregateType, DomainEvent event, byte[] serializedEvent,
                             EntityManager entityManager) {
        entityManager.persist(new DomainEventEntry(aggregateType, event, serializedEvent));
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public byte[] loadLastSnapshotEvent(String aggregateType, AggregateIdentifier identifier,
                                        EntityManager entityManager) {
        List<byte[]> entries = entityManager.createQuery(
                "SELECT e.serializedEvent FROM SnapshotEventEntry e "
                        + "WHERE e.aggregateIdentifier = :id AND e.type = :type "
                        + "ORDER BY e.sequenceNumber DESC")
                                            .setParameter("id", identifier.asString())
                                            .setParameter("type", aggregateType)
                                            .setMaxResults(1)
                                            .setFirstResult(0)
                                            .getResultList();
        if (entries.size() < 1) {
            return null;
        }
        return entries.get(0);
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public List<byte[]> fetchBatch(int startPosition, int batchSize, EntityManager entityManager) {
        return entityManager.createQuery(
                "SELECT e.serializedEvent FROM DomainEventEntry e ORDER BY e.timeStamp ASC, e.sequenceNumber ASC")
                            .setFirstResult(startPosition)
                            .setMaxResults(batchSize)
                            .getResultList();
    }

    @Override
    public void persistSnapshot(String type, DomainEvent snapshotEvent, byte[] serializedEvent,
                                EntityManager entityManager) {
        entityManager.persist(new SnapshotEventEntry(type, snapshotEvent, serializedEvent));
    }

    @Override
    public void pruneSnapshots(String type, DomainEvent mostRecentSnapshotEvent, int maxSnapshotsArchived,
                               EntityManager entityManager) {
        Iterator<Long> redundantSnapshots = findRedundantSnapshots(type, mostRecentSnapshotEvent,
                                                                   maxSnapshotsArchived, entityManager);
        if (redundantSnapshots.hasNext()) {
            Long sequenceOfFirstSnapshotToPrune = redundantSnapshots.next();
            entityManager.createQuery("DELETE FROM SnapshotEventEntry e "
                                              + "WHERE e.type = :type "
                                              + "AND e.aggregateIdentifier = :aggregateIdentifier "
                                              + "AND e.sequenceNumber <= :sequenceOfFirstSnapshotToPrune")
                         .setParameter("type", type)
                         .setParameter("aggregateIdentifier",
                                       mostRecentSnapshotEvent.getAggregateIdentifier().asString())
                         .setParameter("sequenceOfFirstSnapshotToPrune", sequenceOfFirstSnapshotToPrune)
                         .executeUpdate();
        }
    }

    /**
     * Finds the first of redundant snapshots, returned as an iterator for convenience purposes.
     *
     * @param type                 the type of the aggregate for which to find redundant snapshots
     * @param snapshotEvent        the last appended snapshot event
     * @param maxSnapshotsArchived the number of snapshots that may remain archived
     * @param entityManager        the entityManager providing access to the data store
     * @return an iterator over the snapshots found
     */
    @SuppressWarnings({"unchecked"})
    private Iterator<Long> findRedundantSnapshots(String type, DomainEvent snapshotEvent, int maxSnapshotsArchived,
                                                  EntityManager entityManager) {
        return entityManager.createQuery(
                "SELECT e.sequenceNumber FROM SnapshotEventEntry e "
                        + "WHERE e.type = :type AND e.aggregateIdentifier = :aggregateIdentifier "
                        + "ORDER BY e.sequenceNumber DESC")
                            .setParameter("type", type)
                            .setParameter("aggregateIdentifier", snapshotEvent.getAggregateIdentifier().asString())
                            .setFirstResult(maxSnapshotsArchived)
                            .setMaxResults(1)
                            .getResultList().iterator();
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public List<byte[]> fetchBatch(String aggregateType, AggregateIdentifier identifier, long firstSequenceNumber,
                                   int batchSize, EntityManager entityManager) {
        return (List<byte[]>) entityManager.createQuery(
                "SELECT e.serializedEvent "
                        + "FROM DomainEventEntry e "
                        + "WHERE e.aggregateIdentifier = :id AND e.type = :type AND e.sequenceNumber >= :seq "
                        + "ORDER BY e.sequenceNumber ASC")
                                           .setParameter("id",
                                                         identifier.asString())
                                           .setParameter("type", aggregateType)
                                           .setParameter("seq", firstSequenceNumber)
                                           .setMaxResults(batchSize)
                                           .getResultList();
    }
}
