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
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDecorator;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.Converter;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * Generic implementation of the {@link CommandMessage} interface.
 *
 * @param <P> The type of {@link #payload() payload} contained in this {@link CommandMessage}.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 2.0.0
 */
public class GenericCommandMessage<P> extends MessageDecorator<P> implements CommandMessage<P> {

    /**
     * Constructs a {@code GenericCommandMessage} for the given {@code type} and {@code payload}.
     * <p>
     * The {@link MetaData} defaults to an empty instance.
     *
     * @param type    The {@link MessageType type} for this {@link CommandMessage}.
     * @param payload The payload of type {@code P} for this {@link CommandMessage}.
     */
    public GenericCommandMessage(@Nonnull MessageType type,
                                 @Nonnull P payload) {
        this(type, payload, MetaData.emptyInstance());
    }

    /**
     * Constructs a {@code GenericCommandMessage} for the given {@code type}, {@code payload}, and {@code metaData}.
     *
     * @param type     The {@link MessageType type} for this {@link CommandMessage}.
     * @param payload  The payload of type {@code P} for this {@link CommandMessage}.
     * @param metaData The metadata for this {@link CommandMessage}.
     */
    public GenericCommandMessage(@Nonnull MessageType type,
                                 @Nonnull P payload,
                                 @Nonnull Map<String, String> metaData) {
        this(new GenericMessage<>(type, payload, metaData));
    }

    /**
     * Constructs a {@code GenericCommandMessage} with given {@code delegate}.
     * <p>
     * The {@code delegate} will be used supply the {@link Message#payload() payload}, {@link Message#type() type},
     * {@link Message#metaData() metadata} and {@link Message#identifier() identifier} of the resulting
     * {@code GenericCommandMessage}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate The {@link Message} containing {@link Message#payload() payload},
     *                 {@link Message#type() qualifiedName}, {@link Message#identifier() identifier} and
     *                 {@link Message#metaData() metadata} for the {@link CommandMessage} to reconstruct.
     */
    public GenericCommandMessage(@Nonnull Message<P> delegate) {
        super(delegate);
    }

    @Override
    public CommandMessage<P> withMetaData(@Nonnull Map<String, String> metaData) {
        return new GenericCommandMessage<>(getDelegate().withMetaData(metaData));
    }

    @Override
    public CommandMessage<P> andMetaData(@Nonnull Map<String, String> metaData) {
        return new GenericCommandMessage<>(getDelegate().andMetaData(metaData));
    }

    @Override
    public <T> CommandMessage<T> withConvertedPayload(@Nonnull Type type, @Nonnull Converter converter) {
        T convertedPayload = payloadAs(type, converter);
        if (payloadType().isAssignableFrom(convertedPayload.getClass())) {
            //noinspection unchecked
            return (CommandMessage<T>) this;
        }
        Message<P> delegate = getDelegate();
        return new GenericCommandMessage<>(new GenericMessage<T>(delegate.identifier(),
                                                                 delegate.type(),
                                                                 convertedPayload,
                                                                 delegate.metaData()));
    }

    @Override
    protected String describeType() {
        return "GenericCommandMessage";
    }
}
