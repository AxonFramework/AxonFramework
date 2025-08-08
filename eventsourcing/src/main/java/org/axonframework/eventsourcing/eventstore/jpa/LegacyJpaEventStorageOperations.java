/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.eventsourcing.eventstore.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.GapAwareTrackingToken;

import java.util.List;

/**
 * Contains operations that are used to interact with the Aggregate based JPA event storage database structure.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
record LegacyJpaEventStorageOperations(
        TransactionManager transactionManager,
        EntityManagerProvider entityManagerProvider
) {

    List<Object[]> fetchEvents(GapAwareTrackingToken token, int batchSize) {
        TypedQuery<Object[]> query;
        if (token == null || token.getGaps().isEmpty()) {
            query = entityManager().createQuery(
                    "SELECT "
                            + "e.globalIndex, "
                            + "e.identifier, "
                            + "e.type, "
                            + "e.version, "
                            + "e.payload, "
                            + "e.metadata, "
                            + "e.timestamp, "
                            + "e.aggregateType, "
                            + "e.aggregateIdentifier, "
                            + "e.aggregateSequenceNumber "
                            + "FROM AggregateBasedEventEntry e "
                            + "WHERE e.globalIndex > :token "
                            + "ORDER BY e.globalIndex ASC",
                    Object[].class);
        } else {
            query = entityManager().createQuery(
                    "SELECT "
                            + "e.globalIndex, "
                            + "e.identifier, "
                            + "e.type, "
                            + "e.version, "
                            + "e.payload, "
                            + "e.metadata, "
                            + "e.timestamp, "
                            + "e.aggregateType, "
                            + "e.aggregateIdentifier, "
                            + "e.aggregateSequenceNumber "
                            + "FROM AggregateBasedEventEntry e "
                            + "WHERE e.globalIndex > :token "
                            + "OR e.globalIndex "
                            + "IN :gaps "
                            + "ORDER BY e.globalIndex ASC",
                    Object[].class
            ).setParameter("gaps", token.getGaps());
        }
        return query.setParameter("token", token == null ? -1L : token.getIndex())
                    .setMaxResults(batchSize)
                    .getResultList();
    }

    List<AggregateBasedEventEntry> fetchDomainEvents(String aggregateIdentifier,
                                                     long firstSequenceNumber,
                                                     int batchSize) {
        return entityManager().createQuery(
                                      "SELECT new org.axonframework.eventsourcing.eventstore.jpa.AggregateBasedEventEntry("
                                              + "e.identifier, "
                                              + "e.type, "
                                              + "e.version, "
                                              + "e.payload, "
                                              + "e.metadata, "
                                              + "e.timestamp, "
                                              + "e.aggregateType, "
                                              + "e.aggregateIdentifier, "
                                              + "e.aggregateSequenceNumber"
                                              + ") "
                                              + "FROM AggregateBasedEventEntry e "
                                              + "WHERE e.aggregateIdentifier = :id "
                                              + "AND e.aggregateSequenceNumber >= :seq "
                                              + "ORDER BY e.aggregateSequenceNumber ASC"
                              )
                              .setParameter("id", aggregateIdentifier)
                              .setParameter("seq", firstSequenceNumber)
                              .setMaxResults(batchSize)
                              .getResultList();
    }

    List<Object[]> indexAndTimestampBetweenGaps(GapAwareTrackingToken token) {
        return entityManager()
                .createQuery(
                        "SELECT e.globalIndex, e.timestamp FROM AggregateBasedEventEntry e "
                                + "WHERE e.globalIndex >= :firstGapOffset "
                                + "AND e.globalIndex <= :maxGlobalIndex",
                        Object[].class
                )
                .setParameter("firstGapOffset", token.getGaps().first())
                .setParameter("maxGlobalIndex", token.getGaps().last() + 1L)
                .getResultList();
    }

    private EntityManager entityManager() {
        return entityManagerProvider.getEntityManager();
    }
}
