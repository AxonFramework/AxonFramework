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
import org.axonframework.serializer.SerializedMetaData;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.SimpleSerializedObject;
import org.joda.time.DateTime;

import java.util.Arrays;
import javax.persistence.Basic;
import javax.persistence.Lob;
import javax.persistence.MappedSuperclass;

/**
 * Data needed by different types of event logs.
 *
 * @author Allard Buijze
 * @since 0.5
 */
@MappedSuperclass
public abstract class AbstractEventEntry extends AbstractEventEntryData<byte[]> {

    @Basic
    @Lob
    private byte[] metaData;
    @Basic(optional = false)
    @Lob
    private byte[] payload;

    /**
     * Initialize an Event entry for the given <code>event</code>.
     *
     * @param type     The type identifier of the aggregate root the event belongs to
     * @param event    The event to store in the EventStore
     * @param payload  The serialized payload of the Event
     * @param metaData The serialized metaData of the Event
     */
    protected AbstractEventEntry(String type, DomainEventMessage event,
                                 SerializedObject<byte[]> payload, SerializedObject<byte[]> metaData) {
        this(type, event, event.getTimestamp(), payload, metaData);
    }

    /**
     * Initialize an Event entry for the given <code>event</code>.
     *
     * @param type      The type identifier of the aggregate root the event belongs to
     * @param event     The event to store in the EventStore
     * @param timestamp The timestamp to store
     * @param payload   The serialized payload of the Event
     * @param metaData  The serialized metaData of the Event
     */
    protected AbstractEventEntry(String type, DomainEventMessage event, DateTime timestamp,
                                 SerializedObject<byte[]> payload, SerializedObject<byte[]> metaData) {
        super(event.getIdentifier(),
              type,
              event.getAggregateIdentifier().toString(),
              event.getSequenceNumber(),
              timestamp, payload.getType()
        );
        this.metaData = Arrays.copyOf(metaData.getData(), metaData.getData().length);
        this.payload = payload.getData();
    }

    /**
     * Default constructor, as required by JPA specification. Do not use directly!
     */
    protected AbstractEventEntry() {
    }

    @Override
    public SerializedObject<byte[]> getPayload() {
        return new SimpleSerializedObject<byte[]>(payload, byte[].class, getPayloadType());
    }

    @Override
    public SerializedObject<byte[]> getMetaData() {
        return new SerializedMetaData<byte[]>(metaData, byte[].class);
    }


}
