/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.commandhandling.gateway;

import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptorSupport;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Interface towards the Command Handling components of an application. This interface provides a friendlier API toward
 * the command bus. The CommandGateway allows for components dispatching commands to wait for the result.
 *
 * @author Allard Buijze
 * @see DefaultCommandGateway
 * @since 2.0
 */
public interface CommandGateway extends MessageDispatchInterceptorSupport<CommandMessage<?>> {

    /**
     * Sends the given {@code command}, and have the result of the command's execution reported to the given
     * {@code callback}.
     * <p/>
     * The given {@code command} is wrapped as the payload of the CommandMessage that is eventually posted on the
     * Command Bus, unless Command already implements {@link Message}. In that case, a
     * CommandMessage is constructed from that message's payload and MetaData.
     *
     * @param command  The command to dispatch
     * @param callback The callback to notify when the command has been processed
     * @param <R>      The type of result expected from command execution
     */
    <C, R> void send(C command, CommandCallback<? super C, ? super R> callback);

    /**
     * Sends the given {@code command} and wait for it to execute. The result of the execution is returned when
     * available. This method will block indefinitely, until a result is available, or until the Thread is interrupted.
     * When the thread is interrupted, this method returns {@code null}. If command execution resulted in an
     * exception, it is wrapped in a {@link CommandExecutionException}.
     * <p/>
     * The given {@code command} is wrapped as the payload of the CommandMessage that is eventually posted on the
     * Command Bus, unless Command already implements {@link Message}. In that case, a
     * CommandMessage is constructed from that message's payload and MetaData.
     * <p/>
     * Note that the interrupted flag is set back on the thread if it has been interrupted while waiting.
     *
     * @param command The command to dispatch
     * @param <R>     The type of result expected from command execution
     * @return the result of command execution, or {@code null} if the thread was interrupted while waiting for
     *         the command to execute
     *
     * @throws CommandExecutionException when an exception occurred while processing the command
     */
    <R> R sendAndWait(Object command);

    /**
     * Sends the given {@code command} and wait for it to execute. The result of the execution is returned when
     * available. This method will block until a result is available, or the given {@code timeout} was reached, or
     * until the Thread is interrupted. When the timeout is reached or the thread is interrupted, this method returns
     * {@code null}. If command execution resulted in an exception, it is wrapped in a
     * {@link CommandExecutionException}.
     * <p/>
     * The given {@code command} is wrapped as the payload of the CommandMessage that is eventually posted on the
     * Command Bus, unless Command already implements {@link Message}. In that case, a
     * CommandMessage is constructed from that message's payload and MetaData.
     * <p/>
     * Note that the interrupted flag is set back on the thread if it has been interrupted while waiting.
     *
     * @param command The command to dispatch
     * @param timeout The amount of time the thread is allows to wait for the result
     * @param unit    The unit in which {@code timeout} is expressed
     * @param <R>     The type of result expected from command execution
     * @return the result of command execution, or {@code null} if the thread was interrupted while waiting for
     *         the command to execute
     *
     * @throws CommandExecutionException when an exception occurred while processing the command
     */
    <R> R sendAndWait(Object command, long timeout, TimeUnit unit);

    /**
     * Sends the given {@code command} and returns immediately, without waiting for the command to execute. The
     * caller will therefore not receive any feedback on the command's execution.
     * <p/>
     * The given {@code command} is wrapped as the payload of the CommandMessage that is eventually posted on the
     * Command Bus, unless Command already implements {@link Message}. In that case, a
     * CommandMessage is constructed from that message's payload and MetaData.
     *
     * @param command The command to dispatch
     * @return a {@link CompletableFuture} which is resolved when the command is executed
     */
    <R> CompletableFuture<R> send(Object command);
}
