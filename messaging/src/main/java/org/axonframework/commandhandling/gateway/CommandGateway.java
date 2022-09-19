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

package org.axonframework.commandhandling.gateway;

import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.distributed.CommandDispatchException;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptorSupport;
import org.axonframework.messaging.MetaData;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

/**
 * Interface towards the Command Handling components of an application. This interface provides a friendlier API toward
 * the {@link org.axonframework.commandhandling.CommandBus}. The CommandGateway allows for components dispatching
 * commands to wait for the result.
 *
 * @author Allard Buijze
 * @see DefaultCommandGateway
 * @since 2.0
 */
public interface CommandGateway extends MessageDispatchInterceptorSupport<CommandMessage<?>> {

    /**
     * Sends the given {@code command}, and have the result of the command's execution reported to the given {@code
     * callback}.
     * <p/>
     * The given {@code command} is wrapped as the payload of the {@link CommandMessage} that is eventually posted on
     * the {@link org.axonframework.commandhandling.CommandBus}, unless the {@code command} already implements {@link
     * Message}. In that case, a {@code CommandMessage} is constructed from that message's payload and {@link
     * org.axonframework.messaging.MetaData}.
     *
     * @param command  the command to dispatch
     * @param callback the callback to notify when the command has been processed
     * @param <R>      the type of result expected from command execution
     */
    <C, R> void send(@Nonnull C command, @Nonnull CommandCallback<? super C, ? super R> callback);

    /**
     * Sends the given {@code command} and wait for it to execute. The result of the execution is returned when
     * available. This method will block indefinitely, until a result is available, or until the Thread is interrupted.
     * When the thread is interrupted, this method returns {@code null}. If command execution resulted in an exception,
     * it is wrapped in a {@link CommandExecutionException}. If command dispatching failed,
     * {@link CommandDispatchException} is thrown instead.
     * <p/>
     * The given {@code command} is wrapped as the payload of the {@link CommandMessage} that is eventually posted on
     * the {@link org.axonframework.commandhandling.CommandBus}, unless the {@code command} already implements {@link
     * Message}. In that case, a {@code CommandMessage} is constructed from that message's payload and {@link
     * org.axonframework.messaging.MetaData}.
     * <p/>
     * Note that the interrupted flag is set back on the thread if it has been interrupted while waiting.
     *
     * @param command the command to dispatch
     * @param <R>     the type of result expected from command execution
     * @return the result of command execution, or {@code null} if the thread was interrupted while waiting for the
     * command to execute
     * @throws CommandExecutionException when an exception occurred while processing the command
     * @throws CommandDispatchException when an exception occurred while dispatching the command
     */
    <R> R sendAndWait(@Nonnull Object command);

    /**
     * Sends the given {@code command} with the given {@code metaData} and wait for it to execute. The result of the
     * execution is returned when available. This method will block indefinitely, until a result is available, or until
     * the Thread is interrupted. When the thread is interrupted, this method returns {@code null}. If command execution
     * resulted in an exception, it is wrapped in a {@link CommandExecutionException}. If command dispatching failed,
     * {@link CommandDispatchException} is thrown instead.
     * <p/>
     * The given {@code command} and {@code metaData} are wrapped as the payload of the {@link CommandMessage} that is
     * eventually posted on the {@link org.axonframework.commandhandling.CommandBus}, unless the {@code command} already
     * implements {@link Message}. In that case, a {@code CommandMessage} is constructed from that message's payload and
     * {@link org.axonframework.messaging.MetaData}. The provided {@code metaData} is attached afterwards in this case.
     * <p/>
     * Note that the interrupted flag is set back on the thread if it has been interrupted while waiting.
     *
     * @param command  the command to dispatch
     * @param metaData meta-data that must be registered with the {@code command}
     * @param <R>      the type of result expected from command execution
     * @return the result of command execution, or {@code null} if the thread was interrupted while waiting for the
     * command to execute
     * @throws CommandExecutionException when an exception occurred while processing the command
     * @throws CommandDispatchException  when an exception occurred while dispatching the command
     */
    default <R> R sendAndWait(@Nonnull Object command, @Nonnull MetaData metaData) {
        return sendAndWait(GenericCommandMessage.asCommandMessage(command).andMetaData(metaData));
    }

    /**
     * Sends the given {@code command} and wait for it to execute. The result of the execution is returned when
     * available. This method will block until a result is available, or the given {@code timeout} was reached, or until
     * the Thread is interrupted. When the timeout is reached or the thread is interrupted, this method returns {@code
     * null}. If command execution resulted in an exception, it is wrapped in a {@link CommandExecutionException}.
     * If command dispatching failed, {@link CommandDispatchException} is thrown instead.
     * <p/>
     * The given {@code command} is wrapped as the payload of the {@link CommandMessage} that is eventually posted on
     * the {@link org.axonframework.commandhandling.CommandBus}, unless the {@code command} already implements {@link
     * Message}. In that case, a {@code CommandMessage} is constructed from that message's payload and {@link
     * org.axonframework.messaging.MetaData}.
     * <p/>
     * Note that the interrupted flag is set back on the thread if it has been interrupted while waiting.
     *
     * @param command the command to dispatch
     * @param timeout the amount of time in the given {@code unit} the thread is allowed to wait for the result
     * @param unit    the unit in which {@code timeout} is expressed
     * @param <R>     the type of result expected from command execution
     * @return the result of command execution, or {@code null} if the thread was interrupted while waiting for the
     * command to execute
     * @throws CommandExecutionException when an exception occurred while processing the command
     * @throws CommandDispatchException when an exception occurred while dispatching the command
     */
    <R> R sendAndWait(@Nonnull Object command, long timeout, @Nonnull TimeUnit unit);

    /**
     * Sends the given {@code command} with the given {@code metaData} and wait for it to execute. The result of the
     * execution is returned when available. This method will block until a result is available, or the given {@code
     * timeout} was reached, or until the Thread is interrupted. When the timeout is reached or the thread is
     * interrupted, this method returns {@code null}. If command execution resulted in an exception, it is wrapped in a
     * {@link CommandExecutionException}. If command dispatching failed, {@link CommandDispatchException} is thrown
     * instead.
     * <p/>
     * The given {@code command} and {@code metaData} are wrapped as the payload of the {@link CommandMessage} that is
     * eventually posted on the {@link org.axonframework.commandhandling.CommandBus}, unless the {@code command} already
     * implements {@link Message}. In that case, a {@code CommandMessage} is constructed from that message's payload and
     * {@link org.axonframework.messaging.MetaData}. The provided {@code metaData} is attached afterwards in this case.
     * <p/>
     * Note that the interrupted flag is set back on the thread if it has been interrupted while waiting.
     *
     * @param command  the command to dispatch
     * @param metaData meta-data that must be registered with the {@code command}
     * @param timeout  the amount of time in the given {@code unit} the thread is allowed to wait for the result
     * @param unit     the unit in which {@code timeout} is expressed
     * @param <R>      the type of result expected from command execution
     * @return the result of command execution, or {@code null} if the thread was interrupted while waiting for the
     * command to execute
     * @throws CommandExecutionException when an exception occurred while processing the command
     * @throws CommandDispatchException  when an exception occurred while dispatching the command
     */
    default <R> R sendAndWait(@Nonnull Object command, @Nonnull MetaData metaData, long timeout,
                              @Nonnull TimeUnit unit) {
        return sendAndWait(GenericCommandMessage.asCommandMessage(command).andMetaData(metaData), timeout, unit);
    }

    /**
     * Sends the given {@code command} and returns a {@link CompletableFuture} immediately, without waiting for the
     * command to execute. The caller will therefore not receive any immediate feedback on the {@code command}'s
     * execution. Instead hooks <em>can</em> be added to the returned {@code CompletableFuture} to react on success or
     * failure of command execution.
     * <p/>
     * The given {@code command} is wrapped as the payload of the {@link CommandMessage} that is eventually posted on
     * the {@link org.axonframework.commandhandling.CommandBus}, unless the {@code command} already implements {@link
     * Message}. In that case, a {@code CommandMessage} is constructed from that message's payload and {@link
     * org.axonframework.messaging.MetaData}.
     *
     * @param command the command to dispatch
     * @return a {@link CompletableFuture} which will be resolved successfully or exceptionally based on the eventual
     * command execution result
     */
    <R> CompletableFuture<R> send(@Nonnull Object command);

    /**
     * Sends the given {@code command} with the given {@code metaData} and returns a {@link CompletableFuture}
     * immediately, without waiting for the command to execute. The caller will therefore not receive any immediate
     * feedback on the {@code command}'s execution. Instead, hooks <em>can</em> be added to the returned {@code
     * CompletableFuture} to react on success or failure of command execution.
     * <p/>
     * The given {@code command} and {@code metaData} are wrapped as the payload of the {@link CommandMessage} that is
     * eventually posted on the {@link org.axonframework.commandhandling.CommandBus}, unless the {@code command} already
     * implements {@link Message}. In that case, a {@code CommandMessage} is constructed from that message's payload and
     * {@link org.axonframework.messaging.MetaData}. The provided {@code metaData} is attached afterwards in this case.
     *
     * @param command  the command to dispatch
     * @param metaData meta-data that must be registered with the {@code command}
     * @return a {@link CompletableFuture} which will be resolved successfully or exceptionally based on the eventual
     * command execution result
     */
    default <R> CompletableFuture<R> send(@Nonnull Object command, @Nonnull MetaData metaData) {
        return send(GenericCommandMessage.asCommandMessage(command).andMetaData(metaData));
    }
}
