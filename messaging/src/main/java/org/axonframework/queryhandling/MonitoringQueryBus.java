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
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.configuration.DecoratorDefinition;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.reactivestreams.Publisher;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * A {@link QueryBus} wrapper that supports a {@link org.axonframework.monitoring.MessageMonitor}. Actual dispatching
 * and handling of queries is done by a delegate.
 * <p>
 * This {@link MonitoringQueryBus} is typically registered as a
 * {@link org.axonframework.configuration.ComponentRegistry#registerDecorator(DecoratorDefinition) decorator} and
 * automatically kicks in whenever a {@link QueryMessage} specific {@link MessageMonitor} is present.
 */
public class MonitoringQueryBus implements QueryBus {

    /**
     * The order in which the {@link MonitoringQueryBus} is applied as a
     * {@link org.axonframework.configuration.ComponentRegistry#registerDecorator(DecoratorDefinition) decorator} to the
     * {@link QueryBus}.
     * <p>
     * As such, any decorator with a lower value will be applied to the delegate, and any higher value will be applied
     * to the {@code MonitoringQueryBus} itself. Using the same value can either lead to application of the decorator
     * to the delegate or the {@code MonitoringQueryBus}, depending on the order of registration.
     * <p>
     * The order of the {@code MonitoringQueryBus} is set to {@code Integer.MIN_VALUE + 100} to ensure it is applied
     * very early in the configuration process, but not the earliest to allow for other decorators to be applied.
     */
    public static final int DECORATION_ORDER = Integer.MIN_VALUE + 100;

    private final QueryBus delegate;
    private final MessageMonitor<? super QueryMessage> messageMonitor;

    public MonitoringQueryBus(@Nonnull final QueryBus delegate,
                              @Nullable final MessageMonitor<? super QueryMessage> messageMonitor) {
        this.delegate = requireNonNull(delegate, "delegate cannot be null");
        this.messageMonitor = messageMonitor != null ? messageMonitor : NoOpMessageMonitor.INSTANCE;
    }

    @Override
    public @Nonnull MessageStream<QueryResponseMessage> query(@Nonnull QueryMessage query,
                                                              @Nullable ProcessingContext context) {
        // Note: Monitoring callbacks are typically applied at handler subscription time. This wrapper delegates.
        return delegate.query(query, context);
    }

    @Override
    public @Nonnull Publisher<QueryResponseMessage> streamingQuery(@Nonnull StreamingQueryMessage query,
                                                                   @Nullable ProcessingContext context) {
        return delegate.streamingQuery(query, context);
    }

    @Override
    public @Nonnull SubscriptionQueryResponseMessages subscriptionQuery(@Nonnull SubscriptionQueryMessage query,
                                                                        @Nullable ProcessingContext context,
                                                                        int updateBufferSize) {
        return delegate.subscriptionQuery(query, context, updateBufferSize);
    }

    @Override
    public @Nonnull UpdateHandler subscribeToUpdates(@Nonnull SubscriptionQueryMessage query, int updateBufferSize) {
        return delegate.subscribeToUpdates(query, updateBufferSize);
    }

    @Override
    public @Nonnull CompletableFuture<Void> emitUpdate(@Nonnull Predicate<SubscriptionQueryMessage> filter,
                                                       @Nonnull Supplier<SubscriptionQueryUpdateMessage> updateSupplier,
                                                       @Nullable ProcessingContext context) {
        return delegate.emitUpdate(filter, updateSupplier, context);
    }

    @Override
    public @Nonnull CompletableFuture<Void> completeSubscriptions(@Nonnull Predicate<SubscriptionQueryMessage> filter,
                                                                  @Nullable ProcessingContext context) {
        return delegate.completeSubscriptions(filter, context);
    }

    @Override
    public @Nonnull CompletableFuture<Void> completeSubscriptionsExceptionally(
            @Nonnull Predicate<SubscriptionQueryMessage> filter,
            @Nonnull Throwable cause,
            @Nullable ProcessingContext context
    ) {
        return delegate.completeSubscriptionsExceptionally(filter, cause, context);
    }

    @Override
    public QueryBus subscribe(@Nonnull Set<QueryHandlerName> names, @Nonnull QueryHandler queryHandler) {
        delegate.subscribe(names, queryHandler);
        return this;
    }

    @Override
    public QueryBus subscribe(@Nonnull QualifiedName queryName,
                              @Nonnull QualifiedName responseName,
                              @Nonnull QueryHandler queryHandler) {
        delegate.subscribe(queryName, responseName, queryHandler);
        return this;
    }

    @Override
    public QueryBus subscribe(@Nonnull QueryHandlerName handlerName, @Nonnull QueryHandler queryHandler) {
        delegate.subscribe(handlerName, queryHandler);
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
        descriptor.describeProperty("messageMonitor", messageMonitor);
    }
}
