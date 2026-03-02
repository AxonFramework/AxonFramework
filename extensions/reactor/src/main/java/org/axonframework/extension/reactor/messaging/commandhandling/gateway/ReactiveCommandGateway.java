/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.extension.reactor.messaging.commandhandling.gateway;

import jakarta.annotation.Nonnull;
import org.axonframework.extension.reactor.messaging.core.ReactiveMessageDispatchInterceptor;
import org.axonframework.messaging.commandhandling.CommandMessage;
import reactor.core.publisher.Mono;

/**
 * Reactive command gateway that runs dispatch interceptors inside the Reactor subscription.
 * <p>
 * Use this instead of {@link org.axonframework.messaging.commandhandling.gateway.CommandGateway} when interceptors
 * need access to Reactor context (e.g., {@code ReactiveSecurityContextHolder}).
 * <p>
 * The interceptor chain is built and executed within the Reactor subscription, ensuring that the Reactor
 * {@link reactor.util.context.Context} is available throughout the entire dispatch pipeline.
 *
 * @author Theo Emanuelsson
 * @since 5.1.0
 * @see org.axonframework.messaging.commandhandling.gateway.CommandGateway
 * @see ReactiveMessageDispatchInterceptor
 */
public interface ReactiveCommandGateway {

    /**
     * Sends the given {@code command} and returns a {@link Mono} with the typed result.
     *
     * @param command    The command payload to send.
     * @param resultType The expected result type.
     * @param <R>        The result type.
     * @return A {@link Mono} completing with the command result.
     */
    @Nonnull
    <R> Mono<R> send(@Nonnull Object command, @Nonnull Class<R> resultType);

    /**
     * Sends the given {@code command} and returns a {@link Mono} that completes when the command is handled.
     *
     * @param command The command payload to send.
     * @return A {@link Mono} completing when the command is handled.
     */
    @Nonnull
    Mono<Void> send(@Nonnull Object command);

    /**
     * Registers a {@link ReactiveMessageDispatchInterceptor} that will be applied to all commands sent through this
     * gateway.
     *
     * @param interceptor The interceptor to register.
     */
    void registerDispatchInterceptor(
            @Nonnull ReactiveMessageDispatchInterceptor<CommandMessage> interceptor
    );
}
