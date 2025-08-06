/*
 * Copyright (c) 2010-2025. Axon Framework
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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.axonframework.eventhandling.AbstractSequencedDomainEventEntry;
import org.axonframework.eventhandling.DomainEventData;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.serialization.Serializer;

/**
 * Default implementation of the {@link org.axonframework.eventhandling.DomainEventData}, setting the stored
 * {@link #payload()} type to {@code byte[]}.
 *
 * @author Allard Buijze
 * @author Rene de Waele
 * @author Steven van Beelen
 * @since 0.5.0
 */
@Entity
@Table(indexes = @Index(columnList = "aggregateIdentifier,sequenceNumber", unique = true))
public class DomainEventEntry
        extends AbstractSequencedDomainEventEntry<byte[]>
        implements DomainEventData<byte[]> {

    /**
     * Construct a new default domain event entry from a published domain event message to enable storing the event or
     * sending it to a remote location. The event payload and metadata will be serialized to a byte array.
     * <p>
     * The given {@code serializer} will be used to serialize the payload and metadata in the given
     * {@code eventMessage}. The type of the serialized data will be the same as the given {@code contentType}.
     *
     * @param eventMessage The event message to convert to a serialized event entry.
     * @param serializer   The serializer to convert the event.
     * @deprecated In favor of the
     * {@link DomainEventEntry#DomainEventEntry(String, String, String, byte[], byte[], Object, String, String, long)}
     * constructor.
     */
    @Deprecated
    public DomainEventEntry(DomainEventMessage<?> eventMessage, Serializer serializer) {
        super(eventMessage, serializer, byte[].class);
    }

    /**
     * Constructs a {@code DomainEventEntry} with the given parameters.
     *
     * @param eventIdentifier         The identifier of the event.
     * @param payloadType             The fully qualified class name or alias of the event payload.
     * @param payloadRevision         The revision of the event payload.
     * @param payload                 The serialized payload.
     * @param metaData                The serialized metadata.
     * @param timestamp               The time at which the event was originally created.
     * @param aggregateType           The type of aggregate that published this event.
     * @param aggregateIdentifier     The identifier of the aggregate that published this event.
     * @param aggregateSequenceNumber The sequence number of the event in the aggregate.
     */
    public DomainEventEntry(@Nonnull String eventIdentifier,
                            @Nonnull String payloadType,
                            @Nonnull String payloadRevision,
                            @Nullable byte[] payload,
                            @Nullable byte[] metaData,
                            @Nonnull Object timestamp,
                            @Nonnull String aggregateType,
                            @Nonnull String aggregateIdentifier,
                            long aggregateSequenceNumber) {
        super(eventIdentifier,
              payloadType,
              payloadRevision,
              payload,
              metaData,
              timestamp,
              aggregateType,
              aggregateIdentifier,
              aggregateSequenceNumber);
    }

    /**
     * Default constructor required by JPA.
     */
    protected DomainEventEntry() {
        // Default constructor required by JPA.
    }
}
