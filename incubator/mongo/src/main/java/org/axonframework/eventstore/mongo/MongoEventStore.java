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

package org.axonframework.eventstore.mongo;

import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventstore.EventStoreManagement;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.eventstore.EventVisitor;
import org.axonframework.eventstore.SnapshotEventStore;
import org.axonframework.eventstore.XStreamEventSerializer;
import org.axonframework.serializer.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.PostConstruct;

import static org.axonframework.eventstore.mongo.EventEntry.UTF8;

/**
 * <p>Implementation of the <code>EventStore</code> based on a MongoDB instance or replica set. Sharding and pairing
 * are
 * not explicitly supported.</p> <p/> <p>This event store implementation needs a serializer as well as a {@see
 * MongoTemplate} to interact with the mongo database.</p> <p/> <p><strong>Warning:</strong> This implementation is
 * still in progress and may be subject to alterations. The implementation works, but has not been optimized to fully
 * leverage MongoDB's features, yet.</p>
 *
 * @author Jettro Coenradie
 * @since 0.7
 */
public class MongoEventStore implements SnapshotEventStore, EventStoreManagement {

    private static final Logger logger = LoggerFactory.getLogger(MongoEventStore.class);

    private static final int EVENT_VISITOR_BATCH_SIZE = 50;

    private final MongoTemplate mongoTemplate;
    private final Serializer<? super DomainEventMessage> eventSerializer;

    /**
     * Constructor that accepts a Serializer, the MongoTemplate and a string containing the testContext. The
     * TestContext
     * can be Null. Provide true in case of the test context.
     *
     * @param eventSerializer Your own Serializer
     * @param mongo           Mongo instance to obtain the database and the collections.
     */
    public MongoEventStore(Serializer<? super DomainEventMessage> eventSerializer, MongoTemplate mongo) {
        this.eventSerializer = eventSerializer;
        this.mongoTemplate = mongo;
    }

    /**
     * Constructor that uses the default Serializer.
     *
     * @param mongo MongoTemplate instance to obtain the database and the collections.
     */
    public MongoEventStore(MongoTemplate mongo) {
        this(new XStreamEventSerializer(), mongo);
    }

    /**
     * Make sure an index is created on the collection that stores domain events.
     */
    @PostConstruct
    public void ensureIndexes() {
        mongoTemplate.domainEventCollection().ensureIndex(EventEntry.UNIQUE_INDEX, "uniqueAggregateIndex", true);
    }

    @Override
    public void appendEvents(String type, DomainEventStream events) {
        List<DBObject> entries = new ArrayList<DBObject>();
        while (events.hasNext()) {
            DomainEventMessage event = events.next();
            EventEntry entry = new EventEntry(type, event, eventSerializer);
            entries.add(entry.asDBObject());
        }
        mongoTemplate.domainEventCollection().insert(entries.toArray(new DBObject[entries.size()]));

        if (logger.isDebugEnabled()) {
            logger.debug("{} events appended", new Object[]{entries.size()});
        }
    }

    @Override
    public DomainEventStream readEvents(String type, AggregateIdentifier identifier) {
        long snapshotSequenceNumber = -1;
        EventEntry lastSnapshotEvent = loadLastSnapshotEvent(type, identifier);
        if (lastSnapshotEvent != null) {
            snapshotSequenceNumber = lastSnapshotEvent.getSequenceNumber();
        }

        List<DomainEventMessage> events = readEventSegmentInternal(type, identifier, snapshotSequenceNumber + 1);
        if (lastSnapshotEvent != null) {
            events.add(0, lastSnapshotEvent.getDomainEvent(eventSerializer));
        }

        if (events.isEmpty()) {
            throw new EventStreamNotFoundException(type, identifier);
        }

        return new SimpleDomainEventStream(events);
    }

    @Override
    public void appendSnapshotEvent(String type, DomainEventMessage snapshotEvent) {
        EventEntry snapshotEventEntry = new EventEntry(type, snapshotEvent, eventSerializer);
        mongoTemplate.snapshotEventCollection().insert(snapshotEventEntry.asDBObject());
        if (logger.isDebugEnabled()) {
            logger.debug("snapshot event of type {} appended.");
        }
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

    private List<DomainEventMessage> readEventSegmentInternal(String type, AggregateIdentifier identifier,
                                                              long firstSequenceNumber) {

        DBCursor dbCursor = mongoTemplate.domainEventCollection()
                                         .find(EventEntry.forAggregate(type,
                                                                       identifier.asString(),
                                                                       firstSequenceNumber))
                                         .sort(new BasicDBObject(EventEntry.SEQUENCE_NUMBER_PROPERTY, "1"));
        List<DomainEventMessage> events = new ArrayList<DomainEventMessage>(dbCursor.size());
        while (dbCursor.hasNext()) {
            String nextItem = (String) dbCursor.next().get(EventEntry.SERIALIZED_EVENT_PROPERTY);
            DomainEventMessage deserialize = (DomainEventMessage) eventSerializer.deserialize(nextItem.getBytes(UTF8));
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
}
