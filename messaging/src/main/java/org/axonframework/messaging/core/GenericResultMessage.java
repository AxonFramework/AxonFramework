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

package org.axonframework.messaging.core;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.ObjectUtils;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;
import org.axonframework.conversion.Converter;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;

/**
 * Generic implementation of {@link ResultMessage} interface.
 *
 * @author Milan Savic
 * @author Steven van Beelen
 * @since 4.0.0
 */
public class GenericResultMessage extends MessageDecorator implements ResultMessage {

    /**
     * Constructs a {@link GenericResultMessage} for the given {@code type} and {@code result}.
     * <p>
     * Uses the correlation data of the current Unit of Work, if present.
     *
     * @param type   The {@link MessageType type} for this {@link ResultMessage}.
     * @param result The result for this {@link ResultMessage}.
     */
    public GenericResultMessage(@Nonnull MessageType type,
                                @Nullable Object result) {
        this(type, result, Metadata.emptyInstance());
    }

    /**
     * Constructs a {@code GenericResultMessage} for the given {@code type}, {@code result}, and {@code metadata}.
     *
     * @param type     The {@link MessageType type} for this {@link ResultMessage}.
     * @param result   The result for this {@link ResultMessage}.
     * @param metadata The metadata for this {@link ResultMessage}.
     */
    public GenericResultMessage(@Nonnull MessageType type,
                                @Nullable Object result,
                                @Nonnull Map<String, String> metadata) {
        this(new GenericMessage(type, result, metadata));
    }

    /**
     * Constructs a {@code GenericResultMessage} for the given {@code delegate}, intended to reconstruct another
     * {@link ResultMessage}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate The {@link Message} containing {@link Message#payload() payload}, {@link Message#type() type},
     *                 {@link Message#identifier() identifier} and {@link Message#metadata() metadata} for the
     *                 {@link QueryResponseMessage} to reconstruct.
     */
    public GenericResultMessage(@Nonnull Message delegate) {
        super(delegate);
    }

    /**
     * Returns the given {@code result} as a {@link ResultMessage} instance. If {@code result} already implements
     * {@link ResultMessage}, it is returned as-is. If {@code result} implements {@link Message}, payload and meta data
     * will be used to construct new {@code GenericResultMessage}. Otherwise, the given {@code result} is wrapped into a
     * {@code GenericResultMessage} as its payload.
     *
     * @param result the command result to be wrapped as {@link ResultMessage}
     * @return a Message containing given {@code result} as payload, or {@code result} if already implements
     * {@link ResultMessage}
     * @deprecated In favor of using the constructor, as we intend to enforce thinking about the
     * {@link MessageType type}.
     */
    @Deprecated
    public static ResultMessage asResultMessage(Object result) {
        if (result instanceof ResultMessage r) {
            return r;
        }
        if (result instanceof Message resultMessage) {
            return new GenericResultMessage(resultMessage);
        }
        MessageType type = result == null ? new MessageType("empty.result") : new MessageType(result.getClass());
        return new GenericResultMessage(type, result);
    }

    @Override
    @Nonnull
    public ResultMessage withMetadata(@Nonnull Map<String, String> metadata) {
        return new GenericResultMessage(delegate().withMetadata(metadata));
    }

    @Override
    @Nonnull
    public ResultMessage andMetadata(@Nonnull Map<String, String> metadata) {
        return new GenericResultMessage(delegate().andMetadata(metadata));
    }

    @Override
    @Nonnull
    public ResultMessage withConvertedPayload(@Nonnull Type type, @Nonnull Converter converter) {
        Object convertedPayload = payloadAs(type, converter);
        if (ObjectUtils.nullSafeTypeOf(convertedPayload).isAssignableFrom(payloadType())) {
            return this;
        }
        Message delegate = delegate();
        Message converted = new GenericMessage(delegate.identifier(),
                                                    delegate.type(),
                                                    convertedPayload,
                                                    delegate.metadata());
        return new GenericResultMessage(converted);
    }

    @Override
    protected String describeType() {
        return "GenericResultMessage";
    }
}
