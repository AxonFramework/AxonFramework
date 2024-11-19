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

package org.axonframework.commandhandling;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.GenericResultMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.queryhandling.QueryResponseMessage;

import java.io.Serial;
import java.util.Map;
import java.util.function.Function;

/**
 * Generic implementation of the {@link CommandResultMessage} interface.
 *
 * @param <R> The type of {@link #getPayload() result} contained in this {@link CommandResultMessage}.
 * @author Milan Savic
 * @author Steven van Beelen
 * @since 4.0.0
 */
public class GenericCommandResultMessage<R> extends GenericResultMessage<R> implements CommandResultMessage<R> {

    @Serial
    private static final long serialVersionUID = 9013948836930094183L;

    /**
     * Returns the given {@code commandResult} as a {@link CommandResultMessage} instance. If {@code commandResult}
     * already implements {@link CommandResultMessage}, it is returned as-is. If {@code commandResult} implements
     * {@link Message}, payload and meta data will be used to construct new {@link GenericCommandResultMessage}.
     * Otherwise, the given {@code commandResult} is wrapped into a {@link GenericCommandResultMessage} as its payload.
     *
     * @param commandResult The result to be wrapped in a {@link CommandResultMessage}.
     * @param <R>           The type of payload contained in this {@link CommandResultMessage}.
     * @return a Message containing given {@code commandResult} as payload, or {@code commandResult} if already
     * implements {@link CommandResultMessage}
     * @deprecated In favor of using the constructor, as we intend to enforce thinking about the
     * {@link QualifiedName type}.
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public static <R> CommandResultMessage<R> asCommandResultMessage(@Nullable Object commandResult) {
        if (commandResult instanceof CommandResultMessage) {
            return (CommandResultMessage<R>) commandResult;
        } else if (commandResult instanceof Message) {
            Message<R> commandResultMessage = (Message<R>) commandResult;
            return new GenericCommandResultMessage<>(commandResultMessage);
        }
        QualifiedName type = commandResult == null
                ? QualifiedName.dottedName("empty.command.result")
                : QualifiedName.className(commandResult.getClass());
        return new GenericCommandResultMessage<>(type, (R) commandResult);
    }

    /**
     * Creates a Command Result Message with the given {@code exception} result.
     *
     * @param exception The {@link Throwable} describing the error representing the response of this
     *                  {@link CommandResultMessage}.
     * @param <R>       The type of payload contained in this {@link CommandResultMessage}.
     * @return a message containing exception result
     * @deprecated In favor of using the constructor, as we intend to enforce thinking about the
     * {@link QualifiedName type}.
     */
    @Deprecated
    public static <R> CommandResultMessage<R> asCommandResultMessage(@Nonnull Throwable exception) {
        return new GenericCommandResultMessage<>(QualifiedName.className(exception.getClass()), exception);
    }

    /**
     * Constructs a {@link GenericResultMessage} for the given {@code type} and {@code commandResult}.
     * <p>
     * Uses the correlation data of the current Unit of Work, if present.
     *
     * @param type          The {@link QualifiedName type} for this {@link CommandResultMessage}.
     * @param commandResult The result of type {@code R} for this {@link CommandResultMessage}.
     */
    public GenericCommandResultMessage(@Nonnull QualifiedName type,
                                       @Nullable R commandResult) {
        super(type, commandResult);
    }

    /**
     * Constructs a {@link GenericCommandResultMessage} for the given {@code type} and {@code exception}.
     * <p>
     * Uses the correlation data of the current Unit of Work, if present.
     *
     * @param type      The {@link QualifiedName type} for this {@link CommandResultMessage}.
     * @param exception The {@link Throwable} describing the error representing the response of this
     *                  {@link CommandResultMessage}.
     */
    public GenericCommandResultMessage(@Nonnull QualifiedName type,
                                       @Nonnull Throwable exception) {
        super(type, exception);
    }

    /**
     * Constructs a {@link GenericCommandResultMessage} for the given {@code type}, {@code commandResult}, and
     * {@code metaData}.
     *
     * @param type          The {@link QualifiedName type} for this {@link CommandResultMessage}.
     * @param commandResult The result of type {@code R} for this {@link CommandResultMessage}.
     * @param metaData      The metadata for this {@link CommandResultMessage}.
     */
    public GenericCommandResultMessage(@Nonnull QualifiedName type,
                                       @Nonnull R commandResult,
                                       @Nonnull Map<String, ?> metaData) {
        super(type, commandResult, metaData);
    }

    /**
     * Constructs a {@link GenericCommandResultMessage} for the given {@code type}, {@code exception}, and
     * {@code metaData}.
     *
     * @param type      The {@link QualifiedName type} for this {@link CommandResultMessage}.
     * @param exception The {@link Throwable} describing the error representing the response of this
     *                  {@link CommandResultMessage}.
     * @param metaData  The metadata for this {@link CommandResultMessage}.
     */
    public GenericCommandResultMessage(@Nonnull QualifiedName type,
                                       @Nonnull Throwable exception,
                                       @Nonnull Map<String, ?> metaData) {
        super(type, exception, metaData);
    }

    /**
     * Constructs a {@link GenericCommandResultMessage} for the given {@code delegate}, intended to reconstruct another
     * {@link CommandResultMessage}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate The {@link Message} containing {@link Message#getPayload() payload}, {@link Message#type() type},
     *                 {@link Message#getIdentifier() identifier} and {@link Message#getMetaData() metadata} for the
     *                 {@link QueryResponseMessage} to reconstruct.
     */
    public GenericCommandResultMessage(@Nonnull Message<R> delegate) {
        super(delegate);
    }

    /**
     * Constructs a {@link GenericCommandResultMessage} for the given {@code delegate} and {@code exception} as a cause
     * for the failure, intended to reconstruct another {@link CommandResultMessage}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate  The {@link Message} containing {@link Message#getPayload() payload},
     *                  {@link Message#type() type}, {@link Message#getIdentifier() identifier} and
     *                  {@link Message#getMetaData() metadata} for the {@link QueryResponseMessage} to reconstruct.
     * @param exception The {@link Throwable} describing the error representing the response of this
     *                  {@link CommandResultMessage}.
     */
    public GenericCommandResultMessage(@Nonnull Message<R> delegate,
                                       @Nullable Throwable exception) {
        super(delegate, exception);
    }

    @Override
    public GenericCommandResultMessage<R> withMetaData(@Nonnull Map<String, ?> metaData) {
        Throwable exception = optionalExceptionResult().orElse(null);
        return new GenericCommandResultMessage<>(getDelegate().withMetaData(metaData), exception);
    }

    @Override
    public GenericCommandResultMessage<R> andMetaData(@Nonnull Map<String, ?> metaData) {
        Throwable exception = optionalExceptionResult().orElse(null);
        return new GenericCommandResultMessage<>(getDelegate().andMetaData(metaData), exception);
    }

    @Override
    public <T> CommandResultMessage<T> withConvertedPayload(@Nonnull Function<R, T> conversion) {
        // TODO - Once Message declares a convert method, use that
        Throwable exception = optionalExceptionResult().orElse(null);
        Message<R> delegate = getDelegate();
        Message<T> transformed = new GenericMessage<>(delegate.getIdentifier(),
                                                      delegate.type(),
                                                      conversion.apply(delegate.getPayload()),
                                                      delegate.getMetaData());
        return new GenericCommandResultMessage<>(transformed, exception);
    }

    @Override
    protected String describeType() {
        return "GenericCommandResultMessage";
    }
}
