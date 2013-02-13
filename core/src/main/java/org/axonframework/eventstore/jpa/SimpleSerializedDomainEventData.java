/*
 * Copyright (c) 2010-2012. Axon Framework
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

import org.axonframework.serializer.SerializedDomainEventData;
import org.axonframework.serializer.SerializedMetaData;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.SimpleSerializedObject;
import org.joda.time.DateTime;

/**
 * Simple implementation of the {@link SerializedDomainEventData} class, used to reduce memory consumptions by queries
 * accessing Event Entries. Querying from them directly will cause the EntityManager to keep a reference to them,
 * preventing them from being garbage collected.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class SimpleSerializedDomainEventData implements SerializedDomainEventData {

    private final String eventIdentifier;
    private final String aggregateIdentifier;
    private final long sequenceNumber;
    private final DateTime timestamp;
    private final SerializedObject<byte[]> serializedPayload;
    private final SerializedObject<byte[]> serializedMetaData;

    /**
     * Initialize an instance using given properties. This constructor assumes the default SerializedType for meta data
     * (name = 'org.axonframework.domain.MetaData' and revision = <em>null</em>).
     * <p/>
     * Note that the given <code>timestamp</code> must be in a format supported by {@link} DateTime#DateTime(Object)}.
     *
     * @param eventIdentifier     The identifier of the event
     * @param aggregateIdentifier The identifier of the aggregate
     * @param sequenceNumber      The sequence number of the event
     * @param timestamp           The timestamp of the event (format must be supported by {@link
     *                            DateTime#DateTime(Object)})
     * @param payloadType         The type identifier of the serialized payload
     * @param payloadRevision     The revision of the serialized payload
     * @param payload             The serialized representation of the event
     * @param metaData            The serialized representation of the meta data
     */
    public SimpleSerializedDomainEventData(String eventIdentifier, String aggregateIdentifier, // NOSONAR - Long ctor
                                           long sequenceNumber, Object timestamp, String payloadType,
                                           String payloadRevision, byte[] payload, byte[] metaData) { // NOSONAR
        this(eventIdentifier, aggregateIdentifier, sequenceNumber, timestamp,
             new SimpleSerializedObject<byte[]>(payload, byte[].class,  // NOSONAR
                                                payloadType, payloadRevision),
             new SerializedMetaData<byte[]>(metaData, byte[].class)); // NOSONAR Ignore array copy
    }

    /**
     * Initialize an instance using given properties. In contrast to the other constructor, this one allows to
     * explicitly indicate the SerializedType used to represent MetaData.
     * <p/>
     * Note that the given <code>timestamp</code> must be in a format supported by {@link} DateTime#DateTime(Object)}.
     *
     * @param eventIdentifier     The identifier of the event
     * @param aggregateIdentifier The identifier of the aggregate
     * @param sequenceNumber      The sequence number of the event
     * @param timestamp           The timestamp of the event (format must be supported by {@link
     *                            DateTime#DateTime(Object)})
     * @param serializedPayload   The serialized representation of the event
     * @param serializedMetaData  The serialized representation of the meta data
     */
    public SimpleSerializedDomainEventData(String eventIdentifier, String aggregateIdentifier, // NOSONAR - Long ctor
                                           long sequenceNumber, Object timestamp,
                                           SerializedObject<byte[]> serializedPayload,
                                           SerializedObject<byte[]> serializedMetaData) {
        this.eventIdentifier = eventIdentifier;
        this.aggregateIdentifier = aggregateIdentifier;
        this.sequenceNumber = sequenceNumber;
        this.timestamp = new DateTime(timestamp);
        this.serializedPayload = serializedPayload;
        this.serializedMetaData = serializedMetaData;
    }

    @Override
    public String getEventIdentifier() {
        return eventIdentifier;
    }

    @Override
    public Object getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    @Override
    public long getSequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public DateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public SerializedObject getMetaData() {
        return serializedMetaData;
    }

    @Override
    public SerializedObject getPayload() {
        return serializedPayload;
    }
}
