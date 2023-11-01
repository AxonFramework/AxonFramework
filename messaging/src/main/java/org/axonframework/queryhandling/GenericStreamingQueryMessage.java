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

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.responsetypes.PublisherResponseType;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * Generic implementation of the {@link StreamingQueryMessage}.
 *
 * @param <Q> the type of streaming query payload
 * @param <R> the type of the result streamed via {@link Flux}
 * @author Milan Savic
 * @author Stefan Dragisic
 * @since 4.6.0
 */
public class GenericStreamingQueryMessage<Q, R> extends GenericQueryMessage<Q, Publisher<R>>
        implements StreamingQueryMessage<Q, R> {

    /**
     * Initializes the message with the given {@code payload} and expected {@code responseType}. The query name is set
     * to the fully qualified class name of the {@code payload}.
     *
     * @param payload      The payload expressing the query
     * @param responseType The expected response type
     */
    public GenericStreamingQueryMessage(Q payload, Class<R> responseType) {
        this(payload, new PublisherResponseType<>(responseType));
    }

    /**
     * Initializes the message with the given {@code payload}, {@code queryName} and expected {@code responseType}.
     *
     * @param payload      The payload expressing the query
     * @param queryName    The name identifying the query to execute
     * @param responseType The expected response type
     */
    public GenericStreamingQueryMessage(Q payload, String queryName, Class<R> responseType) {
        this(payload, queryName, new PublisherResponseType<>(responseType));
    }

    /**
     * Initializes the message with the given {@code payload} and expected {@code responseType}. The query name is set
     * to the fully qualified class name of the {@code payload}.
     *
     * @param payload      The payload expressing the query
     * @param responseType The expected response type
     */
    public GenericStreamingQueryMessage(Q payload, ResponseType<Publisher<R>> responseType) {
        super(payload, responseType);
    }

    /**
     * Initializes the message with the given {@code payload}, {@code queryName} and expected {@code responseType}.
     *
     * @param payload      The payload expressing the query
     * @param queryName    The name identifying the query to execute
     * @param responseType The expected response type
     */
    public GenericStreamingQueryMessage(Q payload, String queryName, ResponseType<Publisher<R>> responseType) {
        this(new GenericMessage<>(payload, MetaData.emptyInstance()), queryName, responseType);
    }

    /**
     * Initializes the message with the given {@code delegate}, {@code queryName} and expected {@code responseType}.
     *
     * @param delegate     The message containing the payload and meta-data for this message
     * @param queryName    The name identifying the query to execute
     * @param responseType The expected response type
     */
    public GenericStreamingQueryMessage(Message<Q> delegate, String queryName, Class<R> responseType) {
        this(delegate, queryName, new PublisherResponseType<>(responseType));
    }

    /**
     * Initialize the Query Message, using given {@code delegate} as the carrier of payload and metadata and given
     * {@code queryName} and expecting the given {@code responseType}.
     *
     * @param delegate     The message containing the payload and meta-data for this message
     * @param queryName    The name identifying the query to execute
     * @param responseType The expected response type
     */
    public GenericStreamingQueryMessage(Message<Q> delegate, String queryName, ResponseType<Publisher<R>> responseType) {
        super(delegate, queryName, responseType);
    }

    @Override
    public StreamingQueryMessage<Q, R> withMetaData(Map<String, ?> metaData) {
        return new GenericStreamingQueryMessage<>(getDelegate().withMetaData(metaData),
                                                  getQueryName(),
                                                  getResponseType());
    }

    @Override
    public StreamingQueryMessage<Q, R> andMetaData(Map<String, ?> metaData) {
        return new GenericStreamingQueryMessage<>(getDelegate().andMetaData(metaData),
                                                  getQueryName(),
                                                  getResponseType());
    }

    @Override
    protected String describeType() {
        return "GenericStreamingQueryMessage";
    }
}
