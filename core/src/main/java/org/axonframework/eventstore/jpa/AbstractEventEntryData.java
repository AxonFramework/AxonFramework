/*
 * Copyright (c) 2010-2014. Axon Framework
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

import org.axonframework.serializer.SerializedDomainEventData;
import org.axonframework.serializer.SerializedType;
import org.axonframework.serializer.SimpleSerializedType;

import java.io.Serializable;
import java.time.ZonedDateTime;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.MappedSuperclass;

/**
 * Abstract JPA Entity, which defines all fields except for the payload and metaData field. Subclasses should declare
 * those fields and define the format in which the data must be stored.
 *
 * @param <T> The data type used to store the payload
 * @author Allard Buijze
 * @since 2.3
 */
@MappedSuperclass
@IdClass(AbstractEventEntryData.PK.class)
public abstract class AbstractEventEntryData<T> implements SerializedDomainEventData<T> {

    @Id
    private String type;
    @Id
    private String aggregateIdentifier;
    @Id
    private long sequenceNumber;
    @Column(nullable = false, unique = true)
    private String eventIdentifier;
    @Basic(optional = false)
    private String timeStamp;
    @Basic(optional = false)
    private String payloadType;
    @Basic
    private String payloadRevision;

    /**
     * Initializes the fields in this entity using the values provided in the given parameters.
     *
     * @param eventIdentifier     The identifier of the event.
     * @param type                The type identifier of the aggregate that published the event
     * @param aggregateIdentifier The identifier of the aggregate that published the event
     * @param sequenceNumber      The sequence number of the event
     * @param timestamp           The timestamp of the creation of the event
     * @param payloadType         The type of payload contained in the event
     */
    public AbstractEventEntryData(String eventIdentifier, String type,
                                  String aggregateIdentifier, long sequenceNumber, ZonedDateTime timestamp,
                                  SerializedType payloadType) {
        this.eventIdentifier = eventIdentifier;
        this.type = type;
        this.payloadType = payloadType.getName();
        this.payloadRevision = payloadType.getRevision();
        this.aggregateIdentifier = aggregateIdentifier;
        this.sequenceNumber = sequenceNumber;
        this.timeStamp = timestamp.toString();
    }

    /**
     * Constructor required by JPA.
     */
    protected AbstractEventEntryData() {
    }

    @Override
    public String getEventIdentifier() {
        return eventIdentifier;
    }

    /**
     * Returns the Aggregate Identifier of the associated event.
     *
     * @return the Aggregate Identifier of the associated event.
     */
    @Override
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
    public ZonedDateTime getTimestamp() {
        return ZonedDateTime.parse(timeStamp);
    }

    /**
     * Returns the payload type of the event message stored in this entry.
     *
     * @return the payload type of the event message stored in this entry
     */
    protected SerializedType getPayloadType() {
        return new SimpleSerializedType(payloadType, payloadRevision);
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
        public PK() {
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
            return "PK{type='" + type + '\''
                    + ", aggregateIdentifier='" + aggregateIdentifier + '\''
                    + ", sequenceNumber=" + sequenceNumber
                    + '}';
        }
    }
}
