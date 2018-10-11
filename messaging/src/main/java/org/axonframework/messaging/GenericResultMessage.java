/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.messaging;

import java.util.Map;

/**
 * Generic implementation of {@link ResultMessage}.
 *
 * @author Milan Savic
 * @since 4.0
 */
public class GenericResultMessage<R> extends MessageDecorator<R> implements ResultMessage<R> {

    private static final long serialVersionUID = -9086395619674962782L;

    /**
     * Returns the given {@code result} as a {@link ResultMessage} instance. If {@code result} already implements {@link
     * ResultMessage}, it is returned as-is. If {@code result} implements {@link Message}, payload and meta data will be
     * used to construct new {@link GenericResultMessage}. Otherwise, the given {@code result} is wrapped into a {@link
     * GenericResultMessage} as its payload.
     *
     * @param result the command result to be wrapped as {@link ResultMessage}
     * @param <T>    The type of the payload contained in returned Message
     * @return a Message containing given {@code result} as payload, or {@code result} if already
     * implements {@link ResultMessage}
     */
    @SuppressWarnings("unchecked")
    public static <T> ResultMessage<T> asResultMessage(Object result) {
        if (ResultMessage.class.isInstance(result)) {
            return (ResultMessage<T>) result;
        } else if (Message.class.isInstance(result)) {
            Message<?> resultMessage = (Message<?>) result;
            return new GenericResultMessage<>((T) resultMessage.getPayload(), resultMessage.getMetaData());
        }
        return new GenericResultMessage<>((T) result);
    }

    /**
     * Creates a Command Result Message with the given {@code commandResult} as the payload.
     *
     * @param result the payload for the Message
     */
    public GenericResultMessage(R result) {
        this(result, MetaData.emptyInstance());
    }

    /**
     * Creates a Result Message with the given {@code rResult} as the payload and {@code metaData} as the meta data.
     *
     * @param result   the payload for the Message
     * @param metaData the meta data for the Message
     */
    public GenericResultMessage(R result, Map<String, ?> metaData) {
        this(new GenericMessage<>(result, metaData));
    }

    /**
     * Creates a new Result Message with given {@code delegate} message.
     *
     * @param delegate the message delegate
     */
    public GenericResultMessage(Message<R> delegate) {
        super(delegate);
    }

    @Override
    public GenericResultMessage<R> withMetaData(Map<String, ?> metaData) {
        return new GenericResultMessage<>(getDelegate().withMetaData(metaData));
    }

    @Override
    public GenericResultMessage<R> andMetaData(Map<String, ?> metaData) {
        return new GenericResultMessage<>(getDelegate().andMetaData(metaData));
    }

    @Override
    protected String describeType() {
        return "GenericResultMessage";
    }
}
