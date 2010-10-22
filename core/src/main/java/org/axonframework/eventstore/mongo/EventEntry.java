/*
 * Copyright (c) 2010. Axon Framework
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
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.StringAggregateIdentifier;
import org.axonframework.eventstore.EventSerializer;

import java.nio.charset.Charset;

/**
 * Data needed by different types of event logs.
 *
 * @author Allard Buijze
 * @author Jettro Coenradie
 * @since 0.7
 */
class EventEntry {

    public static final String AGGREGATE_IDENTIFIER_PROPERTY = "aggregateIdentifier";
    public static final String SEQUENCE_NUMBER_PROPERTY = "sequenceNumber";
    public static final String TIME_STAMP_PROPERTY = "timeStamp";
    public static final String AGGREGATE_TYPE_PROPERTY = "type";
    public static final String SERIALIZED_EVENT_PROPERTY = "serializedEvent";

    public static final BasicDBObject INDEX = new BasicDBObject(AGGREGATE_IDENTIFIER_PROPERTY, 1)
            .append(SERIALIZED_EVENT_PROPERTY, 1);

    protected static final Charset UTF8 = Charset.forName("UTF-8");

    private final String aggregateIdentifier;
    private final long sequenceNumber;
    private final String timeStamp;
    private final String aggregateType;
    private final String serializedEvent;

    EventEntry(String aggregateType, DomainEvent event, EventSerializer eventSerializer) {
        this.aggregateType = aggregateType;
        this.aggregateIdentifier = event.getAggregateIdentifier().asString();
        this.sequenceNumber = event.getSequenceNumber();
        this.serializedEvent = new String(eventSerializer.serialize(event));
        this.timeStamp = event.getTimestamp().toString();
    }

    EventEntry(DBObject dbObject) {
        this.aggregateIdentifier = (String) dbObject.get(AGGREGATE_IDENTIFIER_PROPERTY);
        this.sequenceNumber = (Long) dbObject.get(SEQUENCE_NUMBER_PROPERTY);
        this.serializedEvent = (String) dbObject.get(SERIALIZED_EVENT_PROPERTY);
        this.timeStamp = (String) dbObject.get(TIME_STAMP_PROPERTY);
        this.aggregateType = (String) dbObject.get(AGGREGATE_TYPE_PROPERTY);
    }

    public DomainEvent getDomainEvent(EventSerializer eventSerializer) {
        return eventSerializer.deserialize(serializedEvent.getBytes(UTF8));
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public AggregateIdentifier getAggregateIdentifier() {
        return new StringAggregateIdentifier(aggregateIdentifier);
    }

    public DBObject asDBObject() {
        return BasicDBObjectBuilder.start()
                .add(AGGREGATE_IDENTIFIER_PROPERTY, aggregateIdentifier)
                .add(SEQUENCE_NUMBER_PROPERTY, sequenceNumber)
                .add(SERIALIZED_EVENT_PROPERTY, serializedEvent)
                .add(TIME_STAMP_PROPERTY, timeStamp)
                .add(AGGREGATE_TYPE_PROPERTY, aggregateType)
                .get();
    }

    public static DBObject forAggregate(String type, String aggregateIdentifier, long firstSequenceNumber) {
        return BasicDBObjectBuilder.start()
                .add(EventEntry.AGGREGATE_IDENTIFIER_PROPERTY, aggregateIdentifier)
                .add(EventEntry.SEQUENCE_NUMBER_PROPERTY, new BasicDBObject("$gte", firstSequenceNumber))
                .add(EventEntry.AGGREGATE_TYPE_PROPERTY, type)
                .get();
    }
}