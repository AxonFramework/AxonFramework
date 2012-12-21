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

package org.axonframework.eventstore.mongo;

import com.mongodb.Bytes;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoException;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.eventstore.EventVisitor;
import org.axonframework.eventstore.SnapshotEventStore;
import org.axonframework.eventstore.management.Criteria;
import org.axonframework.eventstore.management.EventStoreManagement;
import org.axonframework.eventstore.mongo.criteria.MongoCriteria;
import org.axonframework.eventstore.mongo.criteria.MongoCriteriaBuilder;
import org.axonframework.repository.ConcurrencyException;
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
 * are not explicitly supported.</p> <p>This event store implementation needs a serializer as well as a {@link
 * MongoTemplate} to interact with the mongo database.</p> <p><strong>Warning:</strong> This implementation is
 * still in progress and may be subject to alterations. The implementation works, but has not been optimized to fully
 * leverage MongoDB's features, yet.</p>
 *
 * @author Jettro Coenradie
 * @since 2.0 (in incubator since 0.7)
 */
public class MongoEventStore implements SnapshotEventStore, EventStoreManagement, UpcasterAware {

    private static final Logger logger = LoggerFactory.getLogger(MongoEventStore.class);

    private final MongoTemplate mongoTemplate;

    private final Serializer eventSerializer;
    private final StorageStrategy storageStrategy;
    private UpcasterChain upcasterChain = SimpleUpcasterChain.EMPTY;

    /**
     * Constructor that accepts a Serializer and the MongoTemplate. A Document-Per-Event storage strategy is used,
     * causing each event to be stored in a separate Mongo Document.
     *
     * @param eventSerializer Your own Serializer
     * @param mongo           Mongo instance to obtain the database and the collections.
     */
    public MongoEventStore(Serializer eventSerializer, MongoTemplate mongo) {
        this(mongo, eventSerializer, new DocumentPerEventStorageStrategy());
    }

    /**
     * Constructor that uses the default Serializer. A Document-Per-Event storage strategy is used, causing each event
     * to be stored in a separate Mongo Document.
     *
     * @param mongo MongoTemplate instance to obtain the database and the collections.
     */
    public MongoEventStore(MongoTemplate mongo) {
        this(new XStreamSerializer(), mongo);
    }

    /**
     * Constructor that accepts a MongoTemplate and a custom StorageStrategy.
     *
     * @param mongoTemplate   The template giving access to the required collections
     * @param storageStrategy The strategy for storing and retrieving events from the collections
     */
    public MongoEventStore(MongoTemplate mongoTemplate, StorageStrategy storageStrategy) {
        this(mongoTemplate, new XStreamSerializer(), storageStrategy);
    }

    /**
     * Initialize the mongo event store with given <code>mongoTemplate</code>, <code>eventSerializer</code> and
     * <code>storageStrategy</code>.
     *
     * @param mongoTemplate   The template giving access to the required collections
     * @param eventSerializer The serializer to serialize events with
     * @param storageStrategy The strategy for storing and retrieving events from the collections
     */
    public MongoEventStore(MongoTemplate mongoTemplate, Serializer eventSerializer, StorageStrategy storageStrategy) {
        this.eventSerializer = eventSerializer;
        this.mongoTemplate = mongoTemplate;
        this.storageStrategy = storageStrategy;
    }

    /**
     * Make sure an index is created on the collection that stores domain events.
     */
    @PostConstruct
    public void ensureIndexes() {
        storageStrategy.ensureIndexes(mongoTemplate.domainEventCollection(), mongoTemplate.snapshotEventCollection());
    }

    @Override
    public void appendEvents(String type, DomainEventStream events) {
        List<DomainEventMessage> messages = new ArrayList<DomainEventMessage>();
        while (events.hasNext()) {
            messages.add(events.next());
        }

        try {
            mongoTemplate.domainEventCollection().insert(storageStrategy.createDocuments(type, eventSerializer, messages));
        } catch (MongoException.DuplicateKey e) {
            throw new ConcurrencyException("Trying to insert an Event for an aggregate with a sequence "
                                                   + "number that is already present in the Event Store", e);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("{} events appended", new Object[]{messages.size()});
        }
    }

    @Override
    public DomainEventStream readEvents(String type, Object identifier) {
        long snapshotSequenceNumber = -1;
        List<DomainEventMessage> lastSnapshotCommit = loadLastSnapshotEvent(type, identifier);
        if (lastSnapshotCommit != null && !lastSnapshotCommit.isEmpty()) {
            snapshotSequenceNumber = lastSnapshotCommit.get(0).getSequenceNumber();
        }
        final DBCursor dbCursor = storageStrategy.findEvents(mongoTemplate.domainEventCollection(),
                                                             type,
                                                             identifier.toString(),
                                                             snapshotSequenceNumber + 1);

        if (!dbCursor.hasNext() && lastSnapshotCommit == null) {
            throw new EventStreamNotFoundException(type, identifier);
        }
        return new CursorBackedDomainEventStream(dbCursor, lastSnapshotCommit, identifier);
    }

    @Override
    public void appendSnapshotEvent(String type, DomainEventMessage snapshotEvent) {
        final DBObject dbObject = storageStrategy.createDocuments(type, eventSerializer,
                                                                  Collections.singletonList(snapshotEvent))[0];
        mongoTemplate.snapshotEventCollection().insert(dbObject);
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
        DBCursor cursor = storageStrategy.findEvents(mongoTemplate.domainEventCollection(),
                                                                (MongoCriteria) criteria);
        cursor.addOption(Bytes.QUERYOPTION_NOTIMEOUT);
        CursorBackedDomainEventStream events = new CursorBackedDomainEventStream(cursor, null, null);
        while (events.hasNext()) {
            visitor.doWithEvent(events.next());
        }
    }

    @Override
    public MongoCriteriaBuilder newCriteriaBuilder() {
        return new MongoCriteriaBuilder();
    }

    private List<DomainEventMessage> loadLastSnapshotEvent(String type, Object identifier) {
        DBCursor dbCursor = storageStrategy.findLastSnapshot(mongoTemplate.snapshotEventCollection(),
                                                             type,
                                                             identifier.toString());
        if (!dbCursor.hasNext()) {
            return null;
        }
        DBObject first = dbCursor.next();

        return storageStrategy.extractEventMessages(first, identifier, eventSerializer, upcasterChain);
    }

    @Override
    public void setUpcasterChain(UpcasterChain upcasterChain) {
        this.upcasterChain = upcasterChain;
    }

    private class CursorBackedDomainEventStream implements DomainEventStream {

        private Iterator<DomainEventMessage> messagesToReturn = Collections.<DomainEventMessage>emptyList().iterator();
        private DomainEventMessage next;
        private final DBCursor dbCursor;
        private final Object actualAggregateIdentifier;

        /**
         * Initializes the DomainEventStream, streaming events obtained from the given <code>dbCursor</code> and
         * optionally the given <code>lastSnapshotEvent</code>.
         *
         * @param dbCursor                  The cursor providing access to the query results in the Mongo instance
         * @param lastSnapshotCommit        The last snapshot event read, or <code>null</code> if no snapshot is
         *                                  available
         * @param actualAggregateIdentifier The actual aggregateIdentifier instance used to perform the lookup, or
         *                                  <code>null</code> if unknown
         */
        public CursorBackedDomainEventStream(DBCursor dbCursor, List<DomainEventMessage> lastSnapshotCommit,
                                             Object actualAggregateIdentifier) {
            this.dbCursor = dbCursor;
            this.actualAggregateIdentifier = actualAggregateIdentifier;
            if (lastSnapshotCommit != null) {
                messagesToReturn = lastSnapshotCommit.iterator();
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
                messagesToReturn = storageStrategy.extractEventMessages(dbCursor.next(), actualAggregateIdentifier,
                                                                        eventSerializer, upcasterChain).iterator();
            }
            next = messagesToReturn.hasNext() ? messagesToReturn.next() : null;
        }
    }
}
