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

package org.axonframework.messaging.queryhandling.interception;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.DecoratorDefinition;
import org.axonframework.messaging.core.DefaultMessageDispatchInterceptorChain;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageDispatchInterceptor;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.queryhandling.QueryBus;
import org.axonframework.messaging.queryhandling.QueryHandler;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;
import org.axonframework.messaging.queryhandling.SubscriptionQueryUpdateMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * A {@code QueryBus} wrapper that supports {@link MessageDispatchInterceptor MessageDispatchInterceptors}.
 * Actual dispatching and handling of queries is done by a delegate.
 * <p>
 * Handler interceptors are applied at the component level via
 * {@link InterceptingQueryHandlingComponent} rather than at the bus level,
 * ensuring each module gets its own set of handler interceptors with the correct
 * {@link org.axonframework.messaging.core.ApplicationContext ApplicationContext}.
 * <p>
 * This {@code InterceptingQueryBus} is typically registered as a
 * {@link ComponentRegistry#registerDecorator(DecoratorDefinition) decorator} and automatically kicks in whenever
 * any {@code MessageDispatchInterceptors} are present.
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

    private final QueryBus delegate;
    private final List<MessageDispatchInterceptor<? super QueryMessage>> dispatchInterceptors;
    private final List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> updateDispatchInterceptors;

    private final QueryInterceptingDispatcher queryInterceptingDispatcher;
    private final SubscriptionQueryInterceptingDispatcher subscriptionQueryInterceptingDispatcher;
    private final SubscribeToUpdatesInterceptingDispatcher subscribeToUpdatesInterceptingDispatcher;
    private final InterceptingResponseUpdateDispatcher interceptingResponseUpdateDispatcher;

    /**
     * Constructs a {@code InterceptingQueryBus}, delegating dispatching and handling logic to the given
     * {@code delegate}. The given {@code dispatchInterceptors} are invoked before dispatching is provided to the
     * given {@code delegate}. The given {@code updateDispatchInterceptors} are invoked before emitting subscription
     * query update.
     *
     * @param delegate                   The delegate {@code QueryBus} that will handle all dispatching and handling
     *                                   logic.
     * @param dispatchInterceptors       The interception to invoke before dispatching a query and on the query result.
     * @param updateDispatchInterceptors The interception to invoke before emitting subscription query update.
     */
    public InterceptingQueryBus(
            @Nonnull QueryBus delegate,
            @Nonnull List<MessageDispatchInterceptor<? super QueryMessage>> dispatchInterceptors,
            @Nonnull List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> updateDispatchInterceptors
    ) {
        this.delegate = requireNonNull(delegate, "The query bus delegate must not be null.");
        this.dispatchInterceptors = new ArrayList<>(
                requireNonNull(dispatchInterceptors, "The dispatch interception must not be null.")
        );
        this.updateDispatchInterceptors = new ArrayList<>(
                requireNonNull(updateDispatchInterceptors, "The update dispatch interception must not be null.")
        );
        this.queryInterceptingDispatcher = new QueryInterceptingDispatcher(dispatchInterceptors, this::dispatchQuery);
        this.subscriptionQueryInterceptingDispatcher = new SubscriptionQueryInterceptingDispatcher(dispatchInterceptors,
                                                                                                   delegate);
        this.subscribeToUpdatesInterceptingDispatcher = new SubscribeToUpdatesInterceptingDispatcher(dispatchInterceptors,
                                                                                                      delegate);
        this.interceptingResponseUpdateDispatcher = new InterceptingResponseUpdateDispatcher(updateDispatchInterceptors);
    }

    @Override
    public InterceptingQueryBus subscribe(@Nonnull QualifiedName queryName,
                                          @Nonnull QueryHandler queryHandler) {
        delegate.subscribe(queryName, queryHandler);
        return this;
    }

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> query(@Nonnull QueryMessage query,
                                                     @Nullable ProcessingContext context) {
        return queryInterceptingDispatcher.dispatch(query, context);
    }

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> subscriptionQuery(@Nonnull QueryMessage query,
                                                                 @Nullable ProcessingContext context,
                                                                 int updateBufferSize) {
        return subscriptionQueryInterceptingDispatcher.dispatch(query, context, updateBufferSize);
    }

    @Nonnull
    @Override
    public MessageStream<SubscriptionQueryUpdateMessage> subscribeToUpdates(@Nonnull QueryMessage query,
                                                                            int updateBufferSize) {
        return subscribeToUpdatesInterceptingDispatcher.dispatch(query, updateBufferSize);
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> emitUpdate(@Nonnull Predicate<QueryMessage> filter,
                                              @Nonnull Supplier<SubscriptionQueryUpdateMessage> updateSupplier,
                                              @Nullable ProcessingContext context) {
        if (updateDispatchInterceptors.isEmpty()) {
            return delegate.emitUpdate(filter, updateSupplier, context);
        }

        try {
            SubscriptionQueryUpdateMessage update = updateSupplier.get();
            SubscriptionQueryUpdateMessage intercepted = interceptingResponseUpdateDispatcher.intercept(update,
                                                                                                        context);
            return delegate.emitUpdate(filter, () -> intercepted, context);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> completeSubscriptions(@Nonnull Predicate<QueryMessage> filter,
                                                         @Nullable ProcessingContext context) {
        return delegate.completeSubscriptions(filter, context);
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> completeSubscriptionsExceptionally(
            @Nonnull Predicate<QueryMessage> filter,
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
        descriptor.describeProperty("dispatchInterceptors", dispatchInterceptors);
        descriptor.describeProperty("updateDispatchInterceptors", updateDispatchInterceptors);
    }

    private static class QueryInterceptingDispatcher {

        private final DefaultMessageDispatchInterceptorChain<? super QueryMessage> interceptorChain;

        private QueryInterceptingDispatcher(
                List<MessageDispatchInterceptor<? super QueryMessage>> interceptors,
                BiFunction<? super QueryMessage, ProcessingContext, MessageStream<?>> dispatcher
        ) {
            this.interceptorChain = new DefaultMessageDispatchInterceptorChain<>(interceptors, dispatcher);
        }

        private MessageStream<QueryResponseMessage> dispatch(
                @Nonnull QueryMessage query,
                @Nullable ProcessingContext context
        ) {
            return interceptorChain.proceed(query, context)
                                   .cast();
        }
    }

    private static class SubscriptionQueryInterceptingDispatcher {

        private final List<MessageDispatchInterceptor<? super QueryMessage>> interceptors;
        private final QueryBus delegate;

        private SubscriptionQueryInterceptingDispatcher(
                List<MessageDispatchInterceptor<? super QueryMessage>> interceptors,
                QueryBus delegate
        ) {
            this.interceptors = interceptors;
            this.delegate = delegate;
        }

        private MessageStream<QueryResponseMessage> dispatch(
                @Nonnull QueryMessage query,
                @Nullable ProcessingContext context,
                int updateBufferSize
        ) {
            // Create a new chain per call because the dispatcher needs the updateBufferSize parameter
            // which varies per invocation and is not part of the BiFunction signature.
            // We cannot use Processing Context to pass this value, because Processing Context can be null.
            BiFunction<? super QueryMessage, ProcessingContext, MessageStream<?>> subscriptionDispatcher =
                    (interceptedQuery, interceptedContext) -> delegate.subscriptionQuery(interceptedQuery,
                                                                                         interceptedContext,
                                                                                         updateBufferSize);

            return new DefaultMessageDispatchInterceptorChain<>(
                    interceptors,
                    subscriptionDispatcher
            ).proceed(query, context).cast();
        }
    }

    private static class SubscribeToUpdatesInterceptingDispatcher {

        private final List<MessageDispatchInterceptor<? super QueryMessage>> interceptors;
        private final QueryBus delegate;

        private SubscribeToUpdatesInterceptingDispatcher(
                List<MessageDispatchInterceptor<? super QueryMessage>> interceptors,
                QueryBus delegate
        ) {
            this.interceptors = interceptors;
            this.delegate = delegate;
        }

        private MessageStream<SubscriptionQueryUpdateMessage> dispatch(
                @Nonnull QueryMessage query,
                int updateBufferSize
        ) {
            // Create a new chain per call because the dispatcher needs the updateBufferSize parameter
            // which varies per invocation and is not part of the BiFunction signature.
            // We cannot use Processing Context to pass this value, because Processing Context can be null.
            BiFunction<? super QueryMessage, ProcessingContext, MessageStream<?>> subscribeToUpdatesDispatcher =
                    (interceptedQuery, interceptedContext) -> delegate.subscribeToUpdates(interceptedQuery, updateBufferSize);

            return new DefaultMessageDispatchInterceptorChain<>(
                    interceptors,
                    subscribeToUpdatesDispatcher
            ).proceed(query, null).cast();
        }
    }

    private static class InterceptingResponseUpdateDispatcher {

        private final DefaultMessageDispatchInterceptorChain<? super SubscriptionQueryUpdateMessage> interceptorChain;

        private InterceptingResponseUpdateDispatcher(
                List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> interceptors
        ) {
            BiFunction<? super SubscriptionQueryUpdateMessage, ProcessingContext, MessageStream<?>> dispatcher =
                    (message, context) -> MessageStream.just(message).cast();
            this.interceptorChain = new DefaultMessageDispatchInterceptorChain<>(interceptors, dispatcher);
        }

        private SubscriptionQueryUpdateMessage intercept(
                @Nonnull SubscriptionQueryUpdateMessage update,
                @Nullable ProcessingContext context
        ) {
            @SuppressWarnings("unchecked")
            MessageStream<SubscriptionQueryUpdateMessage> intercepted =
                    (MessageStream<SubscriptionQueryUpdateMessage>) interceptorChain.proceed(update, context);
            return intercepted.first()
                              .asCompletableFuture()
                              .join()
                              .message();
        }
    }
}
