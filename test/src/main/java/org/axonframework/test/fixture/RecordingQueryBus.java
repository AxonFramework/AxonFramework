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
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.queryhandling.QueryBus;
import org.axonframework.messaging.queryhandling.QueryHandler;
import org.axonframework.messaging.queryhandling.QueryHandlerRegistry;
import org.axonframework.messaging.queryhandling.QueryHandlingComponent;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;
import org.axonframework.messaging.queryhandling.SubscriptionQueryUpdateMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A QueryBus implementation recording all the queries that are dispatched. The recorded queries and their responses can
 * then be used to assert expectations with test cases.
 *
 * @author Theo Emanuelsson
 * @since 5.1.0
 */
@Internal
public class RecordingQueryBus implements QueryBus {

    private final QueryBus delegate;
    private final Map<QueryMessage, List<QueryResponseMessage>> recorded = new HashMap<>();

    /**
     * Creates a new {@code RecordingQueryBus} that will record all queries dispatched to the given {@code delegate}.
     *
     * @param delegate The {@link QueryBus} to which queries will be dispatched.
     */
    public RecordingQueryBus(@Nonnull QueryBus delegate) {
        this.delegate = Objects.requireNonNull(delegate, "The delegate QueryBus may not be null");
    }

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> query(@Nonnull QueryMessage query,
                                                     @Nullable ProcessingContext context) {
        MessageStream<QueryResponseMessage> responseStream = delegate.query(query, context);

        // Collect responses for recording using reduce
        List<QueryResponseMessage> responses = responseStream.reduce(
                new ArrayList<QueryResponseMessage>(),
                (list, entry) -> {
                    list.add(entry.message());
                    return list;
                }
        ).join();

        recorded.put(query, responses);

        // Return a new stream with the collected responses
        return MessageStream.fromIterable(responses);
    }

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> subscriptionQuery(@Nonnull QueryMessage query,
                                                                 @Nullable ProcessingContext context,
                                                                 int updateBufferSize) {
        return delegate.subscriptionQuery(query, context, updateBufferSize);
    }

    @Nonnull
    @Override
    public MessageStream<SubscriptionQueryUpdateMessage> subscribeToUpdates(@Nonnull QueryMessage query,
                                                                            int updateBufferSize) {
        return delegate.subscribeToUpdates(query, updateBufferSize);
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> emitUpdate(@Nonnull Predicate<QueryMessage> filter,
                                              @Nonnull Supplier<SubscriptionQueryUpdateMessage> updateSupplier,
                                              @Nullable ProcessingContext context) {
        return delegate.emitUpdate(filter, updateSupplier, context);
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> completeSubscriptions(@Nonnull Predicate<QueryMessage> filter,
                                                         @Nullable ProcessingContext context) {
        return delegate.completeSubscriptions(filter, context);
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> completeSubscriptionsExceptionally(@Nonnull Predicate<QueryMessage> filter,
                                                                      @Nonnull Throwable cause,
                                                                      @Nullable ProcessingContext context) {
        return delegate.completeSubscriptionsExceptionally(filter, cause, context);
    }

    @Override
    public QueryBus subscribe(@Nonnull QualifiedName queryName, @Nonnull QueryHandler queryHandler) {
        delegate.subscribe(queryName, queryHandler);
        return this;
    }

    @Override
    public QueryBus subscribe(@Nonnull QueryHandlingComponent handlingComponent) {
        delegate.subscribe(handlingComponent);
        return this;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
    }

    /**
     * Returns a map of all the {@link QueryMessage QueryMessages} dispatched, and their corresponding responses.
     *
     * @return A map of all the {@link QueryMessage QueryMessages} dispatched, and their corresponding responses.
     */
    public Map<QueryMessage, List<QueryResponseMessage>> recorded() {
        return Map.copyOf(recorded);
    }

    /**
     * Returns the queries that have been dispatched to this {@link QueryBus}.
     *
     * @return The queries that have been dispatched to this {@link QueryBus}
     */
    public List<QueryMessage> recordedQueries() {
        return List.copyOf(recorded.keySet());
    }

    /**
     * Returns the responses for the given {@code query}.
     *
     * @param query The query for which the responses are returned.
     * @return The responses for the given {@code query}. May be an empty list if the query has not been dispatched yet.
     */
    @Nonnull
    public List<QueryResponseMessage> responsesOf(@Nonnull QueryMessage query) {
        Objects.requireNonNull(query, "Query Message may not be null.");
        return recorded.getOrDefault(query, List.of());
    }

    /**
     * Resets this recording {@link QueryBus}, by removing all recorded {@link QueryMessage QueryMessages}.
     *
     * @return This recording {@link QueryBus}, for fluent interfacing.
     */
    public RecordingQueryBus reset() {
        recorded.clear();
        return this;
    }
}
