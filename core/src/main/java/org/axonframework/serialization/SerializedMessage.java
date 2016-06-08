/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.serialization;

import org.axonframework.messaging.AbstractMessage;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.metadata.MetaData;

import java.util.Map;

/**
 * @author Rene de Waele
 */
public class SerializedMessage<T> extends AbstractMessage<T> implements SerializationAware {

    private final LazyDeserializingObject<MetaData> metaData;
    private final LazyDeserializingObject<T> payload;

    public SerializedMessage(String identifier, SerializedObject<?> serializedPayload,
                             SerializedObject<?> serializedMetaData, Serializer serializer) {
        this(identifier, new LazyDeserializingObject<>(serializedPayload, serializer),
             new LazyDeserializingObject<>(serializedMetaData, serializer));
    }

    public SerializedMessage(String identifier, LazyDeserializingObject<T> payload,
                             LazyDeserializingObject<MetaData> metaData) {
        super(identifier);
        this.metaData = metaData;
        this.payload = payload;
    }

    private SerializedMessage(SerializedMessage<T> message, LazyDeserializingObject<MetaData> newMetaData) {
        this(message.getIdentifier(), message.payload, newMetaData);
    }

    @Override
    public T getPayload() {
        return payload.getObject();
    }

    @Override
    public MetaData getMetaData() {
        return metaData.getObject();
    }

    @Override
    public Class<T> getPayloadType() {
        return payload.getType();
    }

    @Override
    protected SerializedMessage<T> withMetaData(MetaData metaData) {
        if (getMetaData().equals(metaData)) {
            return this;
        }
        return new SerializedMessage<>(this, new LazyDeserializingObject<>(metaData));
    }

    @Override
    public SerializedMessage<T> withMetaData(Map<String, ?> metaData) {
        return (SerializedMessage<T>) super.withMetaData(metaData);
    }

    @Override
    public SerializedMessage<T> andMetaData(Map<String, ?> metaData) {
        return (SerializedMessage<T>) super.andMetaData(metaData);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R> SerializedObject<R> serializePayload(Serializer serializer, Class<R> expectedRepresentation) {
        if (serializer.equals(payload.getSerializer())) {
            final SerializedObject serializedObject = payload.getSerializedObject();
            return serializer.getConverterFactory()
                    .getConverter(serializedObject.getContentType(), expectedRepresentation).convert(serializedObject);
        }
        return serializer.serialize(payload.getObject(), expectedRepresentation);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R> SerializedObject<R> serializeMetaData(Serializer serializer, Class<R> expectedRepresentation) {
        if (serializer.equals(metaData.getSerializer())) {
            final SerializedObject serializedObject = metaData.getSerializedObject();
            return serializer.getConverterFactory()
                    .getConverter(serializedObject.getContentType(), expectedRepresentation).convert(serializedObject);
        }
        return serializer.serialize(metaData.getObject(), expectedRepresentation);
    }

    /**
     * Indicates whether the payload of this message has already been deserialized.
     *
     * @return <code>true</code> if the payload is deserialized, otherwise <code>false</code>
     */
    public boolean isPayloadDeserialized() {
        return payload.isDeserialized();
    }

    /**
     * Indicates whether the metaData of this message has already been deserialized.
     *
     * @return <code>true</code> if the metaData is deserialized, otherwise <code>false</code>
     */
    public boolean isMetaDataDeserialized() {
        return metaData.isDeserialized();
    }

    /**
     * Java Serialization API Method that provides a replacement to serialize, as the fields contained in this instance
     * are not serializable themselves.
     *
     * @return the GenericMessage to use as a replacement when serializing
     */
    protected Object writeReplace() {
        return new GenericMessage<>(getIdentifier(), getPayload(), getMetaData());
    }
}
