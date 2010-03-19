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

package org.axonframework.core.repository.eventsourcing;

import org.axonframework.core.DomainEvent;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Represents the relevant information of a snapshot event in regard to the event log. It combines the actual event (in
 * serialized form) and the offset in bytes to use when reading from the event log.
 *
 * @author Allard Buijze
 * @since 0.5
 */
class SnapshotEntry {

    private final byte[] serializedEvent;
    private final int offset;

    /**
     * Instantiate a SnapshotEntry for the given <code>serializedEvent</code>, which allows the given
     * <code>offset</code>. The <code>serializedEvent</code> is not stored directly, instead, a copy is used.
     *
     * @param serializedEvent The bytes representing the serialized event object
     * @param offset          The offset that the event allows in the event log
     */
    public SnapshotEntry(byte[] serializedEvent, int offset) {
        this.offset = offset;
        this.serializedEvent = Arrays.copyOf(serializedEvent, serializedEvent.length);
    }

    /**
     * Deserializes the event using the given serializer, and returns it.
     *
     * @param eventSerializer The serializer to use
     * @return the deserialized domain event
     */
    public DomainEvent getSerializedEvent(EventSerializer eventSerializer) {
        return eventSerializer.deserialize(serializedEvent);
    }

    /**
     * Returns an input stream to read the serialized event. The InputStream does not have to be closed.
     *
     * @return An InputStream accessing the bytes.
     */
    public InputStream getBytes() {
        return new ByteArrayInputStream(serializedEvent);
    }

    /**
     * Returns the size of the serialized object (the number of bytes).
     *
     * @return the size of the serialized object in bytes.
     */
    public int getEventSize() {
        return serializedEvent.length;
    }

    /**
     * The number of bytes that may be skipped when applying the snapshot event contained in this entry.
     *
     * @return the offset for this snapshot event.
     */
    public int getOffset() {
        return offset;
    }
}
