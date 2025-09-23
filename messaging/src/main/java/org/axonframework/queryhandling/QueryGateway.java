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
import jakarta.annotation.Nullable;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.reactivestreams.Publisher;
import reactor.util.concurrent.Queues;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Interface towards the Query Handling components of an application.
 * <p>
 * This interface provides a friendlier API toward the {@link QueryBus}.
 *
 * @author Allard Buijze
 * @author Marc Gathier
 * @author Milan Savic
 * @author Steven van Beelen
 * @since 3.1.0
 */
public interface QueryGateway extends DescribableComponent {

    /**
     * Sends given {@code query} over the {@link QueryBus}, expecting a single response with the given
     * {@code responseType} from a single source.
     * <p>
     * Execution may be asynchronous, depending on the {@code QueryBus} implementation.
     * <p>
     * The given {@code query} is wrapped as the payload of the {@link QueryMessage} that is eventually posted on the
     * {@code QueryBus}, unless the {@code query} already implements {@link Message}. In that case, a
     * {@code QueryMessage} is constructed from that message's payload and {@link org.axonframework.messaging.Metadata}.
     * The {@link QueryMessage#responseType()} will <b>at all times</b> be a
     * {@link org.axonframework.messaging.responsetypes.InstanceResponseType}.
     *
     * @param query        The {@code query} to be sent.
     * @param responseType A {@code Class} describing the desired response type.
     * @param context      The processing context, if any, to dispatch the given {@code query} in.
     * @param <R>          The generic type of the expected response.
     * @return A {@code CompletableFuture} containing a single query response of type {@code responseType}.
     */
    @Nonnull
    <R> CompletableFuture<R> query(@Nonnull Object query,
                                   @Nonnull Class<R> responseType,
                                   @Nullable ProcessingContext context);

    /**
     * Sends given {@code query} over the {@link QueryBus}, expecting multiple responses in the form of
     * {@code responseType} from a single source.
     * <p>
     * Execution may be asynchronous, depending on the {@code QueryBus} implementation.
     * <p>
     * The given {@code query} is wrapped as the payload of the {@link QueryMessage} that is eventually posted on the
     * {@code QueryBus}, unless the {@code query} already implements {@link Message}. In that case, a
     * {@code QueryMessage} is constructed from that message's payload and {@link org.axonframework.messaging.Metadata}.
     * The {@link QueryMessage#responseType()} will <b>at all times</b> be a
     * {@link org.axonframework.messaging.responsetypes.MultipleInstancesResponseType}.
     *
     * @param query        The {@code query} to be sent.
     * @param responseType A {@code Class} describing the desired response type.
     * @param context      The processing context, if any, to dispatch the given {@code query} in.
     * @param <R>          The generic type of the expected response(s).
     * @return A {@code CompletableFuture} containing a list of query responses of type {@code responseType}.
     */
    @Nonnull
    <R> CompletableFuture<List<R>> queryMany(@Nonnull Object query,
                                             @Nonnull Class<R> responseType,
                                             @Nullable ProcessingContext context);

    /**
     * Sends given {@code query} over the {@link QueryBus}, expecting a response as
     * {@link org.reactivestreams.Publisher} of {@code responseType}.
     * <p>
     * The {@code query} is sent once the {@code Publisher} is subscribed to. The streaming query allows a client to
     * stream large result sets. Usage of this method requires <a href="https://projectreactor.io/">Project Reactor</a>
     * on the class path.
     * <p>
     * The given {@code query} is wrapped as the payload of the {@link StreamingQueryMessage} that is eventually posted
     * on the {@code QueryBus}, unless the {@code query} already implements {@link Message}. In that case, a
     * {@code QueryMessage} is constructed from that message's payload and
     * {@link org.axonframework.messaging.Metadata}.
     * <p>
     * {@link org.reactivestreams.Publisher} is used for backwards compatibility reason, for clients that don't have
     * Project Reactor on class path. Check <a href="https://docs.axoniq.io/reference-guide/extensions/reactor">Reactor
     * Extension</a> for native Flux type and more.
     * <p>
     * Use {@code Flux.from(publisher)} to convert to Flux stream.
     *
     * @param query        The {@code query} to be sent.
     * @param responseType A {@link java.lang.Class} describing the desired response type.
     * @param <R>          The response class contained in the given {@code responseType}.
     * @param <Q>          The query class.
     * @return A {@link org.reactivestreams.Publisher} streaming the results as dictated by the given
     * {@code responseType}.
     */
    @Nonnull
    <R, Q> Publisher<R> streamingQuery(@Nonnull Q query,
                                       @Nonnull Class<R> responseType);

    /**
     * Sends given {@code query} over the {@link QueryBus}, expecting a response in the form of {@code responseType}
     * from several sources.
     * <p>
     * The stream is completed when a {@code timeout} occurs or when all results are received. Execution may be
     * asynchronous, depending on the {@code QueryBus} implementation.
     * <p>
     * The given {@code query} is wrapped as the payload of the {@link QueryMessage} that is eventually posted on the
     * {@code QueryBus}, unless the {@code query} already implements {@link Message}. In that case, a
     * {@code QueryMessage} is constructed from that message's payload and
     * {@link org.axonframework.messaging.Metadata}.
     *
     * @param query        The {@code query} to be sent.
     * @param responseType The {@link ResponseType} used for this query.
     * @param timeout      A timeout of {@code long} for the query.
     * @param timeUnit     The selected {@link java.util.concurrent.TimeUnit} for the given {@code timeout}.
     * @param <R>          The response class contained in the given {@code responseType}.
     * @param <Q>          The query class.
     * @return A stream of results.
     */
    @Nonnull
    <R, Q> Stream<R> scatterGather(@Nonnull Q query,
                                   @Nonnull ResponseType<R> responseType,
                                   long timeout,
                                   @Nonnull TimeUnit timeUnit);

    /**
     * Sends given {@code query} over the {@link QueryBus} and returns result containing initial response and
     * incremental updates (received at the moment the query is sent, until it is cancelled by the caller or closed by
     * the emitting side).
     * <p>
     * The given {@code query} is wrapped as the payload of the {@link SubscriptionQueryMessage} that is eventually
     * posted on the {@code QueryBus}, unless the {@code query} already implements {@link Message}. In that case, a
     * {@code QueryMessage} is constructed from that message's payload and
     * {@link org.axonframework.messaging.Metadata}.
     * <p>
     * Note that any {@code null} results, on the initial result or the updates, wil lbe filtered out by the gateway. If
     * you require the {@code null} to be returned for the initial and update results, we suggest using the
     * {@code QueryBus} instead.
     *
     * @param query               The {@code query} to be sent.
     * @param initialResponseType The initial response type used for this query.
     * @param updateResponseType  The update response type used for this query.
     * @param <Q>                 The type of the query.
     * @param <I>                 The type of the initial response.
     * @param <U>                 The type of the incremental update.
     * @return Registration which can be used to cancel receiving updates.
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage)
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage, int)
     */
    @Nonnull
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
     * The given {@code query} is wrapped as the payload of the {@link SubscriptionQueryMessage} that is eventually
     * posted on the {@code QueryBus}, unless the {@code query} already implements {@link Message}. In that case, a
     * {@code QueryMessage} is constructed from that message's payload and
     * {@link org.axonframework.messaging.Metadata}.
     * <p>
     * Note that any {@code null} results, on the initial result or the updates, wil lbe filtered out by the gateway. If
     * you require the {@code null} to be returned for the initial and update results, we suggest using the
     * {@code QueryBus} instead.
     *
     * @param query               The {@code query} to be sent.
     * @param initialResponseType The initial response type used for this query.
     * @param updateResponseType  The update response type used for this query.
     * @param <Q>                 The type of the query.
     * @param <I>                 The type of the initial response.
     * @param <U>                 The type of the incremental update.
     * @return Registration which can be used to cancel receiving updates.
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage)
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage, int)
     */
    @Nonnull
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
     * The given {@code query} is wrapped as the payload of the {@link SubscriptionQueryMessage} that is eventually
     * posted on the {@code QueryBus}, unless the {@code query} already implements {@link Message}. In that case, a
     * {@code QueryMessage} is constructed from that message's payload and
     * {@link org.axonframework.messaging.Metadata}.
     * <p>
     * Note that any {@code null} results, on the initial result or the updates, wil lbe filtered out by the gateway. If
     * you require the {@code null} to be returned for the initial and update results, we suggest using the
     * {@code QueryBus} instead.
     *
     * @param query               The {@code query} to be sent.
     * @param initialResponseType The initial response type used for this query.
     * @param updateResponseType  The update response type used for this query.
     * @param updateBufferSize    The size of buffer which accumulates updates before subscription to the flux is made.
     * @param <Q>                 The type of the query.
     * @param <I>                 The type of the initial response.
     * @param <U>                 The type of the incremental update.
     * @return Registration which can be used to cancel receiving updates.
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage)
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage, int)
     */
    @Nonnull
    <Q, I, U> SubscriptionQueryResult<I, U> subscriptionQuery(@Nonnull Q query,
                                                              @Nonnull ResponseType<I> initialResponseType,
                                                              @Nonnull ResponseType<U> updateResponseType,
                                                              int updateBufferSize);
}
