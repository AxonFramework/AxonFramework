/*
 * Copyright (c) 2010. Axon Framework
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
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventstore.EventSerializer;
import org.axonframework.eventstore.EventStoreManagement;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.eventstore.EventVisitor;
import org.axonframework.eventstore.SnapshotEventStore;
import org.axonframework.eventstore.XStreamEventSerializer;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

/**
 * An EventStore implementation that uses JPA to store DomainEvents in a database. The actual DomainEvent is stored as a
 * serialized blob of bytes. Other columns are used to store meta-data that allow quick finding of DomainEvents for a
 * specific aggregate in the correct order.
 * <p/>
 * The serializer used to serialize the events is configurable. By default, the {@link XStreamEventSerializer} is used.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class JpaEventStore implements SnapshotEventStore, EventStoreManagement {

    private EntityManager entityManager;

    private final EventSerializer eventSerializer;
    private static final int EVENT_VISITOR_BATCH_SIZE = 50;

    /**
     * Initialize a JpaEventStore using an {@link org.axonframework.eventstore.XStreamEventSerializer}, which serializes
     * events as XML.
     */
    public JpaEventStore() {
        this(new XStreamEventSerializer());
    }

    /**
     * Initialize a JpaEventStore which serializes events using the given {@link org.axonframework.eventstore.EventSerializer}.
     *
     * @param eventSerializer The serializer to (de)serialize domain events with.
     */
    public JpaEventStore(EventSerializer eventSerializer) {
        this.eventSerializer = eventSerializer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendEvents(String type, DomainEventStream events) {
        while (events.hasNext()) {
            DomainEvent event = events.next();
            DomainEventEntry entry = new DomainEventEntry(type, event, eventSerializer);
            entityManager.persist(entry);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DomainEventStream readEvents(String type, AggregateIdentifier identifier) {
        long snapshotSequenceNumber = -1;
        SnapshotEventEntry lastSnapshotEvent = loadLastSnapshotEvent(type, identifier);
        if (lastSnapshotEvent != null) {
            snapshotSequenceNumber = lastSnapshotEvent.getSequenceNumber();
        }

        List<DomainEvent> events = readEventSegmentInternal(type, identifier, snapshotSequenceNumber + 1);
        if (lastSnapshotEvent != null) {
            events.add(0, lastSnapshotEvent.getDomainEvent(eventSerializer));
        }
        if (events.isEmpty()) {
            throw new EventStreamNotFoundException(type, identifier);
        }
        return new SimpleDomainEventStream(events);
    }

    /**
     * Reads a segment of the events of an aggregate. The sequence number of the first event in de the domain event
     * stream is equal to the given <code>firstSequenceNumber</code>, if any. If no events with sequence number equal or
     * greater to the <code>firstSequenceNumber</code> are available, an empty DomainEventStream is returned.
     * <p/>
     * The DomainEventStream returned by this call will <em>never</em> contain any snapshot events.
     * <p/>
     * Note: To return all events after the <code>firstSequenceNumber</code>, use <code>Long.MAX_VALUE</code> as
     * <code>lastSequenceNumber</code>.
     *
     * @param type                The type descriptor of the object to retrieve
     * @param identifier          The unique aggregate identifier of the events to load
     * @param firstSequenceNumber The sequence number of the first event to return
     * @return a DomainEventStream containing a segment of past events of an aggregate
     */
    public DomainEventStream readEventSegment(String type, AggregateIdentifier identifier, long firstSequenceNumber) {
        return new SimpleDomainEventStream(readEventSegmentInternal(type, identifier, firstSequenceNumber));
    }

    @SuppressWarnings({"unchecked"})
    private List<DomainEvent> readEventSegmentInternal(String type, AggregateIdentifier identifier,
                                                       long firstSequenceNumber) {
        List<DomainEventEntry> entries = (List<DomainEventEntry>) entityManager.createQuery(
                "SELECT e FROM DomainEventEntry e "
                        + "WHERE e.aggregateIdentifier = :id AND e.type = :type AND e.sequenceNumber >= :seq "
                        + "ORDER BY e.sequenceNumber ASC")
                .setParameter("id", identifier.asString())
                .setParameter("type", type)
                .setParameter("seq", firstSequenceNumber)
                .getResultList();
        List<DomainEvent> events = new ArrayList<DomainEvent>(entries.size());
        for (DomainEventEntry entry : entries) {
            events.add(entry.getDomainEvent(eventSerializer));
        }
        return events;
    }

    @SuppressWarnings({"unchecked"})
    private SnapshotEventEntry loadLastSnapshotEvent(String type, AggregateIdentifier identifier) {
        List<SnapshotEventEntry> entries = entityManager.createQuery(
                "SELECT e FROM SnapshotEventEntry e "
                        + "WHERE e.aggregateIdentifier = :id AND e.type = :type "
                        + "ORDER BY e.sequenceNumber DESC")
                .setParameter("id", identifier.asString())
                .setParameter("type", type)
                .setMaxResults(1)
                .setFirstResult(0)
                .getResultList();
        if (entries.size() < 1) {
            return null;
        }
        return entries.get(0);
    }

    @Override
    public void appendSnapshotEvent(String type, DomainEvent snapshotEvent) {
        entityManager.persist(new SnapshotEventEntry(type, snapshotEvent, eventSerializer));
    }

    @Override
    public void visitEvents(EventVisitor visitor) {
        int first = 0;
        List<DomainEventEntry> batch;
        boolean shouldContinue = true;
        while (shouldContinue) {
            batch = fetchBatch(first, EVENT_VISITOR_BATCH_SIZE);
            for (DomainEventEntry entry : batch) {
                visitor.doWithEvent(entry.getDomainEvent(eventSerializer));
            }
            shouldContinue = (batch.size() >= EVENT_VISITOR_BATCH_SIZE);
            first += EVENT_VISITOR_BATCH_SIZE;
        }
    }

    @SuppressWarnings({"unchecked"})
    private List<DomainEventEntry> fetchBatch(int startPosition, int batchSize) {
        return entityManager.createQuery(
                "SELECT e FROM DomainEventEntry e ORDER BY e.timeStamp ASC, e.sequenceNumber ASC")
                .setFirstResult(startPosition)
                .setMaxResults(batchSize)
                .getResultList();
    }

    /**
     * Sets the EntityManager for this EventStore to use. This EntityManager must be assigned to a persistence context
     * that contains the {@link DomainEventEntry} as one of the managed entity types.
     *
     * @param entityManager the EntityManager to use.
     */
    @PersistenceContext
    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }
}
