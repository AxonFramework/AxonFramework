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
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventData;
import org.axonframework.eventsourcing.eventstore.EventUtils;
import org.axonframework.mongo.eventsourcing.eventstore.AbstractMongoEventStorageStrategy;
import org.axonframework.mongo.eventsourcing.eventstore.StorageStrategy;
import org.axonframework.mongo.eventsourcing.eventstore.documentperevent.EventEntryConfiguration;
import org.axonframework.serialization.Serializer;
import org.bson.Document;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of a Mongo {@link StorageStrategy} that stores one {@link Document} per commit of a batch of events.
 * <p>
 * For instance, one command commonly gives rise to more than one event. Using this strategt all events for that single
 * command will be grouped in a single Mongo Document.
 */
public class DocumentPerCommitStorageStrategy extends AbstractMongoEventStorageStrategy {

    private final CommitEntryConfiguration commitEntryConfiguration;

    /**
     * Initializes a {@link DocumentPerCommitStorageStrategy} with default event entry and commit entry configuration.
     */
    public DocumentPerCommitStorageStrategy() {
        this(CommitEntryConfiguration.getDefault());
    }

    /**
     * Initializes a {@link DocumentPerCommitStorageStrategy} with default event entry and given {@code
     * commitEntryConfiguration}.
     *
     * @param commitEntryConfiguration object that configures the naming of commit entry properties
     */
    public DocumentPerCommitStorageStrategy(CommitEntryConfiguration commitEntryConfiguration) {
        this(EventEntryConfiguration.getDefault(), commitEntryConfiguration, null);
    }

    /**
     * Initializes a {@link DocumentPerCommitStorageStrategy} with given {@code eventConfiguration} and {@code
     * commitEntryConfiguration}.
     *
     * @param eventConfiguration       object that configures the naming of event entry properties
     * @param commitEntryConfiguration object that configures the naming of event entry properties
     * @param lookBackTime       the maximum time to look back when fetching new events while tracking.
     */
    public DocumentPerCommitStorageStrategy(EventEntryConfiguration eventConfiguration,
                                            CommitEntryConfiguration commitEntryConfiguration, Duration lookBackTime) {
        super(eventConfiguration, lookBackTime);
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
    protected Stream<? extends DomainEventData<?>> extractEvents(Document object) {
        return Stream.of(new CommitEntry(object, commitEntryConfiguration, eventConfiguration()).getEvents());
    }

    @Override
    protected DomainEventData<?> extractSnapshot(Document object) {
        return new CommitEntry(object, commitEntryConfiguration, eventConfiguration()).getEvents()[0];
    }

    @Override
    public void ensureIndexes(MongoCollection<Document> eventsCollection,
                              MongoCollection<Document> snapshotsCollection) {
        super.ensureIndexes(eventsCollection, snapshotsCollection);
        //prevents duplicate commits
        eventsCollection.createIndex(new BasicDBObject(eventConfiguration().aggregateIdentifierProperty(), ORDER_ASC)
                                             .append(commitEntryConfiguration.firstSequenceNumberProperty(), ORDER_ASC),
                                     new IndexOptions().unique(true).name("uniqueAggregateStartIndex"));
    }
}
