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
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.reactivestreams.Publisher;
import reactor.util.concurrent.Queues;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * The mechanism that dispatches {@link QueryMessage queries} to their appropriate {@link QueryHandler query handler}.
 * <p>
 * Query handlers can {@link #subscribe(QueryHandlerName, QueryHandler) subscribed} to the query bus to handle queries
 * matching the {@link QueryHandlerName#queryName()} and {@link QueryHandlerName#responseName()}. Matching is done based
 * on the {@link QualifiedName} present in the {@link QueryMessage#type() query's type} and the {@code QualifiedName}
 * resulting from the {@link QueryMessage#responseType() response type}.
 * <p>
 * Hence, queries dispatched (through either {@link #query(QueryMessage, ProcessingContext)},
 * {@link #streamingQuery(StreamingQueryMessage)}, {@link #scatterGather(QueryMessage, long, TimeUnit)}, and
 * {@link #subscriptionQuery(SubscriptionQueryMessage)}) match a subscribed query handler based on "query name" and
 * "query response name."
 * <p>
 * There may be multiple handlers for each query- and response-name combination.
 *
 * @author Marc Gathier
 * @author Allard Buijze
 * @since 3.1
 */
public interface QueryBus extends QueryHandlerRegistry<QueryBus>, DescribableComponent {

    /**
     * Dispatch the given {@code query} to a single QueryHandler subscribed to the given {@code query}'s queryName and
     * responseType. This method returns all values returned by the Query Handler as a Collection. This may or may not
     * be the exact collection as defined in the Query Handler.
     * <p>
     * If the Query Handler defines a single return object (i.e. not a collection or array), that object is returned as
     * the sole entry in a singleton collection.
     * <p>
     * When no handlers are available that can answer the given {@code query}, the returned CompletableFuture will be
     * completed with a {@link NoHandlerForQueryException}.
     *
     * @param query the query
     * @return a CompletableFuture that resolves when the response is available
     */
    @Nonnull
    MessageStream<QueryResponseMessage> query(@Nonnull QueryMessage query, @Nullable ProcessingContext context);

    /**
     * Builds a {@link Publisher} of responses to the given {@code query}. The actual query is not dispatched until
     * there is a subscription to the result. The query is dispatched to a single query handler. Implementations may opt
     * for invoking several query handlers and then choosing a response from single one for performance or resilience
     * reasons.
     * <p>
     * When no handlers are available that can answer the given {@code query}, the return Publisher will be completed
     * with a {@link NoHandlerForQueryException}.
     *
     * @param query the streaming query message
     * @return a Publisher of responses
     */
    default Publisher<QueryResponseMessage> streamingQuery(StreamingQueryMessage query) {
        throw new UnsupportedOperationException("Streaming query is not supported by this QueryBus.");
    }

    /**
     * Dispatch the given {@code query} to all QueryHandlers subscribed to the given {@code query}'s
     * queryName/responseType. Returns a stream of results which blocks until all handlers have processed the request or
     * when the timeout occurs.
     * <p>
     * If no handlers are available to provide a result, or when all available handlers throw an exception while
     * attempting to do so, the returned Stream is empty.
     * <p>
     * Note that any terminal operation (such as {@link Stream#forEach(Consumer)}) on the Stream may cause it to block
     * until the {@code timeout} has expired, awaiting additional data to include in the stream.
     *
     * @param query   the query
     * @param timeout time to wait for results
     * @param unit    unit for the timeout
     * @return stream of query results
     */
    Stream<QueryResponseMessage> scatterGather(@Nonnull QueryMessage query, long timeout,
                                               @Nonnull TimeUnit unit);

    /**
     * Dispatch the given {@code query} to a single QueryHandler subscribed to the given {@code query}'s
     * queryName/initialResponseType/updateResponseType. The result is lazily created and there will be no execution of
     * the query handler before there is a subscription to the initial result. In order not to miss updates, the query
     * bus will queue all updates which happen after the subscription query is done and once the subscription to the
     * flux is made, these updates will be emitted.
     * <p>
     * If there is an error during retrieving or consuming initial result, stream for incremental updates is NOT
     * interrupted.
     * <p>
     * If there is an error during emitting an update, subscription is cancelled causing further emits not reaching the
     * destination.
     * <p>
     * The buffer size which accumulates the updates (not to be missed) is {@link Queues#SMALL_BUFFER_SIZE}.
     *
     * @param query the query
     * @param <Q>   the payload type of the query
     * @param <I>   the response type of the query
     * @param <U>   the incremental response types of the query
     * @return query result containing initial result and incremental updates
     */
    default <Q, I, U> SubscriptionQueryResult<QueryResponseMessage, SubscriptionQueryUpdateMessage> subscriptionQuery(
            @Nonnull SubscriptionQueryMessage<Q, I, U> query
    ) {
        return subscriptionQuery(query, Queues.SMALL_BUFFER_SIZE);
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
     * <p>
     * If there is an error during emitting an update, subscription is cancelled causing further emits not reaching the
     * destination.
     *
     * @param query            the query
     * @param updateBufferSize the size of buffer which accumulates updates before subscription to the {@code flux} is
     *                         made
     * @param <Q>              the payload type of the query
     * @param <I>              the response type of the query
     * @param <U>              the incremental response types of the query
     * @return query result containing initial result and incremental updates
     */
    <Q, I, U> SubscriptionQueryResult<QueryResponseMessage, SubscriptionQueryUpdateMessage> subscriptionQuery(
            @Nonnull SubscriptionQueryMessage<Q, I, U> query,
            int updateBufferSize
    );

    /**
     * Gets the {@link QueryUpdateEmitter} associated with this {@link QueryBus}.
     *
     * @return the associated {@link QueryUpdateEmitter}
     */
    QueryUpdateEmitter queryUpdateEmitter();
}
