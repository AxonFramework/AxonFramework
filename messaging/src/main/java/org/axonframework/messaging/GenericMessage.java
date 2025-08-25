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
import org.axonframework.common.ObjectUtils;
import org.axonframework.common.TypeReference;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
import org.axonframework.serialization.ConversionException;
import org.axonframework.serialization.Converter;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * Generic implementation of the {@link Message} interface containing the {@link #payload() payload} and
 * {@link #metaData() metadata} in deserialized form.
 * <p>
 * If a {@link GenericMessage} is created while a {@link LegacyUnitOfWork} is active it copies over the correlation data
 * of the {@code UnitOfWork} to the created message.
 * <p>
 * This {@code Message} implementation is "conversion aware," as it maintains <b>any</b> conversion results from
 * {@link #payloadAs(Type, Converter)} and {@link #withConvertedPayload(Type, Converter)} (either invoked with a
 * {@link Class}, {@link TypeReference}, or {@link Type}), together with the hash of the given {@link Converter}. In
 * doing so, this {@code Message} optimizes subsequent {@code payloadAs/withConvertedPayload} invocations for the same
 * type-and-converter combination. If this optimization should be disabled, the {@code "AXON_CONVERSION_CACHE_ENABLED"}
 * system property can be set to {@code false}.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 2.0.0
 */
public class GenericMessage extends AbstractMessage {

    private final Object payload;
    private final Class<?> payloadType;
    private final MetaData metaData;

    private final ConversionCache convertedPayloads;

    /**
     * Constructs a {@code GenericMessage} for the given {@code type} and {@code payload}.
     * <p>
     * Uses the correlation data of the current Unit of Work, if present.
     *
     * @param type    The {@link MessageType type} for this {@link Message}.
     * @param payload The payload for this {@link Message}.
     */
    public GenericMessage(@Nonnull MessageType type,
                          @Nullable Object payload) {
        this(type, payload, MetaData.emptyInstance());
    }

    /**
     * Constructs a {@code GenericMessage} for the given {@code type}, {@code payload}, and {@code metaData}.
     * <p>
     * The given {@code metaData} is merged with the {@link MetaData} from the correlation data of the current Unit of
     * Work, if present. In case the {@code payload == null}, {@link Void} will be used as the {@code payloadType}.
     *
     * @param type     The {@link MessageType type} for this {@link Message}.
     * @param payload  The payload for this {@link Message}.
     * @param metaData The metadata for this {@link Message}.
     */
    public GenericMessage(@Nonnull MessageType type,
                          @Nullable Object payload,
                          @Nonnull Map<String, String> metaData) {
        this(type, payload, getDeclaredPayloadType(payload), metaData);
    }

    /**
     * Constructs a {@code GenericMessage} for the given {@code type}, {@code payload}, {@code declaredPayloadType}, and
     * {@code metaData}.
     * <p>
     * The given {@code metaData} is merged with the MetaData from the correlation data of the current Unit of Work, if
     * present.
     *
     * @param <P>                 The generic type of the expected payload of the resulting object.
     * @param type                The {@link MessageType type} for this {@link Message}.
     * @param payload             The payload of type {@code P} for this {@link Message}.
     * @param declaredPayloadType The declared type of the {@code payload} of this {@link Message}.
     * @param metaData            The metadata for this {@link Message}.
     */
    public <P> GenericMessage(@Nonnull MessageType type,
                              @Nullable P payload,
                              @Nonnull Class<P> declaredPayloadType,
                              @Nonnull Map<String, ?> metaData) {
        this(IdentifierFactory.getInstance().generateIdentifier(),
             type,
             payload,
             declaredPayloadType,
             CurrentUnitOfWork.correlationData().mergedWith(MetaData.from((Map<String, String>) metaData)));
    }

    /**
     * Constructs a {@code GenericMessage} for the given {@code identifier}, {@code type}, {@code payload}, and
     * {@code metaData}, intended to reconstruct another {@link Message}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work. If you in tend to construct a new {@code GenericMessage}, please use
     * {@link #GenericMessage(MessageType, Object)} instead.
     *
     * @param identifier The identifier of this {@link Message}.
     * @param type       The {@link MessageType type} for this {@link Message}.
     * @param payload    The payload for this {@link Message}.
     * @param metaData   The metadata for this {@link Message}.
     */
    public GenericMessage(@Nonnull String identifier,
                          @Nonnull MessageType type,
                          @Nullable Object payload,
                          @Nonnull Map<String, String> metaData) {
        this(identifier, type, payload, getDeclaredPayloadType(payload), metaData);
    }

    /**
     * Constructs a {@code GenericMessage} for the given {@code identifier}, {@code type}, {@code payload}, and
     * {@code metaData}, intended to reconstruct another {@link Message}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work. If you in tend to construct a new {@code GenericMessage}, please use
     * {@link #GenericMessage(MessageType, Object)} instead.
     *
     * @param <P>                 The generic type of the expected payload of the resulting object.
     * @param identifier          The identifier of this {@link Message}.
     * @param type                The {@link MessageType type} for this {@link Message}.
     * @param payload             The payload of type {@code P} for this {@link Message}.
     * @param declaredPayloadType The declared type of the {@code payload} of this {@link Message}.
     * @param metaData            The metadata for this {@link Message}.
     */
    public <P> GenericMessage(@Nonnull String identifier,
                              @Nonnull MessageType type,
                              @Nullable P payload,
                              @Nonnull Class<P> declaredPayloadType,
                              @Nonnull Map<String, String> metaData) {
        super(identifier, type);
        this.payload = payload;
        this.payloadType = declaredPayloadType;
        this.metaData = MetaData.from(metaData);
        this.convertedPayloads = new ConversionCache(payload);
    }

    private GenericMessage(@Nonnull GenericMessage original,
                           @Nonnull MetaData metaData) {
        super(original.identifier(), original.type());
        this.payload = original.payload();
        this.payloadType = original.payloadType();
        this.metaData = metaData;
        this.convertedPayloads = new ConversionCache(payload);
    }

    /**
     * Extract the {@link Class} of the provided {@code payload}. If {@code payload == null} this function returns
     * {@link Void} as the payload type.
     *
     * @param payload the payload of this {@link Message}
     * @return the declared type of the given {@code payload} or {@link Void} if {@code payload == null}
     */
    private static <T> Class<T> getDeclaredPayloadType(@Nullable T payload) {
        return ObjectUtils.nullSafeTypeOf(payload);
    }

    /**
     * Construct an empty message.
     *
     * @return A message with {@code null} {@link Message#payload()}, no {@link MetaData}, and a {@link Message#type()}
     * of {@code "empty"}.
     */
    public static Message emptyMessage() {
        return new GenericMessage(new MessageType("empty"), null);
    }

    @Override
    @Nullable
    public Object payload() {
        return this.payload;
    }

    @Override
    @Nullable
    public <T> T payloadAs(@Nonnull Type type, @Nullable Converter converter) {
        //noinspection rawtypes,unchecked
        if (type instanceof Class clazz && clazz.isAssignableFrom(payloadType())) {
            //noinspection unchecked
            return (T) payload();
        }

        if (converter == null) {
            throw new ConversionException("Cannot convert " + payloadType() + " to " + type + " without a converter.");
        }
        return convertedPayloads.convertIfAbsent(type, converter);
    }

    @Override
    @Nonnull
    public Class<?> payloadType() {
        return this.payloadType;
    }

    @Override
    @Nonnull
    public MetaData metaData() {
        return this.metaData;
    }

    @Override
    @Nonnull
    protected Message withMetaData(MetaData metaData) {
        return new GenericMessage(this, metaData);
    }

    @Override
    @Nonnull
    public Message withConvertedPayload(@Nonnull Type type,
                                        @Nonnull Converter converter) {
        Object convertedPayload = payloadAs(type, converter);

        return ObjectUtils.nullSafeTypeOf(convertedPayload).isAssignableFrom(payloadType())
                ? (Message) this
                : new GenericMessage(identifier(), type(), convertedPayload, metaData());
    }
}
