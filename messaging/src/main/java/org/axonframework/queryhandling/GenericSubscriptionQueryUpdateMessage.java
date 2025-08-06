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

package org.axonframework.queryhandling;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.GenericResultMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.Converter;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * Generic implementation of the {@link SubscriptionQueryUpdateMessage} interface holding incremental updates of a
 * subscription query.
 *
 * @param <U> The type of {@link #payload() update} contained in this {@link SubscriptionQueryUpdateMessage}.
 * @author Milan Savic
 * @author Steven van Beelen
 * @since 3.3.0
 */
public class GenericSubscriptionQueryUpdateMessage<U>
        extends GenericResultMessage<U>
        implements SubscriptionQueryUpdateMessage<U> {

    /**
     * Constructs a {@code GenericSubscriptionQueryUpdateMessage} for the given {@code type} and {@code payload}.
     * <p>
     * The {@link MetaData} defaults to an empty instance.
     *
     * @param type    The {@link MessageType type} for this {@link SubscriptionQueryUpdateMessage}.
     * @param payload The payload of type {@code U} for this {@link SubscriptionQueryUpdateMessage} representing an
     *                incremental update.
     */
    public GenericSubscriptionQueryUpdateMessage(@Nonnull MessageType type,
                                                 @Nonnull U payload) {
        this(new GenericMessage<>(type, payload, MetaData.emptyInstance()));
    }

    /**
     * Constructs a {@code GenericSubscriptionQueryUpdateMessage} for the given {@code type} and {@code payload}.
     * <p>
     * The {@link MetaData} defaults to an empty instance.
     *
     * @param type               The {@link MessageType type} for this {@link SubscriptionQueryUpdateMessage}.
     * @param payload            The payload of type {@code U} for this {@link SubscriptionQueryUpdateMessage}
     *                           representing an incremental update.
     * @param declaredUpdateType The declared update type of this  {@link SubscriptionQueryUpdateMessage}.
     */
    public GenericSubscriptionQueryUpdateMessage(@Nonnull MessageType type,
                                                 @Nullable U payload,
                                                 @Nonnull Class<U> declaredUpdateType) {
        this(type, payload, declaredUpdateType, MetaData.emptyInstance());
    }

    /**
     * Constructs a {@code GenericSubscriptionQueryUpdateMessage} for the given {@code type}, {@code payload}, and
     * {@code metaData}.
     *
     * @param type               The {@link MessageType type} for this {@link SubscriptionQueryUpdateMessage}.
     * @param payload            The payload of type {@code U} for this {@link SubscriptionQueryUpdateMessage}
     *                           representing an incremental update.
     * @param declaredUpdateType The declared update type of this  {@link SubscriptionQueryUpdateMessage}.
     * @param metaData           The metadata for this {@link SubscriptionQueryUpdateMessage}.
     */
    public GenericSubscriptionQueryUpdateMessage(@Nonnull MessageType type,
                                                 @Nullable U payload,
                                                 @Nonnull Class<U> declaredUpdateType,
                                                 @Nonnull Map<String, ?> metaData) {
        super(new GenericMessage<>(type, payload, declaredUpdateType, metaData));
    }

    /**
     * Constructs a {@code GenericSubscriptionQueryUpdateMessage} for the given {@code type}, {@code exception}, and
     * {@code metaData}.
     *
     * @param type               The {@link MessageType type} for this {@link SubscriptionQueryUpdateMessage}.
     * @param exception          The {@link Throwable} describing the error representing the response of this
     *                           {@link SubscriptionQueryUpdateMessage}.
     * @param declaredUpdateType The declared update type of this  {@link SubscriptionQueryUpdateMessage}.
     * @param metaData           The metadata for this {@link SubscriptionQueryUpdateMessage}.
     */
    public GenericSubscriptionQueryUpdateMessage(@Nonnull MessageType type,
                                                 @Nonnull Throwable exception,
                                                 @Nonnull Class<U> declaredUpdateType,
                                                 @Nonnull Map<String, ?> metaData) {
        super(new GenericMessage<>(type, null, declaredUpdateType, metaData), exception);
    }

    /**
     * Initializes a new decorator with given {@code delegate} message. The decorator delegates to the delegate for the
     * message's payload, metadata and identifier.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate The {@link Message} containing {@link Message#payload() payload}, {@link Message#type() type},
     *                 {@link Message#identifier() identifier} and {@link Message#metaData() metadata} for the
     *                 {@link QueryResponseMessage} to reconstruct.
     */
    protected GenericSubscriptionQueryUpdateMessage(@Nonnull Message<U> delegate) {
        super(delegate);
    }

    @Override
    public SubscriptionQueryUpdateMessage<U> withMetaData(@Nonnull Map<String, String> metaData) {
        return new GenericSubscriptionQueryUpdateMessage<>(delegate().withMetaData(metaData));
    }

    @Override
    public SubscriptionQueryUpdateMessage<U> andMetaData(@Nonnull Map<String, String> metaData) {
        return new GenericSubscriptionQueryUpdateMessage<>(delegate().andMetaData(metaData));
    }

    @Override
    public <T> SubscriptionQueryUpdateMessage<T> withConvertedPayload(@Nonnull Type type,
                                                                      @Nonnull Converter converter) {
        T convertedPayload = payloadAs(type, converter);
        if (payloadType().isAssignableFrom(convertedPayload.getClass())) {
            //noinspection unchecked
            return (SubscriptionQueryUpdateMessage<T>) this;
        }
        Message<U> delegate = delegate();
        return new GenericSubscriptionQueryUpdateMessage<>(new GenericMessage<T>(delegate.identifier(),
                                                                                 delegate.type(),
                                                                                 convertedPayload,
                                                                                 delegate.metaData()));
    }

    @Override
    protected String describeType() {
        return "GenericSubscriptionQueryUpdateMessage";
    }
}
