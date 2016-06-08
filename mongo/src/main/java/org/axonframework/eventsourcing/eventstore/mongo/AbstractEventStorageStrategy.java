/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore.mongo;

import com.mongodb.*;
import org.axonframework.common.Assert;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventData;
import org.axonframework.eventsourcing.eventstore.TrackedEventData;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.eventsourcing.eventstore.legacy.LegacyTrackingToken;
import org.axonframework.eventsourcing.eventstore.mongo.documentperevent.EventEntryConfiguration;
import org.axonframework.serialization.Serializer;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.StreamSupport.stream;

/**
 * @author Rene de Waele
 */
public abstract class AbstractEventStorageStrategy implements StorageStrategy {

    protected static final int ORDER_ASC = 1, ORDER_DESC = -1;

    private final EventEntryConfiguration eventConfiguration;

    public AbstractEventStorageStrategy(EventEntryConfiguration eventConfiguration) {
        this.eventConfiguration = eventConfiguration;
    }

    @Override
    public void appendEvents(DBCollection eventCollection, List<? extends EventMessage<?>> events,
                             Serializer serializer) throws MongoException.DuplicateKey {
        eventCollection.insert(createEventDocuments(events, serializer).collect(Collectors.toList()));
    }

    protected abstract Stream<DBObject> createEventDocuments(List<? extends EventMessage<?>> events,
                                                             Serializer serializer);

    @Override
    public void appendSnapshot(DBCollection snapshotCollection, DomainEventMessage<?> snapshot,
                               Serializer serializer) throws MongoException.DuplicateKey {
        snapshotCollection.insert(createSnapshotDocument(snapshot, serializer));
    }

    protected abstract DBObject createSnapshotDocument(DomainEventMessage<?> snapshot, Serializer serializer);

    @Override
    public void deleteSnapshots(DBCollection snapshotCollection, String aggregateIdentifier) {
        DBObject mongoEntry =
                BasicDBObjectBuilder.start().add(eventConfiguration.aggregateIdentifierProperty(), aggregateIdentifier)
                        .get();
        snapshotCollection.find(mongoEntry).forEach(snapshotCollection::remove);
    }

    @Override
    public List<? extends DomainEventData<?>> findDomainEvents(DBCollection eventCollection, String aggregateIdentifier,
                                                               long firstSequenceNumber, int batchSize) {
        DBCursor cursor = eventCollection.find(BasicDBObjectBuilder.start()
                                                       .add(eventConfiguration().aggregateIdentifierProperty(),
                                                            aggregateIdentifier)
                                                       .add(eventConfiguration().sequenceNumberProperty(),
                                                            new BasicDBObject("$gte", firstSequenceNumber)).get())
                .sort(new BasicDBObject(eventConfiguration().sequenceNumberProperty(), ORDER_ASC));
        cursor = applyBatchSize(cursor, batchSize);
        try {
            return stream(cursor.spliterator(), false).flatMap(this::extractDomainEvents)
                    .filter(event -> event.getSequenceNumber() >= firstSequenceNumber).limit(batchSize)
                    .collect(Collectors.toList());
        } finally {
            cursor.close();
        }
    }

    protected abstract Stream<? extends DomainEventData<?>> extractDomainEvents(DBObject object);

    @Override
    public List<? extends TrackedEventData<?>> findTrackedEvents(DBCollection eventCollection, TrackingToken lastToken,
                                                                 int batchSize) {
        DBCursor cursor;
        if (lastToken == null) {
            cursor = eventCollection.find(BasicDBObjectBuilder.start().get());
        } else {
            Assert.isTrue(lastToken instanceof LegacyTrackingToken,
                          String.format("Token %s is of the wrong type", lastToken));
            LegacyTrackingToken legacyTrackingToken = (LegacyTrackingToken) lastToken;
            cursor = eventCollection.find(BasicDBObjectBuilder.start().add(eventConfiguration.timestampProperty(),
                                                                           new BasicDBObject("$gte", legacyTrackingToken
                                                                                   .getTimestamp().toString()))
                                                  .add(eventConfiguration.sequenceNumberProperty(),
                                                       new BasicDBObject("$gte",
                                                                         legacyTrackingToken.getSequenceNumber()))
                                                  .get());
        }
        cursor = cursor.sort(new BasicDBObject(eventConfiguration().timestampProperty(), ORDER_ASC)
                                     .append(eventConfiguration().sequenceNumberProperty(), ORDER_ASC));
        cursor = applyBatchSize(cursor, batchSize);
        try {
            return stream(cursor.spliterator(), false).flatMap(this::extractTrackedEvents)
                    .filter(event -> event.trackingToken().isAfter(lastToken)).limit(batchSize)
                    .collect(Collectors.toList());
        } finally {
            cursor.close();
        }
    }

    protected abstract DBCursor applyBatchSize(DBCursor cursor, int batchSize);

    protected abstract Stream<? extends TrackedEventData<?>> extractTrackedEvents(DBObject object);

    @Override
    public Optional<? extends DomainEventData<?>> findLastSnapshot(DBCollection snapshotCollection,
                                                                   String aggregateIdentifier) {
        DBObject mongoEntry =
                BasicDBObjectBuilder.start().add(eventConfiguration.aggregateIdentifierProperty(), aggregateIdentifier)
                        .get();
        try (DBCursor cursor = snapshotCollection.find(mongoEntry)
                .sort(new BasicDBObject(eventConfiguration.sequenceNumberProperty(), ORDER_DESC)).limit(1)) {
            return stream(cursor.spliterator(), false).findFirst().map(this::extractSnapshot);
        }
    }

    protected abstract DomainEventData<?> extractSnapshot(DBObject object);

    @Override
    public void ensureIndexes(DBCollection eventsCollection, DBCollection snapshotsCollection) {
        eventsCollection.ensureIndex(new BasicDBObject(eventConfiguration.aggregateIdentifierProperty(), ORDER_ASC)
                                             .append(eventConfiguration.sequenceNumberProperty(), ORDER_ASC),
                                     "uniqueAggregateIndex", true);
        eventsCollection.ensureIndex(new BasicDBObject(eventConfiguration.timestampProperty(), ORDER_ASC)
                                             .append(eventConfiguration.sequenceNumberProperty(), ORDER_ASC),
                                     "orderedEventStreamIndex", false);
        snapshotsCollection.ensureIndex(new BasicDBObject(eventConfiguration.aggregateIdentifierProperty(), ORDER_ASC)
                                                .append(eventConfiguration.sequenceNumberProperty(), ORDER_ASC),
                                        "uniqueAggregateIndex", true);
    }

    protected EventEntryConfiguration eventConfiguration() {
        return eventConfiguration;
    }
}
