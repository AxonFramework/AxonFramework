/*
 * Copyright (c) 2010-2024. Axon Framework
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

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.AbstractMessage;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.QualifiedNameUtils;

import java.io.Serial;
import java.util.Map;

/**
 * A message containing serialized {@link #getPayload() payload data} and {@link #getMetaData() metadata}.
 * <p>
 * A {@link SerializedMessage} will deserialize the payload or metadata on demand when {@link #getPayload()} or
 * {@link #getMetaData()} is called.
 * <p>
 * The {@code SerializedMessage} guarantees that the payload and metadata will not be deserialized more than once.
 * Messages of this type  will not be serialized more than once by the same serializer.
 *
 * @param <P> The type of {@link #getPayload() payload} contained in this {@link org.axonframework.messaging.Message}.
 * @author Rene de Waele
 * @author Steven van Beelen
 * @since 3.0.0
 */
public class SerializedMessage<P> extends AbstractMessage<P> {

    @Serial
    private static final long serialVersionUID = 8079093289710229594L;

    private final LazyDeserializingObject<P> payload;
    private final LazyDeserializingObject<MetaData> metaData;

    /**
     * Constructs a {@link SerializedMessage} with given {@code identifier} from the given {@code serializedPayload} and
     * {@code serializedMetaData}.
     * <p>
     * The given {@code serializer} is used to deserialize the data.
     *
     * @param identifier         The identifier of this {@link SerializedMessage}.
     * @param serializedPayload  The {@link SerializedObject serializer} message payload.
     * @param serializedMetaData The {@link SerializedObject serializer} message metadata.
     * @param serializer         The {@link Serializer} required when the data needs to be deserialized.
     */
    public SerializedMessage(@Nonnull String identifier,
                             @Nonnull SerializedObject<?> serializedPayload,
                             @Nonnull SerializedObject<?> serializedMetaData,
                             @Nonnull Serializer serializer) {
        // TODO #3012 - I think the Serializer/Converter should provide the QualifiedName in this case.
        this(identifier,
             QualifiedNameUtils.fromDottedName(serializedPayload.getType().getName()),
             new LazyDeserializingObject<>(serializedPayload, serializer),
             new LazyDeserializingObject<>(serializedMetaData, serializer));
    }

    /**
     * Constructs a {@link SerializedMessage} with given {@code identifier}, {@code name}, and lazily deserialized
     * {@code payload} and {@code metadata}.
     * <p>
     * The {@code identifier} originates from the {@link org.axonframework.messaging.Message} where the lazily
     * deserialized {@code payload} and {@code metadata} originate from.
     *
     * @param identifier The identifier of this {@link SerializedMessage}.
     * @param name       The {@link QualifiedName name} for this {@link SerializedMessage}.
     * @param payload    serialized payload that can be deserialized on demand and never more than once
     * @param metaData   serialized metadata that can be deserialized on demand and never more than once
     */
    public SerializedMessage(@Nonnull String identifier,
                             @Nonnull QualifiedName name,
                             @Nonnull LazyDeserializingObject<P> payload,
                             @Nonnull LazyDeserializingObject<MetaData> metaData) {
        super(identifier, name);
        this.metaData = metaData;
        this.payload = payload;
    }

    private SerializedMessage(@Nonnull SerializedMessage<P> message,
                              @Nonnull LazyDeserializingObject<MetaData> newMetaData) {
        this(message.getIdentifier(), message.name(), message.payload, newMetaData);
    }

    @Override
    public P getPayload() {
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
    public Class<P> getPayloadType() {
        return payload.getType();
    }

    @Override
    protected SerializedMessage<P> withMetaData(MetaData metaData) {
        if (getMetaData().equals(metaData)) {
            return this;
        }
        return new SerializedMessage<>(this, new LazyDeserializingObject<>(metaData));
    }

    @Override
    public SerializedMessage<P> withMetaData(@Nonnull Map<String, ?> metaData) {
        return (SerializedMessage<P>) super.withMetaData(metaData);
    }

    @Override
    public SerializedMessage<P> andMetaData(@Nonnull Map<String, ?> metaData) {
        return (SerializedMessage<P>) super.andMetaData(metaData);
    }

    @Override
    public <R> SerializedObject<R> serializePayload(Serializer serializer, Class<R> expectedRepresentation) {
        if (serializer.equals(payload.getSerializer())) {
            return serializer.getConverter().convert(payload.getSerializedObject(), expectedRepresentation);
        }
        return serializer.serialize(payload.getObject(), expectedRepresentation);
    }

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
        return new GenericMessage<>(getIdentifier(), name(), getPayload(), getMetaData());
    }
}
