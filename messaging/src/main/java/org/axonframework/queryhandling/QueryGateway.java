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
package org.axonframework.queryhandling;

import org.axonframework.messaging.MessageDispatchInterceptorSupport;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.reactivestreams.Publisher;
import reactor.util.concurrent.Queues;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static org.axonframework.queryhandling.QueryMessage.queryName;

/**
 * Interface towards the Query Handling components of an application. This interface provides a friendlier API toward
 * the query bus.
 *
 * @author Marc Gathier
 * @author Allard Buijze
 * @author Steven van Beelen
 * @author Milan Savic
 * @since 3.1
 */
public interface QueryGateway extends MessageDispatchInterceptorSupport<QueryMessage<?, ?>> {

    /**
     * Sends given {@code query} over the {@link org.axonframework.queryhandling.QueryBus}, expecting a response with
     * the given {@code responseType} from a single source. The query name will be derived from the provided {@code
     * query}. Execution may be asynchronous, depending on the QueryBus implementation.
     *
     * @param query        The {@code query} to be sent
     * @param responseType A {@link java.lang.Class} describing the desired response type
     * @param <R>          The response class contained in the given {@code responseType}
     * @param <Q>          The query class
     * @return A {@link java.util.concurrent.CompletableFuture} containing the query result as dictated by the given
     * {@code responseType}
     */
    default <R, Q> CompletableFuture<R> query(@Nonnull Q query, @Nonnull Class<R> responseType) {
        return query(queryName(query), query, responseType);
    }

    /**
     * Sends given {@code query} over the {@link org.axonframework.queryhandling.QueryBus}, expecting a response with
     * the given {@code responseType} from a single source. Execution may be asynchronous, depending on the QueryBus
     * implementation.
     *
     * @param queryName    A {@link java.lang.String} describing the query to be executed
     * @param query        The {@code query} to be sent
     * @param responseType The {@link ResponseType} used for this query
     * @param <R>          The response class contained in the given {@code responseType}
     * @param <Q>          The query class
     * @return A {@link java.util.concurrent.CompletableFuture} containing the query result as dictated by the given
     * {@code responseType}
     */
    default <R, Q> CompletableFuture<R> query(@Nonnull String queryName, @Nonnull Q query,
                                              @Nonnull Class<R> responseType) {
        return query(queryName, query, ResponseTypes.instanceOf(responseType));
    }

    /**
     * Sends given {@code query} over the {@link org.axonframework.queryhandling.QueryBus}, expecting a response in the
     * form of {@code responseType} from a single source. The query name will be derived from the provided {@code
     * query}. Execution may be asynchronous, depending on the QueryBus implementation.
     *
     * @param query        The {@code query} to be sent
     * @param responseType The {@link ResponseType} used for this query
     * @param <R>          The response class contained in the given {@code responseType}
     * @param <Q>          The query class
     * @return A {@link java.util.concurrent.CompletableFuture} containing the query result as dictated by the given
     * {@code responseType}
     */
    default <R, Q> CompletableFuture<R> query(@Nonnull Q query, @Nonnull ResponseType<R> responseType) {
        return query(queryName(query), query, responseType);
    }

    /**
     * Sends given {@code query} over the {@link org.axonframework.queryhandling.QueryBus}, expecting a response in the
     * form of {@code responseType} from a single source. Execution may be asynchronous, depending on the QueryBus
     * implementation.
     *
     * @param queryName    A {@link java.lang.String} describing the query to be executed
     * @param query        The {@code query} to be sent
     * @param responseType The {@link ResponseType} used for this query
     * @param <R>          The response class contained in the given {@code responseType}
     * @param <Q>          The query class
     * @return A {@link java.util.concurrent.CompletableFuture} containing the query result as dictated by the given
     * {@code responseType}
     */
    <R, Q> CompletableFuture<R> query(@Nonnull String queryName, @Nonnull Q query,
                                      @Nonnull ResponseType<R> responseType);

    /**
     * Sends the specified {@code query} over the {@link QueryBus} and waits for a response synchronously.
     * This method derives the query name from the provided {@code query} object.
     *
     * @param query        The query object containing the details of the request.
     * @param responseType The {@link ResponseType} describing the expected response type.
     * @param <R>          The response class contained in the given {@code responseType}.
     * @param <Q>          The query class.
     * @return The result of the query of type {@code R}.
     * @throws QueryExecutionException if an exception occurs during query execution, with the root cause wrapped in the exception.
     */
    default <R, Q> R queryAndWait(@Nonnull Q query, @Nonnull ResponseType<R> responseType) {
        return queryAndWait(queryName(query), query, responseType);
    }

    /**
     * Sends the specified {@code query} with a {@code queryName} over the {@link QueryBus} and waits for a response
     * synchronously.
     *
     * @param queryName    A {@link String} describing the query to be executed.
     * @param query        The query object containing the details of the request.
     * @param responseType The {@link ResponseType} describing the expected response type.
     * @param <R>          The response class contained in the given {@code responseType}.
     * @param <Q>          The query class.
     * @return The result of the query of type {@code R}.
     * @throws QueryExecutionException if an exception occurs during query execution, with the root cause wrapped in the exception.
     */
    <R, Q> R queryAndWait(@Nonnull String queryName, @Nonnull Q query, @Nonnull ResponseType<R> responseType);

    /**
     * Sends the specified {@code query} over the {@link QueryBus} and waits for a response synchronously, up to a
     * maximum period defined by {@code timeout} and {@code unit}. This method derives the query name from the provided
     * {@code query} object.
     *
     * @param query        The query object containing the details of the request.
     * @param responseType The {@link ResponseType} describing the expected response type.
     * @param timeout      The maximum time to wait for the response.
     * @param unit         The {@link TimeUnit} of the timeout value.
     * @param <R>          The response class contained in the given {@code responseType}.
     * @param <Q>          The query class.
     * @return The result of the query of type {@code R}.
     * @throws QueryExecutionException if an exception occurs during query execution, including cases where a timeout is reached.
     */
    default <R, Q> R queryAndWait(@Nonnull Q query, @Nonnull ResponseType<R> responseType, long timeout, @Nonnull TimeUnit unit) {
        return queryAndWait(queryName(query), query, responseType, timeout, unit);
    }

    /**
     * Sends the specified {@code query} with a {@code queryName} over the {@link QueryBus} and waits for a response
     * synchronously, up to a maximum period defined by {@code timeout} and {@code unit}.
     *
     * @param queryName    A {@link String} describing the query to be executed.
     * @param query        The query object containing the details of the request.
     * @param responseType The {@link ResponseType} describing the expected response type.
     * @param timeout      The maximum time to wait for the response.
     * @param unit         The {@link TimeUnit} of the timeout value.
     * @param <R>          The response class contained in the given {@code responseType}.
     * @param <Q>          The query class.
     * @return The result of the query of type {@code R}.
     * @throws QueryExecutionException if an exception occurs during query execution, including cases where a timeout is reached.
     */
    <R, Q> R queryAndWait(@Nonnull String queryName, @Nonnull Q query, ResponseType<R> responseType, long timeout, @Nonnull TimeUnit unit);

    /**
     * Sends given {@code query} over the {@link org.axonframework.queryhandling.QueryBus}, expecting a response
     * as {@link org.reactivestreams.Publisher} of {@code responseType}.
     * Query is sent once {@link org.reactivestreams.Publisher} is subscribed to.
     * The Streaming query allows a client to stream large result sets.
     *
     * Usage of this method requires
     * <a href="https://projectreactor.io/">Project Reactor</a>
     * on the class path.
     *
     * {@link org.reactivestreams.Publisher} is used for backwards compatibility reason,
     * for clients that don't have Project Reactor on class path.
     * Check <a href="https://docs.axoniq.io/reference-guide/extensions/reactor">Reactor Extension</a>
     * for native Flux type and more.
     *
     * Use {@code Flux.from(publisher)} to convert to Flux stream.
     *
     * @param query        The {@code query} to be sent
     * @param responseType A {@link java.lang.Class} describing the desired response type
     * @param <R>          The response class contained in the given {@code responseType}
     * @param <Q>          The query class
     * @return A {@link org.reactivestreams.Publisher} streaming the results as dictated by the given
     * {@code responseType}
     */
    default <R, Q> Publisher<R> streamingQuery(Q query, Class<R> responseType) {
        return streamingQuery(queryName(query), query, responseType);
    }

    /**
     * Sends given {@code query} over the {@link org.axonframework.queryhandling.QueryBus}, expecting a response
     * as {@link org.reactivestreams.Publisher} of {@code responseType}.
     * Query is sent once {@link org.reactivestreams.Publisher} is subscribed to.
     * The Streaming query allows a client to stream large result sets.
     *
     * Usage of this method requires
     * <a href="https://projectreactor.io/">Project Reactor</a>
     * on the class path.
     *
     * {@link org.reactivestreams.Publisher} is used for backwards compatibility reason,
     * for clients that don't have Project Reactor on class path.
     * Check <a href="https://docs.axoniq.io/reference-guide/extensions/reactor">Reactor Extension</a>
     * for native Flux type and more.
     *
     * Use {@code Flux.from(publisher)} to convert to Flux stream.
     *
     * @param queryName    A {@link java.lang.String} describing the query to be executed
     * @param query        The {@code query} to be sent
     * @param responseType A {@link java.lang.Class} describing the desired response type
     * @param <R>          The response class contained in the given {@code responseType}
     * @param <Q>          The query class
     * @return A {@link org.reactivestreams.Publisher} streaming the results as dictated by the given
     * {@code responseType}.
     */
    <R, Q> Publisher<R> streamingQuery(String queryName, Q query,  Class<R> responseType);

    /**
     * Sends given {@code query} over the {@link org.axonframework.queryhandling.QueryBus}, expecting a response in the
     * form of {@code responseType} from several sources. The stream is completed when a {@code timeout} occurs or when
     * all results are received. The query name will be derived from the provided {@code query}. Execution may be
     * asynchronous, depending on the QueryBus implementation.
     *
     * @param query        The {@code query} to be sent
     * @param responseType The {@link ResponseType} used for this query
     * @param timeout      A timeout of {@code long} for the query
     * @param timeUnit     The selected {@link java.util.concurrent.TimeUnit} for the given {@code timeout}
     * @param <R>          The response class contained in the given {@code responseType}
     * @param <Q>          The query class
     * @return A stream of results.
     */
    default <R, Q> Stream<R> scatterGather(@Nonnull Q query, @Nonnull ResponseType<R> responseType, long timeout,
                                           @Nonnull TimeUnit timeUnit) {
        return scatterGather(queryName(query), query, responseType, timeout, timeUnit);
    }

    /**
     * Sends given {@code query} over the {@link org.axonframework.queryhandling.QueryBus}, expecting a response in the
     * form of {@code responseType} from several sources. The stream is completed when a {@code timeout} occurs or when
     * all results are received. Execution may be asynchronous, depending on the QueryBus implementation.
     *
     * @param queryName    A {@link java.lang.String} describing the query to be executed
     * @param query        The {@code query} to be sent
     * @param responseType The {@link ResponseType} used for this query
     * @param timeout      A timeout of {@code long} for the query
     * @param timeUnit     The selected {@link java.util.concurrent.TimeUnit} for the given {@code timeout}
     * @param <R>          The response class contained in the given {@code responseType}
     * @param <Q>          The query class
     * @return A stream of results.
     */
    <R, Q> Stream<R> scatterGather(@Nonnull String queryName, @Nonnull Q query, @Nonnull ResponseType<R> responseType,
                                   long timeout,
                                   @Nonnull TimeUnit timeUnit);

    /**
     * Sends given {@code query} over the {@link QueryBus} and returns result containing initial response and
     * incremental updates (received at the moment the query is sent, until it is cancelled by the caller or closed by
     * the emitting side).
     * <p>
     * <b>Note</b>: Any {@code null} results, on the initial result or the updates, wil lbe filtered out by the
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
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage)
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage, int)
     */
    default <Q, I, U> SubscriptionQueryResult<I, U> subscriptionQuery(@Nonnull Q query,
                                                                      @Nonnull Class<I> initialResponseType,
                                                                      @Nonnull Class<U> updateResponseType) {
        return subscriptionQuery(queryName(query), query, initialResponseType, updateResponseType);
    }

    /**
     * Sends given {@code query} over the {@link QueryBus} and returns result containing initial response and
     * incremental updates (received at the moment the query is sent, until it is cancelled by the caller or closed by
     * the emitting side).
     * <p>
     * <b>Note</b>: Any {@code null} results, on the initial result or the updates, wil lbe filtered out by the
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
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage)
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage, int)
     */
    default <Q, I, U> SubscriptionQueryResult<I, U> subscriptionQuery(@Nonnull String queryName,
                                                                      @Nonnull Q query,
                                                                      @Nonnull Class<I> initialResponseType,
                                                                      @Nonnull Class<U> updateResponseType) {
        return subscriptionQuery(queryName,
                                 query,
                                 ResponseTypes.instanceOf(initialResponseType),
                                 ResponseTypes.instanceOf(updateResponseType));
    }

    /**
     * Sends given {@code query} over the {@link QueryBus} and returns result containing initial response and
     * incremental updates (received at the moment the query is sent, until it is cancelled by the caller or closed by
     * the emitting side).
     * <p>
     * <b>Note</b>: Any {@code null} results, on the initial result or the updates, wil lbe filtered out by the
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
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage)
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage, int)
     */
    default <Q, I, U> SubscriptionQueryResult<I, U> subscriptionQuery(@Nonnull Q query,
                                                                      @Nonnull ResponseType<I> initialResponseType,
                                                                      @Nonnull ResponseType<U> updateResponseType) {
        return subscriptionQuery(queryName(query),
                                 query,
                                 initialResponseType,
                                 updateResponseType);
    }

    /**
     * Sends given {@code query} over the {@link QueryBus} and returns result containing initial response and
     * incremental updates (received at the moment the query is sent, until it is cancelled by the caller or closed by
     * the emitting side).
     * <p>
     * <b>Note</b>: Any {@code null} results, on the initial result or the updates, wil lbe filtered out by the
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
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage)
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage, SubscriptionQueryBackpressure, int)
     * @deprecated in favour of using {{@link #subscriptionQuery(String, Object, ResponseType, ResponseType)}}.
     * To set a backpressure strategy, use one of the {@code onBackpressure..} operators on the updates flux directly.
     * Example: {@code result.updates().onBackpressureBuffer(100)}
     */
    @Deprecated
    default <Q, I, U> SubscriptionQueryResult<I, U> subscriptionQuery(@Nonnull String queryName,
                                                                      @Nonnull Q query,
                                                                      @Nonnull ResponseType<I> initialResponseType,
                                                                      @Nonnull ResponseType<U> updateResponseType,
                                                                      @Nullable SubscriptionQueryBackpressure backpressure) {
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
     * <p>
     * <b>Note</b>: Any {@code null} results, on the initial result or the updates, will be filtered out by the
     * QueryGateway. If you require the {@code null} to be returned for the initial and update results, we suggest using
     * the {@link QueryBus} instead.
     *
     * @param queryName           a {@link String} describing query to be executed
     * @param query               the {@code query} to be sent
     * @param initialResponseType the initial response type used for this query
     * @param updateResponseType  the update response type used for this query
     * @param <Q>                 the type of the query
     * @param <I>                 the type of the initial response
     * @param <U>                 the type of the incremental update
     * @return registration which can be used to cancel receiving updates
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage)
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage, int)
     */
    default <Q, I, U> SubscriptionQueryResult<I, U> subscriptionQuery(@Nonnull String queryName,
                                                                      @Nonnull Q query,
                                                                      @Nonnull ResponseType<I> initialResponseType,
                                                                      @Nonnull ResponseType<U> updateResponseType) {
        return subscriptionQuery(queryName, query, initialResponseType, updateResponseType, Queues.SMALL_BUFFER_SIZE);
    }

    /**
     * Sends given {@code query} over the {@link QueryBus} and returns result containing initial response and
     * incremental updates (received at the moment the query is sent, until it is cancelled by the caller or closed by
     * the emitting side).
     * <p>
     * <b>Note</b>: Any {@code null} results, on the initial result or the updates, wil lbe filtered out by the
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
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage)
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage, SubscriptionQueryBackpressure, int)
     * @deprecated in favour of using {{@link #subscriptionQuery(String, Object, ResponseType, ResponseType, int)}}.
     * To set a backpressure strategy, use one of the {@code onBackpressure..} operators on the updates flux directly.
     * Example: {@code result.updates().onBackpressureBuffer(100)}
     */
    @Deprecated
    <Q, I, U> SubscriptionQueryResult<I, U> subscriptionQuery(@Nonnull String queryName,
                                                              @Nonnull Q query,
                                                              @Nonnull ResponseType<I> initialResponseType,
                                                              @Nonnull ResponseType<U> updateResponseType,
                                                              @Nullable SubscriptionQueryBackpressure backpressure,
                                                              int updateBufferSize);

    /**
     * Sends given {@code query} over the {@link QueryBus} and returns result containing initial response and
     * incremental updates (received at the moment the query is sent, until it is cancelled by the caller or closed by
     * the emitting side).
     * <p>
     * <b>Note</b>: Any {@code null} results, on the initial result or the updates, wil lbe filtered out by the
     * QueryGateway. If you require the {@code null} to be returned for the initial and update results, we suggest using
     * the {@link QueryBus} instead.
     *
     * @param queryName           a {@link String} describing query to be executed
     * @param query               the {@code query} to be sent
     * @param initialResponseType the initial response type used for this query
     * @param updateResponseType  the update response type used for this query
     * @param updateBufferSize    the size of buffer which accumulates updates before subscription to the flux
     *                            is made
     * @param <Q>                 the type of the query
     * @param <I>                 the type of the initial response
     * @param <U>                 the type of the incremental update
     * @return registration which can be used to cancel receiving updates
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage)
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage, int)
     */
    <Q, I, U> SubscriptionQueryResult<I, U> subscriptionQuery(@Nonnull String queryName,
                                                              @Nonnull Q query,
                                                              @Nonnull ResponseType<I> initialResponseType,
                                                              @Nonnull ResponseType<U> updateResponseType,
                                                              int updateBufferSize);
}
