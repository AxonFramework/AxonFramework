/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore;

import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.MappedSuperclass;
import org.axonframework.eventhandling.AbstractEventEntry;
import org.axonframework.eventhandling.DomainEventData;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.serialization.Serializer;

import java.io.Serializable;
import java.util.Objects;

/**
 * Abstract base class of a serialized snapshot event storing the state of an aggregate. If JPA is used these entries
 * have a primary key that is a combination of the aggregate's identifier, sequence number and type.
 *
 * @author Rene de Waele
 */
@MappedSuperclass
@IdClass(AbstractSnapshotEventEntry.PK.class)
@javax.persistence.MappedSuperclass
@javax.persistence.IdClass(AbstractSnapshotEventEntry.PK.class)
public abstract class AbstractSnapshotEventEntry<T> extends AbstractEventEntry<T> implements DomainEventData<T> {

    @Id
    @javax.persistence.Id
    private String aggregateIdentifier;
    @Id
    @javax.persistence.Id
    private long sequenceNumber;
    @Id
    @javax.persistence.Id
    private String type;

    /**
     * Construct a new event entry from a published domain event message to enable storing the event or sending it to a
     * remote location.
     * <p>
     * The given {@code serializer} will be used to serialize the payload and metadata in the given {@code
     * eventMessage}. The type of the serialized data will be the same as the given {@code contentType}.
     *
     * @param eventMessage The event message to convert to a serialized event entry
     * @param serializer   The serializer to convert the event
     * @param contentType  The data type of the payload and metadata after serialization
     */
    public AbstractSnapshotEventEntry(DomainEventMessage<?> eventMessage, Serializer serializer, Class<T> contentType) {
        super(eventMessage, serializer, contentType);
        type = eventMessage.getType();
        aggregateIdentifier = eventMessage.getAggregateIdentifier();
        sequenceNumber = eventMessage.getSequenceNumber();
    }

    /**
     * Reconstruct an event entry from a stored object.
     *
     * @param type                The type of aggregate that published this event
     * @param aggregateIdentifier The identifier of the aggregate that published this event
     * @param sequenceNumber      The sequence number of the event in the aggregate
     * @param eventIdentifier     The identifier of the event
     * @param timestamp           The time at which the event was originally created
     * @param payloadType         The fully qualified class name or alias of the event payload
     * @param payloadRevision     The revision of the event payload
     * @param payload             The serialized payload
     * @param metaData            The serialized metadata
     */
    public AbstractSnapshotEventEntry(String type, String aggregateIdentifier, long sequenceNumber,
                                      String eventIdentifier, Object timestamp, String payloadType,
                                      String payloadRevision, T payload, T metaData) {
        super(eventIdentifier, timestamp, payloadType, payloadRevision, payload, metaData);
        this.type = type;
        this.aggregateIdentifier = aggregateIdentifier;
        this.sequenceNumber = sequenceNumber;
    }

    /**
     * Default constructor required by JPA
     */
    protected AbstractSnapshotEventEntry() {
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    @Override
    public long getSequenceNumber() {
        return sequenceNumber;
    }

    /**
     * Primary key definition of the AbstractEventEntry class. Is used by JPA to support composite primary keys.
     */
    @SuppressWarnings("UnusedDeclaration")
    public static class PK implements Serializable {

        private static final long serialVersionUID = 9182347799552520594L;

        private String aggregateIdentifier;
        private long sequenceNumber;
        private String type;

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
            return sequenceNumber == pk.sequenceNumber && Objects.equals(aggregateIdentifier, pk.aggregateIdentifier) &&
                    Objects.equals(type, pk.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(aggregateIdentifier, type, sequenceNumber);
        }

        @Override
        public String toString() {
            return "PK{type='" + type + '\'' + ", aggregateIdentifier='" + aggregateIdentifier + '\'' +
                    ", sequenceNumber=" + sequenceNumber + '}';
        }
    }

}
