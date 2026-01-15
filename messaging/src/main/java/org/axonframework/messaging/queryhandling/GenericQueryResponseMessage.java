/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.queryhandling;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.ObjectUtils;
import org.axonframework.messaging.core.GenericMessage;
import org.axonframework.messaging.core.GenericResultMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.conversion.Converter;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * Generic implementation of the {@link QueryResponseMessage} interface.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 3.2.0
 */
public class GenericQueryResponseMessage extends GenericResultMessage implements QueryResponseMessage {

    /**
     * Constructs a {@code GenericQueryResponseMessage} for the given {@code type} and {@code payload}.
     * <p>
     * The {@link Metadata} defaults to an empty instance.
     *
     * @param type   The {@link MessageType type} for this {@link QueryResponseMessage}.
     * @param result The result for this {@link QueryResponseMessage}.
     */
    public GenericQueryResponseMessage(@Nonnull MessageType type,
                                       @Nullable Object result) {
        this(type, result, ObjectUtils.nullSafeTypeOf(result), Metadata.emptyInstance());
    }

    /**
     * Constructs a {@code GenericQueryResponseMessage} for the given {@code type} and {@code payload}.
     * <p>
     * This constructor allows the actual result to be {@code null}. The {@link Metadata} defaults to an empty
     * instance.
     *
     * @param <R>                The generic type of the expected payload of the resulting object.
     * @param type               The {@link MessageType type} for this {@link QueryResponseMessage}.
     * @param result             The result of type {@code R} for this {@link QueryResponseMessage}. May be
     *                           {@code null}.
     * @param declaredResultType The declared result type of this {@link QueryResponseMessage}.
     */
    public <R> GenericQueryResponseMessage(@Nonnull MessageType type,
                                           @Nullable R result,
                                           @Nonnull Class<R> declaredResultType) {
        this(type, result, declaredResultType, Metadata.emptyInstance());
    }

    /**
     * Constructs a {@code GenericQueryResponseMessage} for the given {@code type}, {@code result}, and
     * {@code metadata}.
     * <p>
     * This constructor allows the actual result to be {@code null}.
     *
     * @param type     The {@link MessageType type} for this {@link QueryResponseMessage}.
     * @param result   The result for this {@link QueryResponseMessage}. May be {@code null}.
     * @param metadata The metadata for this {@link QueryResponseMessage}.
     */
    public GenericQueryResponseMessage(@Nonnull MessageType type,
                                       @Nullable Object result,
                                       @Nonnull Map<String, String> metadata) {
        super(new GenericMessage(type, result, metadata));
    }

    /**
     * Constructs a {@code GenericQueryResponseMessage} for the given {@code type}, {@code result}, and
     * {@code metadata}.
     * <p>
     * This constructor allows the actual result to be {@code null}.
     *
     * @param <R>                The generic type of the expected payload of the resulting object.
     * @param type               The {@link MessageType type} for this {@link QueryResponseMessage}.
     * @param result             The result of type {@code R} for this {@link QueryResponseMessage}. May be
     *                           {@code null}.
     * @param declaredResultType The declared result type of this {@link QueryResponseMessage}.
     * @param metadata           The metadata for this {@link QueryResponseMessage}.
     */
    public <R> GenericQueryResponseMessage(@Nonnull MessageType type,
                                           @Nullable R result,
                                           @Nonnull Class<R> declaredResultType,
                                           @Nonnull Map<String, String> metadata) {
        super(new GenericMessage(type, result, declaredResultType, metadata));
    }

    /**
     * Constructs a {@code GenericQueryResponseMessage} for the given {@code delegate}, intended to reconstruct another
     * {@link QueryResponseMessage}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate The {@link Message} containing {@link Message#payload() payload}, {@link Message#type() type},
     *                 {@link Message#identifier() identifier} and {@link Message#metadata() metadata} for the
     *                 {@link QueryResponseMessage} to reconstruct.
     */
    public GenericQueryResponseMessage(@Nonnull Message delegate) {
        super(delegate);
    }

    @Override
    @Nonnull
    public QueryResponseMessage withMetadata(@Nonnull Map<String, String> metadata) {
        return new GenericQueryResponseMessage(delegate().withMetadata(metadata));
    }

    @Override
    @Nonnull
    public QueryResponseMessage andMetadata(@Nonnull Map<String, String> additionalMetadata) {
        return new GenericQueryResponseMessage(delegate().andMetadata(additionalMetadata));
    }

    @Override
    @Nonnull
    public QueryResponseMessage withConvertedPayload(@Nonnull Type type, @Nonnull Converter converter) {
        Object convertedPayload = payloadAs(type, converter);
        if (ObjectUtils.nullSafeTypeOf(convertedPayload).isAssignableFrom(payloadType())) {
            return this;
        }
        Message delegate = delegate();
        Message converted = new GenericMessage(delegate.identifier(),
                                               delegate.type(),
                                               convertedPayload,
                                               delegate.metadata());
        return new GenericQueryResponseMessage(converted);
    }
}
