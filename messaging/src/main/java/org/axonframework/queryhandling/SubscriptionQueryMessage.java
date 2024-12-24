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
import org.axonframework.messaging.responsetypes.ResponseType;

import java.util.Map;

/**
 * A {@link QueryMessage} type that carries a subscription query: a request for information.
 * <p>
 * Besides a payload, subscription query messages also carry the expected
 * {@link #getResponseType() initial response type} and {@link #getUpdateResponseType() update type}. The response type
 * is the type of result expected by the caller. The update type is type of incremental updates.
 * <p>
 * Handlers should only answer a query if they can respond with the appropriate response type and update type.
 *
 * @param <P> The type of {@link #getPayload() payload} expressing the query in this {@link SubscriptionQueryMessage}.
 * @param <I> The type of {@link #getResponseType() initial response} expected from this
 *            {@link SubscriptionQueryMessage}.
 * @param <U> The type of {@link #getUpdateResponseType() incremental updates} expected from this
 *            {@link SubscriptionQueryMessage}.
 * @author Allard Buijze
 * @since 3.3.0
 */
public interface SubscriptionQueryMessage<P, I, U> extends QueryMessage<P, I> {

    /**
     * The {@link ResponseType type of incremental responses} expected by the sender of the query.
     *
     * @return The {@link ResponseType type of incremental responses} expected by the sender of the query.
     */
    ResponseType<U> getUpdateResponseType();

    @Override
    SubscriptionQueryMessage<P, I, U> withMetaData(@Nonnull Map<String, ?> metaData);

    @Override
    SubscriptionQueryMessage<P, I, U> andMetaData(@Nonnull Map<String, ?> additionalMetaData);
}
