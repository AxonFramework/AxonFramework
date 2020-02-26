/*
 * Copyright (c) 2010-2020. Axon Framework
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
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.messaging.ReactiveMessageDispatchInterceptor;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.util.Arrays.asList;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Default implementation of {@link ReactiveCommandGateway}.
 *
 * @author Milan Savic
 * @since 4.4
 */
public class DefaultReactiveCommandGateway implements ReactiveCommandGateway {

    private final List<ReactiveMessageDispatchInterceptor<CommandMessage<?>>> dispatchInterceptors;

    private final CommandBus commandBus;

    /**
     * Creates an instance of {@link DefaultReactiveCommandGateway} based on the fields contained in the {@link
     * Builder}.
     * <p>
     * Will assert that the {@link CommandBus} is not {@code null} and throws an {@link AxonConfigurationException} if
     * it is.
     * </p>
     *
     * @param builder the {@link Builder} used to instantiated a {@link DefaultReactiveCommandGateway} instance
     */
    protected DefaultReactiveCommandGateway(Builder builder) {
        builder.validate();
        this.commandBus = builder.commandBus;
        this.dispatchInterceptors = builder.dispatchInterceptors;
    }

    /**
     * Instantiate a Builder to be able to create a {@link DefaultReactiveCommandGateway}.
     * <p>
     * The {@code dispatchInterceptors} are defaulted to an empty list.
     * The {@link CommandBus} is a <b>hard requirements</b> and as such should be provided.
     * </p>
     *
     * @return a Builder to be able to create a {@link DefaultReactiveCommandGateway}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public <R> Mono<R> send(Mono<Object> command) {
        return processInterceptors(command.map(GenericCommandMessage::asCommandMessage))
                .flatMap(commandMessage -> Mono.create(
                        sink -> commandBus.dispatch(commandMessage, (CommandCallback<Object, R>) (cm, result) -> {
                            try {
                                if (result.isExceptional()) {
                                    sink.error(result.exceptionResult());
                                } else {
                                    sink.success(result.getPayload());
                                }
                            } catch (Exception e) {
                                sink.error(e);
                            }
                        })));
    }

    /**
     * Registers a {@link ReactiveMessageDispatchInterceptor} within this reactive gateway.
     *
     * @param interceptor intercepts a command message
     * @return a registration which can be used to unregister this {@code interceptor}
     */
    public Registration registerCommandDispatchInterceptor(
            ReactiveMessageDispatchInterceptor<CommandMessage<?>> interceptor) {
        dispatchInterceptors.add(interceptor);
        return () -> dispatchInterceptors.remove(interceptor);
    }

    private Mono<CommandMessage<?>> processInterceptors(Mono<CommandMessage<?>> commandMessage) {
        Mono<CommandMessage<?>> message = commandMessage;
        for (ReactiveMessageDispatchInterceptor<CommandMessage<?>> dispatchInterceptor : dispatchInterceptors) {
            try {
                message = dispatchInterceptor.intercept(message);
            } catch (Throwable t) {
                return Mono.error(t);
            }
        }
        return message;
    }

    /**
     * Builder class to instantiate {@link DefaultReactiveCommandGateway}.
     * <p>
     * The {@code dispatchInterceptors} are defaulted to an empty list.
     * The {@link CommandBus} is a <b>hard requirement</b> and as such should be provided.
     * </p>
     */
    public static class Builder {

        private CommandBus commandBus;
        private List<ReactiveMessageDispatchInterceptor<CommandMessage<?>>> dispatchInterceptors = new CopyOnWriteArrayList<>();

        /**
         * Sets the {@link CommandBus} used to dispatch commands.
         *
         * @param commandBus a {@link CommandBus} used to dispatch commands
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder commandBus(CommandBus commandBus) {
            assertNonNull(commandBus, "CommandBus may not be null");
            this.commandBus = commandBus;
            return this;
        }

        /**
         * Sets the {@link List} of {@link ReactiveMessageDispatchInterceptor}s for {@link CommandMessage}s. Are invoked
         * when a command is being dispatched.
         *
         * @param dispatchInterceptors which are invoked when a command is being dispatched
         * @return the current Builder instance, for fluent interfacing
         */
        @SafeVarargs
        public final Builder dispatchInterceptors(
                ReactiveMessageDispatchInterceptor<CommandMessage<?>>... dispatchInterceptors) {
            return dispatchInterceptors(asList(dispatchInterceptors));
        }

        /**
         * Sets the {@link List} of {@link ReactiveMessageDispatchInterceptor}s for {@link CommandMessage}s. Are invoked
         * when a command is being dispatched.
         *
         * @param dispatchInterceptors which are invoked when a command is being dispatched
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder dispatchInterceptors(
                List<ReactiveMessageDispatchInterceptor<CommandMessage<?>>> dispatchInterceptors) {
            this.dispatchInterceptors = dispatchInterceptors != null && dispatchInterceptors.isEmpty()
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

        /**
         * Initializes a {@link DefaultReactiveCommandGateway} as specified through this Builder.
         *
         * @return a {@link DefaultReactiveCommandGateway} as specified through this Builder
         */
        public DefaultReactiveCommandGateway build() {
            return new DefaultReactiveCommandGateway(this);
        }
    }
}
