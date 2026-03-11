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

import org.jspecify.annotations.Nullable;
import org.axonframework.extension.reactor.messaging.core.ReactorMessageDispatchInterceptor;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Reactor query gateway that runs dispatch interceptors inside the Reactor subscription.
 * <p>
 * Use this instead of {@link org.axonframework.messaging.queryhandling.gateway.QueryGateway} when interceptors need
 * access to Reactor context (e.g., {@code ReactiveSecurityContextHolder}).
 * <p>
 * The interceptor chain is built and executed within the Reactor subscription, ensuring that the Reactor
 * {@link reactor.util.context.Context} is available throughout the entire dispatch pipeline.
 *
 * @author Milan Savic
 * @author Theo Emanuelsson
 * @since 4.4.2
 * @see org.axonframework.messaging.queryhandling.gateway.QueryGateway
 * @see ReactorMessageDispatchInterceptor
 */
public interface ReactorQueryGateway {

    /**
     * Sends the given {@code query} in the provided {@code context} (if available) and returns a {@link Mono} with a
     * single typed result.
     *
     * @param query        the query payload to send
     * @param responseType the expected response type
     * @param context      the processing context, if any, to dispatch the given {@code query} in
     * @param <R>          the response type
     * @return a {@link Mono} completing with the query result
     */
    <R> Mono<R> query(Object query, Class<R> responseType, @Nullable ProcessingContext context);

    /**
     * Sends the given {@code query} and returns a {@link Mono} with a single typed result.
     *
     * @param query        the query payload to send
     * @param responseType the expected response type
     * @param <R>          the response type
     * @return a {@link Mono} completing with the query result
     * @see #query(Object, Class, ProcessingContext)
     */
    default <R> Mono<R> query(Object query, Class<R> responseType) {
        return query(query, responseType, null);
    }

    /**
     * Sends the given {@code query} in the provided {@code context} (if available) and returns a {@link Mono} with a
     * list of typed results.
     *
     * @param query        the query payload to send
     * @param responseType the expected element type
     * @param context      the processing context, if any, to dispatch the given {@code query} in
     * @param <R>          the element type
     * @return a {@link Mono} completing with a list of query results
     */
    <R> Mono<List<R>> queryMany(Object query, Class<R> responseType,
                                @Nullable ProcessingContext context);

    /**
     * Sends the given {@code query} and returns a {@link Mono} with a list of typed results.
     *
     * @param query        the query payload to send
     * @param responseType the expected element type
     * @param <R>          the element type
     * @return a {@link Mono} completing with a list of query results
     * @see #queryMany(Object, Class, ProcessingContext)
     */
    default <R> Mono<List<R>> queryMany(Object query, Class<R> responseType) {
        return queryMany(query, responseType, null);
    }

    /**
     * Sends the given {@code query} in the provided {@code context} (if available) as a streaming query, returning
     * results as a {@link Flux}.
     *
     * @param query        the query payload to send
     * @param responseType the expected element type
     * @param context      the processing context, if any, to dispatch the given {@code query} in
     * @param <R>          the element type
     * @return a {@link Flux} streaming query results
     */
    <R> Flux<R> streamingQuery(Object query, Class<R> responseType,
                               @Nullable ProcessingContext context);

    /**
     * Sends the given {@code query} as a streaming query, returning results as a {@link Flux}.
     *
     * @param query        the query payload to send
     * @param responseType the expected element type
     * @param <R>          the element type
     * @return a {@link Flux} streaming query results
     * @see #streamingQuery(Object, Class, ProcessingContext)
     */
    default <R> Flux<R> streamingQuery(Object query, Class<R> responseType) {
        return streamingQuery(query, responseType, null);
    }

    /**
     * Sends the given {@code query} in the provided {@code context} (if available) as a subscription query, combining
     * the initial result and subsequent updates as a {@link Flux}.
     *
     * @param query        the query payload to send
     * @param responseType the response type for both initial result and updates
     * @param context      the processing context, if any, to dispatch the given {@code query} in
     * @param <R>          the response type
     * @return a {@link Flux} streaming the initial result followed by updates
     */
    <R> Flux<R> subscriptionQuery(Object query, Class<R> responseType,
                                  @Nullable ProcessingContext context);

    /**
     * Sends the given {@code query} as a subscription query, combining the initial result and subsequent updates as a
     * {@link Flux}.
     *
     * @param query        the query payload to send
     * @param responseType the response type for both initial result and updates
     * @param <R>          the response type
     * @return a {@link Flux} streaming the initial result followed by updates
     * @see #subscriptionQuery(Object, Class, ProcessingContext)
     */
    default <R> Flux<R> subscriptionQuery(Object query, Class<R> responseType) {
        return subscriptionQuery(query, responseType, null);
    }

}
