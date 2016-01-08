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

import java.time.Instant;
import java.time.temporal.TemporalAccessor;

/**
 * Implementation of the EventEntryFactory that provides the default Axon entities, which store payload and meta data
 * of Events as byte arrays. This implementation also supports converting TimeZone of event timestamps to UTC before
 * storing them.
 *
 * @author Allard Buijze
 * @since 2.3
 */
public final class DefaultEventEntryFactory implements EventEntryFactory<byte[]> {

    private final boolean forceUtc;

    /**
     * Creates a new instance of the factory which stores the timestamps with the original timezone of the event
     * messages.
     */
    public DefaultEventEntryFactory() {
        this(false);
    }

    /**
     * Creates a new instance of the factory which, when <code>forceUtc</code> <code>true</code>, stores the timestamps
     * converted to UTC timezone.
     *
     * @param forceUtc whether to convert timestamps to the UTC time zone.
     */
    public DefaultEventEntryFactory(boolean forceUtc) {
        this.forceUtc = forceUtc;
    }

    @Override
    public Class<byte[]> getDataType() {
        return byte[].class;
    }

    @Override
    public Object createDomainEventEntry(DomainEventMessage event,
                                         SerializedObject<byte[]> serializedPayload,
                                         SerializedObject<byte[]> serializedMetaData) {
        Instant timestamp = event.getTimestamp();
        return new DomainEventEntry(event, timestamp, serializedPayload, serializedMetaData);
    }

    @Override
    public Object createSnapshotEventEntry(DomainEventMessage snapshotEvent,
                                           SerializedObject<byte[]> serializedPayload,
                                           SerializedObject<byte[]> serializedMetaData) {
        return new SnapshotEventEntry(snapshotEvent, serializedPayload, serializedMetaData);
    }

    @Override
    public String getDomainEventEntryEntityName() {
        return "DomainEventEntry";
    }

    @Override
    public String getSnapshotEventEntryEntityName() {
        return "SnapshotEventEntry";
    }

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation returns the a long containing the millis-since-Epoch representation of the given date.
     */
    @Override
    public long resolveDateTimeValue(TemporalAccessor dateTime) {
        return Instant.from(dateTime).toEpochMilli();
    }
}
