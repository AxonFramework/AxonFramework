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

package org.axonframework.messaging;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.ObjectUtils;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.serialization.Converter;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;

/**
 * Generic implementation of {@link ResultMessage} interface.
 *
 * @author Milan Savic
 * @author Steven van Beelen
 * @since 4.0.0
 */
public class GenericResultMessage extends MessageDecorator implements ResultMessage {

    private final Throwable exception;

    /**
     * Constructs a {@link GenericResultMessage} for the given {@code type} and {@code result}.
     * <p>
     * Uses the correlation data of the current Unit of Work, if present.
     *
     * @param type   The {@link MessageType type} for this {@link ResultMessage}.
     * @param result The result for this {@link ResultMessage}.
     */
    public GenericResultMessage(@Nonnull MessageType type,
                                @Nullable Object result) {
        this(type, result, Metadata.emptyInstance());
    }

    /**
     * Constructs a {@link GenericResultMessage} for the given {@code type} and {@code exception}.
     * <p>
     * Uses the correlation data of the current Unit of Work, if present.
     *
     * @param type      The {@link MessageType type} for this {@link ResultMessage}.
     * @param exception The {@link Throwable} describing the error representing the response of this
     *                  {@link ResultMessage}.
     */
    public GenericResultMessage(@Nonnull MessageType type,
                                @Nonnull Throwable exception) {
        this(type, exception, Metadata.emptyInstance());
    }

    /**
     * Constructs a {@code GenericResultMessage} for the given {@code type}, {@code result}, and {@code metadata}.
     *
     * @param type     The {@link MessageType type} for this {@link ResultMessage}.
     * @param result   The result for this {@link ResultMessage}.
     * @param metadata The metadata for this {@link ResultMessage}.
     */
    public GenericResultMessage(@Nonnull MessageType type,
                                @Nullable Object result,
                                @Nonnull Map<String, String> metadata) {
        this(new GenericMessage(type, result, metadata));
    }

    /**
     * Constructs a {@code GenericResultMessage} for the given {@code type}, {@code exception}, and {@code metadata}.
     *
     * @param type      The {@link MessageType type} for this {@link ResultMessage}.
     * @param exception The {@link Throwable} describing the error representing the response of this
     *                  {@link ResultMessage}.
     * @param metadata  The metadata for this {@link ResultMessage}.
     */
    public GenericResultMessage(@Nonnull MessageType type,
                                @Nonnull Throwable exception,
                                @Nonnull Map<String, String> metadata) {
        this(new GenericMessage(type, null, metadata), exception);
    }

    /**
     * Constructs a {@code GenericResultMessage} for the given {@code delegate}, intended to reconstruct another
     * {@link ResultMessage}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate The {@link Message} containing {@link Message#payload() payload}, {@link Message#type() type},
     *                 {@link Message#identifier() identifier} and {@link Message#metadata() metadata} for the
     *                 {@link QueryResponseMessage} to reconstruct.
     */
    public GenericResultMessage(@Nonnull Message delegate) {
        this(delegate, GenericResultMessage.findExceptionResult(delegate));
    }

    /**
     * Constructs a {@code GenericResultMessage} for the given {@code delegate} and {@code exception} as a cause for the
     * failure, intended to reconstruct another {@link ResultMessage}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate  The {@link Message} containing {@link Message#payload() payload}, {@link Message#type() type},
     *                  {@link Message#identifier() identifier} and {@link Message#metadata() metadata} for the
     *                  {@link QueryResponseMessage} to reconstruct.
     * @param exception The {@link Throwable} describing the error representing the response of this
     *                  {@link ResultMessage}.
     */
    public GenericResultMessage(@Nonnull Message delegate,
                                @Nullable Throwable exception) {
        super(delegate);
        this.exception = exception;
    }

    /**
     * Returns the given {@code result} as a {@link ResultMessage} instance. If {@code result} already implements
     * {@link ResultMessage}, it is returned as-is. If {@code result} implements {@link Message}, payload and meta data
     * will be used to construct new {@code GenericResultMessage}. Otherwise, the given {@code result} is wrapped into a
     * {@code GenericResultMessage} as its payload.
     *
     * @param result the command result to be wrapped as {@link ResultMessage}
     * @return a Message containing given {@code result} as payload, or {@code result} if already implements
     * {@link ResultMessage}
     * @deprecated In favor of using the constructor, as we intend to enforce thinking about the
     * {@link MessageType type}.
     */
    @Deprecated
    public static ResultMessage asResultMessage(Object result) {
        if (result instanceof ResultMessage r) {
            return r;
        }
        if (result instanceof Message resultMessage) {
            return new GenericResultMessage(resultMessage);
        }
        MessageType type = result == null ? new MessageType("empty.result") : new MessageType(result.getClass());

        return new GenericResultMessage(type, result);
    }

    /**
     * Creates a ResultMessage with the given {@code exception} result.
     *
     * @param exception the Exception describing the cause of an error
     * @return a message containing exception result
     * @deprecated In favor of using the constructor, as we intend to enforce thinking about the
     * {@link MessageType type}.
     */
    @Deprecated
    public static ResultMessage asResultMessage(Throwable exception) {
        return new GenericResultMessage(new MessageType(exception.getClass()), exception);
    }

    private static Throwable findExceptionResult(Message delegate) {
        if (delegate instanceof ResultMessage && ((ResultMessage) delegate).isExceptional()) {
            return ((ResultMessage) delegate).exceptionResult();
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
    @Nonnull
    public ResultMessage withMetadata(@Nonnull Map<String, String> metadata) {
        return new GenericResultMessage(delegate().withMetadata(metadata), exception);
    }

    @Override
    @Nonnull
    public ResultMessage andMetadata(@Nonnull Map<String, String> metadata) {
        return new GenericResultMessage(delegate().andMetadata(metadata), exception);
    }

    @Override
    @Nonnull
    public ResultMessage withConvertedPayload(@Nonnull Type type, @Nonnull Converter converter) {
        Object convertedPayload = payloadAs(type, converter);
        if (ObjectUtils.nullSafeTypeOf(convertedPayload).isAssignableFrom(payloadType())) {
            return this;
        }
        Message delegate = delegate();
        Message converted = new GenericMessage(delegate.identifier(),
                                                    delegate.type(),
                                                    convertedPayload,
                                                    delegate.metadata());
        return optionalExceptionResult().isPresent()
                ? new GenericResultMessage(converted, optionalExceptionResult().get())
                : new GenericResultMessage(converted);
    }

    @Override
    protected void describeTo(StringBuilder stringBuilder) {
        stringBuilder.append("payload={")
                     .append(isExceptional() ? null : payload())
                     .append('}')
                     .append(", metadata={")
                     .append(metadata())
                     .append('}')
                     .append(", messageIdentifier='")
                     .append(identifier())
                     .append('\'')
                     .append(", exception='")
                     .append(exception)
                     .append('\'');
    }

    @Override
    protected String describeType() {
        return "GenericResultMessage";
    }

    @Override
    public Object payload() {
        if (isExceptional()) {
            throw new IllegalPayloadAccessException(
                    "This result completed exceptionally, payload is not available. "
                            + "Try calling 'exceptionResult' to see the cause of failure.",
                    exception
            );
        }
        return super.payload();
    }
}
