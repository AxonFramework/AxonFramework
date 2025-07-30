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
import org.axonframework.messaging.Message;
import org.axonframework.messaging.responsetypes.ResponseType;

import java.util.Map;

/**
 * A {@link Message} type that carries a Query: a request for information.
 * <p>
 * Besides a {@link #payload() payload}, {@link QueryMessage QueryMessages} also carry the expected
 * {@link #getResponseType() response type}. This is the type of result expected by the caller.
 * <p>
 * Handlers should only answer a query if they can respond with the appropriate response type.
 *
 * @param <P> The type of {@link #payload() payload} expressing the query in this {@link QueryMessage}.
 * @param <R> The type of {@link #getResponseType() response} expected from this {@link QueryMessage}.
 * @author Marc Gathier
 * @since 3.1.0
 */
public interface QueryMessage<P, R> extends Message<P> {

    /**
     * The {@link ResponseType type of response} expected by the sender of the query.
     *
     * @return The {@link ResponseType type of response} expected by the sender of the query.
     */
    ResponseType<R> getResponseType();

    @Override
    QueryMessage<P, R> withMetaData(@Nonnull Map<String, String> metaData);

    @Override
    QueryMessage<P, R> andMetaData(@Nonnull Map<String, String> additionalMetaData);
}
