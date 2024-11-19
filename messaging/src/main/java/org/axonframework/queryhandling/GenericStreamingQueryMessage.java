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
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.responsetypes.PublisherResponseType;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.reactivestreams.Publisher;

import java.util.Map;

/**
 * Generic implementation of the {@link StreamingQueryMessage} interface.
 * <p>
 * Unless explicitly provided, it assumes the {@code queryName} of the {@code StreamingQueryMessage} is the fully
 * qualified class name of the message's payload.
 *
 * @param <P> The type of {@link #getPayload() payload} expressing the query in this {@link StreamingQueryMessage}.
 * @param <R> The type of {@link #getResponseType() response} expected from this {@link StreamingQueryMessage} streamed
 *            via {@link Publisher}.
 * @author Milan Savic
 * @author Stefan Dragisic
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class GenericStreamingQueryMessage<P, R>
        extends GenericQueryMessage<P, Publisher<R>>
        implements StreamingQueryMessage<P, R> {

    /**
     * Constructs a {@link GenericStreamingQueryMessage} for the given {@code type}, {@code payload}, and
     * {@code responseType}.
     * <p>
     * The query name is set to the fully qualified class name of the {@code payload}. The {@link MetaData} defaults to
     * an empty instance.
     *
     * @param type         The {@link QualifiedName type} for this {@link StreamingQueryMessage}.
     * @param payload      The payload of type {@code P} expressing the query for this {@link StreamingQueryMessage}.
     * @param responseType The expected {@link Class response type} for this {@link StreamingQueryMessage}.
     */
    public GenericStreamingQueryMessage(@Nonnull QualifiedName type,
                                        @Nonnull P payload,
                                        @Nonnull Class<R> responseType) {
        this(type, payload, new PublisherResponseType<>(responseType));
    }

    /**
     * Constructs a {@link GenericStreamingQueryMessage} for the given {@code type}, {@code payload}, {@code queryName},
     * and {@code responseType}.
     * <p>
     * The {@link MetaData} defaults to an empty instance.
     *
     * @param type         The {@link QualifiedName type} for this {@link StreamingQueryMessage}.
     * @param queryName    The name identifying the query to execute by this {@link StreamingQueryMessage}.
     * @param payload      The payload of type {@code P} expressing the query for this {@link StreamingQueryMessage}.
     * @param responseType The expected {@link Class response type} for this {@link StreamingQueryMessage}.
     */
    public GenericStreamingQueryMessage(@Nonnull QualifiedName type,
                                        @Nonnull String queryName,
                                        @Nonnull P payload,
                                        @Nonnull Class<R> responseType) {
        this(type, queryName, payload, new PublisherResponseType<>(responseType));
    }

    /**
     * Constructs a {@link GenericStreamingQueryMessage} for the given {@code type}, {@code payload}, and
     * {@code responseType}.
     * <p>
     * The query name is set to the fully qualified class name of the {@code payload}. The {@link MetaData} defaults to
     * an empty instance.
     *
     * @param type         The {@link QualifiedName type} for this {@link StreamingQueryMessage}.
     * @param payload      The payload of type {@code P} expressing the query for this {@link StreamingQueryMessage}.
     * @param responseType The expected {@link ResponseType response type} for this {@link StreamingQueryMessage}.
     */
    public GenericStreamingQueryMessage(@Nonnull QualifiedName type,
                                        @Nonnull P payload,
                                        @Nonnull ResponseType<Publisher<R>> responseType) {
        super(type, payload, responseType);
    }

    /**
     * Constructs a {@link GenericStreamingQueryMessage} for the given {@code type}, {@code payload}, {@code queryName},
     * and {@code responseType}.
     * <p>
     * The {@link MetaData} defaults to an empty instance.
     *
     * @param type         The {@link QualifiedName type} for this {@link StreamingQueryMessage}.
     * @param queryName    The name identifying the query to execute by this {@link StreamingQueryMessage}.
     * @param payload      The payload of type {@code P} expressing the query for this {@link StreamingQueryMessage}.
     * @param responseType The expected {@link ResponseType response type} for this {@link StreamingQueryMessage}.
     */
    public GenericStreamingQueryMessage(@Nonnull QualifiedName type,
                                        @Nonnull String queryName,
                                        @Nonnull P payload,
                                        @Nonnull ResponseType<Publisher<R>> responseType) {
        this(new GenericMessage<>(type, payload, MetaData.emptyInstance()), queryName, responseType);
    }

    /**
     * Constructs a {@link GenericStreamingQueryMessage} with given {@code delegate}, {@code queryName}, and
     * {@code responseType}.
     * <p>
     * The {@code delegate} will be used supply the {@link Message#getPayload() payload}, {@link Message#type() type},
     * {@link Message#getMetaData() metadata} and {@link Message#getIdentifier() identifier} of the resulting
     * {@code GenericQueryMessage}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate     The {@link Message} containing {@link Message#getPayload() payload},
     *                     {@link Message#type() type}, {@link Message#getIdentifier() identifier} and
     *                     {@link Message#getMetaData() metadata} for the {@link SubscriptionQueryMessage} to
     *                     reconstruct.
     * @param queryName    The name identifying the query to execute by this {@link StreamingQueryMessage}.
     * @param responseType The expected {@link Class response type} for this {@link StreamingQueryMessage}.
     */
    public GenericStreamingQueryMessage(@Nonnull Message<P> delegate,
                                        @Nonnull String queryName,
                                        @Nonnull Class<R> responseType) {
        this(delegate, queryName, new PublisherResponseType<>(responseType));
    }

    /**
     * Constructs a {@link GenericStreamingQueryMessage} with given {@code delegate}, {@code queryName}, and
     * {@code responseType}.
     * <p>
     * The {@code delegate} will be used supply the {@link Message#getPayload() payload}, {@link Message#type() type},
     * {@link Message#getMetaData() metadata} and {@link Message#getIdentifier() identifier} of the resulting
     * {@code GenericQueryMessage}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate     The {@link Message} containing {@link Message#getPayload() payload},
     *                     {@link Message#type() type}, {@link Message#getIdentifier() identifier} and
     *                     {@link Message#getMetaData() metadata} for the {@link SubscriptionQueryMessage} to
     *                     reconstruct.
     * @param queryName    The name identifying the query to execute by this {@link StreamingQueryMessage}.
     * @param responseType The expected {@link ResponseType response type} for this {@link StreamingQueryMessage}.
     */
    public GenericStreamingQueryMessage(@Nonnull Message<P> delegate,
                                        @Nonnull String queryName,
                                        @Nonnull ResponseType<Publisher<R>> responseType) {
        super(delegate, queryName, responseType);
    }

    @Override
    public StreamingQueryMessage<P, R> withMetaData(@Nonnull Map<String, ?> metaData) {
        return new GenericStreamingQueryMessage<>(getDelegate().withMetaData(metaData),
                                                  getQueryName(),
                                                  getResponseType());
    }

    @Override
    public StreamingQueryMessage<P, R> andMetaData(@Nonnull Map<String, ?> metaData) {
        return new GenericStreamingQueryMessage<>(getDelegate().andMetaData(metaData),
                                                  getQueryName(),
                                                  getResponseType());
    }

    @Override
    protected String describeType() {
        return "GenericStreamingQueryMessage";
    }
}
