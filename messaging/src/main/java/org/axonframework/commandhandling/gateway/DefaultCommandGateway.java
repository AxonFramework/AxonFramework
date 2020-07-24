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

import org.axonframework.commandhandling.*;
import org.axonframework.commandhandling.callbacks.FailureLoggingCallback;
import org.axonframework.commandhandling.callbacks.FutureCallback;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
     * Instantiate a {@link DefaultCommandGateway} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link CommandBus} is not {@code null} and will throw an {@link AxonConfigurationException}
     * if this is the case.
     *
     * @param builder the {@link DefaultCommandGateway.Builder} used to instantiate a {@link DefaultCommandGateway}
     *                instance
     */
    protected DefaultCommandGateway(Builder builder) {
        super(builder);
    }

    /**
     * Instantiate a Builder to be able to create a {@link DefaultCommandGateway}.
     * <p>
     * The {@code dispatchInterceptors} are defaulted to an empty list.
     * The {@link CommandBus} is a <b>hard requirement</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link DefaultCommandGateway}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public <C, R> void send(C command, CommandCallback<? super C, ? super R> callback) {
        super.send(command, callback);
    }

    /**
     * Sends the given {@code command} and waits for its execution to complete, or until the waiting thread is
     * interrupted.
     *
     * @param command The command to send
     * @param <R>     The expected type of return value
     * @return The result of the command handler execution
     * @throws org.axonframework.commandhandling.CommandExecutionException when command execution threw a checked
     *                                                                     exception
     */
    @Override
    @SuppressWarnings("unchecked")
    public <R> R sendAndWait(Object command) {
        FutureCallback<Object, R> futureCallback = new FutureCallback<>();
        send(command, futureCallback);
        CommandResultMessage<? extends R> commandResultMessage = futureCallback.getResult();
        if (commandResultMessage.isExceptional()) {
            throw asRuntime(commandResultMessage.exceptionResult());
        }
        return commandResultMessage.getPayload();
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
     * @throws org.axonframework.commandhandling.CommandExecutionException when command execution threw a checked
     *                                                                     exception
     */
    @Override
    @SuppressWarnings("unchecked")
    public <R> R sendAndWait(Object command, long timeout, TimeUnit unit) {
        FutureCallback<Object, R> futureCallback = new FutureCallback<>();
        send(command, futureCallback);
        CommandResultMessage<? extends R> commandResultMessage = futureCallback.getResult(timeout, unit);
        if (commandResultMessage.isExceptional()) {
            throw asRuntime(commandResultMessage.exceptionResult());
        }
        return commandResultMessage.getPayload();
    }

    @Override
    public <R> CompletableFuture<R> send(Object command) {
        FutureCallback<Object, R> callback = new FutureCallback<>();
        send(command, new FailureLoggingCallback<>(logger, callback));
        CompletableFuture<R> result = new CompletableFuture<>();
        callback.exceptionally(GenericCommandResultMessage::asCommandResultMessage)
                .thenAccept(r -> {
                    try {
                        if (r.isExceptional()) {
                            result.completeExceptionally(r.exceptionResult());
                        } else {
                            result.complete(r.getPayload());
                        }
                    } catch (Exception e) {
                        result.completeExceptionally(e);
                    }
                });
        return result;
    }

    @Override
    public Registration registerDispatchInterceptor(
            MessageDispatchInterceptor<? super CommandMessage<?>> dispatchInterceptor) {
        return super.registerDispatchInterceptor(dispatchInterceptor);
    }

    private RuntimeException asRuntime(Throwable e) {
        if (e instanceof Error) {
            throw (Error) e;
        } else if (e instanceof RuntimeException) {
            return (RuntimeException) e;
        } else {
            return new CommandExecutionException("An exception occurred while executing a command", e);
        }
    }

    /**
     * Builder class to instantiate a {@link DefaultCommandGateway}.
     * <p>
     * The {@code dispatchInterceptors} are defaulted to an empty list.
     * The {@link CommandBus} is a <b>hard requirements</b> and as such should be provided.
     */
    public static class Builder extends AbstractCommandGateway.Builder {

        @Override
        public Builder commandBus(CommandBus commandBus) {
            super.commandBus(commandBus);
            return this;
        }

        @Override
        public Builder retryScheduler(RetryScheduler retryScheduler) {
            super.retryScheduler(retryScheduler);
            return this;
        }

        @Override
        public Builder dispatchInterceptors(
                MessageDispatchInterceptor<? super CommandMessage<?>>... dispatchInterceptors) {
            super.dispatchInterceptors(dispatchInterceptors);
            return this;
        }

        @Override
        public Builder dispatchInterceptors(
                List<MessageDispatchInterceptor<? super CommandMessage<?>>> dispatchInterceptors) {
            super.dispatchInterceptors(dispatchInterceptors);
            return this;
        }

        /**
         * Initializes a {@link DefaultCommandGateway} as specified through this Builder.
         *
         * @return a {@link DefaultCommandGateway} as specified through this Builder
         */
        public DefaultCommandGateway build() {
            return new DefaultCommandGateway(this);
        }
    }
}
