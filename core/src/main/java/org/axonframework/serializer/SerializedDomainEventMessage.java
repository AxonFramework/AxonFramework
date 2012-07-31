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

package org.axonframework.serializer;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.MetaData;
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
 * @author Frank Versnel
 * @since 2.0
 */
public class SerializedDomainEventMessage<T> implements DomainEventMessage<T> {

    private static final long serialVersionUID = 1946981128830316529L;

    private final long sequenceNumber;
    @SuppressWarnings("NonSerializableFieldInSerializableClass")
    private final Object aggregateIdentifier;
    private final SerializedEventMessage<T> eventMessage;

    /**
     * Reconstructs a DomainEventMessage using the given <code>domainEventData</code>, containing data to be
     * deserialized using the given <code>serializer</code>. The serialized data is deserialized on-demand.
     *
     * @param domainEventData The SerializedDomainEventData providing access to this message's data
     * @param serializer      The Serializer to deserialize the meta data and payload with
     */
    public SerializedDomainEventMessage(SerializedDomainEventData domainEventData, Serializer serializer) {
        eventMessage = new SerializedEventMessage<T>(
                domainEventData.getEventIdentifier(), domainEventData.getTimestamp(),
                domainEventData.getPayload(), domainEventData.getMetaData(), serializer);
        aggregateIdentifier = domainEventData.getAggregateIdentifier();
        sequenceNumber = domainEventData.getSequenceNumber();
    }

    /**
     * Wrapper constructor for wrapping a SerializedEventMessage as a SerializedDomainEventMessage, using given
     * <code>aggregateIdentifier</code> and <code>sequenceNumber</code>. This constructor should be used to reconstruct
     * an instance of an existing serialized Domain Event Message
     *
     * @param eventMessage        The eventMessage to wrap
     * @param aggregateIdentifier The identifier of the aggregate that generated the message
     * @param sequenceNumber      The sequence number of the generated event
     */
    public SerializedDomainEventMessage(SerializedEventMessage<T> eventMessage, Object aggregateIdentifier,
                                        long sequenceNumber) {
        this.eventMessage = eventMessage;
        this.aggregateIdentifier = aggregateIdentifier;
        this.sequenceNumber = sequenceNumber;
    }

    private SerializedDomainEventMessage(SerializedDomainEventMessage<T> original, Map<String, Object> metaData) {
        eventMessage = original.eventMessage.withMetaData(metaData);
        this.aggregateIdentifier = original.getAggregateIdentifier();
        this.sequenceNumber = original.getSequenceNumber();
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
    public DomainEventMessage<T> withMetaData(Map<String, Object> newMetaData) {
        if (eventMessage.isPayloadDeserialized()) {
            return new GenericDomainEventMessage<T>(aggregateIdentifier, sequenceNumber,
                                                    getPayload(), newMetaData);
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

    @Override
    public Class getPayloadType() {
        return eventMessage.getPayloadType();
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public T getPayload() {
        return eventMessage.getPayload();
    }

    @Override
    public MetaData getMetaData() {
        return eventMessage.getMetaData();
    }

    @Override
    public DateTime getTimestamp() {
        return eventMessage.getTimestamp();
    }

    @Override
    public String getIdentifier() {
        return eventMessage.getIdentifier();
    }

    /**
     * Indicates whether the payload of this message has already been deserialized.
     *
     * @return <code>true</code> if the payload is deserialized, otherwise <code>false</code>
     */
    public boolean isPayloadDeserialized() {
        return eventMessage.isPayloadDeserialized();
    }

    /**
     * Java Serialization API Method that provides a replacement to serialize, as the fields contained in this instance
     * are not serializable themselves.
     *
     * @return the GenericDomainEventMessage to use as a replacement when serializing
     */
    protected Object writeReplace() {
        return new GenericDomainEventMessage<T>(getIdentifier(), getTimestamp(),
                                                getAggregateIdentifier(), getSequenceNumber(),
                                                getPayload(), getMetaData());
    }
}
