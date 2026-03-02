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

package org.axonframework.extension.reactor.messaging.queryhandling.gateway;

import jakarta.annotation.Nonnull;
import org.axonframework.extension.reactor.messaging.core.ReactiveMessageDispatchInterceptor;
import org.axonframework.messaging.queryhandling.QueryMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Reactive query gateway that runs dispatch interceptors inside the Reactor subscription.
 * <p>
 * Use this instead of {@link org.axonframework.messaging.queryhandling.gateway.QueryGateway} when interceptors need
 * access to Reactor context (e.g., {@code ReactiveSecurityContextHolder}).
 * <p>
 * The interceptor chain is built and executed within the Reactor subscription, ensuring that the Reactor
 * {@link reactor.util.context.Context} is available throughout the entire dispatch pipeline.
 *
 * @author Theo Emanuelsson
 * @since 5.1.0
 * @see org.axonframework.messaging.queryhandling.gateway.QueryGateway
 * @see ReactiveMessageDispatchInterceptor
 */
public interface ReactiveQueryGateway {

    /**
     * Sends the given {@code query} and returns a {@link Mono} with a single typed result.
     *
     * @param query        The query payload to send.
     * @param responseType The expected response type.
     * @param <R>          The response type.
     * @return A {@link Mono} completing with the query result.
     */
    @Nonnull
    <R> Mono<R> query(@Nonnull Object query, @Nonnull Class<R> responseType);

    /**
     * Sends the given {@code query} and returns a {@link Mono} with a list of typed results.
     *
     * @param query        The query payload to send.
     * @param responseType The expected element type.
     * @param <R>          The element type.
     * @return A {@link Mono} completing with a list of query results.
     */
    @Nonnull
    <R> Mono<List<R>> queryMany(@Nonnull Object query, @Nonnull Class<R> responseType);

    /**
     * Sends the given {@code query} as a streaming query, returning results as a {@link Flux}.
     *
     * @param query        The query payload to send.
     * @param responseType The expected element type.
     * @param <R>          The element type.
     * @return A {@link Flux} streaming query results.
     */
    @Nonnull
    <R> Flux<R> streamingQuery(@Nonnull Object query, @Nonnull Class<R> responseType);

    /**
     * Sends the given {@code query} as a subscription query, combining the initial result and subsequent updates as a
     * {@link Flux}.
     *
     * @param query        The query payload to send.
     * @param responseType The response type for both initial result and updates.
     * @param <R>          The response type.
     * @return A {@link Flux} streaming the initial result followed by updates.
     */
    @Nonnull
    <R> Flux<R> subscriptionQuery(@Nonnull Object query, @Nonnull Class<R> responseType);

    /**
     * Registers a {@link ReactiveMessageDispatchInterceptor} that will be applied to all queries sent through this
     * gateway.
     *
     * @param interceptor The interceptor to register.
     */
    void registerDispatchInterceptor(
            @Nonnull ReactiveMessageDispatchInterceptor<QueryMessage> interceptor
    );
}
