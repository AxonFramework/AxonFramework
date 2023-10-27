/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.serialization;

import org.axonframework.messaging.AbstractMessage;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MetaData;

import java.util.Map;
import javax.annotation.Nonnull;

/**
 * A message containing serialized payload data and metadata. A SerializedMessage will deserialize the payload or
 * metadata on demand when {@link #getPayload()} or {@link #getMetaData()} is called.
 * <p>
 * The SerializedMessage guarantees that the payload and metadata will not be deserialized more than once. Messages of
 * this type  will not be serialized more than once by the same serializer.
 *
 * @author Rene de Waele
 */
public class SerializedMessage<T> extends AbstractMessage<T> {

    private static final long serialVersionUID = 8079093289710229594L;

    private final LazyDeserializingObject<MetaData> metaData;
    private final LazyDeserializingObject<T> payload;

    /**
     * Initializes a {@link SerializedMessage} with given {@code identifier} from the given serialized payload and
     * metadata. The given {@code serializer} will be used to deserialize the data.
     *
     * @param identifier         the message identifier
     * @param serializedPayload  the serialized message payload
     * @param serializedMetaData the serialized message metadata
     * @param serializer         the serializer required when the data needs to be deserialized
     */
    public SerializedMessage(String identifier, SerializedObject<?> serializedPayload,
                             SerializedObject<?> serializedMetaData, Serializer serializer) {
        this(identifier, new LazyDeserializingObject<>(serializedPayload, serializer),
             new LazyDeserializingObject<>(serializedMetaData, serializer));
    }

    /**
     * Initializes a {@link SerializedMessage} with given {@code identifier} from the given lazily deserializing payload
     * and metadata.
     *
     * @param identifier the message identifier
     * @param payload    serialized payload that can be deserialized on demand and never more than once
     * @param metaData   serialized metadata that can be deserialized on demand and never more than once
     */
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
        try {
            return payload.getObject();
        } catch (SerializationException e) {
            throw new SerializationException("Error while deserializing payload of message " + getIdentifier(), e);
        }
    }

    @Override
    public MetaData getMetaData() {
        try {
            return metaData.getObject();
        } catch (SerializationException e) {
            throw new SerializationException("Error while deserializing meta data of message " + getIdentifier(), e);
        }
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
    public SerializedMessage<T> withMetaData(@Nonnull Map<String, ?> metaData) {
        return (SerializedMessage<T>) super.withMetaData(metaData);
    }

    @Override
    public SerializedMessage<T> andMetaData(@Nonnull Map<String, ?> metaData) {
        return (SerializedMessage<T>) super.andMetaData(metaData);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R> SerializedObject<R> serializePayload(Serializer serializer, Class<R> expectedRepresentation) {
        if (serializer.equals(payload.getSerializer())) {
            return serializer.getConverter().convert(payload.getSerializedObject(), expectedRepresentation);
        }
        return serializer.serialize(payload.getObject(), expectedRepresentation);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R> SerializedObject<R> serializeMetaData(Serializer serializer, Class<R> expectedRepresentation) {
        if (serializer.equals(metaData.getSerializer())) {
            return serializer.getConverter().convert(metaData.getSerializedObject(), expectedRepresentation);
        }
        return serializer.serialize(metaData.getObject(), expectedRepresentation);
    }

    /**
     * Indicates whether the payload of this message has already been deserialized.
     *
     * @return {@code true} if the payload is deserialized, otherwise {@code false}
     */
    public boolean isPayloadDeserialized() {
        return payload.isDeserialized();
    }

    /**
     * Indicates whether the metaData of this message has already been deserialized.
     *
     * @return {@code true} if the metaData is deserialized, otherwise {@code false}
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
