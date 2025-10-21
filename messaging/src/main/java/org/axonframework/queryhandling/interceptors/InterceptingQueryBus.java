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
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.DecoratorDefinition;
import org.axonframework.messaging.DefaultMessageDispatchInterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * A {@code QueryBus} wrapper that supports both {@link MessageHandlerInterceptor MessageHandlerInterceptors} and
 * {@link MessageDispatchInterceptor MessageDispatchInterceptors}. Actual dispatching and handling of queries is done
 * by a delegate.
 * <p>
 * This {@code InterceptingQueryBus} is typically registered as a
 * {@link ComponentRegistry#registerDecorator(DecoratorDefinition) decorator} and automatically kicks in whenever
 * {@link QueryMessage} specific {@code MessageHandlerInterceptors} or any {@code MessageDispatchInterceptors} are
 * present.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class InterceptingQueryBus implements QueryBus {

    /**
     * The order in which the {@link InterceptingQueryBus} is applied as a
     * {@link ComponentRegistry#registerDecorator(DecoratorDefinition) decorator} to the {@link QueryBus}.
     * <p>
     * As such, any decorator with a lower value will be applied to the delegate, and any higher value will be applied
     * to the {@code InterceptingQueryBus} itself. Using the same value can either lead to application of the
     * decorator to the delegate or the {@code InterceptingQueryBus}, depending on the order of registration.
     * <p>
     * The order of the {@code InterceptingQueryBus} is set to {@code Integer.MIN_VALUE + 100} to ensure it is applied
     * very early in the configuration process, but not the earliest to allow for other decorators to be applied.
     */
    public static final int DECORATION_ORDER = Integer.MIN_VALUE + 100;

    private final QueryBus delegate;
    private final List<MessageHandlerInterceptor<? super QueryMessage>> handlerInterceptors;
    private final List<MessageDispatchInterceptor<? super QueryMessage>> dispatchInterceptors;
    private final InterceptingDispatcher interceptingDispatcher;

    /**
     * Constructs a {@code InterceptingQueryBus}, delegating dispatching and handling logic to the given
     * {@code delegate}. The given {@code handlerInterceptors} are wrapped around the
     * {@link QueryHandler query handlers} when subscribing. The given {@code dispatchInterceptors} are invoked
     * before dispatching is provided to the given {@code delegate}.
     *
     * @param delegate             The delegate {@code QueryBus} that will handle all dispatching and handling logic.
     * @param handlerInterceptors  The interceptors to invoke before handling a query and if present on the query
     *                             result.
     * @param dispatchInterceptors The interceptors to invoke before dispatching a query and on the query result.
     */
    public InterceptingQueryBus(
            @Nonnull QueryBus delegate,
            @Nonnull List<MessageHandlerInterceptor<? super QueryMessage>> handlerInterceptors,
            @Nonnull List<MessageDispatchInterceptor<? super QueryMessage>> dispatchInterceptors
    ) {
        this.delegate = requireNonNull(delegate, "The query bus delegate must not be null.");
        this.handlerInterceptors = new ArrayList<>(
                requireNonNull(handlerInterceptors, "The handler interceptors must not be null.")
        );
        this.dispatchInterceptors = new ArrayList<>(
                requireNonNull(dispatchInterceptors, "The dispatch interceptors must not be null.")
        );
        this.interceptingDispatcher = new InterceptingDispatcher(dispatchInterceptors, this::dispatchQuery);
    }

    @Override
    public InterceptingQueryBus subscribe(@Nonnull QueryHandlerName handlerName,
                                          @Nonnull QueryHandler queryHandler) {
        delegate.subscribe(handlerName, new InterceptingHandler(queryHandler, handlerInterceptors));
        return this;
    }

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> query(@Nonnull QueryMessage query,
                                                     @Nullable ProcessingContext context) {
        return interceptingDispatcher.interceptAndDispatch(query, context);
    }

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> subscriptionQuery(@Nonnull SubscriptionQueryMessage query,
                                                                 @Nullable ProcessingContext context,
                                                                 int updateBufferSize) {
        // For subscription queries, delegate directly to the underlying bus
        // The initial query and updates will be handled by the delegate bus
        // Handler interceptors are already applied via subscribe(), so they will be invoked
        // when the query is handled
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
        // Delegate directly to the underlying bus
        // Note: Subscription query updates are responses, not queries, so they're not intercepted
        // by query dispatch interceptors
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

    private MessageStream<?> dispatchQuery(@Nonnull Message message,
                                          @Nullable ProcessingContext processingContext) {
        if (!(message instanceof QueryMessage query)) {
            // The compiler should avoid this from happening.
            throw new IllegalArgumentException("Unsupported message implementation: " + message);
        }
        return delegate.query(query, processingContext);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("handlerInterceptors", handlerInterceptors);
        descriptor.describeProperty("dispatchInterceptors", dispatchInterceptors);
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

    private static class InterceptingDispatcher {

        private final DefaultMessageDispatchInterceptorChain<? super QueryMessage> interceptorChain;

        private InterceptingDispatcher(
                List<MessageDispatchInterceptor<? super QueryMessage>> interceptors,
                BiFunction<? super QueryMessage, ProcessingContext, MessageStream<?>> dispatcher
        ) {
            this.interceptorChain = new DefaultMessageDispatchInterceptorChain<>(interceptors, dispatcher);
        }

        private MessageStream<QueryResponseMessage> interceptAndDispatch(
                @Nonnull QueryMessage query,
                @Nullable ProcessingContext context
        ) {
            return interceptorChain.proceed(query, context)
                                  .cast();
        }
    }
}
