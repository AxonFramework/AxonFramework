/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.axonserver.connector.util;


import org.axonframework.axonserver.connector.PriorityTask;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

public class PriorityTaskExecutorService implements ExecutorService {

    private final ExecutorService delegate;
    private final AtomicLong taskSequence;
    private final long priority;

    public PriorityTaskExecutorService(ExecutorService delegate, long priority, AtomicLong taskSequence) {
        this.priority = priority;
        this.delegate = delegate;
        this.taskSequence = taskSequence;
    }

    @Nonnull
    @Override
    public <T> Future<T> submit(@Nonnull Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        PriorityTask priorityTask = createPriorityTask(() -> {
            try {
                T call = task.call();
                future.complete(call);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        delegate.execute(priorityTask);
        return future;
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Nonnull
    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, @Nonnull TimeUnit unit)
            throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    private PriorityTask createPriorityTask(Runnable task) {
        return new PriorityTask(task, priority, taskSequence.incrementAndGet());
    }


    @Nonnull
    @Override
    public <T> Future<T> submit(@Nonnull Runnable task, T result) {
        CompletableFuture<T> future = new CompletableFuture<>();
        delegate.execute(createPriorityTask(() -> {
            task.run();
            future.complete(result);
        }));
        return future;
    }

    @Nonnull
    @Override
    public Future<?> submit(@Nonnull Runnable task) {
        CompletableFuture<?> future = new CompletableFuture<>();
        delegate.execute(createPriorityTask(() -> {
            task.run();
            future.complete(null);
        }));
        return future;
    }

    @Nonnull
    @Override
    public <T> List<Future<T>> invokeAll(@Nonnull Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        return tasks.stream().map(this::submit).collect(Collectors.toList());
    }

    @Nonnull
    @Override
    public <T> List<Future<T>> invokeAll(@Nonnull Collection<? extends Callable<T>> tasks,
                                         long timeout, @Nonnull TimeUnit unit)
            throws InterruptedException {
        return delegate.invokeAll(tasks, timeout, unit);
    }

    @Nonnull
    @Override
    public <T> T invokeAny(@Nonnull Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return delegate.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(@Nonnull Collection<? extends Callable<T>> tasks, long timeout,
                           @Nonnull TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(tasks, timeout, unit);
    }

    @Override
    public void execute(@Nonnull Runnable command) {
        delegate.execute(createPriorityTask(command));
    }
}
