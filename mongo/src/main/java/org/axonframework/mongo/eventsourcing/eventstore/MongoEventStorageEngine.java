/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.mongo.eventsourcing.eventstore;

import com.mongodb.DuplicateKeyException;
import com.mongodb.MongoBulkWriteException;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.BatchingEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.DomainEventData;
import org.axonframework.eventsourcing.eventstore.TrackedEventData;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.mongo.eventsourcing.eventstore.documentperevent.DocumentPerEventStorageStrategy;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.xml.XStreamSerializer;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;

/**
 * EventStorageEngine implementation that uses Mongo to store and fetch events.
 */
public class MongoEventStorageEngine extends BatchingEventStorageEngine {

    private final MongoTemplate template;
    private final StorageStrategy storageStrategy;

    /**
     * Initializes an EventStorageEngine that uses Mongo to store and load events. A Document-Per-Event storage strategy
     * is used, causing each event to be stored in a separate Mongo Document.
     * <p>
     * The payload and metadata of events is stored as a serialized blob of bytes using a new {@link XStreamSerializer}.
     * Events are read in batches of 100. No upcasting is performed after the events have been fetched.
     *
     * @param template MongoTemplate instance to obtain the database and the collections.
     */
    public MongoEventStorageEngine(MongoTemplate template) {
        this(null, null, template, new DocumentPerEventStorageStrategy());
    }

    /**
     * Initializes an EventStorageEngine that uses Mongo to store and load events. Events are fetched in batches of 100.
     *
     * @param serializer      Used to serialize and deserialize event payload and metadata.
     * @param upcasterChain   Allows older revisions of serialized objects to be deserialized.
     * @param template        MongoTemplate instance to obtain the database and the collections.
     * @param storageStrategy The strategy for storing and retrieving events from the collections
     */
    public MongoEventStorageEngine(Serializer serializer, EventUpcaster upcasterChain, MongoTemplate template,
                                   StorageStrategy storageStrategy) {
        this(serializer, upcasterChain, null, template, storageStrategy);
    }

    /**
     * Initializes an EventStorageEngine that uses Mongo to store and load events.
     *
     * @param serializer      Used to serialize and deserialize event payload and metadata.
     * @param upcasterChain   Allows older revisions of serialized objects to be deserialized.
     * @param batchSize       The number of events that should be read at each database access. When more than this
     *                        number of events must be read to rebuild an aggregate's state, the events are read in
     *                        batches of this size. Tip: if you use a snapshotter, make sure to choose snapshot trigger
     *                        and batch size such that a single batch will generally retrieve all events required to
     *                        rebuild an aggregate's state.
     * @param template        MongoTemplate instance to obtain the database and the collections.
     * @param storageStrategy The strategy for storing and retrieving events from the collections
     */
    public MongoEventStorageEngine(Serializer serializer, EventUpcaster upcasterChain, Integer batchSize,
                                   MongoTemplate template, StorageStrategy storageStrategy) {
        super(serializer, upcasterChain, MongoEventStorageEngine::isDuplicateKeyException, batchSize);
        this.template = template;
        this.storageStrategy = storageStrategy;
    }

    /**
     * Initializes an EventStorageEngine that uses Mongo to store and load events.
     *
     * @param serializer                   Used to serialize and deserialize event payload and metadata.
     * @param upcasterChain                Allows older revisions of serialized objects to be deserialized.
     * @param persistenceExceptionResolver Custom resolver of persistence errors
     * @param batchSize                    The number of events that should be read at each database access. When more
     *                                     than this number of events must be read to rebuild an aggregate's state, the
     *                                     events are read in batches of this size. Tip: if you use a snapshotter, make
     *                                     sure to choose snapshot trigger and batch size such that a single batch will
     *                                     generally retrieve all events required to rebuild an aggregate's state.
     * @param template                     MongoTemplate instance to obtain the database and the collections.
     * @param storageStrategy              The strategy for storing and retrieving events from the collections
     */
    public MongoEventStorageEngine(Serializer serializer, EventUpcaster upcasterChain,
                                   PersistenceExceptionResolver persistenceExceptionResolver, Integer batchSize,
                                   MongoTemplate template, StorageStrategy storageStrategy) {
        super(serializer, upcasterChain, persistenceExceptionResolver, batchSize);
        this.template = template;
        this.storageStrategy = storageStrategy;
    }

    private static boolean isDuplicateKeyException(Exception exception) {
        return exception instanceof DuplicateKeyException || (exception instanceof MongoBulkWriteException &&
                ((MongoBulkWriteException) exception).getWriteErrors().stream().anyMatch(e -> e.getCode() == 11000));
    }

    /**
     * Make sure an index is created on the collection that stores domain events.
     */
    @PostConstruct
    public void ensureIndexes() {
        storageStrategy.ensureIndexes(template.eventCollection(), template.snapshotCollection());
    }

    @Override
    protected void appendEvents(List<? extends EventMessage<?>> events, Serializer serializer) {
        if (!events.isEmpty()) {
            try {
                storageStrategy.appendEvents(template.eventCollection(), events, serializer);
            } catch (Exception e) {
                handlePersistenceException(e, events.get(0));
            }
        }
    }

    @Override
    protected void storeSnapshot(DomainEventMessage<?> snapshot, Serializer serializer) {
        try {
            storageStrategy.appendSnapshot(template.snapshotCollection(), snapshot, serializer);
        } catch (Exception e) {
            handlePersistenceException(e, snapshot);
        }
    }

    @Override
    protected Optional<? extends DomainEventData<?>> readSnapshotData(String aggregateIdentifier) {
        return storageStrategy.findLastSnapshot(template.snapshotCollection(), aggregateIdentifier);
    }

    @Override
    protected List<? extends DomainEventData<?>> fetchDomainEvents(String aggregateIdentifier, long firstSequenceNumber,
                                                                   int batchSize) {
        return storageStrategy
                .findDomainEvents(template.eventCollection(), aggregateIdentifier, firstSequenceNumber, batchSize);
    }

    @Override
    protected List<? extends TrackedEventData<?>> fetchTrackedEvents(TrackingToken lastToken, int batchSize) {
        return storageStrategy.findTrackedEvents(template.eventCollection(), lastToken, batchSize);
    }
}
