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

package org.axonframework.commandhandling.distributed;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.serialization.Converter;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Connector implementation that converts the payload of outgoing messages into the expected format. This is generally a
 * {@code byte[]} or another serialized form.
 *
 * @param <T> The type to convert the message's payload into.
 * @author Allard Buijze
 * @since 5.0.0
 */
public class PayloadConvertingCommandBusConnector<T> extends DelegatingCommandBusConnector {

    private final CommandBusConnector delegate;
    private final Converter converter;
    private final Class<?> targetType;

    /**
     * Initialize the {@code PayloadConvertingConnector} to use given {@code converter} to convert each Message's
     * payload into {@code targetType} before passing it to given {@code delegate}.
     *
     * @param delegate   The delegate to pass converted messages to.
     * @param converter  The converter to use to convert each Message's payload.
     * @param targetType The desired representation of forwarded Message's payload.
     */
    public PayloadConvertingCommandBusConnector(@Nonnull CommandBusConnector delegate,
                                                @Nonnull Converter converter,
                                                @Nonnull Class<?> targetType) {
        super(delegate);
        this.delegate = Objects.requireNonNull(delegate, "The delegate must not be null.");
        this.converter = Objects.requireNonNull(converter, "The converter must not be null.");
        this.targetType = Objects.requireNonNull(targetType, "The targetType must not be null.");
    }

    @Nonnull
    @Override
    public CompletableFuture<CommandResultMessage<?>> dispatch(@Nonnull CommandMessage<?> command,
                                                               @Nullable ProcessingContext processingContext) {
        CommandMessage<?> serializedCommand = command.withConvertedPayload(targetType, converter);
        return delegate.dispatch(serializedCommand, processingContext);
    }

    @Override
    public void onIncomingCommand(@Nonnull Handler handler) {
        delegate.onIncomingCommand((commandMessage, callback) -> handler.handle(
                commandMessage,
                new ConvertingResultMessageCallback(callback)
        ));
    }

    /**
     * Callback that converts the payload of the result message to the expected representation before passing it to the
     * original callback.
     */
    private class ConvertingResultMessageCallback implements ResultCallback {

        private final ResultCallback callback;

        private ConvertingResultMessageCallback(ResultCallback callback) {
            this.callback = callback;
        }

        @Override
        public void onSuccess(CommandResultMessage<?> resultMessage) {
            if (resultMessage == null || resultMessage.payload() == null) {
                callback.onSuccess(resultMessage);
                return;
            }
            callback.onSuccess(resultMessage.withConvertedPayload(targetType, converter));
        }

        @Override
        public void onError(@Nonnull Throwable cause) {
            callback.onError(cause);
        }
    }
}