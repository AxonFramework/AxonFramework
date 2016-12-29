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

package org.axonframework.commandhandling.gateway;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.callbacks.FailureLoggingCallback;
import org.axonframework.commandhandling.callbacks.FutureCallback;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;

/**
 * Default implementation of the CommandGateway interface. It allow configuration of a {@link RetryScheduler} and
 * {@link MessageDispatchInterceptor CommandDispatchInterceptors}. The Retry Scheduler allows for Command to be
 * automatically retried when a non-transient exception occurs. The Command Dispatch Interceptors can intercept and
 * alter command dispatched on this specific gateway. Typically, this would be used to add gateway specific meta data
 * to the Command.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DefaultCommandGateway extends AbstractCommandGateway implements CommandGateway {

    private static final Logger logger = LoggerFactory.getLogger(DefaultCommandGateway.class);

    /**
     * Initializes a command gateway that dispatches commands to the given {@code commandBus} after they have been
     * handles by the given {@code commandDispatchInterceptors}. Commands will not be retried when command
     * execution fails.
     *
     * @param commandBus                  The CommandBus on which to dispatch the Command Messages
     * @param messageDispatchInterceptors The interceptors to invoke before dispatching commands to the Command Bus
     */
    @SafeVarargs
    public DefaultCommandGateway(CommandBus commandBus,
                                 MessageDispatchInterceptor<? super CommandMessage<?>>... messageDispatchInterceptors) {
        this(commandBus, null, messageDispatchInterceptors);
    }

    /**
     * Initializes a command gateway that dispatches commands to the given {@code commandBus} after they have been
     * handles by the given {@code commandDispatchInterceptors}. When command execution results in an unchecked
     * exception, the given {@code retryScheduler} is invoked to allow it to retry that command.
     * execution fails.
     *
     * @param commandBus                  The CommandBus on which to dispatch the Command Messages
     * @param retryScheduler              The scheduler that will decide whether to reschedule commands
     * @param messageDispatchInterceptors The interceptors to invoke before dispatching commands to the Command Bus
     */
    @SafeVarargs
    public DefaultCommandGateway(CommandBus commandBus, RetryScheduler retryScheduler,
                                 MessageDispatchInterceptor<? super CommandMessage<?>>... messageDispatchInterceptors) {
        this(commandBus, retryScheduler, asList(messageDispatchInterceptors));
    }

    /**
     * Initializes a command gateway that dispatches commands to the given {@code commandBus} after they have been
     * handles by the given {@code commandDispatchInterceptors}. When command execution results in an unchecked
     * exception, the given {@code retryScheduler} is invoked to allow it to retry that command.
     * execution fails.
     *
     * @param commandBus                  The CommandBus on which to dispatch the Command Messages
     * @param retryScheduler              The scheduler that will decide whether to reschedule commands
     * @param messageDispatchInterceptors The interceptors to invoke before dispatching commands to the Command Bus
     */
    public DefaultCommandGateway(CommandBus commandBus, RetryScheduler retryScheduler,
                                 List<MessageDispatchInterceptor<? super CommandMessage<?>>> messageDispatchInterceptors) {
        super(commandBus, retryScheduler, messageDispatchInterceptors);
    }

    @Override
    public <C, R> void send(C command, CommandCallback<? super C, R> callback) {
        super.send(command, callback);
    }

    /**
     * Sends the given {@code command} and waits for its execution to complete, or until the waiting thread is
     * interrupted.
     *
     * @param command The command to send
     * @param <R>     The expected type of return value
     * @return The result of the command handler execution
     *
     * @throws org.axonframework.commandhandling.CommandExecutionException
     *          when command execution threw a checked exception
     */
    @Override
    @SuppressWarnings("unchecked")
    public <R> R sendAndWait(Object command) {
        FutureCallback<Object, Object> futureCallback = new FutureCallback<>();
        send(command, futureCallback);
        return (R) futureCallback.getResult();
    }

    /**
     * Sends the given {@code command} and waits for its execution to complete, or until the given
     * {@code timeout} has expired, or the waiting thread is interrupted.
     * <p/>
     * When the timeout occurs, or the thread is interrupted, this method returns {@code null}.
     *
     * @param command The command to send
     * @param timeout The maximum time to wait
     * @param unit    The time unit of the timeout argument
     * @param <R>     The expected type of return value
     * @return The result of the command handler execution
     *
     * @throws org.axonframework.commandhandling.CommandExecutionException
     *          when command execution threw a checked exception
     */
    @Override
    @SuppressWarnings("unchecked")
    public <R> R sendAndWait(Object command, long timeout, TimeUnit unit) {
        FutureCallback<Object, Object> futureCallback = new FutureCallback<>();
        send(command, futureCallback);
        return (R) futureCallback.getResult(timeout, unit);
    }

    @Override
    public <R> CompletableFuture<R> send(Object command) {
        FutureCallback<Object, R> callback = new FutureCallback<>();
        send(command, new FailureLoggingCallback<>(logger, callback));
        return callback;
    }
}
