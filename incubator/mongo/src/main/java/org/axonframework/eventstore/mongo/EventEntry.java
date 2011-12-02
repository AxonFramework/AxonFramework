/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.eventstore.mongo;

import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.StringAggregateIdentifier;
import org.axonframework.eventstore.SerializedDomainEventMessage;
import org.axonframework.serializer.SerializedMetaData;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.SimpleSerializedObject;
import org.joda.time.DateTime;

import java.nio.charset.Charset;

/**
 * Data needed by different types of event logs.
 *
 * @author Allard Buijze
 * @author Jettro Coenradie
 * @since 0.7
 */
class EventEntry {

    /**
     * Property name in mongo for the Aggregate Identifier.
     */
    public static final String AGGREGATE_IDENTIFIER_PROPERTY = "aggregateIdentifier";

    /**
     * Property name in mongo for the Sequence Number.
     */
    public static final String SEQUENCE_NUMBER_PROPERTY = "sequenceNumber";

    /**
     * Property name in mongo for the Aggregate's Type Identifier.
     */
    public static final String AGGREGATE_TYPE_PROPERTY = "type";

    /**
     * Property name in mongo for the Time Stamp.
     */
    public static final String TIME_STAMP_PROPERTY = "timeStamp";

    private static final String SERIALIZED_PAYLOAD_PROPERTY = "serializedPayload";
    private static final String PAYLOAD_TYPE_PROPERTY = "payloadType";
    private static final String PAYLOAD_REVISION_PROPERTY = "payloadRevision";
    private static final String META_DATA_PROPERTY = "serializedMetaData";
    private static final String EVENT_IDENTIFIER_PROPERTY = "eventIdentifier";
    /**
     * Mongo object representing the index Events in Mongo.
     */
    public static final BasicDBObject UNIQUE_INDEX = new BasicDBObject(AGGREGATE_IDENTIFIER_PROPERTY, 1)
            .append(AGGREGATE_TYPE_PROPERTY, 1).append(SEQUENCE_NUMBER_PROPERTY, 1);
    /**
     * Charset used for the serialization is usually UTF-8, which is presented by this constant.
     */
    protected static final Charset UTF8 = Charset.forName("UTF-8");
    private final String aggregateIdentifier;
    private final long sequenceNumber;
    private final String timeStamp;
    private final String aggregateType;
    private final String serializedPayload;
    private final String payoadType;
    private final int payloadRevision;
    private final String serializedMetaData;
    private final String eventIdentifier;

    /**
     * Constructor used to create a new event entry to store in Mongo.
     *
     * @param aggregateType   String containing the aggregate type of the event
     * @param event           The actual DomainEvent to store
     * @param eventSerializer Serializer to use for the event to store
     */
    EventEntry(String aggregateType, DomainEventMessage event, Serializer eventSerializer) {
        this.aggregateType = aggregateType;
        this.aggregateIdentifier = event.getAggregateIdentifier().asString();
        this.sequenceNumber = event.getSequenceNumber();
        this.eventIdentifier = event.getEventIdentifier();
        SerializedObject serializedPayload = eventSerializer.serialize(event.getPayload());
        SerializedObject serializedMetaData = eventSerializer.serialize(event.getMetaData());
        this.serializedPayload = new String(serializedPayload.getData(), UTF8);
        this.payoadType = serializedPayload.getType().getName();
        this.payloadRevision = serializedPayload.getType().getRevision();
        this.serializedMetaData = new String(serializedMetaData.getData(), UTF8);
        this.timeStamp = event.getTimestamp().toString();
    }

    /**
     * Creates a new EventEntry based onm data provided by Mongo.
     *
     * @param dbObject Mongo object that contains data to represent an EventEntry
     */
    EventEntry(DBObject dbObject) {
        this.aggregateIdentifier = (String) dbObject.get(AGGREGATE_IDENTIFIER_PROPERTY);
        this.sequenceNumber = (Long) dbObject.get(SEQUENCE_NUMBER_PROPERTY);
        this.serializedPayload = (String) dbObject.get(SERIALIZED_PAYLOAD_PROPERTY);
        this.timeStamp = (String) dbObject.get(TIME_STAMP_PROPERTY);
        this.aggregateType = (String) dbObject.get(AGGREGATE_TYPE_PROPERTY);
        this.payoadType = (String) dbObject.get(PAYLOAD_TYPE_PROPERTY);
        this.payloadRevision = (Integer) dbObject.get(PAYLOAD_REVISION_PROPERTY);
        this.serializedMetaData = (String) dbObject.get(META_DATA_PROPERTY);
        this.eventIdentifier = (String) dbObject.get(EVENT_IDENTIFIER_PROPERTY);
    }

    /**
     * Returns the actual DomainEvent from the EventEntry using the provided Serializer.
     *
     * @param eventSerializer Serializer used to de-serialize the stored DomainEvent
     * @return The actual DomainEvent
     */
    public DomainEventMessage getDomainEvent(Serializer eventSerializer) {
        return new SerializedDomainEventMessage(
                eventIdentifier, new StringAggregateIdentifier(aggregateIdentifier),
                sequenceNumber, new DateTime(timeStamp),
                new SimpleSerializedObject(serializedPayload.getBytes(UTF8), payoadType, payloadRevision),
                new SerializedMetaData(serializedMetaData.getBytes(UTF8)), eventSerializer, eventSerializer);
    }

    /**
     * getter for the sequence number of the event.
     *
     * @return long representing the sequence number of the event
     */
    public long getSequenceNumber() {
        return sequenceNumber;
    }

    /**
     * getter for the aggregate identifier.
     *
     * @return AggregateIdentifier for this EventEntry
     */
    public AggregateIdentifier getAggregateIdentifier() {
        return new StringAggregateIdentifier(aggregateIdentifier);
    }

    /**
     * Returns the current EventEntry as a mongo DBObject.
     *
     * @return DBObject representing the EventEntry
     */
    public DBObject asDBObject() {
        return BasicDBObjectBuilder.start()
                                   .add(AGGREGATE_IDENTIFIER_PROPERTY, aggregateIdentifier)
                                   .add(SEQUENCE_NUMBER_PROPERTY, sequenceNumber)
                                   .add(SERIALIZED_PAYLOAD_PROPERTY, serializedPayload)
                                   .add(TIME_STAMP_PROPERTY, timeStamp)
                                   .add(AGGREGATE_TYPE_PROPERTY, aggregateType)
                                   .add(PAYLOAD_TYPE_PROPERTY, payoadType)
                                   .add(PAYLOAD_REVISION_PROPERTY, payloadRevision)
                                   .add(META_DATA_PROPERTY, serializedMetaData)
                                   .get();
    }

    /**
     * Returns the mongo DBObject used to query mongo for events for specified aggregate identifier and type.
     *
     * @param type                The type of the aggregate to create the mongo DBObject for
     * @param aggregateIdentifier Identifier of the aggregate to obtain the mongo DBObject for
     * @param firstSequenceNumber number representing the first event to obtain
     * @return Created DBObject based on the provided parameters to be used for a query
     */
    public static DBObject forAggregate(String type, String aggregateIdentifier, long firstSequenceNumber) {
        return BasicDBObjectBuilder.start()
                                   .add(EventEntry.AGGREGATE_IDENTIFIER_PROPERTY, aggregateIdentifier)
                                   .add(EventEntry.SEQUENCE_NUMBER_PROPERTY, new BasicDBObject("$gte",
                                                                                               firstSequenceNumber))
                                   .add(EventEntry.AGGREGATE_TYPE_PROPERTY, type)
                                   .get();
    }

    public DomainEventMessage asDomainEventMessage(Serializer eventSerializer) {
        return new SerializedDomainEventMessage(
                eventIdentifier, new StringAggregateIdentifier(aggregateIdentifier),
                sequenceNumber, new DateTime(timeStamp),
                new SimpleSerializedObject(serializedPayload.getBytes(UTF8), payoadType, payloadRevision),
                new SerializedMetaData(serializedMetaData.getBytes(UTF8)), eventSerializer, eventSerializer);
    }
}