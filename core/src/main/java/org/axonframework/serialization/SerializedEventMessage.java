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

package org.axonframework.serialization;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.metadata.MetaData;

import java.time.Instant;
import java.util.Map;

/**
 * EventMessage implementation that is optimized to cope with serialized Payload and MetaData. The Payload and
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
public class SerializedEventMessage<T> implements EventMessage<T>, SerializationAware {

    private static final long serialVersionUID = -4704515337335869770L;
    private final Instant timestamp;
    private final SerializedMessage<T> message;

    /**
     * Constructor to reconstruct an EventMessage using serialized data
     *
     * @param eventIdentifier    The identifier of the message
     * @param timestamp          The timestamp of the event message
     * @param serializedPayload  The serialized payload of the message
     * @param serializedMetaData The serialized meta data of the message
     * @param serializer         The serializer to deserialize the payload and meta data with
     * @throws UnknownSerializedTypeException if the type of the serialized object cannot be resolved to a class
     */
    public SerializedEventMessage(String eventIdentifier, Instant timestamp, SerializedObject<?> serializedPayload,
                                  SerializedObject<?> serializedMetaData, Serializer serializer) {
        message = new SerializedMessage<>(eventIdentifier, serializedPayload, serializedMetaData, serializer);
        this.timestamp = timestamp;
    }

    private SerializedEventMessage(SerializedEventMessage<T> original, Map<String, ?> metaData) {
        message = original.message.withMetaData(metaData);
        this.timestamp = original.getTimestamp();
    }

    @Override
    public <R> SerializedObject<R> serializePayload(Serializer serializer, Class<R> expectedRepresentation) {
        return message.serializePayload(serializer, expectedRepresentation);
    }

    @Override
    public <R> SerializedObject<R> serializeMetaData(Serializer serializer, Class<R> expectedRepresentation) {
        return message.serializeMetaData(serializer, expectedRepresentation);
    }

    @Override
    public String getIdentifier() {
        return message.getIdentifier();
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public MetaData getMetaData() {
        return message.getMetaData();
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public T getPayload() {
        return message.getPayload();
    }

    @Override
    public Class<T> getPayloadType() {
        return message.getPayloadType();
    }

    @SuppressWarnings("EqualsBetweenInconvertibleTypes")
    @Override
    public SerializedEventMessage<T> withMetaData(Map<String, ?> newMetaData) {
        if (getMetaData().equals(newMetaData)) {
            return this;
        } else {
            return new SerializedEventMessage<>(this, newMetaData);
        }
    }

    @Override
    public EventMessage<T> andMetaData(Map<String, ?> additionalMetaData) {
        MetaData newMetaData = getMetaData().mergedWith(additionalMetaData);
        return withMetaData(newMetaData);
    }

    /**
     * Indicates whether the payload of this message has already been deserialized.
     *
     * @return <code>true</code> if the payload is deserialized, otherwise <code>false</code>
     */
    public boolean isPayloadDeserialized() {
        return message.isPayloadDeserialized();
    }

    /**
     * Java Serialization API Method that provides a replacement to serialize, as the fields contained in this instance
     * are not serializable themselves.
     *
     * @return the GenericEventMessage to use as a replacement when serializing
     */
    protected Object writeReplace() {
        return new GenericEventMessage<>(getIdentifier(), getPayload(), getMetaData(), getTimestamp());
    }
}
