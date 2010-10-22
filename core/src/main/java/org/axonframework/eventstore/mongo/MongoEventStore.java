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

package org.axonframework.eventstore.mongo;

import com.mongodb.*;
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventstore.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.axonframework.eventstore.mongo.EventEntry.UTF8;

/**
 * Implementation of the <code>EventStore</code> based on a MongoDB instance or replica set. Sharding and pairing are
 * not explicitly supported.
 * <p/>
 * <strong>Warning:</strong> This implementation is still in progress and may be subject to alterations. The
 * implementation works, but has not been optimized to fully leverage MongoDB's features, yet.
 *
 * @author Jettro Coenradie
 * @since 0.7
 */
public class MongoEventStore implements SnapshotEventStore, EventStoreManagement {

    private static final Logger logger = LoggerFactory.getLogger(MongoEventStore.class);

    private static final int EVENT_VISITOR_BATCH_SIZE = 50;

    private final MongoTemplate mongoTemplate;
    private final EventSerializer eventSerializer;
    private final AtomicBoolean indexesAssured = new AtomicBoolean(false);

    /**
     * Constructor that accepts an EventSerializer, the MongoTemplate and a string containing the testContext. The
     * TestContext can be Null. Provide true in case of the test context.
     *
     * @param eventSerializer Your own EventSerializer
     * @param mongo           Mongo instance to obtain the database and the collections.
     */
    public MongoEventStore(EventSerializer eventSerializer, Mongo mongo) {
        this.eventSerializer = eventSerializer;
        this.mongoTemplate = new MongoTemplate(mongo);
    }

    /**
     * Constructor that uses the default EventSerializer.
     *
     * @param mongo Mongo instance to obtain the database and the collections.
     */
    public MongoEventStore(Mongo mongo) {
        this(new XStreamEventSerializer(), mongo);
    }

    /**
     * Make sure an index is created on the collection that stores domain events
     */
    @PostConstruct
    public void ensureIndexes() {
        if (indexesAssured.compareAndSet(false, true)) {
            mongoTemplate.domainEventCollection().ensureIndex(EventEntry.INDEX, "uniqueAggregateIndex", true);
        }
    }

    @Override
    public void appendEvents(String type, DomainEventStream events) {
        ensureIndexes();

        List<DBObject> entries = new ArrayList<DBObject>();
        while (events.hasNext()) {
            DomainEvent event = events.next();
            EventEntry entry = new EventEntry(type, event, eventSerializer);
            entries.add(entry.asDBObject());
        }
        mongoTemplate.domainEventCollection().insert(entries.toArray(new DBObject[entries.size()]));

        if (logger.isDebugEnabled()) {
            logger.debug("{} events of type {} appended", new Object[]{entries.size(), type});
        }
    }

    @Override
    public DomainEventStream readEvents(String type, AggregateIdentifier identifier) {
        StringBuilder sb = new StringBuilder(250);
        long start = new Date().getTime();

        long snapshotSequenceNumber = -1;
        EventEntry lastSnapshotEvent = loadLastSnapshotEvent(type, identifier);
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
            throw new EventStreamNotFoundException(type, identifier);
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
        EventEntry snapshotEventEntry = new EventEntry(type, snapshotEvent, eventSerializer);
        mongoTemplate.snapshotEventCollection().insert(snapshotEventEntry.asDBObject());
    }

    @Override
    public void visitEvents(EventVisitor visitor) {
        int first = 0;
        List<EventEntry> batch;
        boolean shouldContinue = true;
        while (shouldContinue) {
            batch = fetchBatch(first, EVENT_VISITOR_BATCH_SIZE);
            for (EventEntry entry : batch) {
                visitor.doWithEvent(entry.getDomainEvent(eventSerializer));
            }
            shouldContinue = (batch.size() >= EVENT_VISITOR_BATCH_SIZE);
            first += EVENT_VISITOR_BATCH_SIZE;
        }

    }

    private List<DomainEvent> readEventSegmentInternal(String type, AggregateIdentifier identifier,
                                                       long firstSequenceNumber) {

        DBCursor dbCursor = mongoTemplate.domainEventCollection().find(
                EventEntry.forAggregate(type, identifier.asString(), firstSequenceNumber));
        List<DomainEvent> events = new ArrayList<DomainEvent>(dbCursor.size());
        while (dbCursor.hasNext()) {
            String nextItem = (String) dbCursor.next().get(EventEntry.SERIALIZED_EVENT_PROPERTY);
            DomainEvent deserialize = eventSerializer.deserialize(nextItem.getBytes(UTF8));
            events.add(deserialize);
        }
        return events;
    }

    private EventEntry loadLastSnapshotEvent(String type, AggregateIdentifier identifier) {
        DBObject mongoEntry = BasicDBObjectBuilder.start()
                .add(EventEntry.AGGREGATE_IDENTIFIER_PROPERTY, identifier.asString())
                .add(EventEntry.AGGREGATE_TYPE_PROPERTY, type)
                .get();
        DBCursor dbCursor = mongoTemplate.snapshotEventCollection()
                .find(mongoEntry)
                .sort(new BasicDBObject(EventEntry.SEQUENCE_NUMBER_PROPERTY, -1))
                .limit(1);

        if (!dbCursor.hasNext()) {
            return null;
        }
        DBObject first = dbCursor.next();

        return new EventEntry(first);
    }

    private List<EventEntry> fetchBatch(int startPosition, int batchSize) {
        DBObject sort = BasicDBObjectBuilder.start()
                .add(EventEntry.TIME_STAMP_PROPERTY, -1)
                .add(EventEntry.SEQUENCE_NUMBER_PROPERTY, -1)
                .get();
        DBCursor batchDomainEvents = mongoTemplate.domainEventCollection().find().sort(sort).limit(batchSize).skip(
                startPosition);
        List<EventEntry> entries = new ArrayList<EventEntry>();
        while (batchDomainEvents.hasNext()) {
            DBObject dbObject = batchDomainEvents.next();
            entries.add(new EventEntry(dbObject));
        }
        return entries;
    }

    /**
     * Sets the name of the database in which the event store should create the event collections. Defaults to
     * "axonframework". If no database with the given name exists, it is created.
     *
     * @param databaseName the name of the database where events should be stored
     */
    public void setDatabaseName(String databaseName) {
        mongoTemplate.setDatabaseName(databaseName);
    }

    /**
     * Sets the name of the collection where this event store should store domain events. Defaults to "domainevents".
     * <p/>
     * Note that you should not given this collection the same name as the {@link #setSnapshotEventsCollectionName(String)
     * snapshot events collection}.
     *
     * @param domainEventsCollectionName The name of the collection that stores domain events.
     */
    public void setDomainEventsCollectionName(String domainEventsCollectionName) {
        mongoTemplate.setDomainEventsCollectionName(domainEventsCollectionName);
    }

    /**
     * Sets the name of the collection where this event store should store snapshot events. Defaults to
     * "snapshotevents".
     * <p/>
     * Note that you should not given this collection the same name as the {@link #setDomainEventsCollectionName(String)
     * domain events collection}.
     *
     * @param snapshotEventsCollectionName The name of the collection that stores snapshot events.
     */
    public void setSnapshotEventsCollectionName(String snapshotEventsCollectionName) {
        mongoTemplate.setSnapshotEventsCollectionName(snapshotEventsCollectionName);
    }

}
