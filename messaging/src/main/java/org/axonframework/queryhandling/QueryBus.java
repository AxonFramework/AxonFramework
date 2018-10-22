/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageDispatchInterceptorSupport;
import org.axonframework.messaging.MessageHandlerInterceptorSupport;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.concurrent.Queues;

import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * The mechanism that dispatches Query objects to their appropriate QueryHandlers. QueryHandlers can subscribe and
 * un-subscribe to specific queries (identified by their {@link QueryMessage#getQueryName()} and
 * {@link QueryMessage#getResponseType()} on the query bus. There may be multiple handlers for each combination of
 * queryName/responseType.
 *
 * @author Marc Gathier
 * @author Allard Buijze
 * @since 3.1
 */
public interface QueryBus extends MessageHandlerInterceptorSupport<QueryMessage<?,?>>,
                                  MessageDispatchInterceptorSupport<QueryMessage<?,?>> {

    /**
     * Subscribe the given {@code handler} to queries with the given {@code queryName} and {@code responseType}.
     * Multiple handlers may subscribe to the same combination of queryName/responseType.
     *
     * @param queryName    the name of the query request to subscribe
     * @param responseType the type of response the subscribed component answers with
     * @param handler      a handler that implements the query
     * @return a handle to un-subscribe the query handler
     */
    <R> Registration subscribe(String queryName, Type responseType, MessageHandler<? super QueryMessage<?, R>> handler);

    /**
     * Dispatch the given {@code query} to a single QueryHandler subscribed to the given {@code query}'s queryName
     * and responseType. This method returns all values returned by the Query Handler as a Collection. This may or may
     * not be the exact collection as defined in the Query Handler.
     * <p>
     * If the Query Handler defines a single return object (i.e. not a collection or array), that object is returned
     * as the sole entry in a singleton collection.
     * <p>
     * When no handlers are available that can answer the given {@code query}, the returned CompletableFuture will be
     * completed with a {@link NoHandlerForQueryException}.
     *
     * @param query the query
     * @param <Q>   the payload type of the query
     * @param <R>   the response type of the query
     * @return a CompletableFuture that resolves when the response is available
     */
    <Q, R> CompletableFuture<QueryResponseMessage<R>> query(QueryMessage<Q, R> query);

    /**
     * Dispatch the given {@code query} to all QueryHandlers subscribed to the given {@code query}'s queryName/responseType.
     * Returns a stream of results which blocks until all handlers have processed the request or when the timeout occurs.
     * <p>
     * If no handlers are available to provide a result, or when all available handlers throw an exception while
     * attempting to do so, the returned Stream is empty.
     * <p>
     * Note that any terminal operation (such as {@link Stream#forEach(Consumer)}) on the Stream may cause it to
     * block until the {@code timeout} has expired, awaiting additional data to include in the stream.
     *
     * @param query   the query
     * @param timeout time to wait for results
     * @param unit    unit for the timeout
     * @param <Q>     the payload type of the query
     * @param <R>     the response type of the query
     * @return stream of query results
     */
    <Q, R> Stream<QueryResponseMessage<R>> scatterGather(QueryMessage<Q, R> query, long timeout, TimeUnit unit);

    /**
     * Dispatch the given {@code query} to a single QueryHandler subscribed to the given {@code query}'s
     * queryName/initialResponseType/updateResponseType. The result is lazily created and there will be no execution of
     * the query handler before there is a subscription to the initial result. In order not to miss updates, the query
     * bus will queue all updates which happen after the subscription query is done and once the subscription to the
     * flux is made, these updates will be emitted.
     * <p>
     * If there is an error during retrieving or consuming initial result, stream for incremental updates is NOT
     * interrupted.
     * </p>
     * <p>
     * If there is an error during emitting an update, subscription is cancelled causing further emits not reaching the
     * destination.
     * </p>
     * <p>
     * Backpressure mechanism to be used is {@link SubscriptionQueryBackpressure#defaultBackpressure()}. The size of
     * buffer which accumulates the updates (not to be missed) is {@link Queues#SMALL_BUFFER_SIZE}.
     * </p>
     *
     * @param query the query
     * @param <Q>   the payload type of the query
     * @param <I>   the response type of the query
     * @param <U>   the incremental response types of the query
     * @return query result containing initial result and incremental updates
     */
    default <Q, I, U> SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> subscriptionQuery(
            SubscriptionQueryMessage<Q, I, U> query) {
        return subscriptionQuery(query, SubscriptionQueryBackpressure.defaultBackpressure(), Queues.SMALL_BUFFER_SIZE);
    }

    /**
     * Dispatch the given {@code query} to a single QueryHandler subscribed to the given {@code query}'s
     * queryName/initialResponseType/updateResponseType. The result is lazily created and there will be no execution of
     * the query handler before there is a subscription to the initial result. In order not to miss updates, the query
     * bus will queue all updates which happen after the subscription query is done and once the subscription to the
     * flux is made, these updates will be emitted.
     * <p>
     * If there is an error during retrieving or consuming initial result, stream for incremental updates is NOT
     * interrupted.
     * </p>
     * <p>
     * If there is an error during emitting an update, subscription is cancelled causing further emits not reaching the
     * destination.
     * </p>
     * <p>
     * Provided backpressure mechanism will be used to deal with fast emitters.
     * </p>
     *
     * @param query            the query
     * @param backpressure     the backpressure mechanism to be used for emitting updates
     * @param updateBufferSize the size of buffer which accumulates updates before subscription to the {@code flux} is
     *                         made
     * @param <Q>              the payload type of the query
     * @param <I>              the response type of the query
     * @param <U>              the incremental response types of the query
     * @return query result containing initial result and incremental updates
     */
    default <Q, I, U> SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> subscriptionQuery(
            SubscriptionQueryMessage<Q, I, U> query, SubscriptionQueryBackpressure backpressure, int updateBufferSize) {
        return new SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>>() {

            @Override
            public Mono<QueryResponseMessage<I>> initialResult() {
                return MonoWrapper.<QueryResponseMessage<I>>create(monoSinkWrapper -> query(query)
                        .thenAccept(monoSinkWrapper::success)
                        .exceptionally(t -> {
                            monoSinkWrapper.error(t);
                            return null;
                        })).getMono();
            }

            @Override
            public Flux<SubscriptionQueryUpdateMessage<U>> updates() {
                return Flux.empty();
            }

            @Override
            public boolean cancel() {
                return true;
            }
        };
    }

    /**
     * Gets the {@link QueryUpdateEmitter} associated with this {@link QueryBus}.
     *
     * @return the associated {@link QueryUpdateEmitter}
     */
    QueryUpdateEmitter queryUpdateEmitter();
}
