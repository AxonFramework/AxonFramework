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

package org.axonframework.messaging.commandhandling;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.ObjectUtils;
import org.axonframework.messaging.core.GenericMessage;
import org.axonframework.messaging.core.GenericResultMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;
import org.axonframework.conversion.Converter;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * Generic implementation of the {@link CommandResultMessage} interface.
 *
 * @author Milan Savic
 * @author Steven van Beelen
 * @since 4.0.0
 */
public class GenericCommandResultMessage extends GenericResultMessage implements CommandResultMessage {

    /**
     * Constructs a {@link GenericResultMessage} for the given {@code type} and {@code commandResult}.
     * <p>
     * Uses the correlation data of the current Unit of Work, if present.
     *
     * @param type          The {@link MessageType type} for this {@link CommandResultMessage}.
     * @param commandResult The result for this {@link CommandResultMessage}.
     */
    public GenericCommandResultMessage(@Nonnull MessageType type,
                                       @Nullable Object commandResult) {
        super(type, commandResult);
    }

    /**
     * Constructs a {@code GenericCommandResultMessage} for the given {@code type} and {@code exception}.
     * <p>
     * Uses the correlation data of the current Unit of Work, if present.
     *
     * @param type      The {@link MessageType type} for this {@link CommandResultMessage}.
     * @param exception The {@link Throwable} describing the error representing the response of this
     *                  {@link CommandResultMessage}.
     */
    @Deprecated
    public GenericCommandResultMessage(@Nonnull MessageType type,
                                       @Nonnull Throwable exception) {
        super(type, exception);
    }

    /**
     * Constructs a {@code GenericCommandResultMessage} for the given {@code type}, {@code commandResult}, and
     * {@code metadata}.
     *
     * @param type          The {@link MessageType type} for this {@link CommandResultMessage}.
     * @param commandResult The result for this {@link CommandResultMessage}.
     * @param metadata      The metadata for this {@link CommandResultMessage}.
     */
    public GenericCommandResultMessage(@Nonnull MessageType type,
                                       @Nullable Object commandResult,
                                       @Nonnull Map<String, String> metadata) {
        super(type, commandResult, metadata);
    }

    /**
     * Constructs a {@code GenericCommandResultMessage} for the given {@code type}, {@code exception}, and
     * {@code metadata}.
     *
     * @param type      The {@link MessageType type} for this {@link CommandResultMessage}.
     * @param exception The {@link Throwable} describing the error representing the response of this
     *                  {@link CommandResultMessage}.
     * @param metadata  The metadata for this {@link CommandResultMessage}.
     */
    @Deprecated
    public GenericCommandResultMessage(@Nonnull MessageType type,
                                       @Nonnull Throwable exception,
                                       @Nonnull Map<String, String> metadata) {
        super(type, exception, metadata);
    }

    /**
     * Constructs a {@code GenericCommandResultMessage} for the given {@code delegate}, intended to reconstruct another
     * {@link CommandResultMessage}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate The {@link Message} containing {@link Message#payload() payload}, {@link Message#type() type},
     *                 {@link Message#identifier() identifier} and {@link Message#metadata() metadata} for the
     *                 {@link QueryResponseMessage} to reconstruct.
     */
    public GenericCommandResultMessage(@Nonnull Message delegate) {
        super(delegate);
    }

    @Override
    @Nonnull
    public CommandResultMessage withMetadata(@Nonnull Map<String, String> metadata) {
        return new GenericCommandResultMessage(delegate().withMetadata(metadata));
    }

    @Override
    @Nonnull
    public CommandResultMessage andMetadata(@Nonnull Map<String, String> metadata) {
        return new GenericCommandResultMessage(delegate().andMetadata(metadata));
    }

    @Override
    @Nonnull
    public CommandResultMessage withConvertedPayload(@Nonnull Type type,
                                                     @Nonnull Converter converter) {
        Object convertedPayload = payloadAs(type, converter);
        if (ObjectUtils.nullSafeTypeOf(convertedPayload).isAssignableFrom(payloadType())) {
            return this;
        }
        Message delegate = delegate();
        Message converted = new GenericMessage(delegate.identifier(),
                                               delegate.type(),
                                               convertedPayload,
                                               delegate.metadata());
        return new GenericCommandResultMessage(converted);
    }

    @Override
    protected String describeType() {
        return "GenericCommandResultMessage";
    }
}
