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

package org.axonframework.queryhandling;

import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.concurrent.Queues;

import java.util.concurrent.TimeUnit;

/**
 * Variation of {@link QueryGateway}. Provides support for reactive return types such as {@link Mono} and {@link Flux}
 * from Project Reactor.
 *
 * @author Milan Savic
 * @since 4.4
 */
public interface ReactiveQueryGateway {

    /**
     * Sends the given {@code query} over the {@link QueryBus}, expecting a response with the given {@code responseType}
     * from a single source. The query name will be derived from the provided {@code query}. Execution may be
     * asynchronous, depending on the {@code QueryBus} implementation.
     * <p><b>Do note that the {@code query} will not be dispatched until there is a subscription to the resulting {@link
     * Mono}</b></p>
     *
     * @param query        The {@code query} to be sent
     * @param responseType A {@link Class} describing the desired response type
     * @param <R>          The response class contained in the given {@code responseType}
     * @param <Q>          The query class
     * @return A {@link Mono} containing the query result as dictated by the given {@code responseType}
     */
    default <R, Q> Mono<R> query(Q query, Class<R> responseType) {
        return query(query.getClass().getName(), query, responseType);
    }

    /**
     * Sends the given {@code query} over the {@link QueryBus}, expecting a response with the given {@code responseType}
     * from a single source. Execution may be asynchronous, depending on the {@code QueryBus} implementation.
     * <p><b>Do note that the {@code query} will not be dispatched until there is a subscription to the resulting {@link
     * Mono}</b></p>
     *
     * @param queryName    A {@link String} describing the query to be executed
     * @param query        The {@code query} to be sent
     * @param responseType The {@link ResponseType} used for this query
     * @param <R>          The response class contained in the given {@code responseType}
     * @param <Q>          The query class
     * @return A {@link Mono} containing the query result as dictated by the given {@code responseType}
     */
    default <R, Q> Mono<R> query(String queryName, Q query, Class<R> responseType) {
        return query(queryName, query, ResponseTypes.instanceOf(responseType));
    }

    /**
     * Sends the given {@code query} over the {@link QueryBus}, expecting a response in the form of {@code responseType}
     * from a single source. The query name will be derived from the provided {@code query}. Execution may be
     * asynchronous, depending on the {@code QueryBus} implementation.
     * <p><b>Do note that the {@code query} will not be dispatched until there is a subscription to the resulting {@link
     * Mono}</b></p>
     *
     * @param query        The {@code query} to be sent
     * @param responseType The {@link ResponseType} used for this query
     * @param <R>          The response class contained in the given {@code responseType}
     * @param <Q>          The query class
     * @return A {@link Mono} containing the query result as dictated by the given {@code responseType}
     */
    default <R, Q> Mono<R> query(Q query, ResponseType<R> responseType) {
        return query(query.getClass().getName(), query, responseType);
    }

    /**
     * Sends the given {@code query} over the {@link QueryBus}, expecting a response in the form of {@code responseType}
     * from a single source. Execution may be asynchronous, depending on the {@code QueryBus} implementation.
     * <p><b>Do note that the {@code query} will not be dispatched until there is a subscription to the resulting {@link
     * Mono}</b></p>
     *
     * @param queryName    A {@link String} describing the query to be executed
     * @param query        The {@code query} to be sent
     * @param responseType The {@link ResponseType} used for this query
     * @param <R>          The response class contained in the given {@code responseType}
     * @param <Q>          The query class
     * @return A {@link Mono} containing the query result as dictated by the given {@code responseType}
     */
    default <R, Q> Mono<R> query(String queryName, Q query, ResponseType<R> responseType) {
        return query(queryName, Mono.just(query), responseType);
    }

    /**
     * Sends the given {@code query} over the {@link QueryBus}, expecting a response in the form of {@code responseType}
     * from a single source. Execution may be asynchronous, depending on the {@code QueryBus} implementation.
     * <p><b>Do note that the {@code query} will not be dispatched until there is a subscription to the resulting {@link
     * Mono}</b></p>
     *
     * @param queryName    A {@link String} describing the query to be executed
     * @param query        a {@link Mono} which is resolved once the caller subscribes to the query result
     * @param responseType The {@link ResponseType} used for this query
     * @param <R>          The response class contained in the given {@code responseType}
     * @param <Q>          The query class
     * @return A {@link Mono} containing the query result as dictated by the given {@code responseType}
     */
    <R, Q> Mono<R> query(String queryName, Mono<Q> query, ResponseType<R> responseType);

    /**
     * Sends the given {@code query} over the {@link QueryBus}, expecting a response in the form of {@code responseType}
     * from several sources. The returned {@link Flux} is completed when a {@code timeout} occurs or when all possible results are received. The
     * query name will be derived from the provided {@code query}. Execution may be asynchronous, depending on the
     * {@code QueryBus} implementation.
     * <p><b>Do note that the {@code query} will not be dispatched until there is a subscription to the resulting {@link
     * Mono}</b></p>
     * <b>Note</b>: Any {@code null} results will be filtered out by the {@link ReactiveQueryGateway}. If you require the {@code null}
     * to be returned, we suggest using {@code QueryBus} instead.
     *
     * @param query        The {@code query} to be sent
     * @param responseType The {@link ResponseType} used for this query
     * @param timeout      A timeout of {@code long} for the query
     * @param timeUnit     The selected {@link TimeUnit} for the given {@code timeout}
     * @param <R>          The response class contained in the given {@code responseType}
     * @param <Q>          The query class
     * @return A {@link Flux} containing the query results as dictated by the given {@code responseType}     
     */
    default <R, Q> Flux<R> scatterGather(Q query, ResponseType<R> responseType, long timeout, TimeUnit timeUnit) {
        return scatterGather(query.getClass().getName(), query, responseType, timeout, timeUnit);
    }

    /**
     * Sends given {@code query} over the {@link QueryBus}, expecting a response in the form of {@code responseType}
     * from several sources. The flux is completed when a {@code timeout} occurs or when all results are received.
     * Execution may be asynchronous, depending on the QueryBus implementation.
     * <p><b>Do note that query will not be dispatched until there is a subscription to the resulting {@link
     * Mono}</b></p>
     * <b>Note</b>: Any {@code null} results will be filtered out by the QueryGateway. If you require the {@code null}
     * to be returned, we suggest using {@link QueryBus} instead.
     *
     * @param queryName    A {@link String} describing the query to be executed
     * @param query        The {@code query} to be sent
     * @param responseType The {@link ResponseType} used for this query
     * @param timeout      A timeout of {@code long} for the query
     * @param timeUnit     The selected {@link TimeUnit} for the given {@code timeout}
     * @param <R>          The response class contained in the given {@code responseType}
     * @param <Q>          The query class
     * @return A flux of results.
     */
    default <R, Q> Flux<R> scatterGather(String queryName, Q query, ResponseType<R> responseType, long timeout,
                                         TimeUnit timeUnit) {
        return scatterGather(queryName, Mono.just(query), responseType, timeout, timeUnit);
    }

    /**
     * Sends given {@code query} over the {@link QueryBus}, expecting a response in the form of {@code responseType}
     * from several sources. The flux is completed when a {@code timeout} occurs or when all results are received.
     * Execution may be asynchronous, depending on the QueryBus implementation.
     * <p><b>Do note that query will not be dispatched until there is a subscription to the resulting {@link
     * Mono}</b></p>
     * <b>Note</b>: Any {@code null} results will be filtered out by the QueryGateway. If you require the {@code null}
     * to be returned, we suggest using {@link QueryBus} instead.
     *
     * @param queryName    A {@link String} describing the query to be executed
     * @param query        a {@link Mono} which is resolved once the caller subscribes to the query result
     * @param responseType The {@link ResponseType} used for this query
     * @param timeout      A timeout of {@code long} for the query
     * @param timeUnit     The selected {@link TimeUnit} for the given {@code timeout}
     * @param <R>          The response class contained in the given {@code responseType}
     * @param <Q>          The query class
     * @return A flux of results.
     */
    <R, Q> Flux<R> scatterGather(String queryName, Mono<Q> query, ResponseType<R> responseType, long timeout,
                                 TimeUnit timeUnit);

    /**
     * Sends given {@code query} over the {@link QueryBus} and returns result containing initial response and
     * incremental updates (received at the moment the query is sent, until it is cancelled by the caller or closed by
     * the emitting side).
     * <p><b>Do note that query will not be dispatched until there is a subscription to the resulting {@link
     * Mono}</b></p>
     * <p>
     * <b>Note</b>: Any {@code null} results, on the initial result or the updates, will be filtered out by the
     * QueryGateway. If you require the {@code null} to be returned for the initial and update results, we suggest using
     * the {@link QueryBus} instead.
     *
     * @param query               The {@code query} to be sent
     * @param initialResponseType The initial response type used for this query
     * @param updateResponseType  The update response type used for this query
     * @param <Q>                 The type of the query
     * @param <I>                 The type of the initial response
     * @param <U>                 The type of the incremental update
     * @return registration which can be used to cancel receiving updates
     *
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage)
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage, SubscriptionQueryBackpressure, int)
     */
    default <Q, I, U> Mono<SubscriptionQueryResult<I, U>> subscriptionQuery(Q query, Class<I> initialResponseType,
                                                                            Class<U> updateResponseType) {
        return subscriptionQuery(query.getClass().getName(),
                                 query,
                                 initialResponseType,
                                 updateResponseType);
    }

    /**
     * Sends given {@code query} over the {@link QueryBus} and returns result containing initial response and
     * incremental updates (received at the moment the query is sent, until it is cancelled by the caller or closed by
     * the emitting side).
     * <p><b>Do note that query will not be dispatched until there is a subscription to the resulting {@link
     * Mono}</b></p>
     * <p>
     * <b>Note</b>: Any {@code null} results, on the initial result or the updates, will be filtered out by the
     * QueryGateway. If you require the {@code null} to be returned for the initial and update results, we suggest using
     * the {@link QueryBus} instead.
     *
     * @param queryName           A {@link String} describing query to be executed
     * @param query               The {@code query} to be sent
     * @param initialResponseType The initial response type used for this query
     * @param updateResponseType  The update response type used for this query
     * @param <Q>                 The type of the query
     * @param <I>                 The type of the initial response
     * @param <U>                 The type of the incremental update
     * @return registration which can be used to cancel receiving updates
     *
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage)
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage, SubscriptionQueryBackpressure, int)
     */
    default <Q, I, U> Mono<SubscriptionQueryResult<I, U>> subscriptionQuery(String queryName, Q query,
                                                                            Class<I> initialResponseType,
                                                                            Class<U> updateResponseType) {
        return subscriptionQuery(queryName,
                                 query,
                                 ResponseTypes.instanceOf(initialResponseType),
                                 ResponseTypes.instanceOf(updateResponseType),
                                 SubscriptionQueryBackpressure.defaultBackpressure());
    }

    /**
     * Sends given {@code query} over the {@link QueryBus} and returns result containing initial response and
     * incremental updates (received at the moment the query is sent, until it is cancelled by the caller or closed by
     * the emitting side).
     * <p><b>Do note that query will not be dispatched until there is a subscription to the resulting {@link
     * Mono}</b></p>
     * <p>
     * <b>Note</b>: Any {@code null} results, on the initial result or the updates, will be filtered out by the
     * QueryGateway. If you require the {@code null} to be returned for the initial and update results, we suggest using
     * the {@link QueryBus} instead.
     *
     * @param query               The {@code query} to be sent
     * @param initialResponseType The initial response type used for this query
     * @param updateResponseType  The update response type used for this query
     * @param <Q>                 The type of the query
     * @param <I>                 The type of the initial response
     * @param <U>                 The type of the incremental update
     * @return registration which can be used to cancel receiving updates
     *
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage)
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage, SubscriptionQueryBackpressure, int)
     */
    default <Q, I, U> Mono<SubscriptionQueryResult<I, U>> subscriptionQuery(Q query,
                                                                            ResponseType<I> initialResponseType,
                                                                            ResponseType<U> updateResponseType) {
        return subscriptionQuery(query.getClass().getName(),
                                 query,
                                 initialResponseType,
                                 updateResponseType,
                                 SubscriptionQueryBackpressure.defaultBackpressure());
    }

    /**
     * Sends given {@code query} over the {@link QueryBus} and returns result containing initial response and
     * incremental updates (received at the moment the query is sent, until it is cancelled by the caller or closed by
     * the emitting side).
     * <p><b>Do note that query will not be dispatched until there is a subscription to the resulting {@link
     * Mono}</b></p>
     * <p>
     * <b>Note</b>: Any {@code null} results, on the initial result or the updates, will be filtered out by the
     * QueryGateway. If you require the {@code null} to be returned for the initial and update results, we suggest using
     * the {@link QueryBus} instead.
     *
     * @param queryName           A {@link String} describing query to be executed
     * @param query               The {@code query} to be sent
     * @param initialResponseType The initial response type used for this query
     * @param updateResponseType  The update response type used for this query
     * @param backpressure        The backpressure mechanism to deal with producing of incremental updates
     * @param <Q>                 The type of the query
     * @param <I>                 The type of the initial response
     * @param <U>                 The type of the incremental update
     * @return registration which can be used to cancel receiving updates
     *
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage)
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage, SubscriptionQueryBackpressure, int)
     */
    default <Q, I, U> Mono<SubscriptionQueryResult<I, U>> subscriptionQuery(String queryName, Q query,
                                                                            ResponseType<I> initialResponseType,
                                                                            ResponseType<U> updateResponseType,
                                                                            SubscriptionQueryBackpressure backpressure) {
        return subscriptionQuery(queryName,
                                 query,
                                 initialResponseType,
                                 updateResponseType,
                                 backpressure,
                                 Queues.SMALL_BUFFER_SIZE);
    }

    /**
     * Sends given {@code query} over the {@link QueryBus} and returns result containing initial response and
     * incremental updates (received at the moment the query is sent, until it is cancelled by the caller or closed by
     * the emitting side).
     * <p><b>Do note that query will not be dispatched until there is a subscription to the resulting {@link
     * Mono}</b></p>
     * <p>
     * <b>Note</b>: Any {@code null} results, on the initial result or the updates, will be filtered out by the
     * QueryGateway. If you require the {@code null} to be returned for the initial and update results, we suggest using
     * the {@link QueryBus} instead.
     *
     * @param queryName           A {@link String} describing query to be executed
     * @param query               The {@code query} to be sent
     * @param initialResponseType The initial response type used for this query
     * @param updateResponseType  The update response type used for this query
     * @param backpressure        The backpressure mechanism to deal with producing of incremental updates
     * @param updateBufferSize    The size of buffer which accumulates updates before subscription to the {@code} flux
     *                            is made
     * @param <Q>                 The type of the query
     * @param <I>                 The type of the initial response
     * @param <U>                 The type of the incremental update
     * @return registration which can be used to cancel receiving updates
     *
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage)
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage, SubscriptionQueryBackpressure, int)
     */
    default <Q, I, U> Mono<SubscriptionQueryResult<I, U>> subscriptionQuery(String queryName, Q query,
                                                                            ResponseType<I> initialResponseType,
                                                                            ResponseType<U> updateResponseType,
                                                                            SubscriptionQueryBackpressure backpressure,
                                                                            int updateBufferSize) {
        return subscriptionQuery(queryName,
                                 Mono.just(query),
                                 initialResponseType,
                                 updateResponseType,
                                 backpressure,
                                 updateBufferSize);
    }

    /**
     * Sends given {@code query} over the {@link QueryBus} and returns result containing initial response and
     * incremental updates (received at the moment the query is sent, until it is cancelled by the caller or closed by
     * the emitting side).
     * <p><b>Do note that query will not be dispatched until there is a subscription to the resulting {@link
     * Mono}</b></p>
     * <p>
     * <b>Note</b>: Any {@code null} results, on the initial result or the updates, will be filtered out by the
     * QueryGateway. If you require the {@code null} to be returned for the initial and update results, we suggest using
     * the {@link QueryBus} instead.
     *
     * @param queryName           A {@link String} describing query to be executed
     * @param query               a {@link Mono} which is resolved once the caller subscribes to the query result
     * @param initialResponseType The initial response type used for this query
     * @param updateResponseType  The update response type used for this query
     * @param backpressure        The backpressure mechanism to deal with producing of incremental updates
     * @param updateBufferSize    The size of buffer which accumulates updates before subscription to the {@code} flux
     *                            is made
     * @param <Q>                 The type of the query
     * @param <I>                 The type of the initial response
     * @param <U>                 The type of the incremental update
     * @return registration which can be used to cancel receiving updates
     *
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage)
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage, SubscriptionQueryBackpressure, int)
     */
    <Q, I, U> Mono<SubscriptionQueryResult<I, U>> subscriptionQuery(String queryName, Mono<Q> query,
                                                                    ResponseType<I> initialResponseType,
                                                                    ResponseType<U> updateResponseType,
                                                                    SubscriptionQueryBackpressure backpressure,
                                                                    int updateBufferSize);
}
