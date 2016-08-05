/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore.jpa.legacy;

import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.TrackedEventData;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.eventsourcing.eventstore.legacy.AbstractLegacyDomainEventEntry;
import org.axonframework.eventsourcing.eventstore.legacy.LegacyTrackingToken;
import org.axonframework.serialization.Serializer;

import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;

/**
 * Legacy implementation of a serialized domain event with a format that is compatible with that of events stored using
 * Axon 2. These entries have a primary key that is a combination of the aggregate's identifier, sequence number and
 * type.
 * <p>
 * This implementation is used by the {@link LegacyJpaEventStorageEngine} to store events. Event payload and metadata
 * are serialized to a byte array.
 *
 * @author Rene de Waele
 */
@Entity
@Table(name = "DomainEventEntry", indexes = {@Index(columnList = "timeStamp,sequenceNumber,aggregateIdentifier")})
public class LegacyDomainEventEntry extends AbstractLegacyDomainEventEntry<byte[]> implements TrackedEventData<byte[]> {

    /**
     * Construct a new legacy domain event entry from a published domain event message to enable storing the event or
     * sending it to a remote location. The event payload and metadata will be serialized to a byte array.
     * <p>
     * The given {@code serializer} will be used to serialize the payload and metadata in the given {@code
     * eventMessage}. The type of the serialized data will be the same as the given {@code contentType}.
     *
     * @param eventMessage The event message to convert to a serialized event entry
     * @param serializer   The serializer to convert the event
     */
    public LegacyDomainEventEntry(DomainEventMessage<?> eventMessage, Serializer serializer) {
        super(eventMessage, serializer, byte[].class);
    }

    /**
     * Default constructor required by JPA
     */
    protected LegacyDomainEventEntry() {
    }

    @Override
    public TrackingToken trackingToken() {
        return new LegacyTrackingToken(getTimestamp(), getAggregateIdentifier(), getSequenceNumber());
    }

}
