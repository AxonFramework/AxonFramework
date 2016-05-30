/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.eventsourcing.eventstore.mongo;

import com.mongodb.MongoException;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.BatchingEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.DomainEventData;
import org.axonframework.eventsourcing.eventstore.TrackedEventData;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.eventsourcing.eventstore.mongo.documentperevent.DocumentPerEventStorageStrategy;
import org.axonframework.serialization.Serializer;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;

/**
 * <p>Implementation of the <code>EventStore</code> based on a MongoDB instance or replica set. Sharding and pairing are
 * not explicitly supported.</p> <p>This event store implementation needs a serializer as well as a {@link
 * MongoTemplate} to interact with the mongo database.</p> <p><strong>Warning:</strong> This implementation is still in
 * progress and may be subject to alterations. The implementation works, but has not been optimized to fully leverage
 * MongoDB's features, yet.</p>
 *
 * @author Jettro Coenradie
 * @author Rene de Waele
 */
public class MongoEventStorageEngine extends BatchingEventStorageEngine {

    private final MongoTemplate template;
    private final org.axonframework.eventsourcing.eventstore.mongo.StorageStrategy storageStrategy;

    /**
     * Constructor that accepts only a MongoTemplate. A Document-Per-Event storage strategy is used, causing each event
     * to be stored in a separate Mongo Document.
     *
     * @param template MongoTemplate instance to obtain the database and the collections.
     */
    public MongoEventStorageEngine(MongoTemplate template) {
        this(template, new DocumentPerEventStorageStrategy());
    }

    /**
     * Constructor that accepts a MongoTemplate and a custom StorageStrategy.
     *
     * @param template        The template giving access to the required collections
     * @param storageStrategy The strategy for storing and retrieving events from the collections
     */
    public MongoEventStorageEngine(MongoTemplate template, org.axonframework.eventsourcing.eventstore.mongo.StorageStrategy storageStrategy) {
        this.template = template;
        this.storageStrategy = storageStrategy;
        setPersistenceExceptionResolver(exception -> exception instanceof MongoException.DuplicateKey);
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
        storageStrategy.deleteSnapshots(template.snapshotCollection(), snapshot.getAggregateIdentifier());
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
    protected List<? extends DomainEventData<?>> fetchBatch(String aggregateIdentifier, long firstSequenceNumber,
                                                            int batchSize) {
        return storageStrategy
                .findDomainEvents(template.eventCollection(), aggregateIdentifier, firstSequenceNumber, batchSize);
    }

    @Override
    protected List<? extends TrackedEventData<?>> fetchBatch(TrackingToken lastToken, int batchSize) {
        return storageStrategy.findTrackedEvents(template.eventCollection(), lastToken, batchSize);
    }
}
