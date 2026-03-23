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

import org.axonframework.common.ObjectUtils;
import org.axonframework.conversion.Converter;
import org.axonframework.messaging.core.GenericMessage;
import org.axonframework.messaging.core.GenericResultMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;
import org.jspecify.annotations.Nullable;

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
    public GenericCommandResultMessage(MessageType type,
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
    public GenericCommandResultMessage(MessageType type,
                                       Throwable exception) {
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
    public GenericCommandResultMessage(MessageType type,
                                       @Nullable Object commandResult,
                                       Map<String, String> metadata) {
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
    public GenericCommandResultMessage(MessageType type,
                                       Throwable exception,
                                       Map<String, String> metadata) {
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
    public GenericCommandResultMessage(Message delegate) {
        super(delegate);
    }

    @Override
        public CommandResultMessage withMetadata(Map<String, String> metadata) {
        return new GenericCommandResultMessage(delegate().withMetadata(metadata));
    }

    @Override
        public CommandResultMessage andMetadata(Map<String, String> metadata) {
        return new GenericCommandResultMessage(delegate().andMetadata(metadata));
    }

    @Override
        public CommandResultMessage withConvertedPayload(Type type,
                                                     Converter converter) {
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

    /**
     * Returns a new {@code GenericCommandResultMessage} with the same properties as this message and the given
     * {@code converter} set for use in {@link #payloadAs(Class)}.
     * <p>
     * Note: if called from a subtype, the message will lose subtype information because this method creates a new
     * instance of {@code GenericCommandResultMessage}.
     *
     * @param converter the converter for the new message
     * @return a copy of this instance with the converter set
     */
    public GenericCommandResultMessage withConverter(@Nullable Converter converter) {
        Message updated = delegate() instanceof GenericMessage gm
                ? gm.withConverter(converter)
                : delegate();
        return new GenericCommandResultMessage(updated);
    }

    @Override
    protected String describeType() {
        return "GenericCommandResultMessage";
    }
}
