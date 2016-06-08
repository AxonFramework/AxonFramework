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

package org.axonframework.eventsourcing.eventstore.mongo.documentperevent;

import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventData;
import org.axonframework.eventsourcing.eventstore.TrackedEventData;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.eventsourcing.eventstore.legacy.LegacyTrackingToken;
import org.axonframework.serialization.SerializedMetaData;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;

import java.time.Instant;

import static org.axonframework.serialization.MessageSerializer.serializeMetaData;
import static org.axonframework.serialization.MessageSerializer.serializePayload;

public class EventEntry implements DomainEventData<Object>, TrackedEventData<Object> {

    private final String aggregateIdentifier;
    private final String aggregateType;
    private final long sequenceNumber;
    private final String timestamp;
    private final Object serializedPayload;
    private final String payloadType;
    private final String payloadRevision;
    private final Object serializedMetaData;
    private final String eventIdentifier;

    /**
     * Constructor used to create a new event entry to store in Mongo.
     *
     * @param event      The actual DomainEvent to store
     * @param serializer Serializer to use for the event to store
     */
    public EventEntry(DomainEventMessage<?> event, Serializer serializer) {
        aggregateIdentifier = event.getAggregateIdentifier();
        aggregateType = event.getType();
        sequenceNumber = event.getSequenceNumber();
        eventIdentifier = event.getIdentifier();
        Class<?> serializationTarget = String.class;
        if (serializer.canSerializeTo(DBObject.class)) {
            serializationTarget = DBObject.class;
        }
        SerializedObject<?> serializedPayloadObject = serializePayload(event, serializer, serializationTarget);
        SerializedObject<?> serializedMetaDataObject = serializeMetaData(event, serializer, serializationTarget);
        serializedPayload = serializedPayloadObject.getData();
        payloadType = serializedPayloadObject.getType().getName();
        payloadRevision = serializedPayloadObject.getType().getRevision();
        serializedMetaData = serializedMetaDataObject.getData();
        timestamp = event.getTimestamp().toString();
    }

    /**
     * Creates a new EventEntry based onm data provided by Mongo.
     *
     * @param dbObject      Mongo object that contains data to represent an EventEntry
     * @param configuration Configuration containing the property names
     */
    public EventEntry(DBObject dbObject, EventEntryConfiguration configuration) {
        aggregateIdentifier = (String) dbObject.get(configuration.aggregateIdentifierProperty());
        aggregateType = (String) dbObject.get(configuration.typeProperty());
        sequenceNumber = ((Number) dbObject.get(configuration.sequenceNumberProperty())).longValue();
        serializedPayload = dbObject.get(configuration.payloadProperty());
        timestamp = (String) dbObject.get(configuration.timestampProperty());
        payloadType = (String) dbObject.get(configuration.payloadTypeProperty());
        payloadRevision = (String) dbObject.get(configuration.payloadRevisionProperty());
        serializedMetaData = dbObject.get(configuration.metaDataProperty());
        eventIdentifier = (String) dbObject.get(configuration.eventIdentifierProperty());
    }

    /**
     * Returns the current entry as a mongo DBObject.
     *
     * @return DBObject representing the entry
     */
    public DBObject asDBObject(EventEntryConfiguration configuration) {
        return BasicDBObjectBuilder.start()
                .add(configuration.aggregateIdentifierProperty(), aggregateIdentifier)
                .add(configuration.typeProperty(), aggregateType)
                .add(configuration.sequenceNumberProperty(), sequenceNumber)
                .add(configuration.payloadProperty(), serializedPayload)
                .add(configuration.timestampProperty(), timestamp)
                .add(configuration.payloadTypeProperty(), payloadType)
                .add(configuration.payloadRevisionProperty(), payloadRevision)
                .add(configuration.metaDataProperty(), serializedMetaData)
                .add(configuration.eventIdentifierProperty(), eventIdentifier)
                .get();
    }

    @Override
    public String getType() {
        return aggregateType;
    }

    @Override
    public String getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    @Override
    public long getSequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public TrackingToken trackingToken() {
        return new LegacyTrackingToken(getTimestamp(), aggregateIdentifier, sequenceNumber);
    }

    @Override
    public String getEventIdentifier() {
        return eventIdentifier;
    }

    @Override
    public Instant getTimestamp() {
        return Instant.parse(timestamp);
    }

    @Override
    @SuppressWarnings("unchecked")
    public SerializedObject<Object> getMetaData() {
        return new SerializedMetaData(serializedMetaData, getRepresentationType());
    }

    @Override
    @SuppressWarnings("unchecked")
    public SerializedObject<Object> getPayload() {
        return new SimpleSerializedObject(serializedPayload, getRepresentationType(), payloadType, payloadRevision);
    }

    private Class<?> getRepresentationType() {
        Class<?> representationType = String.class;
        if (serializedPayload instanceof DBObject) {
            representationType = DBObject.class;
        }
        return representationType;
    }
}
