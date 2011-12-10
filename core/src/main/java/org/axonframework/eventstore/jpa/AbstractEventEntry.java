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

package org.axonframework.eventstore.jpa;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.eventstore.SerializedDomainEventData;
import org.axonframework.eventstore.SerializedDomainEventMessage;
import org.axonframework.serializer.SerializedMetaData;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.SimpleSerializedObject;
import org.joda.time.DateTime;

import java.util.Arrays;
import javax.persistence.Basic;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.MappedSuperclass;
import javax.persistence.UniqueConstraint;

/**
 * Data needed by different types of event logs.
 *
 * @author Allard Buijze
 * @since 0.5
 */
@MappedSuperclass
@UniqueConstraint(columnNames = {"eventIdentifier"})
abstract class AbstractEventEntry implements SerializedDomainEventData {

    @Id
    @GeneratedValue
    private Long id;
    @Basic
    private String eventIdentifier;
    @Basic
    private String aggregateIdentifier;
    @Basic
    private long sequenceNumber;
    @Basic
    private String timeStamp;
    @Basic
    private String type;
    @Basic
    private String payloadType;
    @Basic
    private int payloadRevision;
    @Basic
    @Lob
    private byte[] metaData;
    @Basic
    @Lob
    private byte[] payload;

    /**
     * Initialize an Event entry for the given <code>event</code>.
     *
     * @param type     The type identifier of the aggregate root the event belongs to
     * @param event    The event to store in the eventstore
     * @param payload  The serialized payload of the Event
     * @param metaData The serialized metaData of the Event
     */
    protected AbstractEventEntry(String type, DomainEventMessage event,
                                 SerializedObject payload, SerializedObject metaData) {
        this.eventIdentifier = event.getIdentifier();
        this.type = type;
        this.payloadType = payload.getType().getName();
        this.payloadRevision = payload.getType().getRevision();
        this.payload = payload.getData();
        this.aggregateIdentifier = event.getAggregateIdentifier().toString();
        this.sequenceNumber = event.getSequenceNumber();
        this.metaData = Arrays.copyOf(metaData.getData(), metaData.getData().length);
        this.timeStamp = event.getTimestamp().toString();
    }

    /**
     * Default constructor, as required by JPA specification. Do not use directly!
     */
    protected AbstractEventEntry() {
    }

    /**
     * Reconstructs the DomainEvent using the given <code>eventSerializer</code>.
     *
     * @param eventSerializer The Serializer to deserialize the DomainEvent with.
     * @return The deserialized domain event
     */
    public DomainEventMessage<?> getDomainEvent(Serializer eventSerializer) {
        return new SerializedDomainEventMessage<Object>(eventIdentifier, aggregateIdentifier,
                                                        sequenceNumber, new DateTime(timeStamp),
                                                        new SimpleSerializedObject(payload,
                                                                                   payloadType,
                                                                                   payloadRevision),
                                                        new SerializedMetaData(metaData), eventSerializer,
                                                        eventSerializer);
    }

    /**
     * Returns the Aggregate Identifier of the associated event.
     *
     * @return the Aggregate Identifier of the associated event.
     */
    public Object getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    /**
     * Returns the type identifier of the aggregate.
     *
     * @return the type identifier of the aggregate.
     */
    public String getType() {
        return type;
    }

    /**
     * Returns the sequence number of the associated event.
     *
     * @return the sequence number of the associated event.
     */
    public long getSequenceNumber() {
        return sequenceNumber;
    }

    /**
     * Returns the time stamp of the associated event.
     *
     * @return the time stamp of the associated event.
     */
    public DateTime getTimestamp() {
        return new DateTime(timeStamp);
    }

    public String getEventIdentifier() {
        return eventIdentifier;
    }

    public String getTimeStamp() {
        return timeStamp;
    }

    public SerializedObject getPayload() {
        return new SimpleSerializedObject(payload, payloadType, payloadRevision);
    }

    public SerializedObject getMetaData() {
        return new SerializedMetaData(metaData);
    }
}
