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
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.eventstore.EventVisitor;
import org.axonframework.eventstore.SnapshotEventStore;
import org.axonframework.eventstore.management.Criteria;
import org.axonframework.eventstore.management.EventStoreManagement;
import org.axonframework.eventstore.mongo.criteria.MongoCriteria;
import org.axonframework.eventstore.mongo.criteria.MongoCriteriaBuilder;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.XStreamSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.PostConstruct;

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
    private final Serializer eventSerializer;

    /**
     * Constructor that accepts a Serializer and the MongoTemplate.
     *
     * @param eventSerializer Your own Serializer
     * @param mongo           Mongo instance to obtain the database and the collections.
     */
    public MongoEventStore(Serializer eventSerializer, MongoTemplate mongo) {
        this.eventSerializer = eventSerializer;
        this.mongoTemplate = mongo;
    }

    /**
     * Constructor that uses the default Serializer.
     *
     * @param mongo MongoTemplate instance to obtain the database and the collections.
     */
    public MongoEventStore(MongoTemplate mongo) {
        this(new XStreamSerializer(), mongo);
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
    public DomainEventStream readEvents(String type, Object identifier) {
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
        visitEvents(null, visitor);
    }

    @Override
    public void visitEvents(Criteria criteria, EventVisitor visitor) {
        int first = 0;
        List<EventEntry> batch;
        boolean shouldContinue = true;
        DBObject filter = criteria == null ? null : ((MongoCriteria) criteria).asMongoObject();
        while (shouldContinue) {
            batch = fetchBatch(first, EVENT_VISITOR_BATCH_SIZE, filter);
            for (EventEntry entry : batch) {
                visitor.doWithEvent(entry.getDomainEvent(eventSerializer));
            }
            shouldContinue = (batch.size() >= EVENT_VISITOR_BATCH_SIZE);
            first += EVENT_VISITOR_BATCH_SIZE;
        }
    }

    private List<EventEntry> fetchBatch(int startPosition, int batchSize, DBObject filter) {
        DBObject sort = BasicDBObjectBuilder.start()
                                            .add(EventEntry.TIME_STAMP_PROPERTY, -1)
                                            .add(EventEntry.SEQUENCE_NUMBER_PROPERTY, -1)
                                            .get();
        DBCursor batchDomainEvents = mongoTemplate.domainEventCollection().find(filter).sort(sort).limit(batchSize)
                                                  .skip(startPosition);
        List<EventEntry> entries = new ArrayList<EventEntry>();
        while (batchDomainEvents.hasNext()) {
            DBObject dbObject = batchDomainEvents.next();
            entries.add(new EventEntry(dbObject));
        }
        return entries;
    }

    @Override
    public MongoCriteriaBuilder newCriteriaBuilder() {
        return new MongoCriteriaBuilder();
    }

    private List<DomainEventMessage> readEventSegmentInternal(String type, Object identifier,
                                                              long firstSequenceNumber) {

        DBCursor dbCursor = mongoTemplate.domainEventCollection()
                                         .find(EventEntry.forAggregate(type, identifier.toString(),
                                                                       firstSequenceNumber))
                                         .sort(new BasicDBObject(EventEntry.SEQUENCE_NUMBER_PROPERTY, "1"));
        List<DomainEventMessage> events = new ArrayList<DomainEventMessage>(dbCursor.size());
        while (dbCursor.hasNext()) {
            events.add(new EventEntry(dbCursor.next()).asDomainEventMessage(eventSerializer));
        }
        return events;
    }

    private EventEntry loadLastSnapshotEvent(String type, Object identifier) {
        DBObject mongoEntry = BasicDBObjectBuilder.start()
                                                  .add(EventEntry.AGGREGATE_IDENTIFIER_PROPERTY, identifier.toString())
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
}
