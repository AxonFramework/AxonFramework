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

package org.axonframework.eventstore.fs;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.serializer.Serializer;

import java.util.Arrays;

/**
 * Representation of a single (regular) event entry in an aggregate's event log.
 *
 * @author Allard Buijze
 * @since 0.5
 */
class EventEntry {

    private final byte[] serializedEvent;
    private final long sequenceNumber;
    private final String timeStamp;

    /**
     * Initialize an entry using the given <code>sequenceNumber</code> and <code>serializedEvent</code>.
     *
     * @param sequenceNumber  The sequence number of the event
     * @param timeStamp       The ISO8601 timestamp of the event
     * @param serializedEvent The array containing the serialized domain event
     */
    public EventEntry(long sequenceNumber, String timeStamp, byte[] serializedEvent) {
        this.sequenceNumber = sequenceNumber;
        this.timeStamp = timeStamp;
        this.serializedEvent = Arrays.copyOf(serializedEvent, serializedEvent.length);
    }

    /**
     * Deserialize the event using the given <code>serializer</code>.
     *
     * @param serializer the serializer that can deserialize the event in this entry
     * @return the deserialized domain event
     */
    public DomainEventMessage deserialize(Serializer<?> serializer) {
        return (DomainEventMessage) serializer.deserialize(serializedEvent);
    }

    /**
     * Returns the sequence number of the event in this entry.
     *
     * @return the sequence number of the event in this entry
     */
    public long getSequenceNumber() {
        return sequenceNumber;
    }

    /**
     * Returns the ISO8601 timestamp of the event in this entry
     *
     * @return the ISO8601 timestamp of the event in this entry
     */
    public String getTimeStamp() {
        return timeStamp;
    }
}
