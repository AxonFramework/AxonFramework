/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling.callbacks;

import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandExecutionException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Command Handler Callback that allows the dispatching thread to wait for the result of the callback, using the Future
 * mechanism. This callback allows the caller to synchronize calls when an asynchronous command bus is being used.
 *
 * @param <R> the type of result of the command handling
 * @author Allard Buijze
 * @since 0.6
 */
public class FutureCallback<R> implements CommandCallback<R>, Future<R> {

    private volatile R result;
    private volatile Throwable failure;

    private final CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void onSuccess(R executionResult) {
        this.result = executionResult;
        latch.countDown();
    }

    @Override
    public void onFailure(Throwable cause) {
        this.failure = cause;
        latch.countDown();
    }

    /**
     * Waits if necessary for the command handling to complete, and then returns its result.
     *
     * @return the result of the command handler execution.
     *
     * @throws InterruptedException if the current thread is interrupted while waiting
     * @throws ExecutionException   if the command handler threw an exception
     */
    @Override
    public R get() throws InterruptedException, ExecutionException {
        if (!isDone()) {
            latch.await();
        }
        return getFutureResult();
    }

    /**
     * Waits if necessary for at most the given time for the command handling to complete, and then retrieves its
     * result, if available.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the timeout argument
     * @return the result of the command handler execution.
     *
     * @throws InterruptedException if the current thread is interrupted while waiting
     * @throws TimeoutException     if the wait timed out
     * @throws ExecutionException   if the command handler threw an exception
     * @see #getResult(long, java.util.concurrent.TimeUnit)
     */
    @Override
    public R get(long timeout, TimeUnit unit) throws TimeoutException, InterruptedException, ExecutionException {
        if (!isDone()) {
            if(!latch.await(timeout, unit)) {
                throw new TimeoutException("A Timeout occurred while waiting for a Command Callback");
            }
        }
        return getFutureResult();
    }

    /**
     * Waits if necessary for the command handling to complete, and then returns its result.
     * <p/>
     * Unlike {@link #get(long, java.util.concurrent.TimeUnit)}, this method will throw the original exception. Only
     * checked exceptions are wrapped in a {@link CommandExecutionException}.
     * <p/>
     * If the thread is interrupted while waiting, the interrupt flag is set back on the thread, and <code>null</code>
     * is returned. To distinguish between an interrupt and a <code>null</code> result, use the {@link #isDone()}
     * method.
     *
     * @return the result of the command handler execution.
     *
     * @see #get()
     */
    public R getResult() {
        if (!isDone()) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return doGetResult();
    }

    /**
     * Waits if necessary for at most the given time for the command handling to complete, and then retrieves its
     * result, if available.
     * <p/>
     * Unlike {@link #get(long, java.util.concurrent.TimeUnit)}, this method will throw the original exception. Only
     * checked exceptions are wrapped in a {@link CommandExecutionException}.
     * <p/>
     * If the timeout expired or the thread is interrupted before completion, <code>null</code> is returned. In case of
     * an interrupt, the interrupt flag will have been set back on the thread. To distinguish between an interrupt and
     * a
     * <code>null</code> result, use the {@link #isDone()}
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the timeout argument
     * @return the result of the command handler execution.
     */
    public R getResult(long timeout, TimeUnit unit) {
        if (!awaitCompletion(timeout, unit)) {
            return null;
        }
        return doGetResult();
    }

    public boolean awaitCompletion(long timeout, TimeUnit unit) {
        try {
            return isDone() || latch.await(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Always returns <code>false</code>, since command execution cannot be cancelled.
     *
     * @param mayInterruptIfRunning <tt>true</tt> if the thread executing the command should be interrupted; otherwise,
     *                              in-progress tasks are allowed to complete
     * @return <code>false</code>
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    /**
     * Always returns false, since command execution cannot be cancelled.
     *
     * @return false
     */
    @Override
    public boolean isCancelled() {
        return false;
    }

    /**
     * Indicates whether command handler execution has finished.
     *
     * @return <code>true</code> if command handler execution has finished, otherwise <code>false</code>.
     */
    @Override
    public boolean isDone() {
        return latch.getCount() == 0L;
    }

    private R doGetResult() {
        if (failure != null) {
            if (failure instanceof Error) {
                throw (Error) failure;
            } else if (failure instanceof RuntimeException) {
                throw (RuntimeException) failure;
            } else {
                throw new CommandExecutionException("An exception occurred while executing a command", failure);
            }
        } else {
            return result;
        }
    }

    private R getFutureResult() throws ExecutionException {
        if (failure != null) {
            throw new ExecutionException(failure);
        } else {
            return result;
        }
    }
}
