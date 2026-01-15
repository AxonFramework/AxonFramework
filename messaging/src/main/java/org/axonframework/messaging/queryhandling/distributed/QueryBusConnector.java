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
import org.axonframework.common.Registration;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.queryhandling.QueryBus;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;
import org.axonframework.messaging.queryhandling.SubscriptionQueryUpdateMessage;

import java.util.concurrent.CompletableFuture;

/**
 * The {@code QueryBusConnector} interface defines the contract for connecting multiple
 * {@link QueryBus} instances.
 * <p>
 * It allows for the dispatching of queries across different query bus instances, whether they are local or remote. One
 * connector can be wrapped with another through the {@link DelegatingQueryBusConnector}, upon which more functionality
 * can be added, such as payload conversion or conversion.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface QueryBusConnector extends DescribableComponent {

    // region [QueryBus] - methods that are delegated to the query bus

    /**
     * Sends the given {@code query} to the remote QueryBus.
     *
     * @param query The query to send to the remote QueryBus.
     * @param context The context, if available, in which the query is generated
     * @see QueryBus#query(QueryMessage, ProcessingContext)
     * @return the stream of responses for the query
     */
    @Nonnull
    MessageStream<QueryResponseMessage> query(@Nonnull QueryMessage query,
                                              @Nullable ProcessingContext context);

    /**
     * Sends the given {@code query} to the remote QueryBus.
     *
     * @param query The query to send to the remote QueryBus.
     * @param context The context, if available, in which the query is generated
     * @param updateBufferSize The size of the buffer used to store update for the subscription query.
     * @see QueryBus#subscriptionQuery(QueryMessage, ProcessingContext, int)
     * @return the stream of responses for the query
     */
    @Nonnull
    MessageStream<QueryResponseMessage> subscriptionQuery(@Nonnull QueryMessage query,
                                                          @Nullable ProcessingContext context,
                                                          int updateBufferSize);

    // endregion

    // region [Connector] - methods for subscription and handlers

    /**
     * Subscribes this connector to queries matching the given {@code name}.
     *
     * @param name The {@link org.axonframework.messaging.core.QualifiedName} of the
     *             {@link QueryMessage#type()} to subscribe to.
     * @return A {@code CompletableFuture} that completes successfully when this connector subscribed to the given
     * {@code name}.
     */
    CompletableFuture<Void> subscribe(@Nonnull QualifiedName name);

    /**
     * Unsubscribes this connector from queries with the given {@code name}.
     *
     * @param name The {@link org.axonframework.messaging.core.QualifiedName} of the {@link QueryMessage#type()} to unsubscribe from.
     * @return {@code true} if unsubscribing was successful, {@code false} otherwise.
     */
    boolean unsubscribe(@Nonnull QualifiedName name);


    /**
     * Registers a handler to process incoming query messages.
     *
     * @param handler The handler responsible for managing incoming queries.
     */
    void onIncomingQuery(@Nonnull Handler handler);

    /**
     * Defines a handler for processing query messages and managing subscription queries.
     * Implementations of this interface are responsible for handling incoming query messages
     * and optionally registering handlers for subscription queries to send update.
     */
    interface Handler {

        /**
         * Handles the incoming query message.
         *
         * @param query    The query message to handle.
         * @return a MessageStream containing the responses for the query
         */
        MessageStream<QueryResponseMessage> query(@Nonnull QueryMessage query);

        /**
         * Registers an update handler for a given subscription query message and its associated update sender.
         *
         * @param subscriptionQueryMessage The subscription query message for which update are handled.
         * @param updateCallback The callback responsible for sending update back to the subscription.
         * @return A {@link Registration} instance that can be used to deregister the update handler.
         */
        @Nonnull
        Registration registerUpdateHandler(@Nonnull QueryMessage subscriptionQueryMessage,
                                           @Nonnull UpdateCallback updateCallback);

    }

    /**
     * Defines a callback mechanism to handle update messages for subscription queries
     * in a reactive and asynchronous manner.
     * <p/>
     * The {@code UpdateCallback} interface is used to send update, complete the processing,
     * or handle exceptional completion during subscription queries.
     */
    interface UpdateCallback {

        /**
         * Sends a subscription query update message asynchronously.
         * This method handles the delivery of incremental update to listeners
         * in the context of a subscription query.
         *
         * @param update The {@link SubscriptionQueryUpdateMessage} containing the update data
         *               to be sent as part of the subscription query.
         * @return A {@link CompletableFuture} that completes when the update has been successfully
         *         handled. If the update cannot be processed, the returned future will complete
         *         exceptionally.
         */
        @Nonnull
        CompletableFuture<Void> sendUpdate(@Nonnull SubscriptionQueryUpdateMessage update);

        /**
         * Completes the processing of a subscription query gracefully.
         * This method signals that no further update will be sent as part of the
         * subscription query and completes any associated operations.
         *
         * @return A {@link CompletableFuture} that completes when the process has been
         *         finalized successfully. If the completion encounters an issue, the
         *         returned future will complete exceptionally.
         */
        CompletableFuture<Void> complete();

        /**
         * Completes the processing of a subscription query exceptionally.
         * This method signals that an error has occurred during the processing of the subscription query
         * and terminates the operation with the provided exception.
         *
         * @param cause The {@link Throwable} representing the exceptional condition that caused the
         *              subscription query to terminate.
         * @return A {@link CompletableFuture} that completes exceptionally with the provided cause.
         */
        CompletableFuture<Void> completeExceptionally(@Nonnull Throwable cause);
    }

    // endregion [Connector]
}