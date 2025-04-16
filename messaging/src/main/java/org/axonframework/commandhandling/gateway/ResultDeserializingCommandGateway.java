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

package org.axonframework.commandhandling.gateway;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.serialization.Serializer;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A {@link CommandGateway} implementation that deserializes the result of command handling.
 *
 * @author Allard Buijze
 * @since 5.0.0
 */
public class ResultDeserializingCommandGateway implements CommandGateway {

    private final CommandGateway delegate;
    private final Serializer serializer;

    /**
     * @param delegate   The delegate command gateway to wrap within this command gateway.
     * @param serializer The serializer to use while deserializing command results.
     */
    public ResultDeserializingCommandGateway(@Nonnull CommandGateway delegate,
                                             @Nonnull Serializer serializer) {
        this.delegate = requireNonNull(delegate, "The delegate must not be null.");
        this.serializer = requireNonNull(serializer, "The serializer must not be null.");
    }

    @Override
    public CommandResult send(@Nonnull Object command,
                              @Nullable ProcessingContext context) {
        return new SerializingCommandResult(serializer, delegate.send(command, context));
    }

    @Override
    public CommandResult send(@Nonnull Object command,
                              @Nonnull MetaData metaData,
                              @Nullable ProcessingContext context) {
        return new SerializingCommandResult(serializer, delegate.send(command, metaData, context));
    }

    private record SerializingCommandResult(
            Serializer serializer,
            CommandResult delegate
    ) implements CommandResult {

        @Override
        public CompletableFuture<? extends Message<?>> getResultMessage() {
            return delegate.getResultMessage();
        }

        @Override
        public <R> CompletableFuture<R> resultAs(@Nonnull Class<R> type) {
            return delegate.getResultMessage()
                           .thenApply(Message::getPayload)
                           .thenApply(payload -> serializer.convert(payload, type));
        }

        @Override
        public <R> CommandResult onSuccess(@Nonnull Class<R> resultType,
                                           @Nonnull BiConsumer<R, Message<?>> successHandler) {
            delegate.getResultMessage()
                    .whenComplete((message, e) -> {
                        if (e == null) {
                            requireNonNull(successHandler, "The success handler must not be null.")
                                    .accept(serializer.convert(message.getPayload(), resultType), message);
                        }
                    });
            return this;
        }
    }
}
