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

package org.axonframework.test.fixture;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.annotations.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryHandlerName;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.StreamingQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryResponseMessages;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.UpdateHandler;
import org.reactivestreams.Publisher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A {@link QueryBus} implementation recording all the queries that are dispatched. The recorded queries can then be
 * used to assert expectations with test cases.
 * <p>
 * This class records:
 * <ul>
 *   <li>Regular queries and their responses</li>
 *   <li>Streaming queries and their responses</li>
 *   <li>Subscription queries and their initial responses</li>
 * </ul>
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@Internal
public class RecordingQueryBus implements QueryBus {

    private final QueryBus delegate;
    private final Map<QueryMessage, MessageStream<QueryResponseMessage>> recordedQueries = new HashMap<>();

    /**
     * Creates a new {@code RecordingQueryBus} that will record all queries dispatched to the given {@code delegate}.
     *
     * @param delegate The {@link QueryBus} to which queries will be dispatched.
     */
    public RecordingQueryBus(@Nonnull QueryBus delegate) {
        this.delegate = Objects.requireNonNull(delegate, "The delegate QueryBus may not be null");
    }

    @Override
    @Nonnull
    public MessageStream<QueryResponseMessage> query(@Nonnull QueryMessage query, @Nullable ProcessingContext context) {
        MessageStream<QueryResponseMessage> result = delegate.query(query, context);
        recordedQueries.put(query, result);
        return result;
    }

    @Override
    @Nonnull
    public Publisher<QueryResponseMessage> streamingQuery(@Nonnull StreamingQueryMessage query,
                                                          @Nullable ProcessingContext context) {
        Publisher<QueryResponseMessage> result = delegate.streamingQuery(query, context);
        MessageStream<QueryResponseMessage> queryResult = delegate.query(query, context);
        recordedQueries.put(query, queryResult);
        return result;
    }

    @Override
    @Nonnull
    public SubscriptionQueryResponseMessages subscriptionQuery(@Nonnull SubscriptionQueryMessage query,
                                                               @Nullable ProcessingContext context,
                                                               int updateBufferSize) {
        SubscriptionQueryResponseMessages result = delegate.subscriptionQuery(query, context, updateBufferSize);
        recordedQueries.put(query, result.initialResult());
        return result;
    }

    @Override
    @Nonnull
    public UpdateHandler subscribeToUpdates(@Nonnull SubscriptionQueryMessage query, int updateBufferSize) {
        return delegate.subscribeToUpdates(query, updateBufferSize);
    }

    @Override
    @Nonnull
    public CompletableFuture<Void> emitUpdate(@Nonnull Predicate<SubscriptionQueryMessage> filter,
                                              @Nonnull Supplier<SubscriptionQueryUpdateMessage> updateSupplier,
                                              @Nullable ProcessingContext context) {
        return delegate.emitUpdate(filter, updateSupplier, context);
    }

    @Override
    @Nonnull
    public CompletableFuture<Void> completeSubscriptions(@Nonnull Predicate<SubscriptionQueryMessage> filter,
                                                         @Nullable ProcessingContext context) {
        return delegate.completeSubscriptions(filter, context);
    }

    @Override
    @Nonnull
    public CompletableFuture<Void> completeSubscriptionsExceptionally(@Nonnull Predicate<SubscriptionQueryMessage> filter,
                                                                      @Nonnull Throwable cause,
                                                                      @Nullable ProcessingContext context) {
        return delegate.completeSubscriptionsExceptionally(filter, cause, context);
    }

    @Override
    public QueryBus subscribe(@Nonnull QueryHandlerName name, @Nonnull QueryHandler queryHandler) {
        return delegate.subscribe(name, queryHandler);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
    }

    /**
     * Returns map of all the {@link QueryMessage QueryMessages} dispatched, and their corresponding results.
     *
     * @return A map of all the {@link QueryMessage QueryMessages} dispatched, and their corresponding results.
     */
    public Map<QueryMessage, MessageStream<QueryResponseMessage>> recorded() {
        return Map.copyOf(recordedQueries);
    }

    /**
     * Returns the queries that have been dispatched to this {@link QueryBus}.
     *
     * @return The queries that have been dispatched to this {@link QueryBus}
     */
    public List<QueryMessage> recordedQueries() {
        return List.copyOf(recordedQueries.keySet());
    }

    /**
     * Returns the result of the given {@code query}.
     *
     * @param query The query for which the result is returned.
     * @return The result of the given {@code query}. May be {@code null} if the query has not been dispatched yet.
     */
    @Nullable
    public MessageStream<QueryResponseMessage> resultOf(@Nonnull QueryMessage query) {
        Objects.requireNonNull(query, "Query Message may not be null.");
        return recordedQueries.get(query);
    }

    /**
     * Resets this recording {@link QueryBus}, by removing all recorded {@link QueryMessage QueryMessages}.
     *
     * @return This recording {@link QueryBus}, for fluent interfacing.
     */
    public RecordingQueryBus reset() {
        recordedQueries.clear();
        return this;
    }
}