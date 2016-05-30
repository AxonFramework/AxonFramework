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

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.mongo.documentperevent.EventEntry;
import org.axonframework.eventsourcing.eventstore.mongo.documentperevent.EventEntryConfiguration;
import org.axonframework.serialization.Serializer;

import java.util.List;

/**
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
     * Creates a new CommitEntry based onm data provided by Mongo.
     *
     * @param dbObject Mongo object that contains data to represent an CommitEntry
     */
    @SuppressWarnings("unchecked")
    public CommitEntry(DBObject dbObject, CommitEntryConfiguration commitConfiguration,
                       EventEntryConfiguration eventConfiguration) {
        this.aggregateIdentifier = (String) dbObject.get(eventConfiguration.aggregateIdentifierProperty());
        this.firstSequenceNumber =
                ((Number) dbObject.get(commitConfiguration.firstSequenceNumberProperty())).longValue();
        this.lastSequenceNumber = ((Number) dbObject.get(commitConfiguration.lastSequenceNumberProperty())).longValue();
        this.firstTimestamp = (String) dbObject.get(commitConfiguration.firstTimestampProperty());
        this.lastTimestamp = (String) dbObject.get(commitConfiguration.lastTimestampProperty());
        this.aggregateType = (String) dbObject.get(eventConfiguration.typeProperty());
        List<DBObject> entries = (List<DBObject>) dbObject.get(commitConfiguration.eventsProperty());
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
     * Returns the current CommitEntry as a mongo DBObject.
     *
     * @return DBObject representing the CommitEntry
     */
    public DBObject asDBObject(CommitEntryConfiguration commitConfiguration,
                               EventEntryConfiguration eventConfiguration) {
        final BasicDBList events = new BasicDBList();
        BasicDBObjectBuilder commitBuilder =
                BasicDBObjectBuilder.start().add(eventConfiguration.aggregateIdentifierProperty(), aggregateIdentifier)
                        .add(eventConfiguration.sequenceNumberProperty(), lastSequenceNumber)
                        .add(commitConfiguration.firstSequenceNumberProperty(), firstSequenceNumber)
                        .add(commitConfiguration.lastSequenceNumberProperty(), lastSequenceNumber)
                        .add(eventConfiguration.timestampProperty(), lastTimestamp)
                        .add(commitConfiguration.firstTimestampProperty(), firstTimestamp)
                        .add(commitConfiguration.lastTimestampProperty(), lastTimestamp)
                        .add(eventConfiguration.typeProperty(), aggregateType)
                        .add(commitConfiguration.eventsProperty(), events);
        for (EventEntry eventEntry : eventEntries) {
            events.add(eventEntry.asDBObject(eventConfiguration));
        }
        return commitBuilder.get();
    }

}
