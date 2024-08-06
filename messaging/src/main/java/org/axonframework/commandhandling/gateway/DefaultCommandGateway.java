/*
 * Copyright (c) 2010-2023. Axon Framework
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
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.retry.RetryScheduler;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Default implementation of the CommandGateway interface. It allow configuration of a {@link RetryScheduler} and
 * {@link MessageDispatchInterceptor CommandDispatchInterceptors}. The Retry Scheduler allows for Command to be
 * automatically retried when a non-transient exception occurs. The Command Dispatch Interceptors can intercept and
 * alter command dispatched on this specific gateway. Typically, this would be used to add gateway specific meta data to
 * the Command.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DefaultCommandGateway implements CommandGateway {

    private final CommandBus commandBus;

    /**
     * Initialize the CommandGateway to send commands to given {@code commandBus}.
     *
     * @param commandBus The command bus to send commands on
     */
    public DefaultCommandGateway(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @Override
    public CommandResult send(@Nonnull Object command, @Nullable ProcessingContext processingContext) {
        return new FutureCommandResult(
                commandBus.dispatch(GenericCommandMessage.asCommandMessage(command), processingContext)
                          .thenCompose(
                                  msg -> msg instanceof ResultMessage<?> resultMessage && resultMessage.isExceptional()
                                          ? CompletableFuture.failedFuture(resultMessage.exceptionResult())
                                          : CompletableFuture.completedFuture(msg)
                          )
        );
    }
}
