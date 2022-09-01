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


import org.axonframework.axonserver.connector.PriorityCallable;
import org.axonframework.axonserver.connector.PriorityRunnable;
import org.axonframework.axonserver.connector.PriorityTask;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

/**
 * This {@link ExecutorService} wraps an existing one, creating a {@link PriorityTask} for every {@link Runnable} that
 * is submitted that's an instance of one. The created {@code PriorityTask} will have the priority provided in the
 * constructor, as well as the {@code taskSequence} provided there.
 * <p>
 * This implementation also diverts all invocations of {@link ExecutorService#submit(Runnable)} to
 * {@link ExecutorService#execute(Runnable)} instead. This is because the {@code submit} method implementations wrap the
 * tasks in another {@link FutureTask}, which makes the task uncomparable again.
 *
 * @author Mitchell Herrijgers
 * @author Milan Savic
 * @since 4.6.0
 */
public class PriorityExecutorService implements ExecutorService {

    private final ExecutorService delegate;
    private final AtomicLong taskSequence;
    private final long priority;

    /**
     * Creates a new {@link PriorityExecutorService} with the provided priority and sequence.
     * <p>
     * Use {@link PriorityTaskSchedulers#forPriority(ExecutorService, long, AtomicLong)} to create an instance.
     *
     * @param delegate     The delegate {@link ExecutorService} to use when submitting tasks.
     * @param priority     The priority that any tasks submitted to the delegate will have.
     * @param taskSequence The task sequence, used for ordering items with the same priority.
     */
    PriorityExecutorService(ExecutorService delegate, long priority, AtomicLong taskSequence) {
        this.priority = priority;
        this.delegate = delegate;
        this.taskSequence = taskSequence;
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

    private PriorityRunnable createPriorityRunnable(Runnable task) {
        if (task instanceof PriorityRunnable) {
            return (PriorityRunnable) task;
        }
        return new PriorityRunnable(task, priority, taskSequence.incrementAndGet());
    }

    private <T> PriorityCallable<T> createPriorityCallable(Callable<T> task) {
        if (task instanceof PriorityCallable) {
            return (PriorityCallable<T>) task;
        }
        return new PriorityCallable<>(task, priority, taskSequence.incrementAndGet());
    }

    @Nonnull
    @Override
    public <T> Future<T> submit(@Nonnull Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        PriorityRunnable priorityTask = createPriorityRunnable(() -> {
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


    @Nonnull
    @Override
    public <T> Future<T> submit(@Nonnull Runnable task, T result) {
        CompletableFuture<T> future = new CompletableFuture<>();
        delegate.execute(createPriorityRunnable(() -> {
            task.run();
            future.complete(result);
        }));
        return future;
    }

    @Nonnull
    @Override
    public Future<?> submit(@Nonnull Runnable task) {
        CompletableFuture<?> future = new CompletableFuture<>();
        delegate.execute(createPriorityRunnable(() -> {
            task.run();
            future.complete(null);
        }));
        return future;
    }

    @Nonnull
    @Override
    public <T> List<Future<T>> invokeAll(@Nonnull Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return delegate.invokeAll(tasks.stream().map(this::createPriorityCallable).collect(Collectors.toList()));
    }

    @Nonnull
    @Override
    public <T> List<Future<T>> invokeAll(@Nonnull Collection<? extends Callable<T>> tasks,
                                         long timeout, @Nonnull TimeUnit unit)
            throws InterruptedException {
        return delegate.invokeAll(
                tasks.stream().map(this::createPriorityCallable).collect(Collectors.toList()),
                timeout,
                unit
        );
    }

    @Nonnull
    @Override
    public <T> T invokeAny(@Nonnull Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return delegate.invokeAny(
                tasks.stream().map(this::createPriorityCallable).collect(Collectors.toList())
        );
    }

    @Override
    public <T> T invokeAny(@Nonnull Collection<? extends Callable<T>> tasks, long timeout,
                           @Nonnull TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(
                tasks.stream().map(this::createPriorityCallable).collect(Collectors.toList()),
                timeout,
                unit
        );
    }

    @Override
    public void execute(@Nonnull Runnable command) {
        delegate.execute(createPriorityRunnable(command));
    }
}
