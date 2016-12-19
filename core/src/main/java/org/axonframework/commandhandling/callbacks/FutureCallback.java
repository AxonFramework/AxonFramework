/*
 * Copyright (c) 2010-2014. Axon Framework
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
import org.axonframework.commandhandling.CommandMessage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.util.Objects.requireNonNull;

/**
 * Command Handler Callback that allows the dispatching thread to wait for the result of the callback, using the Future
 * mechanism. This callback allows the caller to synchronize calls when an asynchronous command bus is being used.
 *
 * @param <R> the type of result of the command handling
 * @param <C> The type of payload of the dispatched command
 * @author Allard Buijze
 * @since 0.6
 */
public class FutureCallback<C, R> extends CompletableFuture<R> implements CommandCallback<C, R> {

    @Override
    public void onSuccess(CommandMessage<? extends C> commandMessage, R executionResult) {
        super.complete(executionResult);
    }

    @Override
    public void onFailure(CommandMessage commandMessage, Throwable cause) {
        super.completeExceptionally(requireNonNull(cause));
    }

    /**
     * Waits if necessary for the command handling to complete, and then returns its result.
     * <p/>
     * Unlike {@link #get(long, java.util.concurrent.TimeUnit)}, this method will throw the original exception. Only
     * checked exceptions are wrapped in a {@link CommandExecutionException}.
     * <p/>
     * If the thread is interrupted while waiting, the interrupt flag is set back on the thread, and {@code null}
     * is returned. To distinguish between an interrupt and a {@code null} result, use the {@link #isDone()}
     * method.
     *
     * @return the result of the command handler execution.
     *
     * @see #get()
     */
    public R getResult() {
        try {
            return get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            throw asRuntime(e);
        }
    }

    /**
     * Waits if necessary for at most the given time for the command handling to complete, and then retrieves its
     * result, if available.
     * <p/>
     * Unlike {@link #get(long, java.util.concurrent.TimeUnit)}, this method will throw the original exception. Only
     * checked exceptions are wrapped in a {@link CommandExecutionException}.
     * <p/>
     * If the timeout expired or the thread is interrupted before completion, {@code null} is returned. In case of
     * an interrupt, the interrupt flag will have been set back on the thread. To distinguish between an interrupt and
     * a {@code null} result, use the {@link #isDone()}
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the timeout argument
     * @return the result of the command handler execution.
     */
    public R getResult(long timeout, TimeUnit unit) {
        try {
            return get(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (TimeoutException e) {
            return null;
        } catch (ExecutionException e) {
            throw asRuntime(e);
        }
    }

    private RuntimeException asRuntime(Exception e) {
        Throwable failure = e.getCause();
        if (failure instanceof Error) {
            throw (Error) failure;
        } else if (failure instanceof RuntimeException) {
            return (RuntimeException) failure;
        } else {
            return new CommandExecutionException("An exception occurred while executing a command", failure);
        }
    }

    /**
     * Wait for completion of the command, or for the timeout to expire.
     *
     * @param timeout The amount of time to wait for command processing to complete
     * @param unit    The unit in which the timeout is expressed
     * @return {@code true} if command processing completed before the timeout expired, otherwise
     * {@code false}.
     */
    public boolean awaitCompletion(long timeout, TimeUnit unit) {
        try {
            get(timeout, unit);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException e) {
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }
}
