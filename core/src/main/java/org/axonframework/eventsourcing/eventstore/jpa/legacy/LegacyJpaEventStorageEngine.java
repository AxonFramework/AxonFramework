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
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.eventstore.TrackedEventData;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.legacy.LegacyTrackingToken;
import org.axonframework.serialization.Serializer;

import javax.persistence.Query;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.axonframework.eventsourcing.eventstore.EventUtils.asDomainEventMessage;

/**
 * @author Rene de Waele
 */
public class LegacyJpaEventStorageEngine extends JpaEventStorageEngine {
    public LegacyJpaEventStorageEngine(EntityManagerProvider entityManagerProvider) {
        super(entityManagerProvider);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<? extends TrackedEventData<?>> fetchBatch(TrackingToken lastToken, int batchSize) {
        Assert.isTrue(lastToken == null || lastToken instanceof LegacyTrackingToken,
                      String.format("Token %s is of the wrong type", lastToken));
        Map<String, Object> paramRegistry = new HashMap<>();
        Query query = entityManager().createQuery(
                String.format("SELECT new org.axonframework.eventsourcing.eventstore.legacy.GenericLegacyDomainEventEntry("
                                      + "e.type, e.aggregateIdentifier, e.sequenceNumber, e.eventIdentifier, "
                                      + "e.timeStamp, e.payloadType, e.payloadRevision, e.metaData, e.payload)"
                                      + "FROM %s e %s"
                                      + "ORDER BY e.timeStamp ASC, e.sequenceNumber ASC, e.aggregateIdentifier ASC",
                              domainEventEntryEntityName(),
                              buildWhereClause((LegacyTrackingToken) lastToken, paramRegistry))
        );
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
    protected Object createEventEntity(EventMessage<?> eventMessage, Serializer serializer) {
        return new LegacyDomainEventEntry(asDomainEventMessage(eventMessage), serializer);
    }

    @Override
    protected String domainEventEntryEntityName() {
        return LegacyDomainEventEntry.class.getSimpleName();
    }
}
