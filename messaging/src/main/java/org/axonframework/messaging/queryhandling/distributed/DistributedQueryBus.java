/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.queryhandling.distributed;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.Registration;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.core.DelayedMessageStream;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.queryhandling.QueryBus;
import org.axonframework.messaging.queryhandling.QueryHandler;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;
import org.axonframework.messaging.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.common.util.PriorityRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Implementation of a {@code QueryBus} that is aware of multiple instances of a {@code QueryBus} working together to
 * spread the load.
 * <p>
 * Each "physical" {@code QueryBus} instance is considered a "segment" of a conceptual distributed {@code QueryBus}.
 * <p>
 * The {@code DistributedQueryBus} relies on a {@link QueryBusConnector} to dispatch queries and query responses to
 * different segments of the {@code QueryBus}. Depending on the implementation used, each segment may run in a different
 * JVM.
 *
 * @author Steven van Beelen, Jan Galinski
 * @since 5.0.0
 */
public class DistributedQueryBus implements QueryBus {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final QueryBus localSegment;
    private final QueryBusConnector connector;
    private final ExecutorService queryingExecutor;
    private final Map<QueryMessage, QueryBusConnector.UpdateCallback> updateRegistry = new ConcurrentHashMap<>();
    private final boolean localQueryShortcut;
    private final Set<QualifiedName> subscriptions = ConcurrentHashMap.newKeySet();

    /**
     * Constructs a {@code DistributedQueryBus} using the given {@code localSegment} for
     * {@link #subscribe(QualifiedName, QueryHandler) subscribing} handlers and the given {@code connector} to
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
        this.localQueryShortcut = configuration.preferLocalQueryHandler();
        this.queryingExecutor = configuration.queryExecutorService();
        connector.onIncomingQuery(new DistributedHandler());

        // TODO - Add configuration for local segment shortcut on queries
    }

    @Override
    public QueryBus subscribe(@Nonnull QualifiedName queryName,
                              @Nonnull QueryHandler queryHandler) {
        subscriptions.add(queryName);
        localSegment.subscribe(queryName, queryHandler);
        FutureUtils.joinAndUnwrap(connector.subscribe(queryName));
        return this;
    }

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> query(@Nonnull QueryMessage query,
                                                     @Nullable ProcessingContext context) {
        if (localQueryShortcut && subscriptions.contains(query.type().qualifiedName()) ) {
            return localSegment.query(query, context);
        }
        return connector.query(query, context);
    }

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> subscriptionQuery(@Nonnull QueryMessage query,
                                                                 @Nullable ProcessingContext context,
                                                                 int updateBufferSize) {
        return connector.subscriptionQuery(query, context, updateBufferSize);
    }

    @Nonnull
    @Override
    public MessageStream<SubscriptionQueryUpdateMessage> subscribeToUpdates(@Nonnull QueryMessage query,
                                                                            int updateBufferSize) {
        // not ideal, but the AxonServer Connector doesn't support just subscribing to update yet
        return subscriptionQuery(query, null, updateBufferSize)
                .filter(e -> e.message() instanceof SubscriptionQueryUpdateMessage)
                .cast();
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> emitUpdate(@Nonnull Predicate<QueryMessage> filter,
                                              @Nonnull Supplier<SubscriptionQueryUpdateMessage> updateSupplier,
                                              @Nullable ProcessingContext context) {
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        updateRegistry.forEach((message, sender) -> {
            if (filter.test((message))) {
                tasks.add(sender.sendUpdate(updateSupplier.get()));
            }
        });
        return CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]));
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> completeSubscriptions(@Nonnull Predicate<QueryMessage> filter,
                                                         @Nullable ProcessingContext context) {
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        updateRegistry.forEach((message, sender) -> {
            if (filter.test((message))) {
                tasks.add(sender.complete());
            }
        });
        return CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]));
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> completeSubscriptionsExceptionally(
            @Nonnull Predicate<QueryMessage> filter,
            @Nonnull Throwable cause,
            @Nullable ProcessingContext context
    ) {
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        updateRegistry.forEach((message, sender) -> {
            if (filter.test((message))) {
                tasks.add(sender.completeExceptionally(cause));
            }
        });
        return CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]));
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(localSegment);
        descriptor.describeProperty("connector", connector);
    }

    private class DistributedHandler implements QueryBusConnector.Handler {

        private static final AtomicLong TASK_SEQUENCE = new AtomicLong(Long.MIN_VALUE);

        @Override
        public MessageStream<QueryResponseMessage> query(@Nonnull QueryMessage query) {
            int priority = query.priority().orElse(0);
            if (logger.isDebugEnabled()) {
                logger.debug("Received query [{}] for processing with priority [{}].",
                             query.type(), priority);
            }
            long sequence = TASK_SEQUENCE.incrementAndGet();
            CompletableFuture<MessageStream<QueryResponseMessage>> localResult = new CompletableFuture<>();
            queryingExecutor.execute(
                    new PriorityRunnable(() -> {
                        try {
                            localResult.complete(localSegment.query(query, null));
                        } catch (Exception e) {
                            localResult.completeExceptionally(e);
                        }
                    }, priority, sequence));

            return DelayedMessageStream.create(localResult);
        }


        @Nonnull
        @Override
        public Registration registerUpdateHandler(@Nonnull QueryMessage subscriptionQueryMessage,
                                                  @Nonnull QueryBusConnector.UpdateCallback updateCallback) {
            updateRegistry.put(subscriptionQueryMessage, updateCallback);
            return () -> updateRegistry.remove(subscriptionQueryMessage, updateCallback);
        }
    }
}
