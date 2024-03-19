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
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.serialization.Serializer;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class SerializingConnector<T> implements Connector {

    private final Connector delegate;
    private final Serializer serializer;
    private final Class<T> representation;

    public SerializingConnector(Connector delegate, Serializer serializer, Class<T> representation) {
        this.delegate = delegate;
        this.serializer = serializer;
        this.representation = representation;
    }

    @Override
    public CompletableFuture<? extends CommandResultMessage<?>> dispatch(CommandMessage<?> command,
                                                                         ProcessingContext processingContext) {
        CommandMessage<T> serializedCommand = command.withConvertedPayload(p -> serializer.convert(p, representation));
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
        public void success(CommandResultMessage<?> resultMessage) {
            callback.success(resultMessage.withConvertedPayload(p -> serializer.convert(p, representation)));
        }

        @Override
        public void error(Throwable cause) {
            callback.error(cause);
        }
    }
}
