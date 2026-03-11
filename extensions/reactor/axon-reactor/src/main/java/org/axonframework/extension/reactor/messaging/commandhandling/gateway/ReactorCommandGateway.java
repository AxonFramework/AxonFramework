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

import org.jspecify.annotations.Nullable;
import org.axonframework.extension.reactor.messaging.core.ReactorMessageDispatchInterceptor;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import reactor.core.publisher.Mono;

/**
 * Reactor command gateway that runs dispatch interceptors inside the Reactor subscription.
 * <p>
 * Use this instead of {@link org.axonframework.messaging.commandhandling.gateway.CommandGateway} when interceptors
 * need access to Reactor context (e.g., {@code ReactiveSecurityContextHolder}).
 * <p>
 * The interceptor chain is built and executed within the Reactor subscription, ensuring that the Reactor
 * {@link reactor.util.context.Context} is available throughout the entire dispatch pipeline.
 *
 * @author Milan Savic
 * @author Theo Emanuelsson
 * @since 4.4.2
 * @see org.axonframework.messaging.commandhandling.gateway.CommandGateway
 * @see ReactorMessageDispatchInterceptor
 */
public interface ReactorCommandGateway {

    /**
     * Sends the given {@code command} in the provided {@code context} (if available) and returns a {@link Mono} with
     * the typed result.
     *
     * @param command    the command payload to send
     * @param resultType the expected result type
     * @param context    the processing context, if any, to dispatch the given {@code command} in
     * @param <R>        the result type
     * @return a {@link Mono} completing with the command result
     */
    <R> Mono<R> send(Object command, Class<R> resultType, @Nullable ProcessingContext context);

    /**
     * Sends the given {@code command} and returns a {@link Mono} with the typed result.
     *
     * @param command    the command payload to send
     * @param resultType the expected result type
     * @param <R>        the result type
     * @return a {@link Mono} completing with the command result
     * @see #send(Object, Class, ProcessingContext)
     */
    default <R> Mono<R> send(Object command, Class<R> resultType) {
        return send(command, resultType, null);
    }

    /**
     * Sends the given {@code command} in the provided {@code context} (if available) and returns a {@link Mono} that
     * completes when the command is handled.
     *
     * @param command the command payload to send
     * @param context the processing context, if any, to dispatch the given {@code command} in
     * @return a {@link Mono} completing when the command is handled
     */
    Mono<Void> send(Object command, @Nullable ProcessingContext context);

    /**
     * Sends the given {@code command} and returns a {@link Mono} that completes when the command is handled.
     *
     * @param command the command payload to send
     * @return a {@link Mono} completing when the command is handled
     * @see #send(Object, ProcessingContext)
     */
    default Mono<Void> send(Object command) {
        return send(command, (ProcessingContext) null);
    }

}
