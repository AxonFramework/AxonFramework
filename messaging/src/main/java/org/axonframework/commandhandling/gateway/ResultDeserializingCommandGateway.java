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

package org.axonframework.commandhandling.gateway;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.serialization.Serializer;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ResultDeserializingCommandGateway implements CommandGateway {

    private final CommandGateway delegate;
    private final Serializer serializer;

    public ResultDeserializingCommandGateway(CommandGateway delegate, Serializer serializer) {
        this.delegate = delegate;
        this.serializer = serializer;
    }

    @Override
    public CommandResult send(@Nonnull Object command, @Nullable ProcessingContext processingContext) {
        return new SerializingCommandResult(serializer, delegate.send(command, processingContext));
    }

    private static class SerializingCommandResult implements CommandResult {

        private final Serializer serializer;
        private final CommandResult delegate;

        public SerializingCommandResult(Serializer serializer, CommandResult delegate) {
            this.serializer = serializer;
            this.delegate = delegate;
        }

        @Override
        public <R> CompletableFuture<R> resultAs(Class<R> type) {
            return delegate.getResultMessage()
                           .thenApply(Message::getPayload)
                           .thenApply(payload -> serializer.convert(payload, type));
        }

        @Override
        public CompletableFuture<? extends Message<?>> getResultMessage() {
            return delegate.getResultMessage();
        }

        @Override
        public <R> CommandResult onSuccess(Class<R> returnType, BiConsumer<R, Message<?>> handler) {
            delegate.getResultMessage()
                    .whenComplete((message, e) -> {
                        if (e == null) {
                            handler.accept(serializer.convert(message.getPayload(), returnType),
                                           message);
                        }
                    });
            return this;
        }
    }
}
