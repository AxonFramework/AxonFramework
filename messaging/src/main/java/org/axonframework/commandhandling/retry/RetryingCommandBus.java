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

package org.axonframework.commandhandling.retry;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.common.Registration;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class RetryingCommandBus implements CommandBus {

    private final CommandBus delegate;
    private final RetryScheduler retryScheduler;

    public RetryingCommandBus(CommandBus delegate, RetryScheduler retryScheduler) {
        this.delegate = delegate;
        this.retryScheduler = retryScheduler;
    }

    @Override
    public CompletableFuture<? extends CommandResultMessage<?>> dispatch(@Nonnull CommandMessage<?> command,
                                                                         @Nullable ProcessingContext processingContext) {

        return delegate.dispatch(command, processingContext)
                       .<CommandResultMessage<?>>thenApply(Function.identity())
                       .exceptionallyCompose(e -> performRetry(command,
                                                               processingContext,
                                                               e));
    }

    private CompletableFuture<CommandResultMessage<?>> performRetry(CommandMessage<?> command,
                                                                    ProcessingContext processingContext,
                                                                    Throwable e) {
        return retryScheduler.scheduleRetry(command, processingContext, e, delegate::dispatch)
                             .thenApply(Function.identity());
    }

    @Override
    public Registration subscribe(@Nonnull String commandName,
                                  @Nonnull MessageHandler<? super CommandMessage<?>, ? extends CommandResultMessage<?>> handler) {
        return subscribe(commandName, handler);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("retryScheduler", retryScheduler);
    }
}
