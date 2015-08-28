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

import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.serializer.SerializedObject;

import javax.persistence.Entity;
import java.time.Instant;

/**
 * JPA compliant wrapper around a DomainEvent. It stores a DomainEvent by extracting some of the information needed to
 * base searches on, and stores the {@link DomainEventMessage} itself as a serialized object
 * using an {@link org.axonframework.serializer.Serializer}
 *
 * @author Allard Buijze
 * @since 0.5
 */
@Entity
public class DomainEventEntry extends AbstractEventEntry {

    /**
     * Default constructor, as required by JPA specification. Do not use directly!
     */
    protected DomainEventEntry() {
    }

    /**
     * Initialize an Event entry for the given <code>event</code>.
     *
     * @param event    The event to store in the eventstore
     * @param payload  The serialized version of the Event
     * @param metaData The serialized metaData of the Event
     */
    public DomainEventEntry(DomainEventMessage<?> event,
                            SerializedObject<byte[]> payload,
                            SerializedObject<byte[]> metaData) {
        super(event, payload, metaData);
    }

    /**
     * Initialize an Event entry for the given <code>event</code>.
     *
     * @param event    The event to store in the eventstore
     * @param dateTime The timestamp to store in the Event Store
     * @param payload  The serialized version of the Event
     * @param metaData The serialized metaData of the Event
     */
    public DomainEventEntry(DomainEventMessage<?> event, Instant dateTime,
                            SerializedObject<byte[]> payload,
                            SerializedObject<byte[]> metaData) {
        super(event, dateTime, payload, metaData);
    }


}
