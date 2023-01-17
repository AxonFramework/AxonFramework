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

package org.axonframework.eventsourcing.eventstore.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.axonframework.eventhandling.AbstractSequencedDomainEventEntry;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.serialization.Serializer;

/**
 * Default implementation of a tracked domain event entry. This implementation is used by the {@link
 * JpaEventStorageEngine} to store events. Event payload and metadata are serialized to a byte array.
 *
 * @author Rene de Waele
 */
@Entity
@Table(indexes = @Index(columnList = "aggregateIdentifier,sequenceNumber", unique = true))
@javax.persistence.Entity
@javax.persistence.Table(indexes = @javax.persistence.Index(columnList = "aggregateIdentifier,sequenceNumber", unique = true))
public class DomainEventEntry extends AbstractSequencedDomainEventEntry<byte[]> {

    /**
     * Construct a new default domain event entry from a published domain event message to enable storing the event or
     * sending it to a remote location. The event payload and metadata will be serialized to a byte array.
     * <p>
     * The given {@code serializer} will be used to serialize the payload and metadata in the given {@code eventMessage}.
     * The type of the serialized data will be the same as the given {@code contentType}.
     *
     * @param eventMessage The event message to convert to a serialized event entry
     * @param serializer   The serializer to convert the event
     */
    public DomainEventEntry(DomainEventMessage<?> eventMessage, Serializer serializer) {
        super(eventMessage, serializer, byte[].class);
    }

    /**
     * Default constructor required by JPA
     */
    protected DomainEventEntry() {
    }
}
