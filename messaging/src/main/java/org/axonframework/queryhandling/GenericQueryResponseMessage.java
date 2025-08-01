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
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.GenericResultMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;

import java.util.Map;

/**
 * Generic implementation of the {@link QueryResponseMessage} interface.
 *
 * @param <R> The type of {@link #payload() response} contained in this {@link QueryResponseMessage}.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 3.2.0
 */
public class GenericQueryResponseMessage<R> extends GenericResultMessage<R> implements QueryResponseMessage<R> {

    /**
     * Constructs a {@code GenericQueryResponseMessage} for the given {@code type} and {@code payload}.
     * <p>
     * The {@link MetaData} defaults to an empty instance.
     *
     * @param type   The {@link MessageType type} for this {@link QueryResponseMessage}.
     * @param result The result of type {@code R} for this {@link QueryResponseMessage}.
     */
    @SuppressWarnings("unchecked")
    public GenericQueryResponseMessage(@Nonnull MessageType type,
                                       @Nonnull R result) {
        this(type, result, MetaData.emptyInstance(), (Class<R>) result.getClass());
    }

    /**
     * Constructs a {@code GenericQueryResponseMessage} for the given {@code type} and {@code payload}.
     * <p>
     * This constructor allows the actual result to be {@code null}. The {@link MetaData} defaults to an empty
     * instance.
     *
     * @param type   The {@link MessageType type} for this {@link QueryResponseMessage}.
     * @param result The result of type {@code R} for this {@code GenericQueryResponseMessage}. May be {@code null}.
     * @deprecated In favor of {@link #GenericQueryResponseMessage(MessageType, Object)} once the
     * {@code declaredPayloadType} is removed completely.
     */
    @Deprecated
    public GenericQueryResponseMessage(@Nonnull MessageType type,
                                       @Nullable R result,
                                       @Deprecated Class<R> declaredResultType) {
        this(type, result, MetaData.emptyInstance(), declaredResultType);
    }

    /**
     * Constructs a {@code GenericQueryResponseMessage} for the given {@code type} and {@code exception}.
     *
     * @param type      The {@link MessageType type} for this {@link QueryResponseMessage}.
     * @param exception The {@link Throwable} describing the error representing the response of this
     *                  {@link QueryResponseMessage}.
     */
    public GenericQueryResponseMessage(@Nonnull MessageType type,
                                       @Nonnull Throwable exception,
                                       @Deprecated Class<R> declaredResultType) {
        this(type, exception, MetaData.emptyInstance(), declaredResultType);
    }

    /**
     * Constructs a {@code GenericQueryResponseMessage} for the given {@code type}, {@code result}, and
     * {@code metaData}.
     * <p>
     * This constructor allows the actual result to be {@code null}.
     *
     * @param type     The {@link MessageType type} for this {@link QueryResponseMessage}.
     * @param result   The result of type {@code R} for this {@code GenericQueryResponseMessage}. May be {@code null}.
     * @param metaData The metadata for this {@link QueryResponseMessage}.
     */
    public GenericQueryResponseMessage(@Nonnull MessageType type,
                                       @Nullable R result,
                                       @Nonnull Map<String, String> metaData) {
        super(new GenericMessage<>(type, result, metaData));
    }

    /**
     * Constructs a {@code GenericQueryResponseMessage} for the given {@code type}, {@code result}, and
     * {@code metaData}.
     * <p>
     * This constructor allows the actual result to be {@code null}.
     *
     * @param type     The {@link MessageType type} for this {@link QueryResponseMessage}.
     * @param result   The result of type {@code R} for this {@code GenericQueryResponseMessage}. May be {@code null}.
     * @param metaData The metadata for this {@link QueryResponseMessage}.
     * @deprecated In favor of {@link #GenericQueryResponseMessage(MessageType, Object, Map)} once the
     * {@code declaredPayloadType} is removed completely.
     */
    @Deprecated
    public GenericQueryResponseMessage(@Nonnull MessageType type,
                                       @Nullable R result,
                                       @Nonnull Map<String, ?> metaData,
                                       @Deprecated Class<R> declaredResultType) {
        super(new GenericMessage<>(type, result, metaData, declaredResultType));
    }

    /**
     * Constructs a {@code GenericQueryResponseMessage} for the given {@code type}, {@code exception}, and
     * {@code metaData}.
     *
     * @param type      The {@link MessageType type} for this {@link QueryResponseMessage}.
     * @param exception The {@link Throwable} describing the error representing the response of this
     *                  {@link QueryResponseMessage}.
     * @param metaData  The metadata for this {@link QueryResponseMessage}.
     * @deprecated Remove the {@code declaredPayloadType} once the {@code declaredPayloadType} is removed completely
     * from the base {@link Message}.
     */
    @Deprecated
    public GenericQueryResponseMessage(@Nonnull MessageType type,
                                       @Nonnull Throwable exception,
                                       @Nonnull Map<String, ?> metaData,
                                       @Deprecated Class<R> declaredResultType) {
        super(new GenericMessage<>(type, null, metaData, declaredResultType), exception);
    }

    /**
     * Constructs a {@code GenericQueryResponseMessage} for the given {@code delegate}, intended to reconstruct another
     * {@link QueryResponseMessage}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate The {@link Message} containing {@link Message#payload() payload}, {@link Message#type() type},
     *                 {@link Message#identifier() identifier} and {@link Message#getMetaData() metadata} for the
     *                 {@link QueryResponseMessage} to reconstruct.
     */
    public GenericQueryResponseMessage(@Nonnull Message<R> delegate) {
        super(delegate);
    }

    /**
     * Constructs a {@code GenericQueryResponseMessage} for the given {@code delegate} and {@code exception} as a cause
     * for the failure, intended to reconstruct another {@link QueryResponseMessage}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate  The {@link Message} containing {@link Message#payload() payload},
     *                  {@link Message#type() type, {@link Message#identifier() identifier} and {@link
     *                  Message#getMetaData() metadata} for the {@link QueryResponseMessage} to reconstruct.
     * @param exception The {@link Throwable} describing the error representing the response of this
     *                  {@link QueryResponseMessage}.
     */
    public GenericQueryResponseMessage(@Nonnull Message<R> delegate,
                                       @Nonnull Throwable exception) {
        super(delegate, exception);
    }

    @Override
    public GenericQueryResponseMessage<R> withMetaData(@Nonnull Map<String, String> metaData) {
        return new GenericQueryResponseMessage<>(getDelegate().withMetaData(metaData));
    }

    @Override
    public GenericQueryResponseMessage<R> andMetaData(@Nonnull Map<String, String> additionalMetaData) {
        return new GenericQueryResponseMessage<>(getDelegate().andMetaData(additionalMetaData));
    }
}
