/*
 * Copyright (c) 2010-2012. Axon Framework
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
import org.axonframework.serializer.SerializedDomainEventData;
import org.axonframework.serializer.SerializedDomainEventMessage;
import org.axonframework.serializer.SerializedMetaData;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.SimpleSerializedObject;
import org.axonframework.upcasting.SerializedDomainEventUpcastingContext;
import org.axonframework.upcasting.UpcastSerializedDomainEventData;
import org.axonframework.upcasting.UpcasterChain;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;

import static org.axonframework.serializer.MessageSerializer.serializeMetaData;
import static org.axonframework.serializer.MessageSerializer.serializePayload;


/**
 * Class that represents an event to store in the google app engine data store.
 *
 * @author Jettro Coenradie
 * @author Allard Buijze
 * @since 1.0
 */
public class EventEntry implements SerializedDomainEventData<String> {

    private static final String AGGREGATE_IDENTIFIER = "aggregateIdentifier";
    private static final String SEQUENCE_NUMBER = "sequenceNumber";
    private static final String SERIALIZED_EVENT = "serializedEvent";
    private static final String TIME_STAMP = "timeStamp";
    private static final String EVENT_TYPE = "eventType";
    private static final String EVENT_REVISION = "eventRevision";
    private static final String META_DATA = "metaData";
    private static final String AGGREGATE_TYPE = "aggregateType";

    private final String eventIdentifier;
    private final String aggregateIdentifier;
    private final long sequenceNumber;
    private final String timeStamp;
    private final String aggregateType;
    private final String serializedEvent;
    private final String eventRevision;
    private final String eventType;
    private final String serializedMetaData;

    /**
     * Constructor used to create a new event entry to store in Mongo
     *
     * @param aggregateType String containing the aggregate type of the event
     * @param event         The actual DomainEvent to store
     * @param serializer    Serializer to use for the event to store
     */
    EventEntry(String aggregateType, DomainEventMessage event, Serializer serializer) {
        this.eventIdentifier = event.getIdentifier();
        this.aggregateType = aggregateType;
        this.aggregateIdentifier = event.getAggregateIdentifier().toString();
        this.sequenceNumber = event.getSequenceNumber();
        SerializedObject<String> serializedObject = serializePayload(event, serializer, String.class);
        this.serializedEvent = serializedObject.getData();
        this.eventType = serializedObject.getType().getName();
        this.eventRevision = serializedObject.getType().getRevision();
        this.serializedMetaData = serializeMetaData(event, serializer, String.class).getData();
        this.timeStamp = event.getTimestamp().toString();
    }

    /**
     * Reconstruct an EventEntry based on the given <code>entity</code>, which contains the k
     *
     * @param entity the entity containing the fields to build the entry with
     */
    EventEntry(Entity entity) {
        this.eventIdentifier = entity.getKey().getName();
        this.aggregateType = (String) entity.getProperty(AGGREGATE_TYPE);
        this.aggregateIdentifier = (String) entity.getProperty(AGGREGATE_IDENTIFIER);
        this.sequenceNumber = (Long) entity.getProperty(SEQUENCE_NUMBER);
        this.serializedEvent = ((Text) entity.getProperty(SERIALIZED_EVENT)).getValue();
        this.timeStamp = (String) entity.getProperty(TIME_STAMP);
        this.eventRevision = (String) entity.getProperty(EVENT_REVISION);
        this.eventType = (String) entity.getProperty(EVENT_TYPE);
        this.serializedMetaData = ((Text) entity.getProperty(META_DATA)).getValue();
    }

    /**
     * Returns the actual DomainEvent from the EventEntry using the provided Serializer.
     *
     * @param actualAggregateIdentifier The actual aggregate identifier instance used to perform the lookup
     * @param serializer                Serializer used to de-serialize the stored DomainEvent
     * @param upcasterChain             Set of upcasters to use when an event needs upcasting before de-serialization
     * @return The actual DomainEventMessage instances stored in this entry
     */
    @SuppressWarnings("unchecked")
    public List<DomainEventMessage> getDomainEvent(Object actualAggregateIdentifier, Serializer serializer,
                                                   UpcasterChain upcasterChain) {
        final SerializedDomainEventUpcastingContext context = new SerializedDomainEventUpcastingContext(this,
                                                                                                        serializer);
        List<SerializedObject> upcastEvents = upcasterChain.upcast(
                new SimpleSerializedObject<String>(serializedEvent, String.class, eventType, eventRevision), context);
        List<DomainEventMessage> messages = new ArrayList<DomainEventMessage>(upcastEvents.size());
        for (SerializedObject upcastEvent : upcastEvents) {
            DomainEventMessage<Object> message = new SerializedDomainEventMessage<Object>(
                    new UpcastSerializedDomainEventData(this,
                                                        actualAggregateIdentifier == null
                                                                ? aggregateIdentifier : actualAggregateIdentifier,
                                                        upcastEvent),
                    serializer);
            // prevents duplicate deserialization of meta data when it has already been access during upcasting
            if (context.getSerializedMetaData().isDeserialized()) {
                message = message.withMetaData(context.getSerializedMetaData().getObject());
            }
            messages.add(message);
        }
        return messages;
    }

    @Override
    public String getEventIdentifier() {
        return eventIdentifier;
    }

    @Override
    public Object getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    /**
     * getter for the sequence number of the event.
     *
     * @return long representing the sequence number of the event
     */
    public long getSequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public DateTime getTimestamp() {
        return new DateTime(timeStamp);
    }

    @Override
    public SerializedObject<String> getMetaData() {
        return new SerializedMetaData<String>(serializedMetaData, String.class);
    }

    @Override
    public SerializedObject<String> getPayload() {
        return new SimpleSerializedObject<String>(serializedEvent, String.class, eventType, eventRevision);
    }

    /**
     * Returns this EventEntry as a Google App Engine Entity.
     *
     * @return A GAE Entity containing the data from this entry
     */
    Entity asEntity() {
        Key key = KeyFactory.createKey(aggregateType, eventIdentifier);
        Entity entity = new Entity(key);
        entity.setProperty(AGGREGATE_IDENTIFIER, aggregateIdentifier);
        entity.setProperty(AGGREGATE_TYPE, aggregateType);
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
                .addFilter(AGGREGATE_TYPE, Query.FilterOperator.EQUAL, type)
                .addFilter(AGGREGATE_IDENTIFIER, Query.FilterOperator.EQUAL, aggregateIdentifier)
                .addFilter(SEQUENCE_NUMBER, Query.FilterOperator.GREATER_THAN_OR_EQUAL, firstSequenceNumber)
                .addSort(SEQUENCE_NUMBER, Query.SortDirection.ASCENDING);
    }

    /**
     * Builds a Query to select the latest snapshot event for a given aggregate
     *
     * @param type                The type identifier of the aggregate
     * @param aggregateIdentifier The identifier of the aggregate
     * @return the Query to find the latest snapshot
     */
    static Query forLastSnapshot(String type, String aggregateIdentifier) {
        return new Query(type)
                .addFilter(AGGREGATE_IDENTIFIER, Query.FilterOperator.EQUAL, aggregateIdentifier)
                .addFilter(AGGREGATE_TYPE, Query.FilterOperator.EQUAL, type)
                .addSort(SEQUENCE_NUMBER, Query.SortDirection.ASCENDING);
    }
}
