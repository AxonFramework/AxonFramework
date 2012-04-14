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
import org.axonframework.serializer.SerializedDomainEventData;
import org.axonframework.serializer.SerializedMetaData;
import org.axonframework.serializer.SerializedObject;
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
    private String payloadRevision;
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
     * @param event    The event to store in the EventStore
     * @param payload  The serialized payload of the Event
     * @param metaData The serialized metaData of the Event
     */
    protected AbstractEventEntry(String type, DomainEventMessage event,
                                 SerializedObject<byte[]> payload, SerializedObject<byte[]> metaData) {
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
    @Override
    public long getSequenceNumber() {
        return sequenceNumber;
    }

    /**
     * Returns the time stamp of the associated event.
     *
     * @return the time stamp of the associated event.
     */
    @Override
    public DateTime getTimestamp() {
        return new DateTime(timeStamp);
    }

    @Override
    public String getEventIdentifier() {
        return eventIdentifier;
    }

    /**
     * Returns the database-generated identifier for this entry.
     *
     * @return the database-generated identifier for this entry
     */
    public Long getId() {
        return id;
    }

    @Override
    public SerializedObject<byte[]> getPayload() {
        return new SimpleSerializedObject<byte[]>(payload, byte[].class, payloadType, payloadRevision);
    }

    @Override
    public SerializedObject<byte[]> getMetaData() {
        return new SerializedMetaData<byte[]>(metaData, byte[].class);
    }
}
