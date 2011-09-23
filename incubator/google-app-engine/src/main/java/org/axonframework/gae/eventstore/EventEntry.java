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
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.StringAggregateIdentifier;
import org.axonframework.serializer.Serializer;

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

    private String eventIdentifier;
    private String aggregateIdentifier;
    private long sequenceNumber;
    private String timeStamp;
    private String aggregateType;
    private String serializedEvent;

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
    EventEntry(String aggregateType, DomainEvent event, Serializer<? super DomainEvent> eventSerializer) {
        this.eventIdentifier = event.getEventIdentifier().toString();
        this.aggregateType = aggregateType;
        this.aggregateIdentifier = event.getAggregateIdentifier().asString();
        this.sequenceNumber = event.getSequenceNumber();
        this.serializedEvent = new String(eventSerializer.serialize(event));
        this.timeStamp = event.getTimestamp().toString();
    }

    EventEntry(Entity entity) {
        this.eventIdentifier = entity.getKey().getName();
        this.aggregateType = entity.getKey().getName();
        this.aggregateIdentifier = (String) entity.getProperty(AGGREGATE_IDENTIFIER);
        this.sequenceNumber = (Long) entity.getProperty(SEQUENCE_NUMBER);
        this.serializedEvent = ((Text) entity.getProperty(SERIALIZED_EVENT)).getValue();
        this.timeStamp = (String) entity.getProperty(TIME_STAMP);
    }

    /**
     * Returns the actual DomainEvent from the EventEntry using the provided Serializer.
     *
     * @param eventSerializer Serializer used to de-serialize the stored DomainEvent
     * @return The actual DomainEvent
     */
    public DomainEvent getDomainEvent(Serializer<? super DomainEvent> eventSerializer) {
        return (DomainEvent) eventSerializer.deserialize(serializedEvent.getBytes(UTF8));
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
