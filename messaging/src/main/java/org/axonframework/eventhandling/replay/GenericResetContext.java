/*
 * Copyright (c) 2010-2024. Axon Framework
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
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;

import java.io.Serial;
import java.util.Map;

/**
 * Generic implementation of the {@link ResetContext} interface.
 *
 * @param <P> The type of {@link #getPayload()} contained in this {@link GenericResetContext}.
 * @author Steven van Beelen
 * @since 4.4.0
 */
public class GenericResetContext<P> extends MessageDecorator<P> implements ResetContext<P> {

    @Serial
    private static final long serialVersionUID = -6872386525166762225L;

    /**
     * The {@link QualifiedName name} of <em>any</em> {@link GenericResetContext} instance.
     */
    public static final QualifiedName NAME = new QualifiedName("axon.framework", "resetContext", "5.0.0");

    /**
     * Returns the given {@code messageOrPayload} as a {@link ResetContext}. If {@code messageOrPayload} already
     * implements {@code ResetContext}, it is returned as-is. If it implements {@link Message}, {@code messageOrPayload}
     * will be cast to {@code Message} and current time is used to create a {@code ResetContext}. Otherwise, the given
     * {@code messageOrPayload} is wrapped into a {@link GenericResetContext} as its payload.
     *
     * @param messageOrPayload the payload to wrap or cast as {@link ResetContext}
     * @param <T>              the type of payload contained in the message
     * @return a {@link ResetContext} containing given {@code messageOrPayload} as payload, or the
     * {@code messageOrPayload} if it already implements {@code ResetContext}.
     */
    @SuppressWarnings("unchecked")
    public static <T> ResetContext<T> asResetContext(Object messageOrPayload) {
        if (messageOrPayload instanceof ResetContext) {
            return (ResetContext<T>) messageOrPayload;
        } else if (messageOrPayload instanceof Message) {
            return new GenericResetContext<>((Message<T>) messageOrPayload);
        }
        return new GenericResetContext<>((T) messageOrPayload);
    }

    /**
     * Constructs a {@link GenericResetContext} for the given {@code payload}.
     * <p>
     * The {@link MetaData} defaults to an empty instance.
     * <p>
     * The {@link #name()} defaults to {@link #NAME}.
     *
     * @param payload The payload of type {@code P} for this {@link ResetContext}.
     */
    public GenericResetContext(@Nullable P payload) {
        this(payload, MetaData.emptyInstance());
    }

    /**
     * Constructs a {@link GenericResetContext} for the given {@code payload} and {@code metaData}.
     * <p>
     * The {@link #name()} defaults to {@link #NAME}.
     *
     * @param payload  The payload of type {@code P} for this {@link ResetContext}.
     * @param metaData The metadata for this {@link ResetContext}.
     */
    public GenericResetContext(@Nullable P payload,
                               @Nonnull Map<String, ?> metaData) {
        this(new GenericMessage<>(NAME, payload, metaData));
    }

    /**
     * Constructs a {@link GenericResetContext} for the given {@code delegate}, intended to reconstruct another
     * {@link ResetContext}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate The {@link Message} containing {@link Message#getPayload() payload}, {@link Message#name() name},
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
