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

package org.axonframework.eventhandling.replay;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDecorator;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;

import java.util.Map;

/**
 * Generic implementation of the {@link ResetContext} interface.
 *
 * @param <P> The type of {@link #getPayload()} contained in this {@link GenericResetContext}.
 * @author Steven van Beelen
 * @since 4.4.0
 */
public class GenericResetContext<P> extends MessageDecorator<P> implements ResetContext<P> {

    /**
     * Constructs a {@link GenericResetContext} for the given {@code type} and {@code payload}.
     * <p>
     * The {@link MetaData} defaults to an empty instance.
     *
     * @param type    The {@link MessageType type} for this {@link ResetContext}.
     * @param payload The payload of type {@code P} for this {@link ResetContext}.
     */
    public GenericResetContext(@Nonnull MessageType type,
                               @Nullable P payload) {
        this(type, payload, MetaData.emptyInstance());
    }

    /**
     * Constructs a {@link GenericResetContext} for the given {@code type}, {@code payload}, and {@code metaData}.
     *
     * @param type     The {@link MessageType type} for this {@link ResetContext}.
     * @param payload  The payload of type {@code P} for this {@link ResetContext}.
     * @param metaData The metadata for this {@link ResetContext}.
     */
    public GenericResetContext(@Nonnull MessageType type,
                               @Nullable P payload,
                               @Nonnull Map<String, ?> metaData) {
        this(new GenericMessage<>(type, payload, metaData));
    }

    /**
     * Constructs a {@link GenericResetContext} for the given {@code delegate}, intended to reconstruct another
     * {@link ResetContext}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate The {@link Message} containing {@link Message#getPayload() payload}, {@link Message#type() type},
     *                 {@link Message#getIdentifier() identifier} and {@link Message#getMetaData() metadata} for the
     *                 {@link EventMessage} to reconstruct.
     */
    public GenericResetContext(Message<P> delegate) {
        super(delegate);
    }

    @Override
    public GenericResetContext<P> withMetaData(@Nonnull Map<String, ?> metaData) {
        return new GenericResetContext<>(getDelegate().withMetaData(metaData));
    }

    @Override
    public GenericResetContext<P> andMetaData(@Nonnull Map<String, ?> additionalMetaData) {
        return new GenericResetContext<>(getDelegate().andMetaData(additionalMetaData));
    }

    @Override
    protected String describeType() {
        return "GenericResetContext";
    }
}
