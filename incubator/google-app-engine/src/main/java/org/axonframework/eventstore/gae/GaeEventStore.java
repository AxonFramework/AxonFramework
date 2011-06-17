package org.axonframework.eventstore.gae;

import com.google.appengine.api.datastore.*;
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventstore.EventSerializer;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.eventstore.SnapshotEventStore;
import org.axonframework.eventstore.XStreamEventSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Jettro Coenradie
 */
public class GaeEventStore implements SnapshotEventStore {
    private static final Logger logger = LoggerFactory.getLogger(GaeEventStore.class);

    private final EventSerializer eventSerializer;
    private final DatastoreService datastoreService;

    public GaeEventStore(EventSerializer eventSerializer) {
        this.eventSerializer = eventSerializer;
        this.datastoreService = DatastoreServiceFactory.getDatastoreService();
    }

    public GaeEventStore() {
        this(new XStreamEventSerializer());
    }

    public void appendEvents(String type, DomainEventStream events) {
        while (events.hasNext()) {
            DomainEvent event = events.next();
            doStoreEvent(type, event);
        }
    }

    public DomainEventStream readEvents(String type, AggregateIdentifier identifier) {
        long snapshotSequenceNumber = -1;
        EventEntry lastSnapshotEvent = loadLastSnapshotEvent(type, identifier);
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

    public void appendSnapshotEvent(String type, DomainEvent snapshotEvent) {
        String snapshotType = "snapshot_" + type;
        doStoreEvent(snapshotType, snapshotEvent);
    }

    private void doStoreEvent(String type, DomainEvent event) {
        EventEntry entry = new EventEntry(type, event, eventSerializer);
        Transaction transaction = datastoreService.beginTransaction();
        try {
            datastoreService.put(transaction, entry.asEntity());
            transaction.commit();
        } finally {
            if (transaction.isActive()) {
                logger.info("Transaction to commit new events is rolled back because");
                transaction.rollback();
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("event of type {} appended", type);
                }
            }
        }
    }

    private List<DomainEvent> readEventSegmentInternal(String type, AggregateIdentifier identifier,
                                                       long firstSequenceNumber) {
        Query query = EventEntry.forAggregate(type, identifier.asString(), firstSequenceNumber);
        PreparedQuery preparedQuery = datastoreService.prepare(query);
        List<Entity> entities = preparedQuery.asList(FetchOptions.Builder.withDefaults());

        List<DomainEvent> events = new ArrayList<DomainEvent>(entities.size());
        for (Entity entity : entities) {
            byte[] bytes = ((Text) entity.getProperty("serializedEvent")).getValue().getBytes(EventEntry.UTF8);
            events.add(eventSerializer.deserialize(bytes));
        }
        return events;
    }

    private EventEntry loadLastSnapshotEvent(String type, AggregateIdentifier identifier) {
        Query query = EventEntry.forLastSnapshot("snapshot_" + type, identifier.asString());
        PreparedQuery preparedQuery = datastoreService.prepare(query);
        Iterator<Entity> entityIterator = preparedQuery.asIterable().iterator();
        if (entityIterator.hasNext()) {
            Entity lastSnapshot = entityIterator.next();
            return new EventEntry(lastSnapshot);
        }
        return null;
    }

}