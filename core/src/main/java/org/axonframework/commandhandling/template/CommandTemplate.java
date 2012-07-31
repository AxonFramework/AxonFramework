/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.commandhandling.template;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.callbacks.FutureCallback;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This class simplifies the use of the CommandBus, by providing methods for common usage patterns. Once constructed,
 * this class is safe for use in a multi-threaded environment.
 * <p/>
 * Objects passed to the <code>send</code> and <code>sendAndWait</code> methods are automatically wrapped in
 * CommandMessages, if necessary. If the passed object is already a CommandMessage, it is dispatched as-is.
 * <p/>
 * The <code>sendAndWait</code> methods in this template throw any runtime exceptions and errors that resulted from
 * command execution as-is. Checked exceptions are wrapped in a
 * {@link org.axonframework.commandhandling.CommandExecutionException}.
 *
 * @author Allard Buijze
 * @since 1.3
 * @deprecated Use the {@link org.axonframework.commandhandling.gateway.CommandGateway} instead
 */
@Deprecated
public class CommandTemplate {

    private final CommandBus commandBus;

    /**
     * Constructs a CommandTemplate that uses the given <code>commandBus</code> to send its commands.
     *
     * @param commandBus the CommandBus on which commands must be sent
     */
    public CommandTemplate(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    /**
     * Sends the given <code>command</code> and waits for its execution to complete, or until the waiting thread is
     * interrupted.
     *
     * @param command The command to send
     * @param <R>     The expected type of return value
     * @return The result of the command handler execution
     *
     * @throws InterruptedException when the thread is interrupted while waiting
     * @throws org.axonframework.commandhandling.CommandExecutionException
     *                              when command execution threw a checked exception
     */
    @SuppressWarnings("unchecked")
    public <R> R sendAndWait(Object command) throws InterruptedException {
        return (R) doSend(command).getResult();
    }

    /**
     * Sends the given <code>command</code> and waits for its execution to complete, until the given
     * <code>timeout</code> has expired, or the waiting thread is interrupted.
     *
     * @param command The command to send
     * @param timeout The maximum time to wait
     * @param unit    The time unit of the timeout argument
     * @param <R>     The expected type of return value
     * @return The result of the command handler execution
     *
     * @throws InterruptedException when the thread is interrupted while waiting
     * @throws TimeoutException     when the given timeout has expired while waiting for a result
     * @throws org.axonframework.commandhandling.CommandExecutionException
     *                              when command execution threw a checked exception
     */
    @SuppressWarnings("unchecked")
    public <R> R sendAndWait(Object command, long timeout, TimeUnit unit) throws TimeoutException,
                                                                                 InterruptedException {
        final FutureCallback<R> callback = doSend(command);
        if (callback.awaitCompletion(timeout, unit)) {
            return callback.getResult();
        } else {
            throw new TimeoutException("A timeout occurred while waiting for the Command Execution result");
        }
    }

    /**
     * Sends the given <code>command</code> and returns a Future instance that allows the caller to retrieve the result
     * at an appropriate time.
     *
     * @param command The command to send
     * @param <R>     The type of return value expected
     * @return a future providing access to the execution result
     */
    public <R> Future<R> send(Object command) {
        return doSend(command);
    }

    private <R> FutureCallback<R> doSend(Object command) {
        FutureCallback<R> futureCallback = new FutureCallback<R>();
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(command), futureCallback);
        return futureCallback;
    }
}
