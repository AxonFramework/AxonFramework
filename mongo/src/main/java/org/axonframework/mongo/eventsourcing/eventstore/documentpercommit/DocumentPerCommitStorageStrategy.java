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

package org.axonframework.mongo.eventsourcing.eventstore.documentpercommit;

import com.mongodb.BasicDBObject;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventData;
import org.axonframework.eventsourcing.eventstore.EventUtils;
import org.axonframework.eventsourcing.eventstore.TrackedEventData;
import org.axonframework.mongo.eventsourcing.eventstore.AbstractMongoEventStorageStrategy;
import org.axonframework.mongo.eventsourcing.eventstore.documentperevent.EventEntryConfiguration;
import org.axonframework.serialization.Serializer;
import org.bson.Document;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DocumentPerCommitStorageStrategy extends AbstractMongoEventStorageStrategy {

    private final CommitEntryConfiguration commitEntryConfiguration;

    public DocumentPerCommitStorageStrategy() {
        this(CommitEntryConfiguration.getDefault());
    }

    public DocumentPerCommitStorageStrategy(CommitEntryConfiguration commitEntryConfiguration) {
        this(EventEntryConfiguration.getDefault(), commitEntryConfiguration);
    }

    public DocumentPerCommitStorageStrategy(EventEntryConfiguration eventConfiguration,
                                            CommitEntryConfiguration commitEntryConfiguration) {
        super(eventConfiguration);
        this.commitEntryConfiguration = commitEntryConfiguration;
    }

    public DocumentPerCommitStorageStrategy(EventEntryConfiguration eventConfiguration, long gapDetectionInterval,
                                            CommitEntryConfiguration commitEntryConfiguration) {
        super(eventConfiguration);
        this.commitEntryConfiguration = commitEntryConfiguration;
    }

    @Override
    protected Stream<Document> createEventDocuments(List<? extends EventMessage<?>> events, Serializer serializer) {
        return Stream
                .of(new CommitEntry(events.stream().map(EventUtils::asDomainEventMessage).collect(Collectors.toList()),
                                    serializer).asDocument(commitEntryConfiguration, eventConfiguration()));
    }

    @Override
    protected Document createSnapshotDocument(DomainEventMessage<?> snapshot, Serializer serializer) {
        return new CommitEntry(Collections.singletonList(snapshot), serializer)
                .asDocument(commitEntryConfiguration, eventConfiguration());
    }

    @Override
    protected Stream<? extends DomainEventData<?>> extractDomainEvents(Document object) {
        return Stream.of(new CommitEntry(object, commitEntryConfiguration, eventConfiguration()).getEvents());
    }

    @Override
    protected FindIterable<Document> applyBatchSize(FindIterable<Document> cursor, int batchSize) {
        return cursor.batchSize(batchSize);
    }

    @Override
    protected Stream<? extends TrackedEventData<?>> extractTrackedEvents(Document object) {
        return Stream.of(new CommitEntry(object, commitEntryConfiguration, eventConfiguration()).getEvents());
    }

    @Override
    protected DomainEventData<?> extractSnapshot(Document object) {
        return new CommitEntry(object, commitEntryConfiguration, eventConfiguration()).getEvents()[0];
    }

    @Override
    public void ensureIndexes(MongoCollection<Document> eventsCollection, MongoCollection<Document> snapshotsCollection) {
        super.ensureIndexes(eventsCollection, snapshotsCollection);
        //prevents duplicate commits
        eventsCollection.createIndex(new BasicDBObject(eventConfiguration().aggregateIdentifierProperty(), ORDER_ASC)
                                             .append(commitEntryConfiguration.firstSequenceNumberProperty(), ORDER_ASC),
                                     new IndexOptions().unique(true).name("uniqueAggregateStartIndex"));
    }
}
