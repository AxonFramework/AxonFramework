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

package org.axonframework.messaging;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
import org.axonframework.serialization.Converter;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SerializedObjectHolder;
import org.axonframework.serialization.Serializer;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Generic implementation of the {@link Message} interface containing the {@link #payload() payload} and
 * {@link #getMetaData() metadata} in deserialized form.
 * <p>
 * If a {@link GenericMessage} is created while a {@link LegacyUnitOfWork} is active it copies over the correlation data
 * of the {@code UnitOfWork} to the created message.
 *
 * @param <P> The type of {@link #payload() payload} contained in this {@link Message}.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 2.0.0
 */
public class GenericMessage<P> extends AbstractMessage<P> {

    private final P payload;
    private final MetaData metaData;

    private transient volatile SerializedObjectHolder serializedObjectHolder;

    @Deprecated
    private final Class<P> payloadType;

    /**
     * Constructs a {@code GenericMessage} for the given {@code type} and {@code payload}.
     * <p>
     * Uses the correlation data of the current Unit of Work, if present.
     *
     * @param type    The {@link MessageType type} for this {@link Message}.
     * @param payload The payload of type {@code P} for this {@link Message}.
     */
    public GenericMessage(@Nonnull MessageType type,
                          @Nullable P payload) {
        this(type, payload, MetaData.emptyInstance());
    }

    /**
     * Constructs a {@code GenericMessage} for the given {@code type}, {@code payload}, and {@code metaData}.
     * <p>
     * The given {@code metaData} is merged with the {@link MetaData} from the correlation data of the current Unit of
     * Work, if present. In case the {@code payload == null}, {@link Void} will be used as the {@code payloadType}.
     *
     * @param type     The {@link MessageType type} for this {@link Message}.
     * @param payload  The payload of type {@code P} for this {@link Message}.
     * @param metaData The metadata for this {@link Message}.
     */
    public GenericMessage(@Nonnull MessageType type,
                          @Nullable P payload,
                          @Nonnull Map<String, String> metaData) {
        this(type, payload, metaData, getDeclaredPayloadType(payload));
    }

    /**
     * Constructs a {@code GenericMessage} for the given {@code type}, {@code payload}, and {@code metaData}.
     * <p>
     * The given {@code metaData} is merged with the MetaData from the correlation data of the current Unit of Work, if
     * present.
     *
     * @param type     The {@link MessageType type} for this {@link Message}.
     * @param payload  The payload of type {@code P} for this {@link Message}.
     * @param metaData The metadata for this {@link Message}.
     * @deprecated In favor of {@link #GenericMessage(MessageType, Object, Map)} once the {@code declaredPayloadType} is
     * removed completely.
     */
    @Deprecated
    public GenericMessage(@Nonnull MessageType type,
                          @Nullable P payload,
                          @Nonnull Map<String, ?> metaData,
                          @Deprecated Class<P> declaredPayloadType) {
        this(IdentifierFactory.getInstance().generateIdentifier(),
             type, payload,
             CurrentUnitOfWork.correlationData().mergedWith(MetaData.from((Map<String, String>) metaData)),
             declaredPayloadType);
    }

    /**
     * Constructs a {@code GenericMessage} for the given {@code identifier}, {@code type}, {@code payload}, and
     * {@code metaData}, intended to reconstruct another {@link Message}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work. If you in tend to construct a new {@link GenericMessage}, please use
     * {@link #GenericMessage(MessageType, Object)} instead.
     *
     * @param identifier The identifier of this {@link Message}.
     * @param type       The {@link MessageType type} for this {@link Message}.
     * @param payload    The payload of type {@code P} for this {@link Message}.
     * @param metaData   The metadata for this {@link Message}.
     */
    public GenericMessage(@Nonnull String identifier,
                          @Nonnull MessageType type,
                          @Nullable P payload,
                          @Nonnull Map<String, String> metaData) {
        this(identifier, type, payload, metaData, getDeclaredPayloadType(payload));
    }

    /**
     * Constructs a {@link GenericMessage} for the given {@code identifier}, {@code type}, {@code payload}, and
     * {@code metaData}, intended to reconstruct another {@link Message}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work. If you in tend to construct a new {@link GenericMessage}, please use
     * {@link #GenericMessage(MessageType, Object)} instead.
     *
     * @param identifier The identifier of this {@link Message}.
     * @param type       The {@link MessageType type} for this {@link Message}.
     * @param payload    The payload of type {@code P} for this {@link Message}.
     * @param metaData   The metadata for this {@link Message}.
     * @deprecated In favor of {@link #GenericMessage(String, MessageType, Object, Map)} once the
     * {@code declaredPayloadType} is removed completely.
     */
    @Deprecated
    public GenericMessage(@Nonnull String identifier,
                          @Nonnull MessageType type,
                          @Nullable P payload,
                          @Nonnull Map<String, ?> metaData,
                          @Deprecated Class<P> declaredPayloadType) {
        super(identifier, type);
        this.payload = payload;
        this.metaData = MetaData.from((Map<String, String>) metaData);
        this.payloadType = declaredPayloadType;
    }

    private GenericMessage(@Nonnull GenericMessage<P> original,
                           @Nonnull MetaData metaData) {
        super(original.identifier(), original.type());
        this.payload = original.payload();
        this.metaData = metaData;
        this.payloadType = original.getPayloadType();
    }

    /**
     * Extract the {@link Class} of the provided {@code payload}. If {@code payload == null} this function returns
     * {@link Void} as the payload type.
     *
     * @param payload the payload of this {@link Message}
     * @return the declared type of the given {@code payload} or {@link Void} if {@code payload == null}
     * @deprecated Remove this method entirely once the {@link Message#type()} has taken over the
     * {@link #getPayloadType()} entirely.
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    private static <T> Class<T> getDeclaredPayloadType(T payload) {
        return payload != null ? (Class<T>) payload.getClass() : (Class<T>) Void.class;
    }

    /**
     * Construct an empty message.
     *
     * @return A message with {@code null} {@link Message#payload()}, no {@link MetaData}, and a
     * {@link Message#type()} of {@code "empty"}.
     */
    public static Message<Void> emptyMessage() {
        return new GenericMessage<>(new MessageType("empty"), null);
    }

    @Override
    public MetaData getMetaData() {
        return this.metaData;
    }

    @Override
    public P payload() {
        return this.payload;
    }

    @Override
    public <T> T payloadAs(@Nonnull Type type, @Nullable Converter converter) {
        //noinspection unchecked,rawtypes
        return type instanceof Class clazz && getPayloadType().isAssignableFrom(clazz)
                ? (T) payload()
                : Objects.requireNonNull(converter,
                                         "Cannot convert payload to [" + type.getTypeName() + "] with null Converter.")
                         .convert(payload(), type);
    }

    @Override
    public Class<P> getPayloadType() {
        return this.payloadType;
    }

    @Override
    protected Message<P> withMetaData(MetaData metaData) {
        return new GenericMessage<>(this, metaData);
    }

    @Override
    public <R> SerializedObject<R> serializePayload(Serializer serializer, Class<R> expectedRepresentation) {
        return serializedObjectHolder().serializePayload(serializer, expectedRepresentation);
    }

    @Override
    public <R> SerializedObject<R> serializeMetaData(Serializer serializer, Class<R> expectedRepresentation) {
        return serializedObjectHolder().serializeMetaData(serializer, expectedRepresentation);
    }

    private SerializedObjectHolder serializedObjectHolder() {
        if (serializedObjectHolder == null) {
            serializedObjectHolder = new SerializedObjectHolder(this);
        }
        return serializedObjectHolder;
    }

    @Override
    public <C> Message<C> withConvertedPayload(@Nonnull Function<P, C> conversion) {
        C convertedPayload = conversion.apply(payload());
        return new GenericMessage<>(type(),  convertedPayload, MetaData.from(metaData));
    }
}
