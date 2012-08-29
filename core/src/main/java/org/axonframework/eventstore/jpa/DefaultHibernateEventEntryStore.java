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

import org.axonframework.serializer.SerializedDomainEventData;
import org.hibernate.Query;
import org.hibernate.Session;
import org.joda.time.DateTime;

import java.util.Iterator;
import java.util.Map;
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
public class DefaultHibernateEventEntryStore extends DefaultEventEntryStore {

    /**
     * Returns an implementation of the DefaultEventEntryStore. If Hibernate is found on the classpath, it returns the
     * DefaultHibernateEventEntryStore, which contains specialized implementation for better performance. Otherwise, it
     * returns a pure JPA implementation (<code>DefaultEventEntryStore</code>).
     * <p/>
     * Note that this check fails if Hibernate is on the classpath, but is not the JPA implementation being used.
     *
     * @return an EventEntryStore instance compatible with the current JPA Provider
     */
    public static EventEntryStore getSupportedImplementation() {
        boolean hibernateSupported;
        try {
            hibernateSupported = Session.class != null;
        } catch (NoClassDefFoundError e) {
            hibernateSupported = false;
        }
        if (hibernateSupported) {
            return new DefaultHibernateEventEntryStore();
        } else {
            return new DefaultEventEntryStore();
        }
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public Iterator<SerializedDomainEventData> fetchFiltered(String whereClause, Map<String, Object> parameters,
                                                             int batchSize, EntityManager entityManager) {
        try {
            final Session unwrap = entityManager.unwrap(Session.class);
            Query query = unwrap.createQuery(
                    String.format("SELECT new org.axonframework.eventstore.jpa.SimpleSerializedDomainEventData("
                                          + "e.eventIdentifier, e.aggregateIdentifier, e.sequenceNumber, "
                                          + "e.timeStamp, e.payloadType, e.payloadRevision, e.payload, e.metaData) "
                                          + "FROM DomainEventEntry e %s ORDER BY e.timeStamp ASC, e.sequenceNumber ASC",
                                  whereClause != null && whereClause.length() > 0 ? "WHERE " + whereClause : ""))
                                .setFetchSize(batchSize);
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof DateTime) {
                    value = entry.getValue().toString();
                }
                query.setParameter(entry.getKey(), value);
            }
            return query.iterate();
        } catch (RuntimeException e) {
            return super.fetchFiltered(whereClause, parameters, batchSize, entityManager);
        }
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public Iterator<SerializedDomainEventData> fetchAggregateStream(String aggregateType, Object identifier,
                                                                    long firstSequenceNumber,
                                                                    int batchSize, EntityManager entityManager) {
        try {
            final Session session = entityManager.unwrap(Session.class);
            return session.createQuery(
                    "SELECT new org.axonframework.eventstore.jpa.SimpleSerializedDomainEventData("
                            + "e.eventIdentifier, e.aggregateIdentifier, e.sequenceNumber, "
                            + "e.timeStamp, e.payloadType, e.payloadRevision, e.payload, e.metaData) "
                            + "FROM DomainEventEntry e "
                            + "WHERE e.aggregateIdentifier = :id AND e.type = :type "
                            + "AND e.sequenceNumber >= :seq "
                            + "ORDER BY e.sequenceNumber ASC")
                          .setParameter("id", identifier.toString())
                          .setParameter("type", aggregateType)
                          .setParameter("seq", firstSequenceNumber)
                          .setFetchSize(batchSize)
                          .iterate();
        } catch (RuntimeException e) {
            return super.fetchAggregateStream(aggregateType, identifier, firstSequenceNumber, batchSize, entityManager);
        }
    }
}
