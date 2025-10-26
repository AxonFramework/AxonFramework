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
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * A {@code QueryBus} wrapper that supports both {@link MessageHandlerInterceptor MessageHandlerInterceptors} and
 * {@link MessageDispatchInterceptor MessageDispatchInterceptors}. Actual dispatching and handling of queries is done by
 * a delegate.
 * <p>
 * This {@code InterceptingQueryBus} is typically registered as a
 * {@link ComponentRegistry#registerDecorator(DecoratorDefinition) decorator} and automatically kicks in whenever
 * {@link QueryMessage} specific {@code MessageHandlerInterceptors} or any {@code MessageDispatchInterceptors} are
 * present.
 * <p>
 * This implementation is a composition of {@link HandlerInterceptingQueryBus} and {@link DispatchInterceptingQueryBus},
 * which handle handler and dispatch interception respectively.
 *
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class InterceptingQueryBus implements QueryBus {

    /**
     * The order in which the {@link InterceptingQueryBus} is applied as a
     * {@link ComponentRegistry#registerDecorator(DecoratorDefinition) decorator} to the {@link QueryBus}.
     * <p>
     * As such, any decorator with a lower value will be applied to the delegate, and any higher value will be applied
     * to the {@code InterceptingQueryBus} itself. Using the same value can either lead to application of the decorator
     * to the delegate or the {@code InterceptingQueryBus}, depending on the order of registration.
     * <p>
     * The order of the {@code InterceptingQueryBus} is set to {@code Integer.MIN_VALUE + 100} to ensure it is applied
     * very early in the configuration process, but not the earliest to allow for other decorators to be applied.
     */
    public static final int DECORATION_ORDER = Integer.MIN_VALUE + 100;

    private final QueryBus interceptingBus;

    /**
     * Constructs a {@code InterceptingQueryBus}, delegating dispatching and handling logic to the given
     * {@code delegate}. The given {@code handlerInterceptors} are wrapped around the
     * {@link QueryHandler query handlers} when subscribing. The given {@code dispatchInterceptors} are invoked before
     * dispatching is provided to the given {@code delegate}. The given {@code updateDispatchInterceptors} are invoked
     * before emitting subscription query updates.
     *
     * @param delegate                   The delegate {@code QueryBus} that will handle all dispatching and handling
     *                                   logic.
     * @param handlerInterceptors        The interceptors to invoke before handling a query and if present on the query
     *                                   result.
     * @param dispatchInterceptors       The interceptors to invoke before dispatching a query and on the query result.
     * @param updateDispatchInterceptors The interceptors to invoke before emitting subscription query updates.
     */
    public InterceptingQueryBus(
            @Nonnull QueryBus delegate,
            @Nonnull List<MessageHandlerInterceptor<? super QueryMessage>> handlerInterceptors,
            @Nonnull List<MessageDispatchInterceptor<? super QueryMessage>> dispatchInterceptors,
            @Nonnull List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> updateDispatchInterceptors
    ) {
        requireNonNull(delegate, "The query bus delegate must not be null.");
        List<MessageHandlerInterceptor<? super QueryMessage>> handlerInterceptorsCopy = new ArrayList<>(
                requireNonNull(handlerInterceptors, "The handler interceptors must not be null.")
        );
        List<MessageDispatchInterceptor<? super QueryMessage>> dispatchInterceptorsCopy = new ArrayList<>(
                requireNonNull(dispatchInterceptors, "The dispatch interceptors must not be null.")
        );
        List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> updateDispatchInterceptorsCopy =
                new ArrayList<>(
                        requireNonNull(updateDispatchInterceptors, "The update dispatch interceptors must not be null.")
                );

        HandlerInterceptingQueryBus handlerInterceptingBus = new HandlerInterceptingQueryBus(
                delegate, handlerInterceptorsCopy
        );
        this.interceptingBus = new DispatchInterceptingQueryBus(
                handlerInterceptingBus,
                dispatchInterceptorsCopy,
                updateDispatchInterceptorsCopy
        );
    }

    @Override
    public InterceptingQueryBus subscribe(@Nonnull QueryHandlerName handlerName,
                                          @Nonnull QueryHandler queryHandler) {
        interceptingBus.subscribe(handlerName, queryHandler);
        return this;
    }

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> query(@Nonnull QueryMessage query,
                                                     @Nullable ProcessingContext context) {
        return interceptingBus.query(query, context);
    }

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> subscriptionQuery(@Nonnull SubscriptionQueryMessage query,
                                                                 @Nullable ProcessingContext context,
                                                                 int updateBufferSize) {
        return interceptingBus.subscriptionQuery(query, context, updateBufferSize);
    }

    @Nonnull
    @Override
    public MessageStream<SubscriptionQueryUpdateMessage> subscribeToUpdates(@Nonnull SubscriptionQueryMessage query,
                                                                            int updateBufferSize) {
        return interceptingBus.subscribeToUpdates(query, updateBufferSize);
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> emitUpdate(@Nonnull Predicate<SubscriptionQueryMessage> filter,
                                              @Nonnull Supplier<SubscriptionQueryUpdateMessage> updateSupplier,
                                              @Nullable ProcessingContext context) {
        return interceptingBus.emitUpdate(filter, updateSupplier, context);
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> completeSubscriptions(@Nonnull Predicate<SubscriptionQueryMessage> filter,
                                                         @Nullable ProcessingContext context) {
        return interceptingBus.completeSubscriptions(filter, context);
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> completeSubscriptionsExceptionally(
            @Nonnull Predicate<SubscriptionQueryMessage> filter,
            @Nonnull Throwable cause,
            @Nullable ProcessingContext context
    ) {
        return interceptingBus.completeSubscriptionsExceptionally(filter, cause, context);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        interceptingBus.describeTo(descriptor);
    }
}
