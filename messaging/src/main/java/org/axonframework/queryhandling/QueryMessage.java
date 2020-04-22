/*
 * Copyright (c) 2010-2020. Axon Framework
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

/**
 * Message type that carries a Query: a request for information. Besides a payload, Query Messages also carry the
 * expected response type. This is the type of result expected by the caller.
 * <p>
 * Handlers should only answer a query if they can respond with the appropriate response type.
 *
 * @author Marc Gathier
 * @since 3.1
 */
public interface QueryMessage<T, R> extends Message<T> {

    /**
     * Returns the name identifying the query to be executed.
     *
     * @return the name identifying the query to be executed.
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
     * @param payloadOrMessage the object to base the {@code queryName} on
     * @return the {@link QueryMessage#getQueryName()}, the name of {@link Message#getPayloadType()} or the result of {@link
     * Class#getName()}, depending on the type of the {@code payloadOrMessage}
     */
    static String queryName(Object payloadOrMessage) {
        if (payloadOrMessage instanceof QueryMessage) {
            return ((QueryMessage<?, ?>) payloadOrMessage).getQueryName();
        } else if (payloadOrMessage instanceof Message) {
            return ((Message<?>) payloadOrMessage).getPayloadType().getName();
        }
        return payloadOrMessage.getClass().getName();
    }

    /**
     * The type of response expected by the sender of the query
     *
     * @return the type of response expected by the sender of the query
     */
    ResponseType<R> getResponseType();

    /**
     * Returns a copy of this QueryMessage with the given {@code metaData}. The payload remains unchanged.
     *
     * @param metaData The new MetaData for the QueryMessage
     * @return a copy of this message with the given MetaData
     */
    QueryMessage<T, R> withMetaData(Map<String, ?> metaData);

    /**
     * Returns a copy of this QueryMessage with its MetaData merged with given {@code metaData}. The payload remains
     * unchanged.
     *
     * @param additionalMetaData The MetaData to merge into the QueryMessage
     * @return a copy of this message with the given additional MetaData
     */
    QueryMessage<T, R> andMetaData(Map<String, ?> additionalMetaData);
}
