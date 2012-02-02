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
import org.axonframework.serializer.SerializedMetaData;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.Serializer;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;
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
    private final Object aggregateIdentifier;
    private final String eventIdentifier;
    private final DateTime timestamp;
    private transient final LazyDeserializingObject<MetaData> serializedMetaData;
    private transient final LazyDeserializingObject<T> serializedPayload;
    private final int indexOnDeserializedObject;

    private SerializedDomainEventMessage(String eventIdentifier, Object aggregateIdentifier,
                                        long sequenceNumber, DateTime timestamp,
                                        LazyDeserializingObject<T> serializedPayload,
                                        LazyDeserializingObject<MetaData> serializedMetaData,
                                        int indexOnDeserializedObject) {
        this.sequenceNumber = sequenceNumber;
        this.aggregateIdentifier = aggregateIdentifier;
        this.eventIdentifier = eventIdentifier;
        this.timestamp = timestamp;
        this.serializedPayload = serializedPayload;
        this.serializedMetaData = serializedMetaData;
        this.indexOnDeserializedObject = indexOnDeserializedObject;
    }

    private SerializedDomainEventMessage(SerializedDomainEventMessage<T> original, Map<String, Object> metaData,
                                         int indexOnDeserializedObject) {
        this.sequenceNumber = original.getSequenceNumber();
        this.aggregateIdentifier = original.getAggregateIdentifier();
        this.eventIdentifier = original.getIdentifier();
        this.timestamp = original.getTimestamp();
        this.serializedPayload = original.serializedPayload;
        this.serializedMetaData = new LazyDeserializingObject<MetaData>(MetaData.from(metaData));
        this.indexOnDeserializedObject = indexOnDeserializedObject;
    }

    /**
     * Creates new instances with given serialized <code>data</code>, with data to be deserialized with given
     * <code>payloadSerializer</code> and <code>metaDataSerializer</code>.
     *
     * @param data               The serialized data for this EventMessage
     * @param payloadSerializer  The serializer to deserialize the payload data with
     * @param metaDataSerializer The serializer to deserialize meta data with
     * @return the newly created instances, where each instance represents a domain event in the serialized data
     */
    public static List<DomainEventMessage> createDomainEventMessages(SerializedDomainEventData data,
                                                                        Serializer payloadSerializer,
                                                                        Serializer metaDataSerializer) {
        return createDomainEventMessages(payloadSerializer, metaDataSerializer, data.getEventIdentifier(),
                                         data.getAggregateIdentifier(), data.getSequenceNumber(),
                                         data.getTimestamp(), data.getPayload(), data.getMetaData());
    }

    /**
     * Creates new instances with given event details,to be deserialized with given <code>eventSerializer</code>.
     *
     * @param eventSerializer           The serializer to deserialize both the serializedObject and the
     *                                  serializedMetaData
     * @param eventIdentifier           The identifier of the EventMessage
     * @param aggregateIdentifier       The identifier of the Aggregate this message originates from
     * @param sequenceNumber            The sequence number that represents the order in which the message is generated
     * @param timestamp                 The timestamp of (original) message creation
     * @param serializedPayload   The serialized payload of this message
     * @param serializedMetaData  The serialized meta data of the message
     * @return the newly created instances, where each instance represents a domain event in the serialized data
     */
    public static List<DomainEventMessage> createDomainEventMessages(Serializer eventSerializer,
                                                                     String eventIdentifier,
                                                                     Object aggregateIdentifier,
                                                                     long sequenceNumber,
                                                                     DateTime timestamp,
                                                                     SerializedObject serializedPayload,
                                                                     SerializedObject serializedMetaData) {
        return createDomainEventMessages(eventSerializer,
                                         eventSerializer,
                                         eventIdentifier,
                                         aggregateIdentifier,
                                         sequenceNumber,
                                         timestamp,
                                         serializedPayload,
                                         serializedMetaData);
    }

    private static List<DomainEventMessage> createDomainEventMessages(Serializer payloadSerializer,
                                                                      Serializer metaDataSerializer,
                                                                     String eventIdentifier,
                                                                     Object aggregateIdentifier,
                                                                     long sequenceNumber,
                                                                     DateTime timeStamp,
                                                                     SerializedObject serializedPayload,
                                                                     SerializedObject serializedMetaData) {
        LazyDeserializingObject<Object> lazyDeserializedPayload =
                new LazyDeserializingObject<Object>(serializedPayload, payloadSerializer);
        LazyDeserializingObject<MetaData> lazyDeserializedMetadata =
                new LazyDeserializingObject<MetaData>(serializedMetaData, metaDataSerializer);

        List<DomainEventMessage> lazyDeserializedDomainEvents = new ArrayList<DomainEventMessage>();
        for (int eventIndex = 0; eventIndex < lazyDeserializedPayload.deserializedObjectCount(); eventIndex++) {
            lazyDeserializedDomainEvents.add(
                    new SerializedDomainEventMessage(
                            eventIdentifier,
                            aggregateIdentifier,
                            sequenceNumber,
                            timeStamp,
                            lazyDeserializedPayload,
                            lazyDeserializedMetadata,
                            eventIndex));
        }
        return lazyDeserializedDomainEvents;
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
        MetaData metaData = serializedMetaData.getObject().get(indexOnDeserializedObject);
        return metaData == null ? MetaData.emptyInstance() : metaData;
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public T getPayload() {
        return serializedPayload.getObject().get(indexOnDeserializedObject);
    }

    @Override
    public Class getPayloadType() {
        return serializedPayload.getType().get(indexOnDeserializedObject);
    }

    @Override
    public DomainEventMessage<T> withMetaData(Map<String, Object> newMetaData) {
        if (serializedPayload.isDeserialized()) {
            return new GenericDomainEventMessage<T>(aggregateIdentifier, sequenceNumber,
                                                    getPayload(), newMetaData);
        } else {
            return new SerializedDomainEventMessage<T>(this, newMetaData, indexOnDeserializedObject);
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
