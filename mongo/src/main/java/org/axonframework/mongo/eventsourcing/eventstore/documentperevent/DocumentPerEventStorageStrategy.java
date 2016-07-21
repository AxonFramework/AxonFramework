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

package org.axonframework.mongo.eventsourcing.eventstore.documentperevent;

import com.mongodb.client.FindIterable;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventData;
import org.axonframework.eventsourcing.eventstore.EventUtils;
import org.axonframework.eventsourcing.eventstore.TrackedEventData;
import org.axonframework.mongo.eventsourcing.eventstore.AbstractMongoEventStorageStrategy;
import org.axonframework.serialization.Serializer;
import org.bson.Document;

import java.util.List;
import java.util.stream.Stream;

/**
 * @author Rene de Waele
 */
public class DocumentPerEventStorageStrategy extends AbstractMongoEventStorageStrategy {

    public DocumentPerEventStorageStrategy() {
        this(EventEntryConfiguration.getDefault());
    }

    public DocumentPerEventStorageStrategy(EventEntryConfiguration eventConfiguration) {
        super(eventConfiguration);
    }

    @Override
    protected Stream<Document> createEventDocuments(List<? extends EventMessage<?>> events, Serializer serializer) {
        return events.stream().map(EventUtils::asDomainEventMessage)
                .map(event -> new EventEntry(event, serializer))
                .map(entry -> entry.asDocument(eventConfiguration()));
    }

    @Override
    protected Document createSnapshotDocument(DomainEventMessage<?> snapshot, Serializer serializer) {
        return new EventEntry(snapshot, serializer).asDocument(eventConfiguration());
    }

    @Override
    protected Stream<? extends DomainEventData<?>> extractDomainEvents(Document object) {
        return Stream.of(extractEvent(object));
    }

    @Override
    protected FindIterable<Document> applyBatchSize(FindIterable<Document> cursor, int batchSize) {
        return cursor.batchSize(batchSize);
    }

    @Override
    protected Stream<? extends TrackedEventData<?>> extractTrackedEvents(Document object) {
        return Stream.of(extractEvent(object));
    }

    @Override
    protected DomainEventData<?> extractSnapshot(Document object) {
        return extractEvent(object);
    }

    private EventEntry extractEvent(Document object) {
        return new EventEntry(object, eventConfiguration());
    }
}
