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

package org.axonframework.queryhandling.interceptors;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.annotations.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryHandlerName;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * A {@link QueryBus} decorator that wraps {@link QueryHandler query handlers} with
 * {@link MessageHandlerInterceptor handler interceptors} when they are subscribed.
 * <p>
 * This class is used internally by the {@link InterceptingQueryBus} to separate handler interception concerns from
 * dispatch interception. All query handling methods delegate to the wrapped {@code delegate} bus, except for
 * {@link #subscribe(QueryHandlerName, QueryHandler)} which wraps the handler with interceptors before delegating.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@Internal
class HandlerInterceptingQueryBus implements QueryBus {

    private final QueryBus delegate;
    private final List<MessageHandlerInterceptor<? super QueryMessage>> handlerInterceptors;

    /**
     * Constructs a {@code HandlerInterceptingQueryBus} that wraps query handlers with the given
     * {@code handlerInterceptors}.
     *
     * @param delegate            The delegate {@link QueryBus} to forward all operations to.
     * @param handlerInterceptors The list of {@link MessageHandlerInterceptor interceptors} to apply to query
     *                            handlers.
     */
    HandlerInterceptingQueryBus(@Nonnull QueryBus delegate,
                                @Nonnull List<MessageHandlerInterceptor<? super QueryMessage>> handlerInterceptors) {
        this.delegate = requireNonNull(delegate, "The delegate query bus must not be null.");
        this.handlerInterceptors = requireNonNull(handlerInterceptors, "The handler interceptors must not be null.");
    }

    @Override
    public HandlerInterceptingQueryBus subscribe(@Nonnull QueryHandlerName handlerName,
                                                  @Nonnull QueryHandler queryHandler) {
        if (handlerInterceptors.isEmpty()) {
            delegate.subscribe(handlerName, queryHandler);
        } else {
            delegate.subscribe(handlerName, new InterceptingHandler(queryHandler, handlerInterceptors));
        }
        return this;
    }

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> query(@Nonnull QueryMessage query,
                                                     @Nullable ProcessingContext context) {
        return delegate.query(query, context);
    }

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> subscriptionQuery(@Nonnull SubscriptionQueryMessage query,
                                                                 @Nullable ProcessingContext context,
                                                                 int updateBufferSize) {
        return delegate.subscriptionQuery(query, context, updateBufferSize);
    }

    @Nonnull
    @Override
    public MessageStream<SubscriptionQueryUpdateMessage> subscribeToUpdates(@Nonnull SubscriptionQueryMessage query,
                                                                            int updateBufferSize) {
        return delegate.subscribeToUpdates(query, updateBufferSize);
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> emitUpdate(@Nonnull Predicate<SubscriptionQueryMessage> filter,
                                              @Nonnull Supplier<SubscriptionQueryUpdateMessage> updateSupplier,
                                              @Nullable ProcessingContext context) {
        return delegate.emitUpdate(filter, updateSupplier, context);
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> completeSubscriptions(@Nonnull Predicate<SubscriptionQueryMessage> filter,
                                                         @Nullable ProcessingContext context) {
        return delegate.completeSubscriptions(filter, context);
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> completeSubscriptionsExceptionally(
            @Nonnull Predicate<SubscriptionQueryMessage> filter,
            @Nonnull Throwable cause,
            @Nullable ProcessingContext context
    ) {
        return delegate.completeSubscriptionsExceptionally(filter, cause, context);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("handlerInterceptors", handlerInterceptors);
    }

    private static class InterceptingHandler implements QueryHandler {

        private final QueryMessageHandlerInterceptorChain interceptorChain;

        private InterceptingHandler(QueryHandler handler,
                                    List<MessageHandlerInterceptor<? super QueryMessage>> interceptors) {
            this.interceptorChain = new QueryMessageHandlerInterceptorChain(interceptors, handler);
        }

        @Nonnull
        @Override
        public MessageStream<QueryResponseMessage> handle(@Nonnull QueryMessage query,
                                                          @Nonnull ProcessingContext context) {
            return interceptorChain.proceed(query, context)
                                   .cast();
        }
    }
}
