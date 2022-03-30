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

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.callbacks.LoggingCallback;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageDispatchInterceptor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nonnull;

import static java.util.Arrays.asList;
import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Abstract implementation of a CommandGateway, which handles the dispatch interceptors and retrying on failure. The
 * actual dispatching of commands is left to the subclasses.
 *
 * @author Allard Buijze
 * @since 2.2
 */
public abstract class AbstractCommandGateway {

    private final CommandBus commandBus;
    private final RetryScheduler retryScheduler;
    private final List<MessageDispatchInterceptor<? super CommandMessage<?>>> dispatchInterceptors;

    /**
     * Instantiate an {@link AbstractCommandGateway} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link CommandBus} is not {@code null} and throws an {@link AxonConfigurationException}
     * if it is.
     *
     * @param builder the {@link Builder} used to instantiate a {@link AbstractCommandGateway} instance
     */
    protected AbstractCommandGateway(Builder builder) {
        builder.validate();
        this.commandBus = builder.commandBus;
        this.retryScheduler = builder.retryScheduler;
        this.dispatchInterceptors = builder.dispatchInterceptors;
    }

    /**
     * Sends the given {@code command}, and invokes the {@code callback} when the command is processed.
     *
     * @param command  The command to dispatch
     * @param callback The callback to notify with the processing result
     * @param <R>      The type of response expected from the command
     */
    protected <C, R> void send(@Nonnull C command, @Nonnull CommandCallback<? super C, ? super R> callback) {
        CommandMessage<? extends C> commandMessage = processInterceptors(asCommandMessage(command));
        CommandCallback<? super C, ? super R> commandCallback = callback;
        if (retryScheduler != null) {
            commandCallback = new RetryingCallback<>(callback, retryScheduler, commandBus);
        }
        commandBus.dispatch(commandMessage, commandCallback);
    }

    /**
     * Dispatches a command without callback. When dispatching fails, since there is no callback, the command will
     * <em>not</em> be retried.
     *
     * @param command The command to dispatch
     */
    protected void sendAndForget(Object command) {
        if (retryScheduler == null) {
            commandBus.dispatch(processInterceptors(asCommandMessage(command)));
        } else {
            CommandMessage<?> commandMessage = asCommandMessage(command);
            send(commandMessage, LoggingCallback.INSTANCE);
        }
    }

    /**
     * Registers a command dispatch interceptor within a {@link CommandGateway}.
     *
     * @param interceptor To intercept command messages
     * @return a registration which can be used to cancel the registration of given interceptor
     */
    protected Registration registerDispatchInterceptor(
            @Nonnull MessageDispatchInterceptor<? super CommandMessage<?>> interceptor) {
        dispatchInterceptors.add(interceptor);
        return () -> dispatchInterceptors.remove(interceptor);
    }

    /**
     * Invokes all the dispatch interceptors and returns the CommandMessage instance that should be dispatched.
     *
     * @param commandMessage The incoming command message
     * @return The command message to dispatch
     */
    @SuppressWarnings("unchecked")
    protected <C> CommandMessage<? extends C> processInterceptors(CommandMessage<C> commandMessage) {
        CommandMessage<? extends C> message = commandMessage;
        for (MessageDispatchInterceptor<? super CommandMessage<?>> dispatchInterceptor : dispatchInterceptors) {
            message = (CommandMessage) dispatchInterceptor.handle(message);
        }
        return message;
    }

    /**
     * Returns the CommandBus used by this gateway. Should be used to monitoring or testing.
     *
     * @return The CommandBus used by this gateway
     */
    public CommandBus getCommandBus() {
        return commandBus;
    }

    /**
     * Abstract Builder class to instantiate {@link AbstractCommandGateway} implementations.
     * <p>
     * The {@code dispatchInterceptors} are defaulted to an empty list.
     * The {@link CommandBus} is a <b>hard requirement</b> and as such should be provided.
     */
    public abstract static class Builder {

        private CommandBus commandBus;
        private RetryScheduler retryScheduler;
        private List<MessageDispatchInterceptor<? super CommandMessage<?>>> dispatchInterceptors =
                new CopyOnWriteArrayList<>();

        /**
         * Sets the {@link CommandBus} used to dispatch commands.
         *
         * @param commandBus a {@link CommandBus} used to dispatch commands
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder commandBus(@Nonnull CommandBus commandBus) {
            assertNonNull(commandBus, "CommandBus may not be null");
            this.commandBus = commandBus;
            return this;
        }

        /**
         * Sets the {@link RetryScheduler} capable of performing retries of failed commands. May be {@code null} when to
         * prevent retries.
         *
         * @param retryScheduler a {@link RetryScheduler} capable of performing retries of failed commands
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder retryScheduler(@Nonnull RetryScheduler retryScheduler) {
            this.retryScheduler = retryScheduler;
            return this;
        }

        /**
         * Sets the {@link List} of {@link MessageDispatchInterceptor}s for {@link CommandMessage}s.
         * Are invoked when a command is being dispatched.
         *
         * @param dispatchInterceptors which are invoked when a command is being dispatched
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder dispatchInterceptors(
                MessageDispatchInterceptor<? super CommandMessage<?>>... dispatchInterceptors) {
            return dispatchInterceptors(asList(dispatchInterceptors));
        }

        /**
         * Sets the {@link List} of {@link MessageDispatchInterceptor}s for {@link CommandMessage}s.
         * Are invoked when a command is being dispatched.
         *
         * @param dispatchInterceptors which are invoked when a command is being dispatched
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder dispatchInterceptors(
                List<MessageDispatchInterceptor<? super CommandMessage<?>>> dispatchInterceptors) {
            this.dispatchInterceptors = dispatchInterceptors != null && !dispatchInterceptors.isEmpty()
                    ? new CopyOnWriteArrayList<>(dispatchInterceptors)
                    : new CopyOnWriteArrayList<>();
            return this;
        }

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() {
            assertNonNull(commandBus, "The CommandBus is a hard requirement and should be provided");
        }
    }
}
