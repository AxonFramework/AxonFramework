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
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.queryhandling.QueryHandlerName;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The {@code QueryBusConnector} interface defines the contract for connecting multiple
 * {@link org.axonframework.queryhandling.QueryBus} instances.
 * <p>
 * It allows for the dispatching of queries across different query bus instances, whether they are local or remote. One
 * connector can be wrapped with another through the {@link DelegatingQueryBusConnector}, upon which more functionality
 * can be added, such as payload conversion or serialization.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface QueryBusConnector extends DescribableComponent {

    // region [QueryBus] - methods that are delegated to the query bus

    /**
     * Delegates querying to the underlying QueryBus.
     *
     * @see org.axonframework.queryhandling.QueryBus#query(QueryMessage, ProcessingContext)
     */
    @Nonnull
    MessageStream<QueryResponseMessage> query(@Nonnull QueryMessage query,
                                              @Nullable ProcessingContext context);

    /**
     * Delegates subscription querying to the underlying QueryBus.
     *
     * @see org.axonframework.queryhandling.QueryBus#subscriptionQuery(SubscriptionQueryMessage, ProcessingContext, int)
     */
    @Nonnull
    default MessageStream<QueryResponseMessage> subscriptionQuery(@Nonnull SubscriptionQueryMessage query,
                                                          @Nullable ProcessingContext context,
                                                          int updateBufferSize) {
        // FIXME
        // convert to a grpc message and send to axon server. Map all responses to the returned MessageStream
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    /**
     * Delegates update subscription registration to the underlying QueryBus.
     *
     * @see org.axonframework.queryhandling.QueryBus#subscribeToUpdates(SubscriptionQueryMessage, int)
     */
    @Nonnull
    default MessageStream<SubscriptionQueryUpdateMessage> subscribeToUpdates(@Nonnull SubscriptionQueryMessage query,
                                                                     int updateBufferSize) {
        // FIXME
        // convert to a grpc message and send to axon server. Map all responses to the returned MessageStream
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    /**
     * Delegates emitting an update to the underlying QueryBus.
     *
     * @see org.axonframework.queryhandling.QueryBus#emitUpdate(Predicate, Supplier, ProcessingContext)
     */
    @Nonnull
    default CompletableFuture<Void> emitUpdate(@Nonnull Predicate<SubscriptionQueryMessage> filter,
                                       @Nonnull Supplier<SubscriptionQueryUpdateMessage> updateSupplier,
                                       @Nullable ProcessingContext context) {
        // FIXME
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    /**
     * Delegates completing subscription queries to the underlying QueryBus.
     *
     * @see org.axonframework.queryhandling.QueryBus#completeSubscriptions(Predicate, ProcessingContext)
     */
    @Nonnull
    default CompletableFuture<Void> completeSubscriptions(@Nonnull Predicate<SubscriptionQueryMessage> filter,
                                                  @Nullable ProcessingContext context) {
        // FIXME
        // just send message to connector to complete subscription queries.
         throw new UnsupportedOperationException("Not implemented yet.");
    }

    /**
     * Delegates exceptional completion of subscription queries to the underlying QueryBus.
     *
     * @see org.axonframework.queryhandling.QueryBus#completeSubscriptionsExceptionally(Predicate, Throwable, ProcessingContext)
     */
    @Nonnull
    default CompletableFuture<Void> completeSubscriptionsExceptionally(@Nonnull Predicate<SubscriptionQueryMessage> filter,
                                                               @Nonnull Throwable cause,
                                                               @Nullable ProcessingContext context) {
        // FIXME
        throw new UnsupportedOperationException("Not implemented yet.");
    }
    // endregion

    // region [Connector] - methods for subscription and handlers

    /**
     * Subscribes this connector to queries matching the given {@code name}.
     *
     * @param name A combination of the {@link org.axonframework.messaging.QualifiedName} of the
     *             {@link QueryMessage#type()} and {@link QueryMessage#responseType()} to subscribe to.
     * @return A {@code CompletableFuture} that completes successfully when this connector subscribed to the given
     * {@code name}.
     */
    CompletableFuture<Void> subscribe(@Nonnull QueryHandlerName name);

    /**
     * Unsubscribes this connector from queries with the given {@code name}.
     *
     * @param name A combination of the {@link org.axonframework.messaging.QualifiedName} of the
     *             {@link QueryMessage#type()} and {@link QueryMessage#responseType()} to unsubscribe from.
     * @return {@code true} if unsubscribing was successful, {@code false} otherwise.
     */
    boolean unsubscribe(@Nonnull QueryHandlerName name);


    /**
     * TODO
     * @param handler
     */
    void onIncomingQuery(@Nonnull Handler handler);

    /**
     * TODO
     */
    interface Handler {

        /**
         * Handles the incoming query message.
         *
         * @param query    The query message to handle.
         * @param callback The callback to invoke with the result of handling the query.
         */
        MessageStream<QueryResponseMessage> query(@Nonnull QueryMessage query);
    }

    // endregion [Connector]
}