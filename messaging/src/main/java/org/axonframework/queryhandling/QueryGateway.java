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
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.reactivestreams.Publisher;
import reactor.util.concurrent.Queues;

import java.util.List;
import java.util.concurrent.CompletableFuture;

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
     * Sends given {@code query} over the {@link QueryBus}, expecting a response as a
     * {@link org.reactivestreams.Publisher} of {@code responseType}.
     * <p>
     * The {@code query} is sent once the {@code Publisher} is subscribed to. The streaming query allows a client to
     * stream large result sets. Usage of this method requires <a href="https://projectreactor.io/">Project Reactor</a>
     * on the class path.
     * <p>
     * The given {@code query} is wrapped as the payload of the {@link StreamingQueryMessage} that is eventually posted
     * on the {@code QueryBus}, unless the {@code query} already implements {@link Message}. In that case, a
     * {@code StreamingQueryMessage} is constructed from that message's payload and
     * {@link org.axonframework.messaging.Metadata}. The {@link QueryMessage#responseType()} will <b>at all times</b> be
     * a {@link org.axonframework.messaging.responsetypes.PublisherResponseType}.
     * <p>
     * {@link org.reactivestreams.Publisher} is used for backwards compatibility reason, for clients that don't have
     * Project Reactor on class path. Check <a href="https://docs.axoniq.io/reference-guide/extensions/reactor">Reactor
     * Extension</a> for native Flux type and more. Use {@code Flux.from(publisher)} to convert to Flux stream.
     *
     * @param query        The {@code query} to be sent.
     * @param responseType A {@link java.lang.Class} describing the desired response type.
     * @param context      The processing context, if any, to dispatch the given {@code query} in.
     * @param <R>          The generic type of the expected response(s).
     * @return A {@link org.reactivestreams.Publisher} streaming the results as dictated by the given
     * {@code responseType}.
     */
    @Nonnull
    <R> Publisher<R> streamingQuery(@Nonnull Object query,
                                    @Nonnull Class<R> responseType,
                                    @Nullable ProcessingContext context);

    /**
     * Sends given {@code query} over the {@link QueryBus} as a subscription query, combining the initial result and
     * emitted updates as a {@link Publisher} of the given {@code responseType}.
     * <p>
     * The {@code query} is sent once the {@code Publisher} is subscribed to. Furthermore, updates are received at the
     * moment the query is sent, and until it is cancelled by the caller or closed by the emitting side.
     * <p>
     * The given {@code query} is wrapped as the payload of the {@link SubscriptionQueryMessage} that is eventually
     * posted on the {@code QueryBus}, unless the {@code query} already implements {@link Message}. In that case, a
     * {@code SubscriptionQueryMessage} is constructed from that message's payload and
     * {@link org.axonframework.messaging.Metadata}.
     * <p>
     * Note that any {@code null} results, on the initial result or the updates, wil lbe filtered out by the gateway. If
     * you require the {@code null} to be returned for the initial and update results, we suggest using the
     * {@code QueryBus} instead.
     *
     * @param query        The {@code query} to be sent.
     * @param responseType The response type returned by this query as the initial result and the updates.
     * @param context      The processing context, if any, to dispatch the given {@code query} in.context
     * @param <R>          The type of all the responses.
     * @return A {@link org.reactivestreams.Publisher} streaming the results as dictated by the given
     * {@code responseType}.
     */
    @Nonnull
    default <R> Publisher<R> subscriptionQuery(@Nonnull Object query,
                                               @Nonnull Class<R> responseType,
                                               @Nullable ProcessingContext context) {
        return subscriptionQuery(query, responseType, context, Queues.SMALL_BUFFER_SIZE);
    }

    /**
     * Sends given {@code query} over the {@link QueryBus} as a subscription query, combining the initial result and
     * emitted updates as a {@link Publisher} of the given {@code responseType}.
     * <p>
     * The {@code query} is sent once the {@code Publisher} is subscribed to. Furthermore, updates are received at the
     * moment the query is sent, and until it is cancelled by the caller or closed by the emitting side.
     * <p>
     * The given {@code query} is wrapped as the payload of the {@link SubscriptionQueryMessage} that is eventually
     * posted on the {@code QueryBus}, unless the {@code query} already implements {@link Message}. In that case, a
     * {@code SubscriptionQueryMessage} is constructed from that message's payload and
     * {@link org.axonframework.messaging.Metadata}.
     * <p>
     * Note that any {@code null} results, on the initial result or the updates, wil lbe filtered out by the gateway. If
     * you require the {@code null} to be returned for the initial and update results, we suggest using the
     * {@code QueryBus} instead.
     *
     * @param query            The {@code query} to be sent.
     * @param responseType     The response type returned by this query as the initial result and the updates.
     * @param context          The processing context, if any, to dispatch the given {@code query} in.context
     * @param updateBufferSize The size of buffer which accumulates updates before a subscription to the {@code Flux} is
     *                         made.
     * @param <R>              The type of all the responses.
     * @return A {@link org.reactivestreams.Publisher} streaming the results as dictated by the given
     * {@code responseType}.
     */
    @Nonnull
    default <R> Publisher<R> subscriptionQuery(@Nonnull Object query,
                                               @Nonnull Class<R> responseType,
                                               @Nullable ProcessingContext context,
                                               int updateBufferSize) {
        SubscriptionQueryResponse<R, R> result =
                subscriptionQuery(query, responseType, responseType, context, updateBufferSize);
        return result.initialResult().concatWith(result.updates());
    }

    /**
     * Sends given {@code query} over the {@link QueryBus} and returns a {@link SubscriptionQueryResponse} containing an
     * {@link SubscriptionQueryResponse#initialResult() initial result Flux} and incremental
     * {@link SubscriptionQueryResponse#updates() updates}.
     * <p>
     * The {@code query} is sent once the initial result is subscribed to. Furthermore, updates are received at the
     * moment the query is sent, and until it is cancelled by the caller or closed by the emitting side.
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
     * @param context             The processing context, if any, to dispatch the given {@code query} in.
     * @param <I>                 The type of the initial response.
     * @param <U>                 The type of the incremental update.
     * @return Registration which can be used to cancel receiving updates.
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage, ProcessingContext, int)
     */
    @Nonnull
    default <I, U> SubscriptionQueryResponse<I, U> subscriptionQuery(@Nonnull Object query,
                                                                     @Nonnull Class<I> initialResponseType,
                                                                     @Nonnull Class<U> updateResponseType,
                                                                     @Nullable ProcessingContext context) {
        return subscriptionQuery(query, initialResponseType, updateResponseType, context, Queues.SMALL_BUFFER_SIZE);
    }

    /**
     * Sends given {@code query} over the {@link QueryBus} and returns a {@link SubscriptionQueryResponse} containing an
     * {@link SubscriptionQueryResponse#initialResult() initial result Flux} and incremental
     * {@link SubscriptionQueryResponse#updates() updates}.
     * <p>
     * The {@code query} is sent once the initial result is subscribed to. Furthermore, updates are received at the
     * moment the query is sent, and until it is cancelled by the caller or closed by the emitting side.
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
     * @param context             The processing context, if any, to dispatch the given {@code query} in.
     * @param updateBufferSize    The size of buffer which accumulates updates before a subscription to the {@code Flux}
     *                            is made.
     * @param <I>                 The type of the initial response.
     * @param <U>                 The type of the incremental update.
     * @return Registration which can be used to cancel receiving updates.
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage, ProcessingContext, int)
     */
    @Nonnull
    <I, U> SubscriptionQueryResponse<I, U> subscriptionQuery(@Nonnull Object query,
                                                             @Nonnull Class<I> initialResponseType,
                                                             @Nonnull Class<U> updateResponseType,
                                                             @Nullable ProcessingContext context,
                                                             int updateBufferSize);
}
