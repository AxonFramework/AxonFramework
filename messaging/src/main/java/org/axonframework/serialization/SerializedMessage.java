/*
 * Copyright (c) 2010-2025. Axon Framework
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
import jakarta.annotation.Nullable;
import org.axonframework.messaging.AbstractMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.Metadata;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * A message containing serialized {@link #payload() payload data} and {@link #metadata() metadata}.
 * <p>
 * A {@link SerializedMessage} will deserialize the payload or metadata on demand when {@link #payload()} or
 * {@link #metadata()} is called.
 * <p>
 * The {@code SerializedMessage} guarantees that the payload and metadata will not be deserialized more than once.
 * Messages of this type  will not be serialized more than once by the same serializer.
 *
 * @author Rene de Waele
 * @author Steven van Beelen
 * @since 3.0.0
 * TODO #3602 remove
 * @deprecated By shifting from the {@link Serializer} to the {@link Converter}, this exception becomes obsolete.
 */
@Deprecated(forRemoval = true, since = "5.0.0")
public class SerializedMessage<P> extends AbstractMessage {

    private final LazyDeserializingObject<?> payload;
    private final LazyDeserializingObject<Metadata> metadata;

    /**
     * Constructs a {@code SerializedMessage} with given {@code identifier} from the given {@code serializedPayload} and
     * {@code serializedMetadata}.
     * <p>
     * The given {@code serializer} is used to deserialize the data.
     *
     * @param identifier         The identifier of this {@code SerializedMessage}.
     * @param serializedPayload  The {@link SerializedObject serializer} message payload.
     * @param serializedMetadata The {@link SerializedObject serializer} message metadata.
     * @param serializer         The {@link Serializer} required when the data needs to be deserialized.
     */
    public SerializedMessage(@Nonnull String identifier,
                             @Nonnull SerializedObject<?> serializedPayload,
                             @Nonnull SerializedObject<?> serializedMetadata,
                             @Nonnull Serializer serializer) {
        // TODO #3012 - I think the Serializer/Converter should provide the MessageType in this case.
        this(identifier,
             new MessageType(serializedPayload.getType().getName()),
             new LazyDeserializingObject<>(serializedPayload, serializer),
             new LazyDeserializingObject<>(serializedMetadata, serializer));
    }

    /**
     * Constructs a {@code SerializedMessage} with given {@code identifier}, {@code type}, and lazily deserialized
     * {@code payload} and {@code metadata}.
     * <p>
     * The {@code identifier} originates from the {@link org.axonframework.messaging.Message} where the lazily
     * deserialized {@code payload} and {@code metadata} originate from.
     *
     * @param identifier The identifier of this {@code SerializedMessage}.
     * @param type       The {@link MessageType type} for this {@code SerializedMessage}.
     * @param payload    serialized payload that can be deserialized on demand and never more than once
     * @param metadata   serialized metadata that can be deserialized on demand and never more than once
     */
    public SerializedMessage(@Nonnull String identifier,
                             @Nonnull MessageType type,
                             @Nonnull LazyDeserializingObject<?> payload,
                             @Nonnull LazyDeserializingObject<Metadata> metadata) {
        super(identifier, type);
        this.metadata = metadata;
        this.payload = payload;
    }

    private SerializedMessage(@Nonnull SerializedMessage message,
                              @Nonnull LazyDeserializingObject<Metadata> newMetadata) {
        this(message.identifier(), message.type(), message.payload, newMetadata);
    }

    @Override
    @Nullable
    public Object payload() {
        try {
            return payload.getObject();
        } catch (SerializationException e) {
            throw new SerializationException("Error while deserializing payload of message " + identifier(), e);
        }
    }

    @Override
    @Nullable
    public <T> T payloadAs(@Nonnull Type type, @Nullable Converter converter) {
        // This class will be removed/replaced by the ConversionAwareMessage, so skipping implementation
        return null;
    }

    @Override
    @Nonnull
    public Metadata metadata() {
        try {
            return metadata.getObject();
        } catch (SerializationException e) {
            throw new SerializationException("Error while deserializing metadata of message " + identifier(), e);
        }
    }

    @Override
    @Nonnull
    public Class<?> payloadType() {
        return payload.getType();
    }

    @Override
    protected SerializedMessage withMetadata(Metadata metadata) {
        if (metadata().equals(metadata)) {
            return this;
        }
        return new SerializedMessage(this, new LazyDeserializingObject<>(metadata));
    }

    @Override
    @Nonnull
    public SerializedMessage withMetadata(@Nonnull Map<String, String> metadata) {
        return (SerializedMessage) super.withMetadata(metadata);
    }

    @Override
    @Nonnull
    public SerializedMessage andMetadata(@Nonnull Map<String, String> metadata) {
        return (SerializedMessage) super.andMetadata(metadata);
    }

    @Override
    @Nonnull
    public Message withConvertedPayload(@Nonnull Type type, @Nonnull Converter converter) {
        // This class will be removed/replaced by the ConversionAwareMessage, so skipping implementation
        return null;
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
     * Indicates whether the metadata of this message has already been deserialized.
     *
     * @return {@code true} if the metadata is deserialized, otherwise {@code false}
     */
    public boolean isMetadataDeserialized() {
        return metadata.isDeserialized();
    }
}
