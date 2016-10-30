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

package org.axonframework.commandhandling;

import org.axonframework.common.Assert;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Specialization of the SimpleCommandBus that processed Commands asynchronously from the calling thread. By default,
 * the AsynchronousCommandBus uses a Cached Thread Pool (see
 * {@link java.util.concurrent.Executors#newCachedThreadPool()}). It will reuse threads while possible, and shut them
 * down after 60 seconds of inactivity.
 * <p/>
 * Each Command is dispatched in a separate task, which is processed by the Executor.
 * <p/>
 * Note that you should call {@link #shutdown()} to stop any threads waiting for new tasks. Failure to do so may cause
 * the JVM to hang for up to 60 seconds on JVM shutdown.
 *
 * @author Allard Buijze
 * @since 1.3.4
 */
public class AsynchronousCommandBus extends SimpleCommandBus {

    private final Executor executor;

    /**
     * Initialize the AsynchronousCommandBus, using a Cached Thread Pool.
     */
    public AsynchronousCommandBus() {
        this(Executors.newCachedThreadPool());
    }

    /**
     * Initialize the AsynchronousCommandBus using the given {@code executor}.
     *
     * @param executor The executor that processes Command dispatching threads
     */
    public AsynchronousCommandBus(Executor executor) {
        Assert.notNull(executor, () -> "executor may not be null");
        this.executor = executor;
    }

    @Override
    protected <C, R> void doDispatch(CommandMessage<C> command, CommandCallback<? super C, R> callback) {
        executor.execute(new DispatchCommand<>(command, callback));
    }

    /**
     * Shuts down the Executor used to asynchronously dispatch incoming commands. If the {@code Executor} provided
     * in the constructor does not implement {@code ExecutorService}, this method does nothing.
     */
    public void shutdown() {
        if (executor instanceof ExecutorService) {
            ((ExecutorService) executor).shutdown();
            try {
                ((ExecutorService) executor).awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // we've been interrupted. Reset the interruption flag and continue
                Thread.currentThread().interrupt();
            }
        }
    }

    private final class DispatchCommand<C, R> implements Runnable {

        private final CommandMessage<C> command;
        private final CommandCallback<? super C, R> callback;

        public DispatchCommand(CommandMessage<C> command, CommandCallback<? super C, R> callback) {
            this.command = command;
            this.callback = callback;
        }

        @Override
        public void run() {
            AsynchronousCommandBus.super.doDispatch(command, callback);
        }
    }
}
