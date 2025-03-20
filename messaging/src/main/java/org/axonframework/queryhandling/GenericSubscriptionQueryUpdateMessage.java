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

import java.util.Map;

/**
 * Generic implementation of the {@link SubscriptionQueryUpdateMessage} interface holding incremental updates of a
 * subscription query.
 *
 * @param <U> The type of {@link #getPayload() update} contained in this {@link SubscriptionQueryUpdateMessage}.
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
     * @param payload The payload of type {@code U} for this {@code GenericSubscriptionQueryUpdateMessage} representing
     *                an incremental update.
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
     * @param type    The {@link MessageType type} for this {@link SubscriptionQueryUpdateMessage}.
     * @param payload The payload of type {@code U} for this {@code GenericSubscriptionQueryUpdateMessage} representing
     *                an incremental update.
     * @deprecated In favor of {@link #GenericSubscriptionQueryUpdateMessage(MessageType, Object)} once the
     * {@code declaredPayloadType} is removed completely.
     */
    @Deprecated
    public GenericSubscriptionQueryUpdateMessage(@Nonnull MessageType type,
                                                 @Nullable U payload,
                                                 @Deprecated Class<U> declaredType) {
        this(type, payload, MetaData.emptyInstance(), declaredType);
    }

    /**
     * Constructs a {@code GenericSubscriptionQueryUpdateMessage} for the given {@code type}, {@code payload}, and
     * {@code metaData}.
     *
     * @param type     The {@link MessageType type} for this {@link SubscriptionQueryUpdateMessage}.
     * @param payload  The payload of type {@code U} for this {@code GenericSubscriptionQueryUpdateMessage} representing
     *                 an incremental update.
     * @param metaData The metadata for this {@link SubscriptionQueryUpdateMessage}.
     * @deprecated Remove the {@code declaredPayloadType} once the {@code declaredPayloadType} is removed completely
     * from the base {@link Message}.
     */
    @Deprecated
    public GenericSubscriptionQueryUpdateMessage(@Nonnull MessageType type,
                                                 @Nullable U payload,
                                                 @Nonnull Map<String, ?> metaData,
                                                 @Deprecated Class<U> declaredType) {
        super(new GenericMessage<>(type, payload, metaData, declaredType));
    }

    /**
     * Constructs a {@code GenericSubscriptionQueryUpdateMessage} for the given {@code type}, {@code exception}, and
     * {@code metaData}.
     *
     * @param type      The {@link MessageType type} for this {@link SubscriptionQueryUpdateMessage}.
     * @param exception The {@link Throwable} describing the error representing the response of this
     *                  {@link SubscriptionQueryUpdateMessage}.
     * @param metaData  The metadata for this {@link SubscriptionQueryUpdateMessage}.
     * @deprecated Remove the {@code declaredPayloadType} once the {@code declaredPayloadType} is removed completely
     * from the base {@link Message}.
     */
    @Deprecated
    public GenericSubscriptionQueryUpdateMessage(@Nonnull MessageType type,
                                                 @Nonnull Throwable exception,
                                                 @Nonnull Map<String, ?> metaData,
                                                 @Deprecated Class<U> declaredType) {
        super(new GenericMessage<>(type, null, metaData, declaredType), exception);
    }

    /**
     * Initializes a new decorator with given {@code delegate} message. The decorator delegates to the delegate for the
     * message's payload, metadata and identifier.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate The {@link Message} containing {@link Message#getPayload() payload}, {@link Message#type() type},
     *                 {@link Message#getIdentifier() identifier} and {@link Message#getMetaData() metadata} for the
     *                 {@link QueryResponseMessage} to reconstruct.
     */
    protected GenericSubscriptionQueryUpdateMessage(@Nonnull Message<U> delegate) {
        super(delegate);
    }

    @Override
    public GenericSubscriptionQueryUpdateMessage<U> withMetaData(@Nonnull Map<String, ?> metaData) {
        return new GenericSubscriptionQueryUpdateMessage<>(getDelegate().withMetaData(metaData));
    }

    @Override
    public GenericSubscriptionQueryUpdateMessage<U> andMetaData(@Nonnull Map<String, ?> metaData) {
        return new GenericSubscriptionQueryUpdateMessage<>(getDelegate().andMetaData(metaData));
    }

    @Override
    protected String describeType() {
        return "GenericSubscriptionQueryUpdateMessage";
    }
}
