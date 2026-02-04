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
package org.axonframework.messaging.queryhandling;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.common.ObjectUtils;
import org.axonframework.messaging.core.GenericMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageDecorator;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.conversion.Converter;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Generic implementation of the {@link QueryMessage} interface.
 *
 * @author Marc Gathier
 * @author Steven van Beelen
 * @since 3.1.0
 */
public class GenericQueryMessage extends MessageDecorator implements QueryMessage {

    private final Integer priority;

    /**
     * Constructs a {@link GenericQueryMessage} for the given {@code type}, {@code payload}, and {@code responseType}.
     * <p>
     * The {@link Metadata} defaults to an empty instance. Initializes the message with the given {@code payload} and
     * expected {@code responseType}.
     *
     * @param type         The {@link MessageType type} for this {@link QueryMessage}.
     * @param payload      The payload expressing the query for this {@link CommandMessage}.
     */
    public GenericQueryMessage(@Nonnull MessageType type,
                               @Nullable Object payload) {
        this(new GenericMessage(type, payload, Metadata.emptyInstance()),
             null);
    }

    /**
     * Constructs a {@code GenericQueryMessage} with given {@code delegate} and {@code responseType}.
     * <p>
     * The {@code delegate} will be used supply the {@link Message#payload() payload}, {@link Message#type() type},
     * {@link Message#metadata() metadata} and {@link Message#identifier() identifier} of the resulting
     * {@code GenericQueryMessage}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate     The {@link Message} containing {@link Message#payload() payload},
     *                     {@link Message#type() type}, {@link Message#identifier() identifier} and
     *                     {@link Message#metadata() metadata} for the {@link QueryMessage} to reconstruct.
     * @see GenericQueryMessage(Message, MessageType, Integer)
     */
    public GenericQueryMessage(@Nonnull Message delegate) {
        this(delegate, null);
    }

    /**
     * Constructs a {@code GenericQueryMessage} with given {@code delegate}, {@code responseType} and {@code priority}.
     * <p>
     * The {@code delegate} will be used supply the {@link Message#payload() payload}, {@link Message#type() type},
     * {@link Message#metadata() metadata} and {@link Message#identifier() identifier} of the resulting
     * {@code GenericQueryMessage}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate     The {@link Message} containing {@link Message#payload() payload},
     *                     {@link Message#type() type}, {@link Message#identifier() identifier} and
     *                     {@link Message#metadata() metadata} for the {@link QueryMessage} to reconstruct.
     * @param priority     The priority of this query message. May be {@code null} to indicate no priority.
     */
    public GenericQueryMessage(@Nonnull Message delegate,
                               @Nullable Integer priority) {
        super(delegate);
        this.priority = priority;
    }

    @Override
    @Nonnull
    public QueryMessage withMetadata(@Nonnull Map<String, String> metadata) {
        return new GenericQueryMessage(delegate().withMetadata(metadata), priority);
    }

    @Override
    @Nonnull
    public QueryMessage andMetadata(@Nonnull Map<String, String> metadata) {
        return new GenericQueryMessage(delegate().andMetadata(metadata), priority);
    }

    @Override
    @Nonnull
    public QueryMessage withConvertedPayload(@Nonnull Type type, @Nonnull Converter converter) {
        Object convertedPayload = payloadAs(type, converter);
        if (ObjectUtils.nullSafeTypeOf(convertedPayload).isAssignableFrom(payloadType())) {
            return this;
        }
        Message delegate = delegate();
        Message converted = new GenericMessage(delegate.identifier(),
                                               delegate.type(),
                                               convertedPayload,
                                               delegate.metadata());
        return new GenericQueryMessage(converted, priority);
    }

    @Override
    public OptionalInt priority() {
        return priority != null ? OptionalInt.of(priority) : OptionalInt.empty();
    }

    @Override
    protected void describeTo(StringBuilder stringBuilder) {
        super.describeTo(stringBuilder);
        stringBuilder
                .append(", priority='").append(priority().orElse(0)).append("'");
    }

    @Override
    protected String describeType() {
        return "GenericQueryMessage";
    }
}
