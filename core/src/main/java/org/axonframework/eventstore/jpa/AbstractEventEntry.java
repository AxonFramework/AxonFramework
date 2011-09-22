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

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.StringAggregateIdentifier;
import org.axonframework.serializer.Serializer;
import org.joda.time.DateTime;

import java.util.Arrays;
import javax.persistence.Basic;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.MappedSuperclass;

/**
 * Data needed by different types of event logs.
 *
 * @author Allard Buijze
 * @since 0.5
 */
@MappedSuperclass
abstract class AbstractEventEntry {

    @Id
    @GeneratedValue
    private Long id;
    @Basic
    private String aggregateIdentifier;
    @Basic
    private long sequenceNumber;
    @Basic
    private String timeStamp;
    @Basic
    private String type;
    @Basic
    @Lob
    private byte[] serializedEvent;

    /**
     * Initialize an Event entry for the given <code>event</code>, to be serialized using the given
     * <code>serializer</code>.
     *
     * @param type            The type identifier of the aggregate root the event belongs to
     * @param event           The event to store in the eventstore
     * @param serializedEvent The serialized version of the Event
     */
    protected AbstractEventEntry(String type, DomainEvent event, byte[] serializedEvent) {
        this.type = type;
        this.aggregateIdentifier = event.getAggregateIdentifier().asString();
        this.sequenceNumber = event.getSequenceNumber();
        this.serializedEvent = Arrays.copyOf(serializedEvent, serializedEvent.length);
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
     * @param eventSerializer The EventSerializer to deserialize the DomainEvent with.
     * @return The deserialized domain event
     */
    public DomainEvent getDomainEvent(Serializer<? super DomainEvent> eventSerializer) {
        return (DomainEvent) eventSerializer.deserialize(serializedEvent);
    }

    /**
     * Returns the unique identifier of this entry. Returns <code>null</code> if the entry has not been persisted.
     *
     * @return the unique identifier of this entry
     */
    public Long getId() {
        return id;
    }

    /**
     * Returns the Aggregate Identifier of the associated event.
     *
     * @return the Aggregate Identifier of the associated event.
     */
    public AggregateIdentifier getAggregateIdentifier() {
        return new StringAggregateIdentifier(aggregateIdentifier);
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
}
