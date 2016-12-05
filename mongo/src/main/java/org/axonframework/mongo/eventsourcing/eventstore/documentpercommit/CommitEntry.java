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

import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.mongo.eventsourcing.eventstore.documentperevent.EventEntry;
import org.axonframework.mongo.eventsourcing.eventstore.documentperevent.EventEntryConfiguration;
import org.axonframework.serialization.Serializer;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;

/**
 * Mongo event storage entry containing an array of {@link EventEntry event entries} that are part of the same
 * UnitOfWork commit.
 *
 * @author Rene de Waele
 */
public class CommitEntry {

    private final String aggregateIdentifier;
    private final String aggregateType;
    private final long firstSequenceNumber;
    private final long lastSequenceNumber;
    private final String firstTimestamp;
    private final String lastTimestamp;
    private final EventEntry[] eventEntries;

    /**
     * Constructor used to create a new event entry to store in Mongo.
     *
     * @param serializer Serializer to use for the event to store
     * @param events     The events contained in this commit
     */
    public CommitEntry(List<? extends DomainEventMessage<?>> events, Serializer serializer) {
        DomainEventMessage firstEvent = events.get(0);
        DomainEventMessage lastEvent = events.get(events.size() - 1);
        firstSequenceNumber = firstEvent.getSequenceNumber();
        firstTimestamp = firstEvent.getTimestamp().toString();
        lastTimestamp = lastEvent.getTimestamp().toString();
        lastSequenceNumber = lastEvent.getSequenceNumber();
        aggregateIdentifier = lastEvent.getAggregateIdentifier();
        aggregateType = lastEvent.getType();
        eventEntries = new EventEntry[events.size()];
        for (int i = 0, eventsLength = events.size(); i < eventsLength; i++) {
            DomainEventMessage event = events.get(i);
            eventEntries[i] = new EventEntry(event, serializer);
        }
    }

    /**
     * Creates a new CommitEntry based on data provided by Mongo.
     *
     * @param dbObject            Mongo object that contains data to represent a CommitEntry
     * @param commitConfiguration commit entry specific configuration
     * @param eventConfiguration  event entry specific configuration
     */
    @SuppressWarnings("unchecked")
    public CommitEntry(Document dbObject, CommitEntryConfiguration commitConfiguration,
                       EventEntryConfiguration eventConfiguration) {
        this.aggregateIdentifier = (String) dbObject.get(eventConfiguration.aggregateIdentifierProperty());
        this.firstSequenceNumber =
                ((Number) dbObject.get(commitConfiguration.firstSequenceNumberProperty())).longValue();
        this.lastSequenceNumber = ((Number) dbObject.get(commitConfiguration.lastSequenceNumberProperty())).longValue();
        this.firstTimestamp = (String) dbObject.get(commitConfiguration.firstTimestampProperty());
        this.lastTimestamp = (String) dbObject.get(commitConfiguration.lastTimestampProperty());
        this.aggregateType = (String) dbObject.get(eventConfiguration.typeProperty());
        List<Document> entries = (List<Document>) dbObject.get(commitConfiguration.eventsProperty());
        eventEntries = new EventEntry[entries.size()];
        for (int i = 0, entriesSize = entries.size(); i < entriesSize; i++) {
            eventEntries[i] = new EventEntry(entries.get(i), eventConfiguration);
        }
    }

    /**
     * Returns the event entries stored as part of this commit.
     *
     * @return The event instances stored in this entry
     */
    public EventEntry[] getEvents() {
        return eventEntries;
    }

    /**
     * Returns the current CommitEntry as a mongo Document.
     *
     * @param commitConfiguration Configuration of commit-specific properties on the document
     * @param eventConfiguration  Configuration of event-related properties on the document
     * @return Document representing the CommitEntry
     */
    public Document asDocument(CommitEntryConfiguration commitConfiguration,
                               EventEntryConfiguration eventConfiguration) {
        List<Bson> events = new ArrayList<>();
        for (EventEntry eventEntry : eventEntries) {
            events.add(eventEntry.asDocument(eventConfiguration));
        }
        return new Document(eventConfiguration.aggregateIdentifierProperty(), aggregateIdentifier)
                .append(eventConfiguration.sequenceNumberProperty(), lastSequenceNumber)
                .append(commitConfiguration.lastSequenceNumberProperty(), lastSequenceNumber)
                .append(commitConfiguration.firstSequenceNumberProperty(), firstSequenceNumber)
                .append(eventConfiguration.timestampProperty(), firstTimestamp)
                .append(commitConfiguration.firstTimestampProperty(), firstTimestamp)
                .append(commitConfiguration.lastTimestampProperty(), lastTimestamp)
                .append(eventConfiguration.typeProperty(), aggregateType)
                .append(commitConfiguration.eventsProperty(), events);
    }

}
