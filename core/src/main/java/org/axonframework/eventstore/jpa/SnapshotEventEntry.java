/*
 * Copyright (c) 2010. Axon Framework
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

import org.axonframework.domain.DomainEvent;
import org.axonframework.eventstore.EventSerializer;

import javax.persistence.Entity;

/**
 * JPA compatible entry that stores data required for the use of snapshot events.
 *
 * @author Allard Buijze
 * @since 0.5
 */
@Entity
class SnapshotEventEntry extends AbstractEventEntry {

    /**
     * Default constructor, as required by JPA specification. Do not use directly!
     */
    protected SnapshotEventEntry() {
    }

    /**
     * Initialize a snapshot event entry using given <code>type</code>, <code>event</code> and
     * <code>eventSerializer</code>.
     *
     * @param type            The type of the aggregate the snapshot event belongs to.
     * @param event           The actual snapshot event
     * @param eventSerializer The serializer that must be used to serialize the snapshot event for storage
     */
    public SnapshotEventEntry(String type, DomainEvent event, EventSerializer eventSerializer) {
        super(type, event, eventSerializer);
    }
}
