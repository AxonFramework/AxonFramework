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

package org.axonframework.messaging.retry;

import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.DelayedMessageStream;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static org.axonframework.common.FutureUtils.unwrap;

/**
 * RetryScheduler implementation that schedules retries on a {@link ScheduledExecutorService} based on a given
 * {@link RetryPolicy policy}.
 *
 * @author Allard Buijze
 */
public class AsyncRetryScheduler implements RetryScheduler, DescribableComponent {

    private final RetryPolicy retryPolicy;
    private final ScheduledExecutorService executor;

    /**
     * Initialize the retry scheduler using given {@code retryPolicy} and execute retries on given {@code executor}.
     * <p>
     * This implementation will schedule retry tasks in memory.
     *
     * @param retryPolicy The policy indicating if and when to perform retries
     * @param executor    The executor service on which retries are scheduled
     */
    public AsyncRetryScheduler(RetryPolicy retryPolicy, ScheduledExecutorService executor) {
        this.retryPolicy = retryPolicy;
        this.executor = executor;
    }

    @Override
    public <M extends Message<?>, R extends Message<?>> MessageStream<R> scheduleRetry(@Nonnull M message,
                                                                                       @Nullable ProcessingContext processingContext,
                                                                                       @Nonnull Throwable cause,
                                                                                       @Nonnull Dispatcher<M, R> dispatcher) {
        RetryPolicy.Outcome outcome = retryPolicy.defineFor(message, cause, Collections.emptyList());
        if (!outcome.shouldReschedule()) {
            return MessageStream.failed(cause);
        }

        RetryTask<R> retryTask = new RetryTask<>(message, cause, () -> dispatcher.dispatch(message, processingContext));
        executor.schedule(retryTask, outcome.rescheduleInterval(), outcome.rescheduleIntervalTimeUnit());

        return DelayedMessageStream.create(retryTask.finalResult);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("retryPolicy", this.retryPolicy);
        descriptor.describeProperty("executor", this.executor);
    }

    private class RetryTask<T extends Message<?>> implements Runnable {

        private final CompletableFuture<MessageStream<T>> finalResult = new CompletableFuture<>();
        private final List<Class<? extends Throwable>[]> history;
        private final Message<?> message;
        private final Supplier<MessageStream<T>> dispatcher;

        public RetryTask(Message<?> message,
                         Throwable initialFailure,
                         Supplier<MessageStream<T>> dispatcher) {
            this.message = message;
            this.dispatcher = dispatcher;
            this.history = List.<Class<? extends Throwable>[]>of(simplify(initialFailure));
        }

        private RetryTask(RetryTask<T> previous, Throwable newFailure) {
            this.message = previous.message;
            this.dispatcher = previous.dispatcher;
            this.history = new ArrayList<>(previous.history.size());
            this.history.addAll(previous.history);
            this.history.add(simplify(newFailure));
        }

        private Class<? extends Throwable>[] simplify(Throwable failure) {
            List<Class<? extends Throwable>> causes = new ArrayList<>();
            Throwable cause = failure;
            do {
                causes.add(cause.getClass());
            } while ((cause = cause.getCause()) != null);
            //noinspection unchecked
            return causes.toArray(new Class[0]);
        }

        @Override
        public void run() {
            AtomicBoolean itemSeen = new AtomicBoolean(false);
            AtomicReference<MessageStream<T>> retryResult = new AtomicReference<>();
            finalResult.complete(
                    dispatcher.get()
                              .onNextItem(i -> itemSeen.set(true))
                              .onErrorContinue(failure -> {
                                  Throwable unwrapped = unwrap(failure);
                                  // When we've read items successfully before, we will simply propagate the failure
                                  if (itemSeen.get()) {
                                      return MessageStream.failed(unwrapped);
                                  }
                                  // we only want to schedule once, and repeat the previous result on next invocations
                                  return retryResult.updateAndGet(current -> {
                                      if (current != null) {
                                          return current;
                                      }
                                      RetryPolicy.Outcome decision = retryPolicy.defineFor(message, unwrapped, history);
                                      if (decision.shouldReschedule()) {
                                          RetryTask<T> newTask = new RetryTask<>(this, unwrapped);
                                          executor.schedule(newTask,
                                                            decision.rescheduleInterval(),
                                                            decision.rescheduleIntervalTimeUnit());
                                          return DelayedMessageStream.create(newTask.finalResult);
                                      }
                                      return MessageStream.failed(unwrapped);
                                  });
                              }));
        }
    }
}

