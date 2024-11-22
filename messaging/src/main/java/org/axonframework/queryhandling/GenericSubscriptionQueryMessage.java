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

package org.axonframework.queryhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.responsetypes.ResponseType;

import java.util.Map;

/**
 * Generic implementation of the {@link SubscriptionQueryMessage} interface.
 * <p>
 * Unless explicitly provided, it assumes the {@code queryName} of the {@code SubscriptionQueryMessage} is the fully
 * qualified class name of the message's payload.
 *
 * @param <P> The type of {@link #getPayload() payload} expressing the query in this {@link SubscriptionQueryMessage}.
 * @param <I> The type of {@link #getResponseType() initial response} expected from this
 *            {@link SubscriptionQueryMessage}.
 * @param <U> The type of {@link #getUpdateResponseType() incremental updates} expected from this
 *            {@link SubscriptionQueryMessage}.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 3.3.0
 */
public class GenericSubscriptionQueryMessage<P, I, U>
        extends GenericQueryMessage<P, I>
        implements SubscriptionQueryMessage<P, I, U> {

    private final ResponseType<U> updateResponseType;

    /**
     * Constructs a {@link GenericSubscriptionQueryMessage} for the given {@code type}, {@code payload},
     * {@code responseType}, and {@code updateResponseType}.
     * <p>
     * The query name is set to the fully qualified class name of the {@code payload}. The {@link MetaData} defaults to
     * an empty instance.
     *
     * @param type               The {@link QualifiedName type} for this {@link SubscriptionQueryMessage}.
     * @param payload            The payload of type {@code P} expressing the query for this
     *                           {@link SubscriptionQueryMessage}.
     * @param responseType       The expected {@link ResponseType response type} for this
     *                           {@link SubscriptionQueryMessage}.
     * @param updateResponseType The expected {@link ResponseType type} of incremental updates for this
     *                           {@link SubscriptionQueryMessage}.
     */
    public GenericSubscriptionQueryMessage(@Nonnull QualifiedName type,
                                           @Nonnull P payload,
                                           @Nonnull ResponseType<I> responseType,
                                           @Nonnull ResponseType<U> updateResponseType) {
        this(type, payload.getClass().getName(), payload, responseType, updateResponseType);
    }

    /**
     * Constructs a {@link GenericSubscriptionQueryMessage} for the given {@code type}, {@code payload},
     * {@code queryName}, {@code responseType}, and {@code updateResponseType}.
     * <p>
     * The {@link MetaData} defaults to an empty instance.
     *
     * @param type               The {@link QualifiedName type} for this {@link SubscriptionQueryMessage}.
     * @param queryName          The name identifying the query to execute by this {@link SubscriptionQueryMessage}.
     * @param payload            The payload of type {@code P} expressing the query for this
     *                           {@link SubscriptionQueryMessage}.
     * @param responseType       The expected {@link ResponseType response type} for this
     *                           {@link SubscriptionQueryMessage}.
     * @param updateResponseType The expected {@link ResponseType type} of incremental updates for this
     *                           {@link SubscriptionQueryMessage}.
     */
    public GenericSubscriptionQueryMessage(@Nonnull QualifiedName type,
                                           @Nonnull String queryName,
                                           @Nonnull P payload,
                                           @Nonnull ResponseType<I> responseType,
                                           @Nonnull ResponseType<U> updateResponseType) {
        super(type, queryName, payload, responseType);
        this.updateResponseType = updateResponseType;
    }

    /**
     * Constructs a {@link GenericSubscriptionQueryMessage} with given {@code delegate}, {@code queryName},
     * {@code responseType}, and {@code updateResponseType}.
     * <p>
     * The {@code delegate} will be used supply the {@link Message#getPayload() payload}, {@link Message#type() type},
     * {@link Message#getMetaData() metadata} and {@link Message#getIdentifier() identifier} of the resulting
     * {@code GenericQueryMessage}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate           The {@link Message} containing {@link Message#getPayload() payload},
     *                           {@link Message#type() type}, {@link Message#getIdentifier() identifier} and
     *                           {@link Message#getMetaData() metadata} for the {@link SubscriptionQueryMessage} to
     *                           reconstruct.
     * @param queryName          The name identifying the query to execute by this {@link SubscriptionQueryMessage}.
     * @param responseType       The expected {@link ResponseType response type} for this
     *                           {@link SubscriptionQueryMessage}.
     * @param updateResponseType The expected {@link ResponseType type} of incremental updates for this
     *                           {@link SubscriptionQueryMessage}.
     */
    public GenericSubscriptionQueryMessage(@Nonnull Message<P> delegate,
                                           @Nonnull String queryName,
                                           @Nonnull ResponseType<I> responseType,
                                           @Nonnull ResponseType<U> updateResponseType) {
        super(delegate, queryName, responseType);
        this.updateResponseType = updateResponseType;
    }

    @Override
    public ResponseType<U> getUpdateResponseType() {
        return updateResponseType;
    }

    @Override
    public GenericSubscriptionQueryMessage<P, I, U> withMetaData(@Nonnull Map<String, ?> metaData) {
        return new GenericSubscriptionQueryMessage<>(getDelegate().withMetaData(metaData),
                                                     getQueryName(),
                                                     getResponseType(),
                                                     updateResponseType);
    }

    @Override
    public GenericSubscriptionQueryMessage<P, I, U> andMetaData(@Nonnull Map<String, ?> metaData) {
        return new GenericSubscriptionQueryMessage<>(getDelegate().andMetaData(metaData),
                                                     getQueryName(),
                                                     getResponseType(),
                                                     updateResponseType);
    }
}
