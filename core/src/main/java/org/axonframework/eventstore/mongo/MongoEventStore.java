package org.axonframework.eventstore.mongo;

import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventstore.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * <p>This is an implementation of the <code>SnapshotEventStore</code> based on a MongoDB instance or set.</p>
 *
 * @author Jettro Coenradie
 * @since 0.7
 */
public class MongoEventStore implements SnapshotEventStore, EventStoreManagement {
    private static final Logger logger = LoggerFactory.getLogger(MongoEventStore.class);

    private static final String AGGREGATE_IDENTIFIER = "aggregateIdentifier";
    private static final String SEQUENCE_NUMBER = "sequenceNumber";
    private static final String TIME_STAMP = "timeStamp";
    private static final String TYPE = "type";
    private static final String SERIALIZED_EVENT = "serializedEvent";
    private static final int EVENT_VISITOR_BATCH_SIZE = 50;

    private final MongoHelper mongo;
    private final EventSerializer eventSerializer;

    /**
     * Constructor that accepts an EventSerializer, the MongoHelper and a string containing the testContext. The
     * TestContext can be Null. Provide true in case of the test context.
     *
     * @param eventSerializer Your own EventSerializer
     * @param mongo           MongoHelper to obtain the database and the collections.
     */
    public MongoEventStore(EventSerializer eventSerializer, MongoHelper mongo) {
        this.eventSerializer = eventSerializer;
        this.mongo = mongo;
    }

    /**
     * Constructor that uses the default EventSerializer.
     *
     * @param mongo       MongoHelper to obtain the database and the collections.
     */
    public MongoEventStore(MongoHelper mongo) {
        this(new XStreamEventSerializer(), mongo);
    }

    @Override
    public void appendEvents(String type, DomainEventStream events) {
        List<DBObject> entries = new ArrayList<DBObject>();
        while (events.hasNext()) {
            DomainEvent event = events.next();
            DomainEventEntry entry = new DomainEventEntry(type, event, eventSerializer);
            DBObject mongoEntry = createMongoEventEntry(entry);
            entries.add(mongoEntry);
        }
        mongo.domainEvents().insert(entries.toArray(new DBObject[entries.size()]));

        if (logger.isDebugEnabled()) {
            logger.debug("{} events of type {} appended", new Object[]{entries.size(), type});
        }
    }

    @Override
    public DomainEventStream readEvents(String type, AggregateIdentifier identifier) {
        StringBuilder sb = new StringBuilder(250);
        long start = new Date().getTime();

        long snapshotSequenceNumber = -1;
        SnapshotEventEntry lastSnapshotEvent = loadLastSnapshotEvent(type, identifier);
        if (lastSnapshotEvent != null) {
            snapshotSequenceNumber = lastSnapshotEvent.getSequenceNumber();
        }

        sb.append("snapshot : ").append(new Date().getTime() - start);

        List<DomainEvent> events = readEventSegmentInternal(type, identifier, snapshotSequenceNumber + 1);
        if (lastSnapshotEvent != null) {
            events.add(0, lastSnapshotEvent.getDomainEvent(eventSerializer));
        }

        sb.append(", event : ").append(new Date().getTime() - start);
        if (events.isEmpty()) {
            throw new EventStreamNotFoundException(
                    String.format("Aggregate of type [%s] with identifier [%s] cannot be found.",
                            type,
                            identifier.asString()));
        }
        SimpleDomainEventStream simpleDomainEventStream = new SimpleDomainEventStream(events);

        sb.append(", serialize : ").append(new Date().getTime() - start);
        return simpleDomainEventStream;
    }

    public DomainEventStream readEventSegment(String type, AggregateIdentifier identifier, long firstSequenceNumber) {
        return new SimpleDomainEventStream(readEventSegmentInternal(type, identifier, firstSequenceNumber));
    }

    @Override
    public void appendSnapshotEvent(String type, DomainEvent snapshotEvent) {
        SnapshotEventEntry snapshotEventEntry = new SnapshotEventEntry(type, snapshotEvent, eventSerializer);
        DBObject mongoSnapshotEntry = BasicDBObjectBuilder.start()
                .add(AGGREGATE_IDENTIFIER, snapshotEventEntry.getAggregateIdentifier().asString())
                .add(SEQUENCE_NUMBER, snapshotEventEntry.getSequenceNumber())
                .add(SERIALIZED_EVENT, snapshotEventEntry.getSerializedEvent())
                .add(TIME_STAMP, snapshotEventEntry.getTimeStamp().toString())
                .add(TYPE, snapshotEventEntry.getType())
                .get();
        mongo.snapshotEvents().insert(mongoSnapshotEntry);
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

    private DBObject createMongoEventEntry(DomainEventEntry entry) {
        return BasicDBObjectBuilder.start()
                .add(AGGREGATE_IDENTIFIER, entry.getAggregateIdentifier().toString())
                .add(SEQUENCE_NUMBER, entry.getSequenceNumber())
                .add(TIME_STAMP, entry.getTimeStamp().toString())
                .add(TYPE, entry.getType())
                .add(SERIALIZED_EVENT, entry.getSerializedEvent())
                .get();
    }

    private List<DomainEvent> readEventSegmentInternal(String type, AggregateIdentifier identifier, long firstSequenceNumber) {
        DBObject mongoEntry = BasicDBObjectBuilder.start()
                .add(AGGREGATE_IDENTIFIER, identifier.asString())
                .add(SEQUENCE_NUMBER, new BasicDBObject("$gte", firstSequenceNumber))
                .add(TYPE, type)
                .get();

        DBCursor dbCursor = mongo.domainEvents().find(mongoEntry);
        List<DomainEvent> events = new ArrayList<DomainEvent>(dbCursor.size());
        while (dbCursor.hasNext()) {
            byte[] nextItem = (byte[]) dbCursor.next().get(SERIALIZED_EVENT);
            DomainEvent deserialize = eventSerializer.deserialize(nextItem);
            events.add(deserialize);
        }
        return events;
    }


    private SnapshotEventEntry loadLastSnapshotEvent(String type, AggregateIdentifier identifier) {
        DBObject mongoEntry = BasicDBObjectBuilder.start()
                .add(AGGREGATE_IDENTIFIER, identifier.asString())
                .add(TYPE, type)
                .get();
        DBCursor dbCursor = mongo.snapshotEvents().find(mongoEntry).sort(new BasicDBObject(SEQUENCE_NUMBER, -1));

        if (!dbCursor.hasNext()) {
            return null;
        }
        DBObject first = dbCursor.next();
        return new SnapshotEventEntry(
                (String) first.get(AGGREGATE_IDENTIFIER),
                (Long) first.get(SEQUENCE_NUMBER),
                (byte[]) first.get(SERIALIZED_EVENT),
                (String) first.get(TIME_STAMP),
                (String) first.get(TYPE)
        );
    }

    private List<DomainEventEntry> fetchBatch(int startPosition, int batchSize) {
        DBObject sort = BasicDBObjectBuilder.start()
                .add(TIME_STAMP, -1)
                .add(SEQUENCE_NUMBER, -1)
                .get();
        DBCursor batchDomainEvents = mongo.domainEvents().find().sort(sort).limit(batchSize).skip(startPosition);
        List<DomainEventEntry> entries = new ArrayList<DomainEventEntry>();
        while (batchDomainEvents.hasNext()) {
            DBObject dbObject = batchDomainEvents.next();
            DomainEventEntry entry = createDomainEventEntry(dbObject);
            entries.add(entry);
        }
        return entries;
    }

    private DomainEventEntry createDomainEventEntry(DBObject dbObject) {
        return new DomainEventEntry(
                (String) dbObject.get(AGGREGATE_IDENTIFIER),
                (Long) dbObject.get(SEQUENCE_NUMBER),
                (byte[]) dbObject.get(SERIALIZED_EVENT),
                (String) dbObject.get(TIME_STAMP),
                (String) dbObject.get(TYPE)
        );
    }
}
