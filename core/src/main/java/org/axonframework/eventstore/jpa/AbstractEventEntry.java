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

import java.io.Serializable;
import java.util.Arrays;
import javax.persistence.Basic;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Lob;
import javax.persistence.MappedSuperclass;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

/**
 * Data needed by different types of event logs.
 *
 * @author Allard Buijze
 * @since 0.5
 */
@MappedSuperclass
@Table(uniqueConstraints =
       @UniqueConstraint(columnNames = {"eventIdentifier"}))
@IdClass(AbstractEventEntry.PK.class)
public abstract class AbstractEventEntry implements SerializedDomainEventData {

    @Id
    private String type;
    @Id
    private String aggregateIdentifier;
    @Id
    private long sequenceNumber;
    @Basic(optional = false)
    private String eventIdentifier;
    @Basic(optional = false)
    private String timeStamp;
    @Basic(optional = false)
    private String payloadType;
    @Basic
    private String payloadRevision;
    @Basic
    @Lob
    private byte[] metaData;
    @Basic(optional = false)
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

    @Override
    public SerializedObject<byte[]> getPayload() {
        return new SimpleSerializedObject<byte[]>(payload, byte[].class, payloadType, payloadRevision);
    }

    @Override
    public SerializedObject<byte[]> getMetaData() {
        return new SerializedMetaData<byte[]>(metaData, byte[].class);
    }

    /**
     * Primary key definition of the AbstractEventEntry class. Is used by JPA to support composite primary keys.
     */
    @SuppressWarnings("UnusedDeclaration")
    public static class PK implements Serializable {

        private static final long serialVersionUID = 9182347799552520594L;

        private String aggregateIdentifier;
        private String type;
        private long sequenceNumber;

        /**
         * Constructor for JPA. Not to be used directly
         */
        PK() {
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            PK pk = (PK) o;

            if (sequenceNumber != pk.sequenceNumber) {
                return false;
            }
            if (!aggregateIdentifier.equals(pk.aggregateIdentifier)) {
                return false;
            }
            if (!type.equals(pk.type)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = aggregateIdentifier.hashCode();
            result = 31 * result + type.hashCode();
            result = 31 * result + (int) (sequenceNumber ^ (sequenceNumber >>> 32));
            return result;
        }

        @Override
        public String toString() {
            return "PK{" +
                    "type='" + type + '\'' +
                    ", aggregateIdentifier='" + aggregateIdentifier + '\'' +
                    ", sequenceNumber=" + sequenceNumber +
                    '}';
        }
    }
}
