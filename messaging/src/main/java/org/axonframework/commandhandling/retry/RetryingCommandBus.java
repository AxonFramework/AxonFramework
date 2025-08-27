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

package org.axonframework.commandhandling.retry;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageStream.Entry;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.retry.RetryScheduler;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.axonframework.common.FutureUtils.unwrap;

/**
 * A {@code CommandBus} wrapper that will retry dispatching {@link CommandMessage commands} that resulted in a failure.
 * <p>
 * A {@link RetryScheduler} is used to determine if and how retries are performed.
 *
 * @author Allard Buijze
 * @since 5.0.0
 */
public class RetryingCommandBus implements CommandBus {

    private final CommandBus delegate;
    private final RetryScheduler retryScheduler;

    /**
     * Initialize the {@code RetryingCommandBus} to dispatch commands on given {@code delegate} and perform retries
     * using the given {@code retryScheduler}.
     *
     * @param delegate       The delegate {@code CommandBus} that will handle all dispatching and handling logic.
     * @param retryScheduler The retry scheduler to use to reschedule failed commands.
     */
    public RetryingCommandBus(@Nonnull CommandBus delegate,
                              @Nonnull RetryScheduler retryScheduler) {
        this.delegate = requireNonNull(delegate, "The command bus delegate must be null.");
        this.retryScheduler = requireNonNull(retryScheduler, "the RetryScheduler must not be null.");
    }

    @Override
    public RetryingCommandBus subscribe(@Nonnull QualifiedName name,
                                        @Nonnull CommandHandler handler) {
        delegate.subscribe(name, handler);
        return this;
    }

    @Override
    public CompletableFuture<CommandResultMessage<?>> dispatch(@Nonnull CommandMessage command,
                                                               @Nullable ProcessingContext processingContext) {
        return dispatchToDelegate(command, processingContext)
                .exceptionallyCompose(e -> performRetry(command, processingContext, unwrap(e)));
    }

    private CompletableFuture<CommandResultMessage<?>> dispatchToDelegate(CommandMessage command,
                                                                          ProcessingContext processingContext) {
        return delegate.dispatch(command, processingContext)
                       .thenApply(Function.identity());
    }

    private CompletableFuture<CommandResultMessage<?>> performRetry(CommandMessage command,
                                                                    ProcessingContext processingContext,
                                                                    Throwable e) {
        return retryScheduler.scheduleRetry(command, processingContext, e, this::redispatch)
                             .first().asCompletableFuture()
                             .thenApply(Entry::message);
    }

    private MessageStream<CommandResultMessage<?>> redispatch(CommandMessage cmd, ProcessingContext ctx) {
        return MessageStream.fromFuture(dispatchToDelegate(cmd, ctx));
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("retryScheduler", retryScheduler);
    }
}
