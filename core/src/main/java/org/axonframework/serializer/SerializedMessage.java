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

package org.axonframework.serializer;

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;

import java.util.Map;

/**
 * Message implementation that is optimized to cope with serialized Payload and MetaData. The Payload and MetaData will
 * only be deserialized when requested.
 * <p/>
 * This implementation is Serializable as per Java specification. Both MetaData and Payload are deserialized prior to
 * being written to the OutputStream.
 *
 * @param <T> The type of payload contained in this message
 * @author Allard Buijze
 * @since 2.0
 */
public class SerializedMessage<T> implements Message<T>, SerializationAware {

    private static final long serialVersionUID = 6332429891815042291L;
    private static final ConverterFactory CONVERTER_FACTORY = new ChainingConverterFactory();

    private final String identifier;
    private final LazyDeserializingObject<MetaData> serializedMetaData;
    private final LazyDeserializingObject<T> serializedPayload;

    /**
     * Reconstructs a Message using the given <code>identifier</code>, <code>serializedPayload</code>,
     * <code>serializedMetaData</code> and <code>serializer</code>.
     *
     * @param identifier         The identifier of the message
     * @param serializedPayload  The serialized payload of the message
     * @param serializedMetaData The serialized meta data of the message
     * @param serializer         The serializer to deserialize the payload and meta data with
     * @throws UnknownSerializedTypeException if the type of the serialized object cannot be resolved to a class
     */
    public SerializedMessage(String identifier, SerializedObject<?> serializedPayload,
                             SerializedObject<?> serializedMetaData, Serializer serializer) {
        this.identifier = identifier;
        this.serializedMetaData = new LazyDeserializingObject<>(serializedMetaData, serializer);
        this.serializedPayload = new LazyDeserializingObject<>(serializedPayload, serializer);
    }

    private SerializedMessage(SerializedMessage<T> message, Map<String, ?> metaData) {
        this.identifier = message.getIdentifier();
        this.serializedMetaData = new LazyDeserializingObject<>(MetaData.from(metaData));
        this.serializedPayload = message.serializedPayload;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R> SerializedObject<R> serializePayload(Serializer serializer, Class<R> expectedRepresentation) {
        if (serializer.equals(serializedPayload.getSerializer())) {
            final SerializedObject serializedObject = serializedPayload.getSerializedObject();
            return CONVERTER_FACTORY.getConverter(serializedObject.getContentType(), expectedRepresentation)
                    .convert(serializedObject);
        }
        return serializer.serialize(serializedPayload.getObject(), expectedRepresentation);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R> SerializedObject<R> serializeMetaData(Serializer serializer, Class<R> expectedRepresentation) {
        if (serializer.equals(serializedMetaData.getSerializer())) {
            final SerializedObject serializedObject = serializedMetaData.getSerializedObject();
            return CONVERTER_FACTORY.getConverter(serializedObject.getContentType(), expectedRepresentation)
                                    .convert(serializedObject);
        }
        return serializer.serialize(serializedMetaData.getObject(), expectedRepresentation);
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public MetaData getMetaData() {
        MetaData metaData = serializedMetaData.getObject();
        return metaData == null ? MetaData.emptyInstance() : metaData;
    }

    @Override
    public T getPayload() {
        return serializedPayload.getObject();
    }

    @Override
    public Class getPayloadType() {
        return serializedPayload.getType();
    }

    @Override
    public SerializedMessage<T> withMetaData(Map<String, ?> metaData) {
        if (this.serializedMetaData.getObject().equals(metaData)) {
            return this;
        }
        return new SerializedMessage<>(this, metaData);
    }

    @Override
    public SerializedMessage<T> andMetaData(Map<String, ?> metaData) {
        if (metaData.isEmpty()) {
            return this;
        }
        return new SerializedMessage<>(this, getMetaData().mergedWith(metaData));
    }

    /**
     * Indicates whether the payload of this message has already been deserialized.
     *
     * @return <code>true</code> if the payload is deserialized, otherwise <code>false</code>
     */
    public boolean isPayloadDeserialized() {
        return serializedPayload.isDeserialized();
    }

    /**
     * Java Serialization API Method that provides a replacement to serialize, as the fields contained in this instance
     * are not serializable themselves.
     *
     * @return the GenericMessage to use as a replacement when serializing
     */
    protected Object writeReplace() {
        return new GenericMessage<>(identifier, getPayload(), getMetaData());
    }
}
