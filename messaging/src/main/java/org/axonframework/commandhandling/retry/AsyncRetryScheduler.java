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

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * RetryScheduler implementation that retries commands at regular intervals when they fail because of an exception that
 * is not explicitly non-transient. Checked exceptions are considered non-transient and will not result in a retry.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class AsyncRetryScheduler implements RetryScheduler {

    private final RetryPolicy retryPolicy;
    private final ScheduledExecutorService executor;

    public AsyncRetryScheduler(RetryPolicy retryPolicy, ScheduledExecutorService executor) {
        this.retryPolicy = retryPolicy;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<? extends CommandResultMessage<?>> scheduleRetry(
            @Nonnull CommandMessage<?> commandMessage, @Nullable ProcessingContext processingContext,
            @Nonnull Throwable cause, @Nonnull Dispatcher dispatcher) {

        RetryPolicy.Outcome outcome = retryPolicy.defineFor(commandMessage, cause, Collections.emptyList());
        if (!outcome.shouldReschedule()) {
            return CompletableFuture.failedFuture(cause);
        }

        RetryTask retryTask = new RetryTask(commandMessage,
                                            retryPolicy,
                                            cause,
                                            () -> dispatcher.dispatch(commandMessage, processingContext));
        executor.schedule(retryTask, outcome.rescheduleInterval(), outcome.rescheduleIntervalTimeUnit());

        return retryTask.finalResult;
    }

    private class RetryTask implements Runnable {

        private final CompletableFuture<CommandResultMessage<?>> finalResult = new CompletableFuture<>();
        private final List<Class<? extends Throwable>[]> history = new ArrayList<>();
        private final CommandMessage<?> commandMessage;
        private final RetryPolicy retryPolicy;
        private final Supplier<CompletableFuture<? extends CommandResultMessage<?>>> dispatcher;

        public RetryTask(CommandMessage<?> commandMessage, RetryPolicy retryPolicy,
                         Throwable initialFailure,
                         Supplier<CompletableFuture<? extends CommandResultMessage<?>>> dispatcher) {
            this.commandMessage = commandMessage;
            this.retryPolicy = retryPolicy;
            this.dispatcher = dispatcher;
            this.history.add(simplify(initialFailure));
        }

        private Class<? extends Throwable>[] simplify(Throwable failure) {
            List<Class<? extends Throwable>> causes = new ArrayList<>();
            causes.add(failure.getClass());
            Throwable cause;
            while ((cause = failure.getCause()) != null) {
                causes.add(cause.getClass());
            }
            //noinspection unchecked
            return causes.toArray(new Class[0]);
        }

        @Override
        public void run() {
            dispatcher.get()
                      .exceptionally(failure -> {
                          RetryPolicy.Outcome decision = retryPolicy.defineFor(commandMessage, failure, history);
                          if (decision.shouldReschedule()) {
                              history.add(simplify(failure));
                              executor.schedule(this,
                              decision.rescheduleInterval(),
                              decision.rescheduleIntervalTimeUnit());
                          } else {
                              finalResult.completeExceptionally(failure);
                          }
                          return null;
                      })
                      .thenAccept(r -> {
                          if (r != null) {
                              finalResult.complete(r);
                          }
                      });
        }
    }
}

