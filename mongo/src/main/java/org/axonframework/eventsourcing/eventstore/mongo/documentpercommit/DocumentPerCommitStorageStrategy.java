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

package org.axonframework.eventsourcing.eventstore.mongo.documentpercommit;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventData;
import org.axonframework.eventsourcing.eventstore.EventUtils;
import org.axonframework.eventsourcing.eventstore.TrackedEventData;
import org.axonframework.eventsourcing.eventstore.mongo.AbstractEventStorageStrategy;
import org.axonframework.eventsourcing.eventstore.mongo.documentperevent.EventEntryConfiguration;
import org.axonframework.serialization.Serializer;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DocumentPerCommitStorageStrategy extends AbstractEventStorageStrategy {

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

    @Override
    protected Stream<DBObject> createEventDocuments(List<? extends EventMessage<?>> events, Serializer serializer) {
        return Stream
                .of(new CommitEntry(events.stream().map(EventUtils::asDomainEventMessage).collect(Collectors.toList()),
                                    serializer).asDBObject(commitEntryConfiguration, eventConfiguration()));
    }

    @Override
    protected DBObject createSnapshotDocument(DomainEventMessage<?> snapshot, Serializer serializer) {
        return new CommitEntry(Collections.singletonList(snapshot), serializer)
                .asDBObject(commitEntryConfiguration, eventConfiguration());
    }

    @Override
    protected Stream<? extends DomainEventData<?>> extractDomainEvents(DBObject object) {
        return Stream.of(new CommitEntry(object, commitEntryConfiguration, eventConfiguration()).getEvents());
    }

    @Override
    protected DBCursor applyBatchSize(DBCursor cursor, int batchSize) {
        return cursor.batchSize(batchSize/8).limit(batchSize);
    }

    @Override
    protected Stream<? extends TrackedEventData<?>> extractTrackedEvents(DBObject object) {
        return Stream.of(new CommitEntry(object, commitEntryConfiguration, eventConfiguration()).getEvents());
    }

    @Override
    protected DomainEventData<?> extractSnapshot(DBObject object) {
        return new CommitEntry(object, commitEntryConfiguration, eventConfiguration()).getEvents()[0];
    }

    @Override
    public void ensureIndexes(DBCollection eventsCollection, DBCollection snapshotsCollection) {
        super.ensureIndexes(eventsCollection, snapshotsCollection);
        //prevents duplicate commits
        eventsCollection.ensureIndex(new BasicDBObject(eventConfiguration().aggregateIdentifierProperty(), ORDER_ASC)
                                             .append(commitEntryConfiguration.firstSequenceNumberProperty(), ORDER_ASC),
                                     "uniqueAggregateStartIndex", true);
    }
}
