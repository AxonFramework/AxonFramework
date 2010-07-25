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
import org.axonframework.commandhandling.CommandContext;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Command Handler Callback that allows the dispatching thread to wait for the result of the callback, using the Future
 * mechanism. This callback allows the caller to synchronize calls when an asynchronous command bus is being used.
 *
 * @author Allard Buijze
 * @param <C> the type of the dispatched command
 * @param <R> the type of result of the command handling
 * @since 0.6
 */
public class FutureCallback<C, R> implements CommandCallback<C, R>, Future<R> {

    private R result;
    private Throwable failure;

    private final CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void onSuccess(R executionResult, CommandContext<C> context) {
        this.result = executionResult;
        latch.countDown();
    }

    @Override
    public void onFailure(Throwable cause, CommandContext<C> context) {
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
        latch.await();
        return doGetResult();
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
     */
    @Override
    public R get(long timeout, TimeUnit unit) throws TimeoutException, InterruptedException, ExecutionException {
        if (!latch.await(timeout, unit)) {
            throw new TimeoutException("The timeout of the getResult operation was reached");
        }
        return doGetResult();
    }

    /**
     * Always returns false, since command execution cannot be cancelled.
     *
     * @param mayInterruptIfRunning <tt>true</tt> if the thread executing the command should be interrupted; otherwise,
     *                              in-progress tasks are allowed to complete
     * @return false
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

    private R doGetResult() throws ExecutionException {
        if (result != null) {
            return result;
        } else if (failure != null) {
            throw new ExecutionException(failure);
        }
        throw new IllegalStateException("The callback was released, but there is no result to report.");
    }
}
