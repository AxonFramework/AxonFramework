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
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The mechanism that dispatches {@link QueryMessage queries} to their appropriate {@link QueryHandler query handler}.
 * <p>
 * Query handlers can {@link #subscribe(QueryHandlerName, QueryHandler) subscribe} to the query bus to handle queries
 * matching the {@link QueryHandlerName#queryName()} and {@link QueryHandlerName#responseName()}. Matching is done based
 * on the {@link QualifiedName} present in the {@link QueryMessage#type() query's type} and the {@code QualifiedName}
 * resulting from the {@link QueryMessage#responseType() response type}.
 * <p>
 * Hence, queries dispatched (through either {@link #query(QueryMessage, ProcessingContext)},
 * {@link #streamingQuery(StreamingQueryMessage, ProcessingContext)}, and
 * {@link #subscriptionQuery(SubscriptionQueryMessage, ProcessingContext, int)}) match a subscribed query handler based
 * on "query name" and "query response name."
 * <p>
 * There may be multiple handlers for each query- and response-name combination.
 *
 * @author Marc Gathier
 * @author Allard Buijze
 * @since 3.1
 */
public interface QueryBus extends QueryHandlerRegistry<QueryBus>, DescribableComponent {

    /**
     * Dispatch the given {@code query} to a {@link QueryHandler}
     * {@link #subscribe(QueryHandlerName, QueryHandler) subscribed} to the given {@code query}'s
     * {@link MessageType#qualifiedName() query name} and {@link QueryMessage#responseType()}, returning a
     * {@link MessageStream} of {@link QueryResponseMessage responses} to the given {@code query}.
     * <p>
     * The resulting {@code MessageStream} will contain 0, 1, or N {@link QueryResponseMessage QueryResponseMessages},
     * depending on the {@code QueryHandler} that handled the given {@code query}.
     * <p>
     * As several {@code QueryHandlers} can be registered for the same query name and response name, this method will
     * loop through them (in insert order) until one has a suitable return valuable. A suitable response is any value or
     * user exception returned from a {@code QueryHandler} When no handlers are available that can answer the given
     * {@code query}, the returned {@code MessageStream} will have {@link MessageStream#failed(Throwable) failed} with a
     * {@link NoHandlerForQueryException}.
     *
     * @param query   The query to dispatch.
     * @param context The processing context under which the query is being published (can be {@code null}).
     * @return A {@code MessageStream} containing either 0, 1, or N {@link QueryResponseMessage QueryResponseMessages}.
     * @throws NoHandlerForQueryException When no {@link QueryHandler} is registered for the given {@code query}'s
     *                                    {@link MessageType#qualifiedName() query name} and
     *                                    {@link QueryMessage#responseType()}.
     */
    @Nonnull
    MessageStream<QueryResponseMessage> query(@Nonnull QueryMessage query, @Nullable ProcessingContext context);

    /**
     * Dispatch the given {@code query} to a {@link QueryHandler}
     * {@link #subscribe(QueryHandlerName, QueryHandler) subscribed} to the given {@code query}'s
     * {@link MessageType#qualifiedName() query name} and {@link QueryMessage#responseType()}, returning a
     * {@link Publisher} of {@link QueryResponseMessage responses} to the given {@code query}.
     * <p>
     * The actual query is not dispatched until there is a subscription to the result. The query is dispatched to a
     * single query handler. Implementations may opt for invoking several query handlers and then choosing a response
     * from single one for performance or resilience reasons.
     * <p>
     * When no handlers are available that can answer the given {@code query}, the return Publisher will be completed
     * with a {@link NoHandlerForQueryException}.
     *
     * @param query   The query to dispatch.
     * @param context The processing context under which the query is being published (can be {@code null}).
     * @return A {@code Publisher} of {@link QueryResponseMessage responses}.
     * @throws NoHandlerForQueryException When no {@link QueryHandler} is registered for the given {@code query}'s
     *                                    {@link MessageType#qualifiedName() query name} and
     *                                    {@link QueryMessage#responseType()}.
     */
    @Nonnull
    default Publisher<QueryResponseMessage> streamingQuery(@Nonnull StreamingQueryMessage query,
                                                           @Nullable ProcessingContext context) {
        return Mono.fromSupplier(() -> query(query, context))
                   .flatMapMany(MessageStream::asFlux)
                   .map(MessageStream.Entry::message);
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
     * @param query            The subscription query to dispatch.
     * @param context          The processing context under which the query is being published (can be {@code null}).
     * @param updateBufferSize the size of buffer which accumulates updates before subscription to the {@code flux} is
     *                         made.
     * @return query result containing initial result and incremental updates
     */
    @Nonnull
    SubscriptionQueryResponseMessages subscriptionQuery(@Nonnull SubscriptionQueryMessage query,
                                                        @Nullable ProcessingContext context,
                                                        int updateBufferSize);

    /**
     * Subscribes the given {@code query} with the given {@code updateBufferSize}, resulting in an {@link UpdateHandler}
     * providing a {@link reactor.core.publisher.Flux} to the emitted updates.
     * <p>
     * Can be used directly instead when fine-grained control of update handlers is required. If using the
     * {@link UpdateHandler} directly is not mandatory for your use case, we strongly recommend using
     * {@link #subscriptionQuery(SubscriptionQueryMessage, ProcessingContext, int)} instead.
     *
     * @param query            The subscription query for which we register an update handler.
     * @param updateBufferSize The size of buffer that accumulates updates before a subscription to the
     *                         {@link UpdateHandler#updates()} is made.
     * @return The update handler containing the {@link reactor.core.publisher.Flux} of emitted updates, as well as
     * {@link UpdateHandler#cancel()} and {@link UpdateHandler#complete()} hooks.
     * @throws SubscriptionQueryAlreadyRegisteredException Whenever an update handler was already registered for the
     *                                                     given {@code query}.
     */
    @Nonnull
    UpdateHandler subscribeToUpdates(@Nonnull SubscriptionQueryMessage query, int updateBufferSize);

    /**
     * Emits the outcome of the {@code updateSupplier} to
     * {@link QueryBus#subscriptionQuery(SubscriptionQueryMessage, ProcessingContext, int) subscription queries}
     * matching the given {@code queryName} and given {@code filter}.
     *
     * @param filter         A predicate filtering on {@link SubscriptionQueryMessage SubscriptionQueryMessages}. The
     *                       {@code updateSupplier} will only be sent to subscription queries matching this filter.
     * @param updateSupplier The update supplier to emit for
     *                       {@link QueryBus#subscriptionQuery(SubscriptionQueryMessage, ProcessingContext, int)
     *                       subscription queries} matching the given {@code filter}.
     * @param context        The processing context under which the updateSupplier is being emitted (can be
     *                       {@code null}).
     * @return A future completing whenever the updateSupplier has been emitted.
     */
    @Nonnull
    CompletableFuture<Void> emitUpdate(@Nonnull Predicate<SubscriptionQueryMessage> filter,
                                       @Nonnull Supplier<SubscriptionQueryUpdateMessage> updateSupplier,
                                       @Nullable ProcessingContext context);

    /**
     * Completes
     * {@link QueryBus#subscriptionQuery(SubscriptionQueryMessage, ProcessingContext, int) subscription queries}
     * matching the given {@code filter}.
     * <p>
     * To be used whenever there are no subsequent update to
     * {@link #emitUpdate(Predicate, Supplier, ProcessingContext) emit} left.
     *
     * @param filter  A predicate filtering on {@link SubscriptionQueryMessage SubscriptionQueryMessages}. Subscription
     *                queries matching this filter will be completed.
     * @param context The processing context within which to complete subscription queries (can be {@code null}).
     * @return A future completing whenever all matching
     * {@link QueryBus#subscriptionQuery(SubscriptionQueryMessage, ProcessingContext, int) subscription queries} have
     * been completed.
     */
    @Nonnull
    CompletableFuture<Void> completeSubscriptions(@Nonnull Predicate<SubscriptionQueryMessage> filter,
                                                  @Nullable ProcessingContext context);

    /**
     * Completes
     * {@link QueryBus#subscriptionQuery(SubscriptionQueryMessage, ProcessingContext, int) subscription queries}
     * matching the given {@code filter} exceptionally with the given {@code cause}.
     * <p>
     * To be used whenever {@link #emitUpdate(Predicate, Supplier, ProcessingContext) emitting updates} should be
     * stopped due to some exception.
     *
     * @param filter  A predicate filtering on {@link SubscriptionQueryMessage SubscriptionQueryMessages}. Subscription
     *                queries matching this filter will be completed exceptionally.
     * @param cause   the cause of an error
     * @param context The processing context within which to complete subscription queries exceptionally (can be
     *                {@code null}).
     * @return A future completing whenever all matching
     * {@link QueryBus#subscriptionQuery(SubscriptionQueryMessage, ProcessingContext, int) subscription queries} have
     * been completed exceptionally.
     */
    @Nonnull
    CompletableFuture<Void> completeSubscriptionsExceptionally(@Nonnull Predicate<SubscriptionQueryMessage> filter,
                                                               @Nonnull Throwable cause,
                                                               @Nullable ProcessingContext context);
}
