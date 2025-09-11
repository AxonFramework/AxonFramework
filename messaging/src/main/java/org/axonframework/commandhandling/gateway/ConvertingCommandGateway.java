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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.Metadata;
import org.axonframework.messaging.conversion.MessageConverter;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import static java.util.Objects.requireNonNull;

/**
 * A {@link CommandGateway} implementation that wraps the {@link CommandResult} of the delegate into a result that can
 * convert the payload of the result using a provided {@link MessageConverter}.
 *
 * @author Allard Buijze
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class ConvertingCommandGateway implements CommandGateway {

    private final CommandGateway delegate;
    private final MessageConverter converter;

    /**
     * Constructs a {@code ConvertingCommandGateway} with the given {@code delegate} and {@code converter}.
     *
     * @param delegate  The delegate command gateway to wrap within this command gateway.
     * @param converter The converter to use for converting the result of command handling.
     */
    public ConvertingCommandGateway(@Nonnull CommandGateway delegate,
                                    @Nonnull MessageConverter converter) {
        this.delegate = requireNonNull(delegate, "The delegate must not be null.");
        this.converter = requireNonNull(converter, "The MessageConverter must not be null.");
    }

    @Override
    public CommandResult send(@Nonnull Object command,
                              @Nullable ProcessingContext context) {
        return new ConvertingCommandResult(converter, delegate.send(command, context));
    }

    @Override
    public CommandResult send(@Nonnull Object command,
                              @Nonnull Metadata metadata,
                              @Nullable ProcessingContext context) {
        return new ConvertingCommandResult(converter, delegate.send(command, metadata, context));
    }

    private record ConvertingCommandResult(
            MessageConverter commandConverter,
            CommandResult delegate
    ) implements CommandResult {

        @Override
        public CompletableFuture<? extends Message> getResultMessage() {
            return delegate.getResultMessage();
        }

        @Override
        public <R> CompletableFuture<R> resultAs(@Nonnull Class<R> type) {
            return delegate.getResultMessage()
                           .thenApply(resultMessage -> resultMessage.payloadAs(type, commandConverter));
        }

        @Override
        public <R> CommandResult onSuccess(@Nonnull Class<R> resultType,
                                           @Nonnull BiConsumer<R, Message> successHandler) {
            requireNonNull(successHandler, "The success handler must not be null.");
            delegate.getResultMessage()
                    .whenComplete((message, e) -> {
                        if (e == null) {
                            successHandler.accept(message.payloadAs(resultType, commandConverter), message);
                        }
                    });
            return this;
        }
    }
}
