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
import org.axonframework.common.ObjectUtils;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.Metadata;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.serialization.Converter;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * Generic implementation of the {@link SubscriptionQueryMessage} interface.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 3.3.0
 */
public class GenericSubscriptionQueryMessage extends GenericQueryMessage implements SubscriptionQueryMessage {

    private final ResponseType<?> updateResponseType;

    /**
     * Constructs a {@code GenericSubscriptionQueryMessage} for the given {@code type}, {@code payload},
     * {@code queryName}, {@code responseType}, and {@code updateResponseType}.
     * <p>
     * The {@link Metadata} defaults to an empty instance.
     *
     * @param type               The {@link MessageType type} for this {@link SubscriptionQueryMessage}.
     * @param payload            The payload of type {@code P} expressing the query for this
     *                           {@link SubscriptionQueryMessage}.
     * @param responseType       The expected {@link ResponseType response type} for this
     *                           {@link SubscriptionQueryMessage}.
     * @param updateResponseType The expected {@link ResponseType type} of incremental updates for this
     *                           {@link SubscriptionQueryMessage}.
     */
    public GenericSubscriptionQueryMessage(@Nonnull MessageType type,
                                           @Nullable Object payload,
                                           @Nonnull ResponseType<?> responseType,
                                           @Nonnull ResponseType<?> updateResponseType) {
        super(type, payload, responseType);
        this.updateResponseType = updateResponseType;
    }

    /**
     * Constructs a {@code GenericSubscriptionQueryMessage} with given {@code delegate}, {@code responseType}, and
     * {@code updateResponseType}.
     * <p>
     * The {@code delegate} will be used supply the {@link Message#payload() payload}, {@link Message#type() type},
     * {@link Message#metadata() metadata} and {@link Message#identifier() identifier} of the resulting
     * {@code GenericQueryMessage}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate           The {@link Message} containing {@link Message#payload() payload},
     *                           {@link Message#type() type}, {@link Message#identifier() identifier} and
     *                           {@link Message#metadata() metadata} for the {@link SubscriptionQueryMessage} to
     *                           reconstruct.
     * @param responseType       The expected {@link ResponseType response type} for this
     *                           {@link SubscriptionQueryMessage}.
     * @param updateResponseType The expected {@link ResponseType type} of incremental updates for this
     *                           {@link SubscriptionQueryMessage}.
     */
    public GenericSubscriptionQueryMessage(@Nonnull Message delegate,
                                           @Nonnull ResponseType<?> responseType,
                                           @Nonnull ResponseType<?> updateResponseType) {
        super(delegate, responseType);
        this.updateResponseType = updateResponseType;
    }

    @Override
    @Nonnull
    public ResponseType<?> updatesResponseType() {
        return updateResponseType;
    }

    @Override
    @Nonnull
    public SubscriptionQueryMessage withMetadata(@Nonnull Map<String, String> metadata) {
        return new GenericSubscriptionQueryMessage(delegate().withMetadata(metadata),
                                                   responseType(),
                                                   updateResponseType);
    }

    @Override
    @Nonnull
    public SubscriptionQueryMessage andMetadata(@Nonnull Map<String, String> metadata) {
        return new GenericSubscriptionQueryMessage(delegate().andMetadata(metadata),
                                                   responseType(),
                                                   updateResponseType);
    }

    @Override
    @Nonnull
    public SubscriptionQueryMessage withConvertedPayload(@Nonnull Type type,
                                                         @Nonnull Converter converter) {
        Object convertedPayload = payloadAs(type, converter);
        if (ObjectUtils.nullSafeTypeOf(convertedPayload).isAssignableFrom(payloadType())) {
            return this;
        }
        Message delegate = delegate();
        Message converted = new GenericMessage(delegate.identifier(),
                                               delegate.type(),
                                               convertedPayload,
                                               delegate.metadata());
        return new GenericSubscriptionQueryMessage(converted, responseType(), updatesResponseType());
    }

    @Override
    protected void describeTo(StringBuilder stringBuilder) {
        super.describeTo(stringBuilder);
        stringBuilder.append(", expectedUpdateResponseType='")
                     .append(updateResponseType)
                     .append('\'');
    }

    @Override
    protected String describeType() {
        return "GenericSubscriptionQueryMessage";
    }
}
