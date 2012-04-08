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
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.eventstore.EventVisitor;
import org.axonframework.eventstore.SnapshotEventStore;
import org.axonframework.eventstore.management.Criteria;
import org.axonframework.eventstore.management.EventStoreManagement;
import org.axonframework.eventstore.mongo.criteria.MongoCriteria;
import org.axonframework.eventstore.mongo.criteria.MongoCriteriaBuilder;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.xml.XStreamSerializer;
import org.axonframework.upcasting.SimpleUpcasterChain;
import org.axonframework.upcasting.UpcasterAware;
import org.axonframework.upcasting.UpcasterChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.annotation.PostConstruct;

/**
 * <p>Implementation of the <code>EventStore</code> based on a MongoDB instance or replica set. Sharding and pairing
 * are not explicitly supported.</p> <p>This event store implementation needs a serializer as well as a {@see
 * MongoTemplate} to interact with the mongo database.</p> <p><strong>Warning:</strong> This implementation is
 * still in progress and may be subject to alterations. The implementation works, but has not been optimized to fully
 * leverage MongoDB's features, yet.</p>
 *
 * @author Jettro Coenradie
 * @since 2.0 (in incubator since 0.7)
 */
public class MongoEventStore implements SnapshotEventStore, EventStoreManagement, UpcasterAware {

    private static final Logger logger = LoggerFactory.getLogger(MongoEventStore.class);
    private static final String ORDER_ASC = "1";
    private static final String ORDER_DESC = "-1";

    private final MongoTemplate mongoTemplate;

    private final Serializer eventSerializer;
    private UpcasterChain upcasterChain = SimpleUpcasterChain.EMPTY;

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

        final DBCursor dbCursor = mongoTemplate.domainEventCollection()
                                               .find(EventEntry.forAggregate(type, identifier.toString(),
                                                                             snapshotSequenceNumber + 1))
                                               .sort(new BasicDBObject(EventEntry.SEQUENCE_NUMBER_PROPERTY, ORDER_ASC));
        if (!dbCursor.hasNext() && lastSnapshotEvent == null) {
            throw new EventStreamNotFoundException(type, identifier);
        }
        return new CursorBackedDomainEventStream(dbCursor, lastSnapshotEvent);
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
        DBObject filter = criteria == null ? null : ((MongoCriteria) criteria).asMongoObject();
        DBObject sort = BasicDBObjectBuilder.start()
                                            .add(EventEntry.TIME_STAMP_PROPERTY, ORDER_ASC)
                                            .add(EventEntry.SEQUENCE_NUMBER_PROPERTY, ORDER_ASC)
                                            .get();
        DBCursor batchDomainEvents = mongoTemplate.domainEventCollection().find(filter).sort(sort);
        CursorBackedDomainEventStream events = new CursorBackedDomainEventStream(batchDomainEvents, null);
        while (events.hasNext()) {
            visitor.doWithEvent(events.next());
        }
    }

    @Override
    public MongoCriteriaBuilder newCriteriaBuilder() {
        return new MongoCriteriaBuilder();
    }

    private EventEntry loadLastSnapshotEvent(String type, Object identifier) {
        DBObject mongoEntry = BasicDBObjectBuilder.start()
                                                  .add(EventEntry.AGGREGATE_IDENTIFIER_PROPERTY, identifier.toString())
                                                  .add(EventEntry.AGGREGATE_TYPE_PROPERTY, type)
                                                  .get();
        DBCursor dbCursor = mongoTemplate.snapshotEventCollection()
                                         .find(mongoEntry)
                                         .sort(new BasicDBObject(EventEntry.SEQUENCE_NUMBER_PROPERTY, ORDER_DESC))
                                         .limit(1);

        if (!dbCursor.hasNext()) {
            return null;
        }
        DBObject first = dbCursor.next();

        return new EventEntry(first);
    }

    @Override
    public void setUpcasterChain(UpcasterChain upcasterChain) {
        this.upcasterChain = upcasterChain;
    }

    private class CursorBackedDomainEventStream implements DomainEventStream {

        private Iterator<DomainEventMessage> messagesToReturn = Collections.emptyIterator();
        private DomainEventMessage next;
        private final DBCursor dbCursor;

        /**
         * Initializes the DomainEventStream, streaming events obtained from the given <code>dbCursor</code> and
         * optionally the given <code>lastSnapshotEvent</code>.
         *
         * @param dbCursor          The cursor providing access to the query results in the Mongo instance
         * @param lastSnapshotEvent The last snapshot event read, or <code>null</code> if no snapshot is available
         */
        public CursorBackedDomainEventStream(DBCursor dbCursor, EventEntry lastSnapshotEvent) {
            this.dbCursor = dbCursor;
            if (lastSnapshotEvent != null) {
                messagesToReturn = lastSnapshotEvent.getDomainEvents(eventSerializer, upcasterChain).iterator();
            }
            initializeNextItem();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public DomainEventMessage next() {
            DomainEventMessage itemToReturn = next;
            initializeNextItem();
            return itemToReturn;
        }

        @Override
        public DomainEventMessage peek() {
            return next;
        }

        /**
         * Ensures that the <code>next</code> points to the correct item, possibly reading from the dbCursor.
         */
        private void initializeNextItem() {
            while (!messagesToReturn.hasNext() && dbCursor.hasNext()) {
                messagesToReturn = new EventEntry(dbCursor.next()).getDomainEvents(eventSerializer, upcasterChain)
                                                                  .iterator();
            }
            next = messagesToReturn.hasNext() ? messagesToReturn.next() : null;
        }
    }
}
