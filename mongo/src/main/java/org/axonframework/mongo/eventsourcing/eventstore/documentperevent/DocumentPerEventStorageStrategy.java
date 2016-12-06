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

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventData;
import org.axonframework.eventsourcing.eventstore.EventUtils;
import org.axonframework.mongo.eventsourcing.eventstore.AbstractMongoEventStorageStrategy;
import org.axonframework.mongo.eventsourcing.eventstore.StorageStrategy;
import org.axonframework.serialization.Serializer;
import org.bson.Document;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

/**
 * Implementation of a {@link StorageStrategy} that stores one {@link Document} per {@link EventMessage}.
 *
 * @author Rene de Waele
 */
public class DocumentPerEventStorageStrategy extends AbstractMongoEventStorageStrategy {

    /**
     * Initializes a {@link DocumentPerEventStorageStrategy} with default configuration.
     */
    public DocumentPerEventStorageStrategy() {
        this(EventEntryConfiguration.getDefault(), null);
    }

    /**
     * Initializes a {@link DocumentPerEventStorageStrategy} with given {@code eventConfiguration}.
     *
     * @param eventConfiguration object that configures the naming of event entry properties
     * @param lookBackTime       the maximum time to look back when fetching new events while tracking.
     */
    public DocumentPerEventStorageStrategy(EventEntryConfiguration eventConfiguration, Duration lookBackTime) {
        super(eventConfiguration, lookBackTime);
    }

    @Override
    protected Stream<Document> createEventDocuments(List<? extends EventMessage<?>> events, Serializer serializer) {
        return events.stream().map(EventUtils::asDomainEventMessage).map(event -> new EventEntry(event, serializer))
                .map(entry -> entry.asDocument(eventConfiguration()));
    }

    @Override
    protected Document createSnapshotDocument(DomainEventMessage<?> snapshot, Serializer serializer) {
        return new EventEntry(snapshot, serializer).asDocument(eventConfiguration());
    }

    @Override
    protected Stream<? extends DomainEventData<?>> extractEvents(Document object) {
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
