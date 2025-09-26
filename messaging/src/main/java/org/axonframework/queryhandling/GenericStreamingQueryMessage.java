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
import org.axonframework.messaging.responsetypes.PublisherResponseType;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.serialization.Converter;
import org.reactivestreams.Publisher;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * Generic implementation of the {@link StreamingQueryMessage} interface.
 *
 * @author Milan Savic
 * @author Stefan Dragisic
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class GenericStreamingQueryMessage
        extends GenericQueryMessage
        implements StreamingQueryMessage {

    /**
     * Constructs a {@code GenericStreamingQueryMessage} for the given {@code type}, {@code payload}, and
     * {@code responseType}.
     * <p>
     * The query name is set to the fully qualified class name of the {@code payload}. The {@link Metadata} defaults to
     * an empty instance.
     *
     * @param <P>          The type of {@link #payload() payload} expressing the query in this class.
     * @param type         The {@link MessageType type} for this {@link StreamingQueryMessage}.
     * @param payload      The payload expressing the query for this {@link StreamingQueryMessage}.
     * @param responseType The expected {@link Class response type} for this {@link StreamingQueryMessage}.
     */
    public <P> GenericStreamingQueryMessage(@Nonnull MessageType type,
                                            @Nullable Object payload,
                                            @Nonnull Class<P> responseType) {
        this(new GenericMessage(type, payload, Metadata.emptyInstance()), new PublisherResponseType<>(responseType));
    }

    /**
     * Constructs a {@code GenericStreamingQueryMessage} with given {@code delegate}, and {@code responseType}.
     * <p>
     * The {@code delegate} will be used supply the {@link Message#payload() payload}, {@link Message#type() type},
     * {@link Message#metadata() metadata} and {@link Message#identifier() identifier} of the resulting
     * {@code GenericQueryMessage}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param <P>          The type of {@link #payload() payload} expressing the query in this class.
     * @param delegate     The {@link Message} containing {@link Message#payload() payload},
     *                     {@link Message#type() type}, {@link Message#identifier() identifier} and
     *                     {@link Message#metadata() metadata} for the {@link SubscriptionQueryMessage} to reconstruct.
     * @param responseType The expected {@link Class response type} for this {@link StreamingQueryMessage}.
     */
    public <P> GenericStreamingQueryMessage(@Nonnull Message delegate,
                                            @Nonnull Class<P> responseType) {
        this(delegate, new PublisherResponseType<>(responseType));
    }

    private <P> GenericStreamingQueryMessage(@Nonnull Message delegate,
                                             @Nonnull ResponseType<Publisher<P>> responseType) {
        super(delegate, responseType);
    }

    @SuppressWarnings("unchecked")
    @Override
    @Nonnull
    public ResponseType<Publisher<?>> responseType() {
        return (ResponseType<Publisher<?>>) super.responseType();
    }

    @Override
    @Nonnull
    public StreamingQueryMessage withMetadata(@Nonnull Map<String, String> metadata) {
        return new GenericStreamingQueryMessage(delegate().withMetadata(metadata), internalResponseType());
    }

    @Override
    @Nonnull
    public StreamingQueryMessage andMetadata(@Nonnull Map<String, String> metadata) {
        return new GenericStreamingQueryMessage(delegate().andMetadata(metadata), internalResponseType());
    }

    @Override
    @Nonnull
    public StreamingQueryMessage withConvertedPayload(@Nonnull Type type, @Nonnull Converter converter) {
        Object convertedPayload = payloadAs(type, converter);
        if (ObjectUtils.nullSafeTypeOf(convertedPayload).isAssignableFrom(payloadType())) {
            return (StreamingQueryMessage) super.withConvertedPayload(type, converter);
        }
        Message delegate = delegate();
        GenericMessage converted = new GenericMessage(delegate.identifier(),
                                                      delegate.type(),
                                                      convertedPayload,
                                                      delegate.metadata());
        return new GenericStreamingQueryMessage(converted, internalResponseType());
    }

    @Override
    protected String describeType() {
        return "GenericStreamingQueryMessage";
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    private <P> ResponseType<Publisher<P>> internalResponseType() {
        return (ResponseType<Publisher<P>>) super.responseType();
    }
}
