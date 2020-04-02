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
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.ReactiveMessageDispatchInterceptorSupport;
import reactor.core.publisher.Mono;

/**
 * Variation of {@link CommandGateway}. Provides support for reactive return type such as {@link Mono} from Project
 * Reactor.
 *
 * @author Milan Savic
 * @since 4.4
 */
public interface ReactiveCommandGateway extends ReactiveMessageDispatchInterceptorSupport<CommandMessage<?>> {

    /**
     * Sends the given {@code command} once the caller subscribes to the command result. Returns immediately.
     * <p/>
     * The given {@code command} is wrapped as the payload of a {@link CommandMessage} that is eventually posted on the
     * {@link CommandBus}, unless the {@code command} already implements {@link Message}. In that case, a
     * {@code CommandMessage} is constructed from that message's payload and {@link MetaData}.
     *
     * @param command the command to dispatch
     * @param <R>     the type of the command result
     * @return a {@link Mono} which is resolved when the command is executed
     */
    default <R> Mono<R> send(Object command) {
        return send(Mono.just(command));
    }

    /**
     * Sends the given {@code command} once the caller subscribes to the command result. Returns immediately.
     *
     * @param command a {@link Mono} which is resolved once the caller subscribes to the command result
     * @param <R>     the type of the command result
     * @return a {@link Mono} which is resolved when the command is executed
     * @see #send(Object)
     */
    <R> Mono<R> send(Mono<Object> command);
}
