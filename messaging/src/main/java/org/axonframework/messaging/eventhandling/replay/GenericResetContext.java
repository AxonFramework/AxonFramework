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

package org.axonframework.messaging.eventhandling.replay;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.ObjectUtils;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.core.GenericMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageDecorator;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.conversion.Converter;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * Generic implementation of the {@link ResetContext} interface.
 *
 * @author Steven van Beelen
 * @since 4.4.0
 */
public class GenericResetContext extends MessageDecorator implements ResetContext {

    /**
     * Constructs a {@code GenericResetContext} for the given {@code type} and {@code payload}.
     * <p>
     * The {@link Metadata} defaults to an empty instance.
     *
     * @param type    The {@link MessageType type} for this {@link ResetContext}.
     * @param payload The payload for this {@link ResetContext}.
     */
    public GenericResetContext(@Nonnull MessageType type,
                               @Nullable Object payload) {
        this(type, payload, Metadata.emptyInstance());
    }

    /**
     * Constructs a {@code GenericResetContext} for the given {@code type}, {@code payload}, and {@code metadata}.
     *
     * @param type     The {@link MessageType type} for this {@link ResetContext}.
     * @param payload  The payload for this {@link ResetContext}.
     * @param metadata The metadata for this {@link ResetContext}.
     */
    public GenericResetContext(@Nonnull MessageType type,
                               @Nullable Object payload,
                               @Nonnull Map<String, String> metadata) {
        this(new GenericMessage(type, payload, metadata));
    }

    /**
     * Constructs a {@code GenericResetContext} for the given {@code delegate}, intended to reconstruct another
     * {@link ResetContext}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate The {@link Message} containing {@link Message#payload() payload}, {@link Message#type() type},
     *                 {@link Message#identifier() identifier} and {@link Message#metadata() metadata} for the
     *                 {@link EventMessage} to reconstruct.
     */
    public GenericResetContext(@Nonnull Message delegate) {
        super(delegate);
    }

    @Override
    @Nonnull
    public ResetContext withMetadata(@Nonnull Map<String, String> metadata) {
        return new GenericResetContext(delegate().withMetadata(metadata));
    }

    @Override
    @Nonnull
    public ResetContext andMetadata(@Nonnull Map<String, String> additionalMetadata) {
        return new GenericResetContext(delegate().andMetadata(additionalMetadata));
    }

    @Override
    @Nonnull
    public ResetContext withConvertedPayload(@Nonnull Type type, @Nonnull Converter converter) {
        Object convertedPayload = this.payloadAs(type, converter);
        if (ObjectUtils.nullSafeTypeOf(convertedPayload).isAssignableFrom(payloadType())) {
            return this;
        }
        Message delegate = delegate();
        return new GenericResetContext(new GenericMessage(delegate.identifier(),
                                                          delegate.type(),
                                                          convertedPayload,
                                                          delegate.metadata()));
    }

    @Override
    protected String describeType() {
        return "GenericResetContext";
    }
}
