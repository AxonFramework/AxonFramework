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

package org.axonframework.eventstore.mongo;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.MetaData;
import org.axonframework.eventstore.mongo.criteria.MongoCriteria;
import org.axonframework.serializer.LazyDeserializingObject;
import org.axonframework.serializer.SerializedDomainEventData;
import org.axonframework.serializer.SerializedDomainEventMessage;
import org.axonframework.serializer.SerializedMetaData;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.SimpleSerializedObject;
import org.axonframework.upcasting.UpcastSerializedDomainEventData;
import org.axonframework.upcasting.UpcasterChain;
import org.axonframework.upcasting.UpcastingContext;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;

import static org.axonframework.serializer.MessageSerializer.serializeMetaData;
import static org.axonframework.serializer.MessageSerializer.serializePayload;

/**
 * Implementation of the StorageStrategy that stores each commit as a single document. The document contains an array
 * containing each separate event.
 * <p/>
 * The structure is as follows:
 * <ul>
 * <li>aggregateIdentifier => [aggregateIdentifier]</li>
 * <li>sequenceNumber => [sequenceNumber of first event]</li>
 * <li>firstSequenceNumber => [sequenceNumber of first event]</li>
 * <li>lastSequenceNumber => [sequenceNumber of last event]</li>
 * <li>timestamp => [timestamp of first event]</li>
 * <li>firstTimeStamp => [timestamp of first event]</li>
 * <li>lastTimeStamp => [timestamp of last event]</li>
 * <li>type => [aggregate type]</li>
 * <li>events => array of:
 * <ul>
 * <li>serializedPayload => [payload of the event]</li>
 * <li>payloadType => [type of the payload]</li>
 * <li>payloadRevision => [revision of the payload]</li>
 * <li>serializedMetaData => [meta data of the event]</li>
 * <li>eventIdentifier => [identifier of the event]</li>
 * <li>sequenceNumber => [sequence number of the event]</li>
 * <li>timestamp => [timestamp of the event]</li>
 * </ul>
 * </li>
 * </ul>
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DocumentPerCommitStorageStrategy implements StorageStrategy {

    private static final int ORDER_ASC = 1;
    private static final int ORDER_DESC = -1;

    @Override
    public DBObject[] createDocuments(String type, Serializer eventSerializer, List<DomainEventMessage> messages) {
        return new DBObject[]{new CommitEntry(type, eventSerializer, messages).asDBObject()};
    }

    @Override
    public DBCursor findEvents(DBCollection collection, String aggregateType, String aggregateIdentifier,
                               long firstSequenceNumber) {
        return collection.find(CommitEntry.forAggregate(aggregateType, aggregateIdentifier, firstSequenceNumber))
                         .sort(new BasicDBObject(CommitEntry.SEQUENCE_NUMBER_PROPERTY, ORDER_ASC));
    }

    @Override
    public DBCursor findEvents(DBCollection collection, MongoCriteria criteria) {
        DBObject filter = criteria == null ? null : criteria.asMongoObject();
        DBObject sort = BasicDBObjectBuilder.start()
                                            .add(CommitEntry.TIME_STAMP_PROPERTY, ORDER_ASC)
                                            .add(CommitEntry.SEQUENCE_NUMBER_PROPERTY, ORDER_ASC)
                                            .get();
        return collection.find(filter).sort(sort);
    }

    @Override
    public List<DomainEventMessage> extractEventMessages(DBObject entry, Object aggregateIdentifier,
                                                         Serializer serializer, UpcasterChain upcasterChain) {
        return new CommitEntry(entry).getDomainEvents(aggregateIdentifier,
                                                      serializer, upcasterChain);
    }

    @Override
    public void ensureIndexes(DBCollection eventsCollection, DBCollection snapshotsCollection) {
        eventsCollection.ensureIndex(new BasicDBObject(CommitEntry.AGGREGATE_IDENTIFIER_PROPERTY, 1)
                                             .append(CommitEntry.AGGREGATE_TYPE_PROPERTY, 1)
                                             .append(CommitEntry.SEQUENCE_NUMBER_PROPERTY, 1),
                                     "uniqueAggregateIndex",
                                     true);
    }

    @Override
    public DBCursor findLastSnapshot(DBCollection collection, String aggregateType, String aggregateIdentifier) {
        DBObject mongoEntry = BasicDBObjectBuilder
                .start()
                .add(CommitEntry.AGGREGATE_IDENTIFIER_PROPERTY, aggregateIdentifier)
                .add(CommitEntry.AGGREGATE_TYPE_PROPERTY, aggregateType)
                .get();
        return collection.find(mongoEntry)
                         .sort(new BasicDBObject(CommitEntry.SEQUENCE_NUMBER_PROPERTY, ORDER_DESC))
                         .limit(1);
    }

    /**
     * Data needed by different types of event logs.
     *
     * @author Allard Buijze
     * @author Jettro Coenradie
     * @since 2.0 (in incubator since 0.7)
     */
    private static final class CommitEntry {

        private static final String AGGREGATE_IDENTIFIER_PROPERTY = "aggregateIdentifier";
        private static final String SEQUENCE_NUMBER_PROPERTY = "sequenceNumber";
        private static final String AGGREGATE_TYPE_PROPERTY = "type";
        private static final String TIME_STAMP_PROPERTY = "timestamp";
        private static final String FIRST_TIME_STAMP_PROPERTY = "firstTimeStamp";
        private static final String LAST_TIME_STAMP_PROPERTY = "lastTimeStamp";
        private static final String FIRST_SEQUENCE_NUMBER_PROPERTY = "firstSequenceNumber";
        private static final String LAST_SEQUENCE_NUMBER_PROPERTY = "lastSequenceNumber";
        private static final String EVENTS_PROPERTY = "events";

        /**
         * Charset used for the serialization is usually UTF-8, which is presented by this constant.
         */
        private final String aggregateIdentifier;
        private final long firstSequenceNumber;
        private final long lastSequenceNumber;
        private final String firstTimestamp;
        private final String lastTimestamp;
        private final String aggregateType;
        private final EventEntry[] eventEntries;

        /**
         * Constructor used to create a new event entry to store in Mongo.
         *
         * @param aggregateType   String containing the aggregate type of the event
         * @param eventSerializer Serializer to use for the event to store
         * @param events          The events contained in this commit
         */
        private CommitEntry(String aggregateType, Serializer eventSerializer, List<DomainEventMessage> events) {
            this.aggregateType = aggregateType;
            this.aggregateIdentifier = events.get(0).getAggregateIdentifier().toString();
            this.firstSequenceNumber = events.get(0).getSequenceNumber();
            this.firstTimestamp = events.get(0).getTimestamp().toString();
            final DomainEventMessage lastEvent = events.get(events.size() - 1);
            this.lastTimestamp = lastEvent.getTimestamp().toString();
            this.lastSequenceNumber = lastEvent.getSequenceNumber();
            eventEntries = new EventEntry[events.size()];
            for (int i = 0, eventsLength = events.size(); i < eventsLength; i++) {
                DomainEventMessage event = events.get(i);
                eventEntries[i] = new EventEntry(eventSerializer, event);
            }
        }

        /**
         * Creates a new CommitEntry based onm data provided by Mongo.
         *
         * @param dbObject Mongo object that contains data to represent an CommitEntry
         */
        @SuppressWarnings("unchecked")
        private CommitEntry(DBObject dbObject) {
            this.aggregateIdentifier = (String) dbObject.get(AGGREGATE_IDENTIFIER_PROPERTY);
            this.firstSequenceNumber = (Long) dbObject.get(FIRST_SEQUENCE_NUMBER_PROPERTY);
            this.lastSequenceNumber = (Long) dbObject.get(LAST_SEQUENCE_NUMBER_PROPERTY);
            this.firstTimestamp = (String) dbObject.get(FIRST_TIME_STAMP_PROPERTY);
            this.lastTimestamp = (String) dbObject.get(LAST_TIME_STAMP_PROPERTY);
            this.aggregateType = (String) dbObject.get(AGGREGATE_TYPE_PROPERTY);
            List<DBObject> entries = (List<DBObject>) dbObject.get(EVENTS_PROPERTY);
            eventEntries = new EventEntry[entries.size()];
            for (int i = 0, entriesSize = entries.size(); i < entriesSize; i++) {
                eventEntries[i] = new EventEntry(entries.get(i));
            }
        }

        /**
         * Returns the actual DomainEvent from the CommitEntry using the provided Serializer.
         *
         * @param actualAggregateIdentifier The actual aggregate identifier instance used to perform the lookup, or
         *                                  <code>null</code> if unknown
         * @param eventSerializer           Serializer used to de-serialize the stored DomainEvent
         * @param upcasterChain             Set of upcasters to use when an event needs upcasting before
         *                                  de-serialization
         * @return The actual DomainEventMessage instances stored in this entry
         */
        @SuppressWarnings("unchecked")
        public List<DomainEventMessage> getDomainEvents(Object actualAggregateIdentifier, Serializer eventSerializer,
                                                        UpcasterChain upcasterChain) {
            List<DomainEventMessage> messages = new ArrayList<DomainEventMessage>();
            for (final EventEntry eventEntry : eventEntries) {
                final EntryBasedUpcastingContext context = new EntryBasedUpcastingContext(this,
                                                                                          eventEntry,
                                                                                          eventSerializer);
                List<SerializedObject> upcastObjects = upcasterChain.upcast(
                        eventEntry.getPayload(), context);
                for (SerializedObject upcastObject : upcastObjects) {
                    DomainEventMessage message = new SerializedDomainEventMessage(
                            new UpcastSerializedDomainEventData(
                                    new DomainEventData(this, eventEntry),
                                    actualAggregateIdentifier == null ? aggregateIdentifier : actualAggregateIdentifier,
                                    upcastObject),
                            eventSerializer);

                    // prevents duplicate deserialization of meta data when it has already been access during upcasting
                    if (context.getSerializedMetaData().isDeserialized()) {
                        message = message.withMetaData(context.getSerializedMetaData().getObject());
                    }

                    messages.add(message);
                }
            }
            return messages;
        }

        public Object getAggregateIdentifier() {
            return aggregateIdentifier;
        }

        /**
         * Returns the current CommitEntry as a mongo DBObject.
         *
         * @return DBObject representing the CommitEntry
         */
        public DBObject asDBObject() {
            final BasicDBList events = new BasicDBList();
            BasicDBObjectBuilder commitBuilder = BasicDBObjectBuilder.start()
                                                                     .add(AGGREGATE_IDENTIFIER_PROPERTY,
                                                                          aggregateIdentifier)
                                                                     .add(SEQUENCE_NUMBER_PROPERTY, firstSequenceNumber)
                                                                     .add(LAST_SEQUENCE_NUMBER_PROPERTY,
                                                                          lastSequenceNumber)
                                                                     .add(FIRST_SEQUENCE_NUMBER_PROPERTY,
                                                                          firstSequenceNumber)
                                                                     .add(TIME_STAMP_PROPERTY, firstTimestamp)
                                                                     .add(FIRST_TIME_STAMP_PROPERTY, firstTimestamp)
                                                                     .add(LAST_TIME_STAMP_PROPERTY, lastTimestamp)
                                                                     .add(AGGREGATE_TYPE_PROPERTY, aggregateType)
                                                                     .add(EVENTS_PROPERTY, events);

            for (EventEntry eventEntry : eventEntries) {
                events.add(eventEntry.asDBObject());
            }
            return commitBuilder.get();
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
                                       .add(CommitEntry.AGGREGATE_IDENTIFIER_PROPERTY, aggregateIdentifier)
                                       .add(CommitEntry.SEQUENCE_NUMBER_PROPERTY, new BasicDBObject("$gte",
                                                                                                    firstSequenceNumber))
                                       .add(CommitEntry.AGGREGATE_TYPE_PROPERTY, type)
                                       .get();
        }

        private static class DomainEventData implements SerializedDomainEventData {

            private final CommitEntry commitEntry;
            private final EventEntry eventEntry;

            public DomainEventData(CommitEntry commitEntry, EventEntry eventEntry) {
                this.commitEntry = commitEntry;
                this.eventEntry = eventEntry;
            }

            @Override
            public String getEventIdentifier() {
                return eventEntry.getEventIdentifier();
            }

            @Override
            public Object getAggregateIdentifier() {
                return commitEntry.getAggregateIdentifier();
            }

            @Override
            public long getSequenceNumber() {
                return eventEntry.getSequenceNumber();
            }

            @Override
            public DateTime getTimestamp() {
                return eventEntry.getTimestamp();
            }

            @Override
            public SerializedObject getMetaData() {
                return eventEntry.getMetaData();
            }

            @Override
            public SerializedObject getPayload() {
                return eventEntry.getPayload();
            }
        }

        private static class EntryBasedUpcastingContext implements UpcastingContext {

            private final EventEntry eventEntry;
            private final Object aggregateIdentifier;
            private final LazyDeserializingObject<MetaData> serializedMetaData;

            public EntryBasedUpcastingContext(CommitEntry commitEntry, EventEntry eventEntry, Serializer serializer) {
                this.eventEntry = eventEntry;
                this.aggregateIdentifier = commitEntry.getAggregateIdentifier();
                this.serializedMetaData = new LazyDeserializingObject<MetaData>(eventEntry.getMetaData(), serializer);
            }

            @Override
            public String getMessageIdentifier() {
                return eventEntry.getEventIdentifier();
            }

            @Override
            public Object getAggregateIdentifier() {
                return aggregateIdentifier;
            }

            @Override
            public Long getSequenceNumber() {
                return eventEntry.getSequenceNumber();
            }

            @Override
            public DateTime getTimestamp() {
                return eventEntry.getTimestamp();
            }

            @Override
            public MetaData getMetaData() {
                return serializedMetaData.getObject();
            }

            public LazyDeserializingObject<MetaData> getSerializedMetaData() {
                return serializedMetaData;
            }
        }
    }

    /**
     * Represents an entry for a single event inside a commit
     */
    private static final class EventEntry {

        private static final String SERIALIZED_PAYLOAD_PROPERTY = "serializedPayload";
        private static final String PAYLOAD_TYPE_PROPERTY = "payloadType";
        private static final String PAYLOAD_REVISION_PROPERTY = "payloadRevision";
        private static final String META_DATA_PROPERTY = "serializedMetaData";
        private static final String EVENT_IDENTIFIER_PROPERTY = "eventIdentifier";
        private static final String EVENT_SEQUENCE_NUMBER_PROPERTY = "sequenceNumber";
        private static final String EVENT_TIMESTAMP_PROPERTY = "timestamp";

        private final Object serializedPayload;
        private final String payloadType;
        private final String payloadRevision;
        private final Object serializedMetaData;
        private final String eventIdentifier;
        private final long sequenceNumber;
        private final String timestamp;

        private EventEntry(Serializer serializer, DomainEventMessage event) {
            this.eventIdentifier = event.getIdentifier();
            Class<?> serializationTarget = String.class;
            if (serializer.canSerializeTo(DBObject.class)) {
                serializationTarget = DBObject.class;
            }
            SerializedObject serializedPayloadObject = serializePayload(event, serializer, serializationTarget);
            SerializedObject serializedMetaDataObject = serializeMetaData(event, serializer, serializationTarget);

            this.serializedPayload = serializedPayloadObject.getData();
            this.payloadType = serializedPayloadObject.getType().getName();
            this.payloadRevision = serializedPayloadObject.getType().getRevision();
            this.serializedMetaData = serializedMetaDataObject.getData();
            this.sequenceNumber = event.getSequenceNumber();
            this.timestamp = event.getTimestamp().toString();
        }

        private EventEntry(DBObject dbObject) {
            this.serializedPayload = dbObject.get(SERIALIZED_PAYLOAD_PROPERTY);
            this.payloadType = (String) dbObject.get(PAYLOAD_TYPE_PROPERTY);
            this.payloadRevision = (String) dbObject.get(PAYLOAD_REVISION_PROPERTY);
            this.serializedMetaData = dbObject.get(META_DATA_PROPERTY);
            this.eventIdentifier = (String) dbObject.get(EVENT_IDENTIFIER_PROPERTY);
            this.sequenceNumber = (Long) dbObject.get(EVENT_SEQUENCE_NUMBER_PROPERTY);
            this.timestamp = (String) dbObject.get(EVENT_TIMESTAMP_PROPERTY);
        }

        public Class<?> getRepresentationType() {
            Class<?> representationType = String.class;
            if (serializedPayload instanceof DBObject) {
                representationType = DBObject.class;
            }
            return representationType;
        }

        public String getEventIdentifier() {
            return eventIdentifier;
        }

        @SuppressWarnings("unchecked")
        public SerializedObject getMetaData() {
            return new SerializedMetaData(serializedMetaData, getRepresentationType());
        }

        @SuppressWarnings("unchecked")
        public SerializedObject getPayload() {
            return new SimpleSerializedObject(serializedPayload, getRepresentationType(), payloadType, payloadRevision);
        }

        public long getSequenceNumber() {
            return sequenceNumber;
        }

        public DateTime getTimestamp() {
            return new DateTime(timestamp);
        }

        public DBObject asDBObject() {
            final BasicDBObjectBuilder entryBuilder = BasicDBObjectBuilder.start();
            return entryBuilder.add(SERIALIZED_PAYLOAD_PROPERTY, serializedPayload)
                               .add(PAYLOAD_TYPE_PROPERTY, payloadType)
                               .add(PAYLOAD_REVISION_PROPERTY, payloadRevision)
                               .add(EVENT_TIMESTAMP_PROPERTY, timestamp)
                               .add(EVENT_SEQUENCE_NUMBER_PROPERTY, sequenceNumber)
                               .add(META_DATA_PROPERTY, serializedMetaData)
                               .get();
        }
    }
}
