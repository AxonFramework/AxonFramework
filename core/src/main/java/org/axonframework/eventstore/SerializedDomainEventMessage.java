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

package org.axonframework.eventstore;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.MetaData;
import org.axonframework.serializer.Serializer;
import org.joda.time.DateTime;

import java.util.Map;

/**
 * DomainEventMessage implementation that is optimized to cope with serialized Payload and MetaData. The Payload and
 * MetaData will only be deserialized when requested. This means that loaded event for which there is no handler will
 * never be deserialized.
 * <p/>
 * This implementation is Serializable as per Java specification. Both MetaData and Payload are deserialized prior to
 * being written to the OutputStream.
 *
 * @param <T> The type of payload contained in this message
 * @author Allard Buijze
 * @since 2.0
 */
public class SerializedDomainEventMessage<T> implements DomainEventMessage<T> {

    private static final long serialVersionUID = 1946981128830316529L;

    private final long sequenceNumber;
    private final Object aggregateIdentifier;
    private final String eventIdentifier;
    private final DateTime timestamp;
    private transient final LazyDeserializingObject<MetaData> serializedMetaData;
    private transient final LazyDeserializingObject<T> serializedPayload;

    /**
     * Creates a new instance with given serialized <code>data</code>, with data to be deserialized with given
     * <code>payloadSerializer</code> and <code>metaDataSerializer</code>.
     *
     * @param data               The serialized data for this EventMessage
     * @param payloadSerializer  The serializer to deserialize the payload data with
     * @param metaDataSerializer The serializer to deserialize meta data with
     */
    public SerializedDomainEventMessage(SerializedDomainEventData data, Serializer payloadSerializer,
                                        Serializer metaDataSerializer) {
        this(data.getEventIdentifier(),
             data.getAggregateIdentifier(),
             data.getSequenceNumber(),
             data.getTimestamp(),
             new LazyDeserializingObject<T>(data.getPayload(), payloadSerializer),
             new LazyDeserializingObject<MetaData>(data.getMetaData(), metaDataSerializer));
    }

    /**
     * Creates a new instance with given event details,to be deserialized with given
     * <code>payloadSerializer</code> and <code>metaDataSerializer</code>.
     *
     * @param eventIdentifier     The identifier of the EventMessage
     * @param aggregateIdentifier The identifier of the Aggregate this message originates from
     * @param sequenceNumber      The sequence number that represents the order in which the message is generated
     * @param timestamp           The timestamp of (original) message creation
     * @param serializedPayload   The serialized payload of this message
     * @param serializedMetaData  The serialized meta data of the message
     */
    public SerializedDomainEventMessage(String eventIdentifier, Object aggregateIdentifier,
                                        long sequenceNumber, DateTime timestamp,
                                        LazyDeserializingObject<T> serializedPayload,
                                        LazyDeserializingObject<MetaData> serializedMetaData) {
        this.sequenceNumber = sequenceNumber;
        this.aggregateIdentifier = aggregateIdentifier;
        this.eventIdentifier = eventIdentifier;
        this.timestamp = timestamp;
        this.serializedPayload = serializedPayload;
        this.serializedMetaData = serializedMetaData;
    }

    private SerializedDomainEventMessage(SerializedDomainEventMessage<T> original, Map<String, Object> metaData) {
        this.serializedMetaData = new LazyDeserializingObject<MetaData>(MetaData.from(metaData));
        this.aggregateIdentifier = original.getAggregateIdentifier();
        this.sequenceNumber = original.getSequenceNumber();
        this.eventIdentifier = original.getIdentifier();
        this.timestamp = original.getTimestamp();
        this.serializedPayload = original.serializedPayload;
    }

    @Override
    public long getSequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public Object getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    @Override
    public String getIdentifier() {
        return eventIdentifier;
    }

    @Override
    public DateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public MetaData getMetaData() {
        MetaData metaData = serializedMetaData.getObject();
        return metaData == null ? MetaData.emptyInstance() : metaData;
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public T getPayload() {
        return serializedPayload.getObject();
    }

    @Override
    public Class getPayloadType() {
        return serializedPayload.getType();
    }

    @Override
    public DomainEventMessage<T> withMetaData(Map<String, Object> newMetaData) {
        if (serializedPayload.isDeserialized()) {
            return new GenericDomainEventMessage<T>(aggregateIdentifier, sequenceNumber,
                                                    serializedPayload.getObject(), newMetaData);
        } else {
            return new SerializedDomainEventMessage<T>(this, newMetaData);
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * This method will force the MetaData to be deserialized if not already done.
     */
    @Override
    public DomainEventMessage<T> andMetaData(Map<String, Object> additionalMetaData) {
        MetaData newMetaData = getMetaData().mergedWith(additionalMetaData);
        return withMetaData(newMetaData);
    }
}
