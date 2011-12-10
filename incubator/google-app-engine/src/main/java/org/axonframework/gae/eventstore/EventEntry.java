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

package org.axonframework.gae.eventstore;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Text;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.eventstore.SerializedDomainEventMessage;
import org.axonframework.serializer.SerializedMetaData;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.SimpleSerializedObject;
import org.joda.time.DateTime;

import java.nio.charset.Charset;

/**
 * <p>Class that represents an event to store in the google app engine data store. </p>
 *
 * @author Jettro Coenradie
 */
public class EventEntry {
    private static final String AGGREGATE_IDENTIFIER = "aggregateIdentifier";
    private static final String SEQUENCE_NUMBER = "sequenceNumber";
    private static final String SERIALIZED_EVENT = "serializedEvent";
    private static final String TIME_STAMP = "timeStamp";
    private static final String EVENT_TYPE = "eventType";
    private static final String EVENT_REVISION = "eventRevision";
    private static final String META_DATA = "metaData";

    private final String eventIdentifier;
    private final String aggregateIdentifier;
    private final long sequenceNumber;
    private final String timeStamp;
    private final String aggregateType;
    private final String serializedEvent;
    private final int eventRevision;
    private final String eventType;
    private final String serializedMetaData;

    /**
     * Charset used for the serialization is usually UTF-8, which is presented by this constant
     */
    protected static final Charset UTF8 = Charset.forName("UTF-8");

    /**
     * Constructor used to create a new event entry to store in Mongo
     *
     * @param aggregateType   String containing the aggregate type of the event
     * @param event           The actual DomainEvent to store
     * @param eventSerializer Serializer to use for the event to store
     */
    EventEntry(String aggregateType, DomainEventMessage event, Serializer eventSerializer) {
        this.eventIdentifier = event.getIdentifier();
        this.aggregateType = aggregateType;
        this.aggregateIdentifier = event.getAggregateIdentifier().toString();
        this.sequenceNumber = event.getSequenceNumber();
        SerializedObject serializedEvent = eventSerializer.serialize(event.getPayload());
        this.serializedEvent = new String(serializedEvent.getData(), UTF8);
        this.eventType = serializedEvent.getType().getName();
        this.eventRevision = serializedEvent.getType().getRevision();
        this.serializedMetaData = new String(eventSerializer.serialize(event.getMetaData()).getData(), UTF8);
        this.timeStamp = event.getTimestamp().toString();
    }

    EventEntry(Entity entity) {
        this.eventIdentifier = entity.getKey().getName();
        this.aggregateType = entity.getKey().getName();
        this.aggregateIdentifier = (String) entity.getProperty(AGGREGATE_IDENTIFIER);
        this.sequenceNumber = (Long) entity.getProperty(SEQUENCE_NUMBER);
        this.serializedEvent = ((Text) entity.getProperty(SERIALIZED_EVENT)).getValue();
        this.timeStamp = (String) entity.getProperty(TIME_STAMP);
        this.eventRevision = Integer.valueOf(entity.getProperty(EVENT_REVISION).toString());
        this.eventType = (String) entity.getProperty(EVENT_TYPE);
        this.serializedMetaData = ((Text) entity.getProperty(META_DATA)).getValue();
    }

    /**
     * Returns the actual DomainEvent from the EventEntry using the provided Serializer.
     *
     * @param eventSerializer Serializer used to de-serialize the stored DomainEvent
     * @return The actual DomainEvent
     */
    public DomainEventMessage getDomainEvent(Serializer eventSerializer) {
        return new SerializedDomainEventMessage(eventIdentifier, aggregateIdentifier,
                                                sequenceNumber, new DateTime(timeStamp),
                                                new SimpleSerializedObject(serializedEvent.getBytes(UTF8),
                                                                           eventType, eventRevision),
                                                new SerializedMetaData(serializedMetaData.getBytes(UTF8)),
                                                eventSerializer,
                                                eventSerializer);
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
    public Object getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getSerializedEvent() {
        return serializedEvent;
    }

    public String getTimeStamp() {
        return timeStamp;
    }

    Entity asEntity() {
        Key key = KeyFactory.createKey(aggregateType, eventIdentifier);
        Entity entity = new Entity(key);
        entity.setProperty(AGGREGATE_IDENTIFIER, aggregateIdentifier);
        entity.setProperty(SEQUENCE_NUMBER, sequenceNumber);
        entity.setProperty(TIME_STAMP, timeStamp);
        entity.setProperty(SERIALIZED_EVENT, new Text(serializedEvent));
        entity.setProperty(EVENT_TYPE, eventType);
        entity.setProperty(EVENT_REVISION, eventRevision);
        entity.setProperty(META_DATA, new Text(serializedMetaData));
        return entity;
    }

    /**
     * Returns the gae query used to query google app engine for events for specified aggregate identifier and type
     *
     * @param type                The type of the aggregate to create the mongo DBObject for
     * @param aggregateIdentifier Identifier of the aggregate to obtain the mongo DBObject for
     * @param firstSequenceNumber number representing the first event to obtain
     * @return Created DBObject based on the provided parameters to be used for a query
     */
    static Query forAggregate(String type, String aggregateIdentifier, long firstSequenceNumber) {
        return new Query(type)
                .addFilter(AGGREGATE_IDENTIFIER, Query.FilterOperator.EQUAL, aggregateIdentifier)
                .addFilter(SEQUENCE_NUMBER, Query.FilterOperator.GREATER_THAN_OR_EQUAL, firstSequenceNumber)
                .addSort(SEQUENCE_NUMBER, Query.SortDirection.ASCENDING);
    }

    static Query forLastSnapshot(String type, String aggregateIdentifier) {
        return new Query(type)
                .addFilter(AGGREGATE_IDENTIFIER, Query.FilterOperator.EQUAL, aggregateIdentifier)
                .addSort(SEQUENCE_NUMBER, Query.SortDirection.ASCENDING);
    }
}
