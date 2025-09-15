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

package org.axonframework.commandhandling;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.ObjectUtils;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDecorator;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.Metadata;
import org.axonframework.serialization.Converter;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Generic implementation of the {@link CommandMessage} interface.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 2.0.0
 */
public class GenericCommandMessage extends MessageDecorator implements CommandMessage {

    private final String routingKey;
    private final Integer priority;

    /**
     * Constructs a {@code GenericCommandMessage} for the given {@code type} and {@code payload}.
     * <p>
     * The {@link Metadata} defaults to an empty instance.
     *
     * @param type    The {@link MessageType type} for this {@link CommandMessage}.
     * @param payload The payload for this {@link CommandMessage}.
     */
    public GenericCommandMessage(@Nonnull MessageType type,
                                 @Nullable Object payload) {
        this(type, payload, Metadata.emptyInstance());
    }

    /**
     * Constructs a {@code GenericCommandMessage} for the given {@code type}, {@code payload}, and {@code metadata}.
     *
     * @param type     The {@link MessageType type} for this {@link CommandMessage}.
     * @param payload  The payload for this {@link CommandMessage}.
     * @param metadata The metadata for this {@link CommandMessage}.
     */
    public GenericCommandMessage(@Nonnull MessageType type,
                                 @Nullable Object payload,
                                 @Nonnull Map<String, String> metadata) {
        this(new GenericMessage(type, payload, metadata));
    }


    /**
     * Constructs a {@code GenericCommandMessage} for the given {@code type}, {@code payload}, and {@code metadata}.
     * <p>
     * Optionally, a {@code routingKey} and/or a {@code priority} may be passed.
     *
     * @param type       The {@link MessageType type} for this {@link CommandMessage}.
     * @param payload    The payload for this {@link CommandMessage}.
     * @param metadata   The metadata for this {@link CommandMessage}.
     * @param routingKey The routing key for this {@link CommandMessage}, if any.
     * @param priority   The priority for this {@link CommandMessage}, if any.
     */
    public GenericCommandMessage(@Nonnull MessageType type,
                                 @Nonnull Object payload,
                                 @Nonnull Map<String, String> metadata,
                                 @Nullable String routingKey,
                                 @Nullable Integer priority) {
        this(new GenericMessage(type, payload, metadata), routingKey, priority);
    }

    /**
     * Constructs a {@code GenericCommandMessage} with given {@code delegate}.
     * <p>
     * The {@code delegate} will be used supply the {@link Message#payload() payload}, {@link Message#type() type},
     * {@link Message#metadata() metadata} and {@link Message#identifier() identifier} of the resulting
     * {@code GenericCommandMessage}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate The {@link Message} containing {@link Message#payload() payload},
     *                 {@link Message#type() qualifiedName}, {@link Message#identifier() identifier} and
     *                 {@link Message#metadata() metadata} for the {@link CommandMessage} to reconstruct.
     */
    public GenericCommandMessage(@Nonnull Message delegate) {
        this(delegate, null, null);
    }

    /**
     * Constructs a {@code GenericCommandMessage} with given {@code delegate}, {@code routingKey}, and
     * {@code priority}.
     * <p>
     * The {@code delegate} will be used supply the {@link Message#payload() payload}, {@link Message#type() type},
     * {@link Message#metadata() metadata} and {@link Message#identifier() identifier} of the resulting
     * {@code GenericCommandMessage}.<br/> Optionally, a {@code routingKey} and/or a {@code priority} may be passed.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate   The {@link Message} containing {@link Message#payload() payload},
     *                   {@link Message#type() qualifiedName}, {@link Message#identifier() identifier} and
     *                   {@link Message#metadata() metadata} for the {@link CommandMessage} to reconstruct.
     * @param routingKey The routing key for this {@link CommandMessage}, if any.
     * @param priority   The priority for this {@link CommandMessage}, if any.
     */
    public GenericCommandMessage(@Nonnull Message delegate,
                                 @Nullable String routingKey,
                                 @Nullable Integer priority) {
        super(delegate);
        this.routingKey = routingKey;
        this.priority = priority;
    }

    @Override
    public Optional<String> routingKey() {
        return Optional.ofNullable(routingKey);
    }

    @Override
    public OptionalInt priority() {
        if (priority == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(priority);
    }

    @Override
    @Nonnull
    public CommandMessage withMetadata(@Nonnull Map<String, String> metadata) {
        return new GenericCommandMessage(delegate().withMetadata(metadata));
    }

    @Override
    @Nonnull
    public CommandMessage andMetadata(@Nonnull Map<String, String> metadata) {
        return new GenericCommandMessage(delegate().andMetadata(metadata));
    }

    @Override
    @Nonnull
    public CommandMessage withConvertedPayload(@Nonnull Type type, @Nonnull Converter converter) {
        Object convertedPayload = payloadAs(type, converter);
        if (ObjectUtils.nullSafeTypeOf(convertedPayload).isAssignableFrom(payloadType())) {
            return this;
        }
        Message delegate = delegate();
        Message converted = new GenericMessage(delegate.identifier(),
                                               delegate.type(),
                                               convertedPayload,
                                               delegate.metadata());
        return new GenericCommandMessage(converted, routingKey, priority);
    }

    @Override
    protected String describeType() {
        return "GenericCommandMessage";
    }
}