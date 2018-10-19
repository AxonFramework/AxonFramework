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

package org.axonframework.commandhandling;

import org.axonframework.messaging.GenericResultMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.ResultMessage;

import java.util.Map;

/**
 * Generic implementation of {@link CommandResultMessage}.
 *
 * @param <R> The type of the payload contained in this Message
 * @author Milan Savic
 * @since 4.0
 */
public class GenericCommandResultMessage<R> extends GenericResultMessage<R> implements CommandResultMessage<R> {

    private static final long serialVersionUID = 9013948836930094183L;

    /**
     * Returns the given {@code commandResult} as a {@link CommandResultMessage} instance. If {@code commandResult}
     * already implements {@link CommandResultMessage}, it is returned as-is. If {@code commandResult} implements {@link
     * Message}, payload and meta data will be used to construct new {@link GenericCommandResultMessage}. Otherwise, the
     * given {@code commandResult} is wrapped into a {@link GenericCommandResultMessage} as its payload.
     *
     * @param commandResult the command result to be wrapped as {@link CommandResultMessage}
     * @param <T>           The type of the payload contained in returned Message
     * @return a Message containing given {@code commandResult} as payload, or {@code commandResult} if already
     * implements {@link CommandResultMessage}
     */
    @SuppressWarnings("unchecked")
    public static <T> CommandResultMessage<T> asCommandResultMessage(Object commandResult) {
        if (CommandResultMessage.class.isInstance(commandResult)) {
            return (CommandResultMessage<T>) commandResult;
        } else if (ResultMessage.class.isInstance(commandResult)) {
            ResultMessage<T> resultMessage = (ResultMessage<T>) commandResult;
            if (resultMessage.isExceptional()) {
                Throwable cause = resultMessage.exceptionResult();
                return new GenericCommandResultMessage<>(cause, resultMessage.getMetaData());
            }
            return new GenericCommandResultMessage<>(resultMessage.getPayload(), resultMessage.getMetaData());
        } else if (Message.class.isInstance(commandResult)) {
            Message<T> commandResultMessage = (Message<T>) commandResult;
            return new GenericCommandResultMessage<>(commandResultMessage.getPayload(),
                                                     commandResultMessage.getMetaData());
        }
        return new GenericCommandResultMessage<>((T) commandResult);
    }

    /**
     * Creates a Command Result Message with the given {@code exception} result.
     *
     * @param exception the Exception describing the cause of an error
     * @param <T> the type of payload
     * @return a message containing exception result
     */
    public static <T> CommandResultMessage<T> asCommandResultMessage(Throwable exception) {
        return new GenericCommandResultMessage<>(exception);
    }

    /**
     * Creates a Command Result Message with the given {@code commandResult} as the payload.
     *
     * @param commandResult the payload for the Message
     */
    public GenericCommandResultMessage(R commandResult) {
        super(commandResult);
    }

    /**
     * Creates a Command Result Message with the given {@code exception}.
     *
     * @param exception the Exception describing the cause of an error
     */
    public GenericCommandResultMessage(Throwable exception) {
        super(exception);
    }

    /**
     * Creates a Command Result Message with the given {@code commandResult} as the payload and {@code metaData} as
     * the meta data.
     *
     * @param commandResult the payload for the Message
     * @param metaData      the meta data for the Message
     */
    public GenericCommandResultMessage(R commandResult, Map<String, ?> metaData) {
        super(commandResult, metaData);
    }

    /**
     * Creates a Command Result Message with the given {@code exception} and {@code metaData}.
     *
     * @param exception the Exception describing the cause of an error
     * @param metaData  the meta data for the Message
     */
    public GenericCommandResultMessage(Throwable exception, Map<String, ?> metaData) {
        super(exception, metaData);
    }

    /**
     * Creates a new Command Result Message with given {@code delegate} message.
     *
     * @param delegate the message delegate
     */
    public GenericCommandResultMessage(Message<R> delegate) {
        super(delegate);
    }

    /**
     * Creates a Command Result Message with given {@code delegate} message and {@code exception}.
     *
     * @param delegate  the Message delegate
     * @param exception the Exception describing the cause of an error
     */
    public GenericCommandResultMessage(Message<R> delegate, Throwable exception) {
        super(delegate, exception);
    }

    @Override
    public GenericCommandResultMessage<R> withMetaData(Map<String, ?> metaData) {
        Throwable exception = optionalExceptionResult().orElse(null);
        return new GenericCommandResultMessage<>(getDelegate().withMetaData(metaData), exception);
    }

    @Override
    public GenericCommandResultMessage<R> andMetaData(Map<String, ?> metaData) {
        Throwable exception = optionalExceptionResult().orElse(null);
        return new GenericCommandResultMessage<>(getDelegate().andMetaData(metaData), exception);
    }

    @Override
    protected String describeType() {
        return "GenericCommandResultMessage";
    }
}
