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
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.SubscriptionQueryResponse;
import org.reactivestreams.Publisher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link QueryGateway} implementation that records all queries dispatched through it. The recorded queries can then
 * be used to assert expectations in test cases.
 * <p>
 * This class records different types of queries separately:
 * <ul>
 *   <li>Single queries - via {@link #query(Object, Class, ProcessingContext)}</li>
 *   <li>Multiple queries - via {@link #queryMany(Object, Class, ProcessingContext)}</li>
 *   <li>Streaming queries - via {@link #streamingQuery(Object, Class, ProcessingContext)}</li>
 *   <li>Subscription queries - via {@link #subscriptionQuery(Object, Class, Class, ProcessingContext, int)}</li>
 * </ul>
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@Internal
public class RecordingQueryGateway implements QueryGateway {

    private final QueryGateway delegate;

    // Record different query types separately
    private final Map<Object, CompletableFuture<?>> recordedQueries = new HashMap<>();
    private final Map<Object, CompletableFuture<List<?>>> recordedQueryMany = new HashMap<>();
    private final Map<Object, Publisher<?>> recordedStreamingQueries = new HashMap<>();
    private final Map<Object, SubscriptionQueryResponse<?, ?>> recordedSubscriptionQueries = new HashMap<>();

    /**
     * Creates a new {@code RecordingQueryGateway} that will record all queries dispatched to the given
     * {@code delegate}.
     *
     * @param delegate The {@link QueryGateway} to which queries will be dispatched.
     */
    public RecordingQueryGateway(@Nonnull QueryGateway delegate) {
        this.delegate = Objects.requireNonNull(delegate, "The delegate QueryGateway may not be null");
    }

    @Override
    @Nonnull
    public <R> CompletableFuture<R> query(@Nonnull Object query,
                                          @Nonnull Class<R> responseType,
                                          @Nullable ProcessingContext context) {
        CompletableFuture<R> result = delegate.query(query, responseType, context);
        recordedQueries.put(query, result);
        return result;
    }

    @Override
    @Nonnull
    @SuppressWarnings("unchecked")
    public <R> CompletableFuture<List<R>> queryMany(@Nonnull Object query,
                                                    @Nonnull Class<R> responseType,
                                                    @Nullable ProcessingContext context) {
        CompletableFuture<List<R>> result = delegate.queryMany(query, responseType, context);
        recordedQueryMany.put(query, (CompletableFuture<List<?>>) (CompletableFuture<?>) result);
        return result;
    }

    @Override
    @Nonnull
    public <R> Publisher<R> streamingQuery(@Nonnull Object query,
                                           @Nonnull Class<R> responseType,
                                           @Nullable ProcessingContext context) {
        Publisher<R> result = delegate.streamingQuery(query, responseType, context);
        recordedStreamingQueries.put(query, result);
        return result;
    }

    @Override
    @Nonnull
    public <I, U> SubscriptionQueryResponse<I, U> subscriptionQuery(@Nonnull Object query,
                                                                    @Nonnull Class<I> initialResponseType,
                                                                    @Nonnull Class<U> updateResponseType,
                                                                    @Nullable ProcessingContext context,
                                                                    int updateBufferSize) {
        SubscriptionQueryResponse<I, U> result = delegate.subscriptionQuery(query, initialResponseType, updateResponseType, context, updateBufferSize);
        recordedSubscriptionQueries.put(query, result);
        return result;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
    }

    /**
     * Returns map of all single queries dispatched and their corresponding results.
     *
     * @return A map of all single queries dispatched and their corresponding results.
     */
    public Map<Object, CompletableFuture<?>> recordedQueries() {
        return Map.copyOf(recordedQueries);
    }

    /**
     * Returns map of all "query many" queries dispatched and their corresponding results.
     *
     * @return A map of all "query many" queries dispatched and their corresponding results.
     */
    public Map<Object, CompletableFuture<List<?>>> recordedQueryMany() {
        return Map.copyOf(recordedQueryMany);
    }

    /**
     * Returns map of all streaming queries dispatched and their corresponding results.
     *
     * @return A map of all streaming queries dispatched and their corresponding results.
     */
    public Map<Object, Publisher<?>> recordedStreamingQueries() {
        return Map.copyOf(recordedStreamingQueries);
    }

    /**
     * Returns map of all subscription queries dispatched and their corresponding results.
     *
     * @return A map of all subscription queries dispatched and their corresponding results.
     */
    public Map<Object, SubscriptionQueryResponse<?, ?>> recordedSubscriptionQueries() {
        return Map.copyOf(recordedSubscriptionQueries);
    }

    /**
     * Returns all queries that have been dispatched to this {@link QueryGateway}, regardless of type.
     *
     * @return All queries that have been dispatched to this {@link QueryGateway}.
     */
    public List<Object> allRecordedQueries() {
        List<Object> allQueries = new ArrayList<>();
        allQueries.addAll(recordedQueries.keySet());
        allQueries.addAll(recordedQueryMany.keySet());
        allQueries.addAll(recordedStreamingQueries.keySet());
        allQueries.addAll(recordedSubscriptionQueries.keySet());
        return allQueries;
    }

    /**
     * Returns the result of the given single {@code query}.
     *
     * @param query The query for which the result is returned.
     * @return The result of the given {@code query}. May be {@code null} if the query has not been dispatched yet.
     */
    @Nullable
    public CompletableFuture<?> resultOf(@Nonnull Object query) {
        Objects.requireNonNull(query, "Query may not be null.");
        return recordedQueries.get(query);
    }

    /**
     * Returns the result of the given "query many" {@code query}.
     *
     * @param query The query for which the result is returned.
     * @return The result of the given {@code query}. May be {@code null} if the query has not been dispatched yet.
     */
    @Nullable
    public CompletableFuture<List<?>> resultOfMany(@Nonnull Object query) {
        Objects.requireNonNull(query, "Query may not be null.");
        return recordedQueryMany.get(query);
    }

    /**
     * Returns the result of the given streaming {@code query}.
     *
     * @param query The query for which the result is returned.
     * @return The result of the given {@code query}. May be {@code null} if the query has not been dispatched yet.
     */
    @Nullable
    public Publisher<?> resultOfStreaming(@Nonnull Object query) {
        Objects.requireNonNull(query, "Query may not be null.");
        return recordedStreamingQueries.get(query);
    }

    /**
     * Returns the result of the given subscription {@code query}.
     *
     * @param query The query for which the result is returned.
     * @return The result of the given {@code query}. May be {@code null} if the query has not been dispatched yet.
     */
    @Nullable
    public SubscriptionQueryResponse<?, ?> resultOfSubscription(@Nonnull Object query) {
        Objects.requireNonNull(query, "Query may not be null.");
        return recordedSubscriptionQueries.get(query);
    }

    /**
     * Resets this recording {@link QueryGateway}, by removing all recorded queries.
     *
     * @return This recording {@link QueryGateway}, for fluent interfacing.
     */
    public RecordingQueryGateway reset() {
        recordedQueries.clear();
        recordedQueryMany.clear();
        recordedStreamingQueries.clear();
        recordedSubscriptionQueries.clear();
        return this;
    }
}