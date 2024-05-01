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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.serialization.PayloadConverter;

import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Connector implementation that converts the payload of outgoing messages into the expected format. This is generally a
 * {@code byte[]} or another serialized form.
 *
 * @param <T> The type to convert the message's payload into.
 */
public class PayloadConvertingConnector<T> implements Connector {

    private final Connector delegate;
    private final PayloadConverter converter;
    private final Type representation;

    /**
     * Initialize the PayloadConvertingConnector to use given {@code converter} to convert each Message's payload into
     * {@code representation} before passing it to given {@code delegate}.
     *
     * @param delegate       The delegate to pass converted messages to
     * @param converter      The converter to use to convert each Message's payload
     * @param representation The desired representation of forwarded Message's payload
     */
    public PayloadConvertingConnector(Connector delegate, PayloadConverter converter, Class<T> representation) {
        this(delegate, converter, (Type) representation);
    }

    /**
     * Initialize the PayloadConvertingConnector to use given {@code converter} to convert each Message's payload into
     * {@code representation} before passing it to given {@code delegate}.
     *
     * @param delegate       The delegate to pass converted messages to
     * @param converter      The converter to use to convert each Message's payload
     * @param representation The desired representation of forwarded Message's payload
     */
    public PayloadConvertingConnector(Connector delegate, PayloadConverter converter, Type representation) {
        this.delegate = delegate;
        this.converter = converter;
        this.representation = representation;
    }

    @Override
    public CompletableFuture<? extends Message<?>> dispatch(CommandMessage<?> command,
                                                            ProcessingContext processingContext) {
        CommandMessage<T> serializedCommand = converter.convertPayload(command, representation);
        return delegate.dispatch(serializedCommand, processingContext);
    }

    @Override
    public void subscribe(String commandName, int loadFactor) {
        delegate.subscribe(commandName, loadFactor);
    }

    @Override
    public boolean unsubscribe(String commandName) {
        return delegate.unsubscribe(commandName);
    }

    @Override
    public void onIncomingCommand(BiConsumer<CommandMessage<?>, ResultCallback> handler) {
        delegate.onIncomingCommand((commandMessage, callback) -> handler.accept(commandMessage,
                                                                                new SerializingResultCallback(callback)));
    }

    private class SerializingResultCallback implements ResultCallback {

        private final ResultCallback callback;

        public SerializingResultCallback(ResultCallback callback) {
            this.callback = callback;
        }

        @Override
        public void success(Message<?> resultMessage) {
            callback.success(converter.convertPayload(resultMessage, representation));
        }

        @Override
        public void error(Throwable cause) {
            callback.error(cause);
        }
    }
}
