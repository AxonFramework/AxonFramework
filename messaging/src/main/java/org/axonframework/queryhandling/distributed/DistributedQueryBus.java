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

package org.axonframework.queryhandling.distributed;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryHandlerName;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryResponseMessages;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.UpdateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Implementation of a {@code QueryBus} that is aware of multiple instances of a {@code QueryBus} working together to
 * spread load.
 * <p>
 * Each "physical" {@code QueryBus} instance is considered a "segment" of a conceptual distributed {@code QueryBus}.
 * <p>
 * The {@code DistributedQueryBus} relies on a {@link QueryBusConnector} to dispatch queries and query responses to
 * different segments of the {@code QueryBus}. Depending on the implementation used, each segment may run in a different
 * JVM.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class DistributedQueryBus implements QueryBus {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final int QUERY_AND_RESPONSE_QUEUE_CAPACITY = 1000;

    private final QueryBus localSegment;
    private final QueryBusConnector connector;
    private final ExecutorService queryingExecutor;
    private final ExecutorService responseHandlingExecutor;

    /**
     * Constructs a {@code DistributedQueryBus} using the given {@code localSegment} for
     * {@link #subscribe(QueryHandlerName, QueryHandler) subscribing} handlers and the given {@code connector} to
     * dispatch and receive queries and query responses with, to and from different segments of the {@code QueryBus}.
     *
     * @param localSegment  The local {@code QueryBus} used to subscribe handlers to.
     * @param connector     The {@code QueryBusConnector} to dispatch and receive queries and query responses with.
     * @param configuration The {@code DistributedCommandBusConfiguration} containing the
     *                      {@link ExecutorService ExecutorServices} for querying and handling query responses.
     */
    public DistributedQueryBus(@Nonnull QueryBus localSegment,
                               @Nonnull QueryBusConnector connector,
                               @Nonnull DistributedQueryBusConfiguration configuration) {
        this.localSegment = localSegment;
        this.connector = connector;
        this.queryingExecutor =
                configuration.queryExecutorServiceFactory()
                             .createExecutorService(configuration,
                                                    new PriorityBlockingQueue<>(QUERY_AND_RESPONSE_QUEUE_CAPACITY));
        this.responseHandlingExecutor =
                configuration.queryResponseExecutorServiceFactory()
                             .createExecutorService(configuration,
                                                    new PriorityBlockingQueue<>(QUERY_AND_RESPONSE_QUEUE_CAPACITY));
    }

    @Override
    public QueryBus subscribe(@Nonnull QueryHandlerName handlerName,
                              @Nonnull QueryHandler queryHandler) {
        connector.subscribe(handlerName);
        return this;
    }

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> query(@Nonnull QueryMessage query,
                                                     @Nullable ProcessingContext context) {
        return connector.query(query, context);
    }

    @Nonnull
    @Override
    public SubscriptionQueryResponseMessages subscriptionQuery(@Nonnull SubscriptionQueryMessage query,
                                                               @Nullable ProcessingContext context,
                                                               int updateBufferSize) {
        return null;
    }

    @Nonnull
    @Override
    public UpdateHandler subscribeToUpdates(@Nonnull SubscriptionQueryMessage query, int updateBufferSize) {
        return null;
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> emitUpdate(@Nonnull Predicate<SubscriptionQueryMessage> filter,
                                              @Nonnull Supplier<SubscriptionQueryUpdateMessage> updateSupplier,
                                              @Nullable ProcessingContext context) {
        return null;
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> completeSubscriptions(@Nonnull Predicate<SubscriptionQueryMessage> filter,
                                                         @Nullable ProcessingContext context) {
        return null;
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> completeSubscriptionsExceptionally(
            @Nonnull Predicate<SubscriptionQueryMessage> filter,
            @Nonnull Throwable cause,
            @Nullable ProcessingContext context
    ) {
        return null;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(localSegment);
        descriptor.describeProperty("connector", connector);
    }
}
