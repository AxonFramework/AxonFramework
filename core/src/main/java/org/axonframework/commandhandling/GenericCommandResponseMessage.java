/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling;

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDecorator;
import org.axonframework.messaging.MetaData;

import java.util.Map;

/**
 * Generic implementation of {@link CommandResponseMessage}.
 *
 * @param <R> The type of the payload contained in this Message
 * @author Milan Savic
 * @since 4.0
 */
public class GenericCommandResponseMessage<R> extends MessageDecorator<R> implements CommandResponseMessage<R> {

    private static final long serialVersionUID = 9013948836930094183L;

    /**
     * Returns the given {@code commandResponse} as a {@link CommandResponseMessage} instance. If {@code
     * commandResponse} already implements {@link CommandResponseMessage}, it is returned as-is. Otherwise, the given
     * {@code commandResponse} is wrapped into a {@link GenericCommandResponseMessage} as its payload.
     *
     * @param commandResponse the command response to be wrapped as {@link CommandResponseMessage}
     * @param <T>             The type of the payload contained in returned Message
     * @return a Message containing given {@code commandResponse} as payload, or {@code commandResponse} if already
     * implements {@link CommandResponseMessage}
     */
    @SuppressWarnings("unchecked")
    public static <T> CommandResponseMessage<T> asCommandResponseMessage(Object commandResponse) {
        if (CommandResponseMessage.class.isInstance(commandResponse)) {
            return (CommandResponseMessage<T>) commandResponse;
        }
        return new GenericCommandResponseMessage<>((T) commandResponse);
    }

    /**
     * Creates a Command Response Message with the given {@code commandResponse} as the payload.
     *
     * @param commandResponse the payload for the Message
     */
    public GenericCommandResponseMessage(R commandResponse) {
        this(commandResponse, MetaData.emptyInstance());
    }

    /**
     * Creates a Command Response Message with the given {@code commandResponse} as the payload and {@code metaData} as
     * the meta data.
     *
     * @param commandResponse the payload for the Message
     * @param metaData        the meta data for the Message
     */
    public GenericCommandResponseMessage(R commandResponse, Map<String, ?> metaData) {
        this(new GenericMessage<>(commandResponse, metaData));
    }

    /**
     * Creates a new Command Response Message with given {@code delegate} message.
     *
     * @param delegate the message delegate
     */
    public GenericCommandResponseMessage(Message<R> delegate) {
        super(delegate);
    }

    @Override
    public GenericCommandResponseMessage<R> withMetaData(Map<String, ?> metaData) {
        return new GenericCommandResponseMessage<>(getDelegate().withMetaData(metaData));
    }

    @Override
    public GenericCommandResponseMessage<R> andMetaData(Map<String, ?> metaData) {
        return new GenericCommandResponseMessage<>(getDelegate().andMetaData(metaData));
    }

    @Override
    protected String describeType() {
        return "GenericCommandResponseMessage";
    }
}
