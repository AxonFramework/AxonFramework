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
import org.axonframework.common.Registration;
import org.axonframework.messaging.ReactiveMessageDispatchInterceptor;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Default implementation of {@link ReactiveCommandGateway}.
 *
 * @author Milan Savic
 * @since 4.4
 */
public class DefaultReactiveCommandGateway implements ReactiveCommandGateway {

    private final List<ReactiveMessageDispatchInterceptor<CommandMessage<?>>> dispatchInterceptors = new CopyOnWriteArrayList<>();

    private final CommandBus commandBus;

    /**
     * Creates an instance of {@link DefaultReactiveCommandGateway}.
     *
     * @param commandBus used for command dispatching
     */
    public DefaultReactiveCommandGateway(CommandBus commandBus) {
        this.commandBus = commandBus;
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
}
