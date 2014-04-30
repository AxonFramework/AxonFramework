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

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.serializer.SerializedObject;

import javax.persistence.Entity;

/**
 * JPA compatible entry that stores data required for the use of snapshot events.
 *
 * @author Allard Buijze
 * @since 0.5
 */
@Entity
public class SnapshotEventEntry extends AbstractEventEntry {

    /**
     * Default constructor, as required by JPA specification. Do not use directly!
     */
    protected SnapshotEventEntry() {
    }

    /**
     * Initialize an Event entry for the given <code>event</code>.
     *
     * @param type     The type identifier of the aggregate root the event belongs to
     * @param event    The event to store in the eventstore
     * @param payload  The serialized version of the Event
     * @param metaData The serialized metaData of the Event
     */
    public SnapshotEventEntry(String type, DomainEventMessage event,
                              SerializedObject<byte[]> payload,
                              SerializedObject<byte[]> metaData) {
        super(type, event, payload, metaData);
    }
}
