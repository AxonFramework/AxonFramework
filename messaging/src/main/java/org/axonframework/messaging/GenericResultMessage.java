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

package org.axonframework.messaging;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;

import java.io.Serial;
import java.util.Map;
import java.util.Optional;

/**
 * Generic implementation of {@link ResultMessage} interface.
 *
 * @param <R> The type of {@link #getPayload() result} contained in this {@link ResultMessage}.
 * @author Milan Savic
 * @author Steven van Beelen
 * @since 4.0.0
 */
public class GenericResultMessage<R> extends MessageDecorator<R> implements ResultMessage<R> {

    @Serial
    private static final long serialVersionUID = -9086395619674962782L;

    private final Throwable exception;

    /**
     * Constructs a {@link GenericResultMessage} for the given {@code name} and {@code result}.
     * <p>
     * Uses the correlation data of the current Unit of Work, if present.
     *
     * @param name   The {@link QualifiedName name} for this {@link ResultMessage}.
     * @param result The result of type {@code R} for this {@link ResultMessage}.
     */
    public GenericResultMessage(@Nonnull QualifiedName name,
                                @Nullable R result) {
        this(name, result, MetaData.emptyInstance());
    }

    /**
     * Constructs a {@link GenericResultMessage} for the given {@code name} and {@code exception}.
     * <p>
     * Uses the correlation data of the current Unit of Work, if present.
     *
     * @param name      The {@link QualifiedName name} for this {@link ResultMessage}.
     * @param exception The {@link Throwable} describing the error representing the response of this
     *                  {@link ResultMessage}.
     */
    public GenericResultMessage(@Nonnull QualifiedName name,
                                @Nonnull Throwable exception) {
        this(name, exception, MetaData.emptyInstance());
    }

    /**
     * Constructs a {@link GenericResultMessage} for the given {@code name}, {@code result}, and {@code metaData}.
     *
     * @param name     The {@link QualifiedName name} for this {@link ResultMessage}.
     * @param result   The result of type {@code R} for this {@link ResultMessage}.
     * @param metaData The metadata for this {@link ResultMessage}.
     */
    public GenericResultMessage(@Nonnull QualifiedName name,
                                @Nullable R result,
                                @Nonnull Map<String, ?> metaData) {
        this(new GenericMessage<>(name, result, metaData));
    }

    /**
     * Constructs a {@link GenericResultMessage} for the given {@code name}, {@code exception}, and {@code metaData}.
     *
     * @param name      The {@link QualifiedName name} for this {@link ResultMessage}.
     * @param exception The {@link Throwable} describing the error representing the response of this
     *                  {@link ResultMessage}.
     * @param metaData  The metadata for this {@link ResultMessage}.
     */
    public GenericResultMessage(@Nonnull QualifiedName name,
                                @Nonnull Throwable exception,
                                @Nonnull Map<String, ?> metaData) {
        this(new GenericMessage<>(name, null, metaData), exception);
    }

    /**
     * Constructs a {@link GenericResultMessage} for the given {@code delegate}, intended to reconstruct another
     * {@link ResultMessage}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate The {@link Message} containing {@link Message#getPayload() payload}, {@link Message#name() name},
     *                 {@link Message#getIdentifier() identifier} and {@link Message#getMetaData() metadata} for the
     *                 {@link QueryResponseMessage} to reconstruct.
     */
    public GenericResultMessage(@Nonnull Message<R> delegate) {
        this(delegate, GenericResultMessage.findExceptionResult(delegate));
    }

    /**
     * Constructs a {@link GenericResultMessage} for the given {@code delegate} and {@code exception} as a cause for the
     * failure, intended to reconstruct another {@link ResultMessage}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate  The {@link Message} containing {@link Message#getPayload() payload},
     *                  {@link Message#name() name}, {@link Message#getIdentifier() identifier} and
     *                  {@link Message#getMetaData() metadata} for the {@link QueryResponseMessage} to reconstruct.
     * @param exception The {@link Throwable} describing the error representing the response of this
     *                  {@link ResultMessage}.
     */
    public GenericResultMessage(@Nonnull Message<R> delegate,
                                @Nonnull Throwable exception) {
        super(delegate);
        this.exception = exception;
    }

    /**
     * Returns the given {@code result} as a {@link ResultMessage} instance. If {@code result} already implements
     * {@link ResultMessage}, it is returned as-is. If {@code result} implements {@link Message}, payload and meta data
     * will be used to construct new {@link GenericResultMessage}. Otherwise, the given {@code result} is wrapped into a
     * {@link GenericResultMessage} as its payload.
     *
     * @param result the command result to be wrapped as {@link ResultMessage}
     * @param <R>    The type of payload contained in this {@link ResultMessage}.
     * @return a Message containing given {@code result} as payload, or {@code result} if already implements
     * {@link ResultMessage}
     * @deprecated In favor of using the constructor, as we intend to enforce thinking about the
     * {@link QualifiedName name}.
     */
    @Deprecated
    public static <R> ResultMessage<R> asResultMessage(Object result) {
        if (result instanceof ResultMessage) {
            //noinspection unchecked
            return (ResultMessage<R>) result;
        } else if (result instanceof Message<?> resultMessage) {
            //noinspection unchecked
            return (ResultMessage<R>) new GenericResultMessage<>(resultMessage);
        }
        QualifiedName name = result == null
                ? QualifiedNameUtils.fromDottedName("empty.result")
                : QualifiedNameUtils.fromClassName(result.getClass());
        //noinspection unchecked
        return new GenericResultMessage<>(name, (R) result);
    }

    /**
     * Creates a ResultMessage with the given {@code exception} result.
     *
     * @param exception the Exception describing the cause of an error
     * @param <R>       The type of payload contained in this {@link ResultMessage}.
     * @return a message containing exception result
     * @deprecated In favor of using the constructor, as we intend to enforce thinking about the
     * {@link QualifiedName name}.
     */
    @Deprecated
    public static <R> ResultMessage<R> asResultMessage(Throwable exception) {
        return new GenericResultMessage<>(QualifiedNameUtils.fromClassName(exception.getClass()), exception);
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
