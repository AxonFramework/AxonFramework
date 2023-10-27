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

package org.axonframework.queryhandling;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.responsetypes.ResponseType;

import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Generic implementation of the {@link SubscriptionQueryMessage}. Unless explicitly provided, it assumes the {@code
 * queryName} of the message is the fully qualified class name of the message's payload.
 *
 * @param <Q> The type of payload expressing the query in this message
 * @param <I> The type of initial response expected from this query
 * @param <U> The type of incremental updates expected from this query
 * @author Allard Buijze
 * @since 3.3
 */
public class GenericSubscriptionQueryMessage<Q, I, U> extends GenericQueryMessage<Q, I>
        implements SubscriptionQueryMessage<Q, I, U> {

    private final ResponseType<U> updateResponseType;

    /**
     * Initializes the message with the given {@code payload}, expected {@code responseType} and expected {@code
     * updateResponseType}. The query name is set to the fully qualified class name of the {@code payload}.
     *
     * @param payload            The payload expressing the query
     * @param responseType       The expected response type
     * @param updateResponseType The expected type of incremental updates
     */
    public GenericSubscriptionQueryMessage(Q payload, ResponseType<I> responseType,
                                           ResponseType<U> updateResponseType) {
        this(payload, payload.getClass().getName(), responseType, updateResponseType);
    }

    /**
     * Initializes the message with the given {@code payload}, {@code queryName}, expected {@code responseType} and
     * expected {@code updateResponseType}.
     *
     * @param payload            The payload expressing the query
     * @param queryName          The name identifying the query to execute
     * @param responseType       The expected response type
     * @param updateResponseType The expected type of incremental updates
     */
    public GenericSubscriptionQueryMessage(Q payload, String queryName, ResponseType<I> responseType,
                                           ResponseType<U> updateResponseType) {
        super(payload, queryName, responseType);
        this.updateResponseType = updateResponseType;
    }

    /**
     * Initializes the message, using given {@code delegate} as the carrier of payload and metadata and given {@code
     * queryName}, expected {@code responseType} and expected {@code updateResponseType}.
     *
     * @param delegate           The message containing the payload and meta data for this message
     * @param queryName          The name identifying the query to execute
     * @param responseType       The expected response type
     * @param updateResponseType The expected type of incremental updates
     */
    public GenericSubscriptionQueryMessage(Message<Q> delegate, String queryName, ResponseType<I> responseType,
                                           ResponseType<U> updateResponseType) {
        super(delegate, queryName, responseType);
        this.updateResponseType = updateResponseType;
    }

    @Override
    public ResponseType<U> getUpdateResponseType() {
        return updateResponseType;
    }

    @Override
    public GenericSubscriptionQueryMessage<Q, I, U> withMetaData(@Nonnull Map<String, ?> metaData) {
        return new GenericSubscriptionQueryMessage<>(getDelegate().withMetaData(metaData),
                                                     getQueryName(),
                                                     getResponseType(),
                                                     updateResponseType);
    }

    @Override
    public GenericSubscriptionQueryMessage<Q, I, U> andMetaData(@Nonnull Map<String, ?> metaData) {
        return new GenericSubscriptionQueryMessage<>(getDelegate().andMetaData(metaData),
                                                     getQueryName(),
                                                     getResponseType(),
                                                     updateResponseType);
    }
}
