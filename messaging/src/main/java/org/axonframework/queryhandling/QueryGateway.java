/*
 * Copyright (c) 2010-2025. Axon Framework
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

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.reactivestreams.Publisher;
import reactor.util.concurrent.Queues;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

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
public interface QueryGateway {

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
        return query(query, ResponseTypes.instanceOf(responseType));
    }

    /**
     * Sends given {@code query} over the {@link org.axonframework.queryhandling.QueryBus}, expecting a response in the
     * form of {@code responseType} from a single source. Execution may be asynchronous, depending on the QueryBus
     * implementation.
     *
     * @param query        The {@code query} to be sent
     * @param responseType The {@link ResponseType} used for this query
     * @param <R>          The response class contained in the given {@code responseType}
     * @param <Q>          The query class
     * @return A {@link java.util.concurrent.CompletableFuture} containing the query result as dictated by the given
     * {@code responseType}
     */
    <R, Q> CompletableFuture<R> query(@Nonnull Q query,
                                      @Nonnull ResponseType<R> responseType);

    /**
     * Sends given {@code query} over the {@link org.axonframework.queryhandling.QueryBus}, expecting a response
     * as {@link org.reactivestreams.Publisher} of {@code responseType}.
     * Query is sent once {@link org.reactivestreams.Publisher} is subscribed to.
     * The Streaming query allows a client to stream large result sets.
     * <p>
     * Usage of this method requires
     * <a href="https://projectreactor.io/">Project Reactor</a>
     * on the class path.
     * <p>
     * {@link org.reactivestreams.Publisher} is used for backwards compatibility reason,
     * for clients that don't have Project Reactor on class path.
     * Check <a href="https://docs.axoniq.io/reference-guide/extensions/reactor">Reactor Extension</a>
     * for native Flux type and more.
     * <p>
     * Use {@code Flux.from(publisher)} to convert to Flux stream.
     *
     * @param query        The {@code query} to be sent
     * @param responseType A {@link java.lang.Class} describing the desired response type
     * @param <R>          The response class contained in the given {@code responseType}
     * @param <Q>          The query class
     * @return A {@link org.reactivestreams.Publisher} streaming the results as dictated by the given
     * {@code responseType}.
     */
    <R, Q> Publisher<R> streamingQuery(Q query,  Class<R> responseType);

    /**
     * Sends given {@code query} over the {@link org.axonframework.queryhandling.QueryBus}, expecting a response in the
     * form of {@code responseType} from several sources. The stream is completed when a {@code timeout} occurs or when
     * all results are received. Execution may be asynchronous, depending on the QueryBus implementation.
     *
     * @param query        The {@code query} to be sent
     * @param responseType The {@link ResponseType} used for this query
     * @param timeout      A timeout of {@code long} for the query
     * @param timeUnit     The selected {@link java.util.concurrent.TimeUnit} for the given {@code timeout}
     * @param <R>          The response class contained in the given {@code responseType}
     * @param <Q>          The query class
     * @return A stream of results.
     */
    <R, Q> Stream<R> scatterGather(@Nonnull Q query, @Nonnull ResponseType<R> responseType,
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
        return subscriptionQuery(query,
                                 ResponseTypes.instanceOf(initialResponseType),
                                 ResponseTypes.instanceOf(updateResponseType));
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
    default <Q, I, U> SubscriptionQueryResult<I, U> subscriptionQuery(@Nonnull Q query,
                                                                      @Nonnull ResponseType<I> initialResponseType,
                                                                      @Nonnull ResponseType<U> updateResponseType) {
        return subscriptionQuery(query, initialResponseType, updateResponseType, Queues.SMALL_BUFFER_SIZE);
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
    <Q, I, U> SubscriptionQueryResult<I, U> subscriptionQuery(@Nonnull Q query,
                                                              @Nonnull ResponseType<I> initialResponseType,
                                                              @Nonnull ResponseType<U> updateResponseType,
                                                              int updateBufferSize);
}
