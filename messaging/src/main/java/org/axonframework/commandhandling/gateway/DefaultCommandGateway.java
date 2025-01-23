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

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.retry.RetryScheduler;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Default implementation of the {@link CommandGateway} interface.
 * <p>
 * It allows configuration of a {@link RetryScheduler} and
 * {@link MessageDispatchInterceptor CommandDispatchInterceptors}. The {@code RetryScheduler} allows for commands to be
 * automatically retried when a non-transient exception occurs. The {@code CommandDispatchInterceptors} can intercept
 * and alter command dispatched on this specific gateway. Typically, this would be used to add gateway-specific metadata
 * to the command.
 *
 * @author Allard Buijze
 * @since 2.0.0
 */
public class DefaultCommandGateway implements CommandGateway {

    private final CommandBus commandBus;
    private final MessageTypeResolver messageTypeResolver;

    /**
     * Initialize the {@link DefaultCommandGateway} to send commands through given {@code commandBus}. The
     * {@link org.axonframework.messaging.QualifiedName names} for
     * {@link org.axonframework.commandhandling.CommandMessage CommandMessages} are resolved through the given
     * {@code nameResolver}.
     *
     * @param commandBus          The {@link CommandBus} to send commands on.
     * @param messageTypeResolver The {@link MessageTypeResolver} resolving the
     *                            {@link org.axonframework.messaging.QualifiedName names} for
     *                            {@link org.axonframework.commandhandling.CommandMessage CommandMessages} being
     *                            dispatched on the {@code commandBus}.
     */
    public DefaultCommandGateway(@Nonnull CommandBus commandBus,
                                 @Nonnull MessageTypeResolver messageTypeResolver) {
        this.commandBus = commandBus;
        this.messageTypeResolver = messageTypeResolver;
    }

    @Override
    public CommandResult send(@Nonnull Object command,
                              @Nullable ProcessingContext processingContext) {
        return new FutureCommandResult(
                commandBus.dispatch(asCommandMessage(command, MetaData.emptyInstance()), processingContext)
                          .thenCompose(
                                  msg -> msg instanceof ResultMessage<?> resultMessage && resultMessage.isExceptional()
                                          ? CompletableFuture.failedFuture(resultMessage.exceptionResult())
                                          : CompletableFuture.completedFuture(msg)
                          )
        );
    }

    @Override
    public CommandResult send(@Nonnull Object command,
                              @Nonnull MetaData metaData,
                              @Nullable ProcessingContext processingContext) {
        return new FutureCommandResult(
                commandBus.dispatch(asCommandMessage(command, metaData), processingContext)
                          .thenCompose(
                                  msg -> msg instanceof ResultMessage<?> resultMessage && resultMessage.isExceptional()
                                          ? CompletableFuture.failedFuture(resultMessage.exceptionResult())
                                          : CompletableFuture.completedFuture(msg)
                          )
        );
    }

    /**
     * Returns the given command as a {@link CommandMessage}. If {@code command} already implements
     * {@code CommandMessage}, it is returned as-is. When the {@code command} is another implementation of
     * {@link Message}, the {@link Message#getPayload()} and {@link Message#getMetaData()} are used as input for a new
     * {@link GenericCommandMessage}. Otherwise, the given {@code command} is wrapped into a
     * {@code GenericCommandMessage} as its payload.
     *
     * @param command The command to wrap as {@link CommandMessage}.
     * @return A {@link CommandMessage} containing given {@code command} as payload, a {@code command} if it already
     * implements {@code CommandMessage}, or a {@code CommandMessage} based on the result of
     * {@link Message#getPayload()} and {@link Message#getMetaData()} for other {@link Message} implementations.
     */
    @SuppressWarnings("unchecked")
    private <C> CommandMessage<C> asCommandMessage(Object command, MetaData metaData) {
        if (command instanceof CommandMessage<?>) {
            return (CommandMessage<C>) command;
        }

        if (command instanceof Message<?> message) {
            return new GenericCommandMessage<>(
                    message.type(),
                    (C) message.getPayload(),
                    message.getMetaData()
            );
        }

        return new GenericCommandMessage<>(
                messageTypeResolver.resolve(command),
                (C) command,
                metaData
        );
    }
}
