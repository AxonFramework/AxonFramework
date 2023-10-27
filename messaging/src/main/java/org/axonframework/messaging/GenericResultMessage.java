/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;

import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;

/**
 * Generic implementation of {@link ResultMessage}.
 *
 * @author Milan Savic
 * @since 4.0
 */
public class GenericResultMessage<R> extends MessageDecorator<R> implements ResultMessage<R> {

    private static final long serialVersionUID = -9086395619674962782L;

    private final Throwable exception;

    /**
     * Creates a ResultMessage with the given {@code result} as the payload.
     *
     * @param result the payload for the Message
     */
    public GenericResultMessage(R result) {
        this(result, MetaData.emptyInstance());
    }

    /**
     * Creates a ResultMessage with the given {@code exception}.
     *
     * @param exception the Exception describing the cause of an error
     */
    public GenericResultMessage(Throwable exception) {
        this(exception, MetaData.emptyInstance());
    }

    /**
     * Creates a ResultMessage with the given {@code result} as the payload and {@code metaData} as the meta data.
     *
     * @param result   the payload for the Message
     * @param metaData the meta data for the Message
     */
    public GenericResultMessage(R result, Map<String, ?> metaData) {
        this(new GenericMessage<>(result, metaData));
    }

    /**
     * Creates a ResultMessage with the given {@code exception} and {@code metaData}.
     *
     * @param exception the Exception describing the cause of an error
     * @param metaData  the meta data for the Message
     */
    public GenericResultMessage(Throwable exception, Map<String, ?> metaData) {
        this(new GenericMessage<>(null, metaData), exception);
    }

    /**
     * Creates a new ResultMessage with given {@code delegate} message.
     *
     * @param delegate the message delegate
     */
    public GenericResultMessage(Message<R> delegate) {
        this(delegate, GenericResultMessage.findExceptionResult(delegate));
    }

    /**
     * Creates a ResultMessage with given {@code delegate} message and {@code exception}.
     *
     * @param delegate  the Message delegate
     * @param exception the Exception describing the cause of an error
     */
    public GenericResultMessage(Message<R> delegate, Throwable exception) {
        super(delegate);
        this.exception = exception;
    }

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
        if (result instanceof ResultMessage) {
            return (ResultMessage<T>) result;
        } else if (result instanceof Message) {
            Message<?> resultMessage = (Message<?>) result;
            return (ResultMessage<T>) new GenericResultMessage<>(resultMessage);
        }
        return new GenericResultMessage<>((T) result);
    }

    /**
     * Creates a ResultMessage with the given {@code exception} result.
     *
     * @param exception the Exception describing the cause of an error
     * @param <T>       the type of payload
     * @return a message containing exception result
     */
    public static <T> ResultMessage<T> asResultMessage(Throwable exception) {
        return new GenericResultMessage<>(exception);
    }

    private static <R> Throwable findExceptionResult(Message<R> delegate) {
        if (delegate instanceof ResultMessage && ((ResultMessage<R>) delegate).isExceptional()) {
            return ((ResultMessage<R>) delegate).exceptionResult();
        }
        return null;
    }

    @Override
    public boolean isExceptional() {
        return exception != null;
    }

    @Override
    public Optional<Throwable> optionalExceptionResult() {
        return Optional.ofNullable(exception);
    }

    @Override
    public <S> SerializedObject<S> serializePayload(Serializer serializer, Class<S> expectedRepresentation) {
        if (isExceptional()) {
            return serializer.serialize(exceptionDetails().orElse(null), expectedRepresentation);
        }
        return super.serializePayload(serializer, expectedRepresentation);
    }

    @Override
    public GenericResultMessage<R> withMetaData(@Nonnull Map<String, ?> metaData) {
        return new GenericResultMessage<>(getDelegate().withMetaData(metaData), exception);
    }

    @Override
    public GenericResultMessage<R> andMetaData(@Nonnull Map<String, ?> metaData) {
        return new GenericResultMessage<>(getDelegate().andMetaData(metaData), exception);
    }

    @Override
    protected void describeTo(StringBuilder stringBuilder) {
        stringBuilder.append("payload={")
                     .append(isExceptional() ? null : getPayload())
                     .append('}')
                     .append(", metadata={")
                     .append(getMetaData())
                     .append('}')
                     .append(", messageIdentifier='")
                     .append(getIdentifier())
                     .append('\'')
                     .append(", exception='")
                     .append(exception)
                     .append('\'');
    }

    @Override
    protected String describeType() {
        return "GenericResultMessage";
    }

    @SuppressWarnings("unchecked")
    @Override
    public R getPayload() {
        if (isExceptional()) {
            throw new IllegalPayloadAccessException(
                    "This result completed exceptionally, payload is not available. "
                            + "Try calling 'exceptionResult' to see the cause of failure.",
                    exception
            );
        }
        return super.getPayload();
    }
}
