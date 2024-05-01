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
import org.axonframework.common.Registration;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.retry.RetryScheduler;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static org.axonframework.common.FutureUtils.unwrap;

/**
 * CommandBus wrapper that will retry dispatching Commands that resulted in a failure. A {@link RetryScheduler} is used
 * to determine if and how retries are performed.
 */
public class RetryingCommandBus implements CommandBus {

    private final CommandBus delegate;
    private final RetryScheduler retryScheduler;

    /**
     * Initialize the RetryingCommandBus to dispatch Commands on given {@code delegate} and perform retries using the
     * given {@code retryScheduler}
     *
     * @param delegate       The delegate to dispatch Commands to
     * @param retryScheduler The retry scheduler to use to reschedule failed Commands
     */
    public RetryingCommandBus(CommandBus delegate, RetryScheduler retryScheduler) {
        this.delegate = delegate;
        this.retryScheduler = retryScheduler;
    }

    @Override
    public CompletableFuture<? extends Message<?>> dispatch(@Nonnull CommandMessage<?> command,
                                                            @Nullable ProcessingContext processingContext) {
        return dispatchToDelegate(command, processingContext)
                .exceptionallyCompose(e -> performRetry(command, processingContext, unwrap(e)));
    }

    private CompletableFuture<Message<?>> dispatchToDelegate(CommandMessage<?> command,
                                                             ProcessingContext processingContext) {
        return delegate.dispatch(command, processingContext).thenApply(Function.identity());
    }

    private CompletableFuture<Message<?>> performRetry(CommandMessage<?> command,
                                                       ProcessingContext processingContext,
                                                       Throwable e) {
        return retryScheduler.scheduleRetry(command, processingContext, e, this::redispatch)
                             .asCompletableFuture();
    }

    private MessageStream<Message<?>> redispatch(CommandMessage<?> cmd, ProcessingContext ctx) {
        return MessageStream.fromFuture(dispatchToDelegate(cmd, ctx));
    }

    @Override
    public Registration subscribe(@Nonnull String commandName,
                                  @Nonnull MessageHandler<? super CommandMessage<?>, ? extends Message<?>> handler) {
        return delegate.subscribe(commandName, handler);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("retryScheduler", retryScheduler);
    }
}
