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
import org.axonframework.messaging.responsetypes.ResponseType;

import java.util.Map;

/**
 * A {@link Message} type that carries a Query: a request for information.
 * <p>
 * Besides a {@link #getPayload() payload}, {@link QueryMessage QueryMessages} also carry the expected
 * {@link #getResponseType() response type}. This is the type of result expected by the caller.
 * <p>
 * Handlers should only answer a query if they can respond with the appropriate response type.
 *
 * @param <P> The type of {@link #getPayload() payload} expressing the query in this {@link QueryMessage}.
 * @param <R> The type of {@link #getResponseType() response} expected from this {@link QueryMessage}.
 * @author Marc Gathier
 * @since 3.1.0
 */
public interface QueryMessage<P, R> extends Message<P> {

    /**
     * Returns the name of the {@link QueryMessage query} to execute.
     *
     * @return The name of the {@link QueryMessage query}.
     */
    String getQueryName();

    /**
     * Extracts the {@code queryName} from the given {@code payloadOrMessage}, with three possible outcomes:
     * <ul>
     * <li>The {@code payloadOrMessage} is an instance of {@link QueryMessage} - {@link QueryMessage#getQueryName()} is returned.</li>
     * <li>The {@code payloadOrMessage} is an instance of {@link Message} - the name of {@link Message#getPayloadType()} is returned.</li>
     * <li>The {@code payloadOrMessage} is the query payload - {@link Class#getName()} is returned.</li>
     * </ul>
     *
     * @param payloadOrMessage The object to base the {@code queryName} on.
     * @return The {@link QueryMessage#getQueryName()}, the name of {@link Message#getPayloadType()} or the result of
     * {@link Class#getName()}, depending on the type of the {@code payloadOrMessage}.
     */
    static String queryName(@Nonnull Object payloadOrMessage) {
        if (payloadOrMessage instanceof QueryMessage) {
            return ((QueryMessage<?, ?>) payloadOrMessage).getQueryName();
        } else if (payloadOrMessage instanceof Message) {
            return ((Message<?>) payloadOrMessage).getPayloadType().getName();
        }
        return payloadOrMessage.getClass().getName();
    }

    /**
     * The {@link ResponseType type of response} expected by the sender of the query.
     *
     * @return The {@link ResponseType type of response} expected by the sender of the query.
     */
    ResponseType<R> getResponseType();

    QueryMessage<P, R> withMetaData(@Nonnull Map<String, ?> metaData);

    QueryMessage<P, R> andMetaData(@Nonnull Map<String, ?> additionalMetaData);
}
