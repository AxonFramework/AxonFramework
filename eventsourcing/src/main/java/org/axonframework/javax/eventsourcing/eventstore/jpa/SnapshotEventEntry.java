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

package org.axonframework.javax.eventsourcing.eventstore.jpa;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.javax.eventsourcing.eventstore.AbstractSnapshotEventEntry;
import org.axonframework.serialization.Serializer;

import javax.persistence.Entity;

/**
 * Default implementation of an event entry containing a serialized snapshot of an aggregate. This implementation is
 * used by the {@link JpaEventStorageEngine} to store snapshot events. Event payload and metadata are serialized to a
 * byte array.
 *
 * @author Rene de Waele
 * @deprecated in favor of using {@link org.axonframework.eventsourcing.eventstore.jpa.SnapshotEventEntry} which moved
 * to jakarta.
 */
@Deprecated
@Entity
public class SnapshotEventEntry extends AbstractSnapshotEventEntry<byte[]> {

    /**
     * Construct a new default snapshot event entry from an aggregate. The snapshot payload and metadata will be
     * serialized to a byte array.
     * <p>
     * The given {@code serializer} will be used to serialize the payload and metadata in the given
     * {@code eventMessage}. The type of the serialized data will be the same as the given {@code contentType}.
     *
     * @param eventMessage The snapshot event message to convert to a serialized event entry
     * @param serializer   The serializer to convert the snapshot event
     */
    public SnapshotEventEntry(DomainEventMessage<?> eventMessage, Serializer serializer) {
        super(eventMessage, serializer, byte[].class);
    }

    /**
     * Default constructor required by JPA
     */
    protected SnapshotEventEntry() {
    }
}
