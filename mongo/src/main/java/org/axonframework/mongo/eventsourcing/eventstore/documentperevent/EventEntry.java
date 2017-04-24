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

import com.mongodb.DBObject;
import org.axonframework.common.DateTimeUtils;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventData;
import org.axonframework.serialization.SerializedMetaData;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.bson.Document;

import java.time.Instant;

import static org.axonframework.serialization.MessageSerializer.serializeMetaData;
import static org.axonframework.serialization.MessageSerializer.serializePayload;

/**
 * Implementation of a serialized event message that can be used to create a Mongo document.
 */
public class EventEntry implements DomainEventData<Object> {

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
     * Creates a new EventEntry based on data provided by Mongo.
     *
     * @param dbObject      Mongo object that contains data to represent an EventEntry
     * @param configuration Configuration containing the property names
     */
    public EventEntry(Document dbObject, EventEntryConfiguration configuration) {
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
     * Returns the current entry as a mongo Document.
     *
     * @param configuration The configuration describing property names
     * @return Document representing the entry
     */
    public Document asDocument(EventEntryConfiguration configuration) {
        return new Document(configuration.aggregateIdentifierProperty(), aggregateIdentifier)
                .append(configuration.typeProperty(), aggregateType)
                .append(configuration.sequenceNumberProperty(), sequenceNumber)
                .append(configuration.payloadProperty(), serializedPayload)
                .append(configuration.timestampProperty(), timestamp)
                .append(configuration.payloadTypeProperty(), payloadType)
                .append(configuration.payloadRevisionProperty(), payloadRevision)
                .append(configuration.metaDataProperty(), serializedMetaData)
                .append(configuration.eventIdentifierProperty(), eventIdentifier);
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
    public String getEventIdentifier() {
        return eventIdentifier;
    }

    @Override
    public Instant getTimestamp() {
        return DateTimeUtils.parseInstant(timestamp);
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
        } else if (serializedPayload instanceof Document) {
            representationType = Document.class;
        }
        return representationType;
    }
}
