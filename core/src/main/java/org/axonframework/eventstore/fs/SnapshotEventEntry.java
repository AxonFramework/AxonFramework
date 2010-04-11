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

package org.axonframework.eventstore.fs;

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
class SnapshotEventEntry {

    private final byte[] serializedEvent;
    private final long offset;
    private final long sequenceNumber;
    private final String timeStamp;

    /**
     * Instantiate a SnapshotEventEntry for the given <code>serializedEvent</code>, which allows the given
     * <code>offset</code> number of bytes to be skipped when reading the Events file. The <code>serializedEvent</code>
     * is not stored directly, instead, a copy is used.
     *
     * @param serializedEvent The bytes representing the serialized event object
     * @param sequenceNumber  The sequence number of the snapshot
     * @param timeStamp       The ISO8601 timestamp of the event
     * @param offset          The offset that the event allows in the event log
     */
    public SnapshotEventEntry(byte[] serializedEvent, long sequenceNumber, String timeStamp, long offset) {
        this.offset = offset;
        this.timeStamp = timeStamp;
        this.serializedEvent = Arrays.copyOf(serializedEvent, serializedEvent.length);
        this.sequenceNumber = sequenceNumber;
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
     * Returns the sequence number of the snapshot event. This is the sequence number of the last regular event that was
     * included in this snapshot.
     *
     * @return the sequence number of the snapshot event
     */
    public long getSequenceNumber() {
        return sequenceNumber;
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
    public long getOffset() {
        return offset;
    }

    /**
     * The ISO8601 timestamp of this entry
     *
     * @return the ISO8601 timestamp of this entry
     */
    public String getTimeStamp() {
        return timeStamp;
    }
}
