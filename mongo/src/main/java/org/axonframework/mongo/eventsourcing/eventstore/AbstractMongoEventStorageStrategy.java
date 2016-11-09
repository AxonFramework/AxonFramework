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

import com.mongodb.BasicDBObject;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import org.axonframework.common.Assert;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventData;
import org.axonframework.eventsourcing.eventstore.TrackedEventData;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.eventsourcing.eventstore.legacy.LegacyTrackingToken;
import org.axonframework.mongo.eventsourcing.eventstore.documentperevent.EventEntryConfiguration;
import org.axonframework.serialization.Serializer;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mongodb.client.model.Filters.*;
import static java.util.stream.StreamSupport.stream;

/**
 * Abstract implementation of a Mongo {@link StorageStrategy}. Implementations only need to provide methods to convert
 * events and snapshots into Documents and vice versa.
 *
 * @author Rene de Waele
 */
public abstract class AbstractMongoEventStorageStrategy implements StorageStrategy {

    protected static final int ORDER_ASC = 1, ORDER_DESC = -1;
    private final EventEntryConfiguration eventConfiguration;

    /**
     * Initializes a new StorageStrategy for a EventStorageEngine that uses Mongo.
     *
     * @param eventConfiguration configuration of the event entry 'schema'
     */
    public AbstractMongoEventStorageStrategy(EventEntryConfiguration eventConfiguration) {
        this.eventConfiguration = eventConfiguration;
    }

    @Override
    public void appendEvents(MongoCollection<Document> eventCollection, List<? extends EventMessage<?>> events,
                             Serializer serializer) {
        eventCollection.insertMany(createEventDocuments(events, serializer).collect(Collectors.toList()));
    }

    protected abstract Stream<Document> createEventDocuments(List<? extends EventMessage<?>> events,
                                                             Serializer serializer);

    @Override
    public void appendSnapshot(MongoCollection<Document> snapshotCollection, DomainEventMessage<?> snapshot,
                               Serializer serializer) {
        snapshotCollection.insertOne(createSnapshotDocument(snapshot, serializer));
    }

    protected abstract Document createSnapshotDocument(DomainEventMessage<?> snapshot, Serializer serializer);

    @Override
    public void deleteSnapshots(MongoCollection<Document> snapshotCollection, String aggregateIdentifier) {
        Bson mongoEntry = new BsonDocument(eventConfiguration.aggregateIdentifierProperty(), new BsonString(aggregateIdentifier));
        snapshotCollection.deleteMany(mongoEntry);
    }

    @Override
    public List<? extends DomainEventData<?>> findDomainEvents(MongoCollection<Document> collection, String aggregateIdentifier,
                                                               long firstSequenceNumber, int batchSize) {
        FindIterable<Document> cursor = collection.find(and(
                eq(eventConfiguration.aggregateIdentifierProperty(), aggregateIdentifier),
                gte(eventConfiguration.sequenceNumberProperty(), firstSequenceNumber)))
                .sort(new BasicDBObject(eventConfiguration().sequenceNumberProperty(), ORDER_ASC));
        cursor = applyBatchSize(cursor, batchSize);
        return stream(cursor.spliterator(), false).flatMap(this::extractDomainEvents)
                .filter(event -> event.getSequenceNumber() >= firstSequenceNumber)
                .collect(Collectors.toList());
    }

    protected abstract Stream<? extends DomainEventData<?>> extractDomainEvents(Document object);

    @Override
    public List<? extends TrackedEventData<?>> findTrackedEvents(MongoCollection<Document> eventCollection,
                                                                 TrackingToken lastToken,
                                                                 int batchSize) {
        FindIterable<Document> cursor;
        if (lastToken == null) {
            cursor = eventCollection.find();
        } else {
            Assert.isTrue(lastToken instanceof LegacyTrackingToken,
                          () -> String.format("Token %s is of the wrong type", lastToken));
            LegacyTrackingToken legacyTrackingToken = (LegacyTrackingToken) lastToken;
            cursor = eventCollection.find(
                    and(gte(eventConfiguration.timestampProperty(), legacyTrackingToken
                                .getTimestamp().toString()),
                        gte(eventConfiguration.sequenceNumberProperty(), legacyTrackingToken.getSequenceNumber())));
        }
        cursor = cursor.sort(new BasicDBObject(eventConfiguration().timestampProperty(), ORDER_ASC)
                                     .append(eventConfiguration().sequenceNumberProperty(), ORDER_ASC));
        cursor = applyBatchSize(cursor, batchSize);
        return stream(cursor.spliterator(), false).flatMap(this::extractTrackedEvents)
                .filter(event -> event.trackingToken().isAfter(lastToken)).limit(batchSize)
                .collect(Collectors.toList());
    }

    protected abstract FindIterable<Document> applyBatchSize(FindIterable<Document> cursor, int batchSize);

    protected abstract Stream<? extends TrackedEventData<?>> extractTrackedEvents(Document object);

    @Override
    public Optional<? extends DomainEventData<?>> findLastSnapshot(MongoCollection<Document> snapshotCollection,
                                                                   String aggregateIdentifier) {
        FindIterable<Document> cursor = snapshotCollection.find(eq(eventConfiguration.aggregateIdentifierProperty(), aggregateIdentifier))
                .sort(new BasicDBObject(eventConfiguration.sequenceNumberProperty(), ORDER_DESC)).limit(1);
            return stream(cursor.spliterator(), false).findFirst().map(this::extractSnapshot);
    }

    protected abstract DomainEventData<?> extractSnapshot(Document object);

    @Override
    public void ensureIndexes(MongoCollection<Document> eventsCollection, MongoCollection<Document> snapshotsCollection) {
        eventsCollection.createIndex(new BasicDBObject(eventConfiguration.aggregateIdentifierProperty(), ORDER_ASC)
                                             .append(eventConfiguration.sequenceNumberProperty(), ORDER_ASC),
                                     new IndexOptions().unique(true).name("uniqueAggregateIndex"));

        eventsCollection.createIndex(new BasicDBObject(eventConfiguration.timestampProperty(), ORDER_ASC)
                                             .append(eventConfiguration.sequenceNumberProperty(), ORDER_ASC),
                                     new IndexOptions().unique(false).name("orderedEventStreamIndex"));
        snapshotsCollection.createIndex(new BasicDBObject(eventConfiguration.aggregateIdentifierProperty(), ORDER_ASC)
                                                .append(eventConfiguration.sequenceNumberProperty(), ORDER_ASC),
                                        new IndexOptions().unique(true).name("uniqueAggregateIndex"));
    }

    protected EventEntryConfiguration eventConfiguration() {
        return eventConfiguration;
    }
}
