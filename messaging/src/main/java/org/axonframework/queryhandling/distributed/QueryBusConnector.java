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
import org.reactivestreams.Publisher;

import java.util.concurrent.CompletableFuture;

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
     * Dispatches the given {@code query} to the appropriate query bus, which may be local or remote.
     *
     * @param query   The query message to dispatch.
     * @param context The processing context for the query, if any.
     * @return A {@link MessageStream} containing the {@link QueryResponseMessage responses} from handling the
     * dispatched {@code query}.
     */
    @Nonnull
    MessageStream<QueryResponseMessage> query(@Nonnull QueryMessage query,
                                              @Nullable ProcessingContext context);

    /**
     * Dispatches the given {@code query} to the appropriate query bus, which may be local or remote, returning a
     * {@link Publisher} of {@link QueryResponseMessage responses}.
     *
     * @param query   The query message to dispatch.
     * @param context The processing context for the query.
     * @return A {@link CompletableFuture} that will complete with the result of the query handling.
     */
    @Nonnull
    Publisher<QueryResponseMessage> streamingQuery(@Nonnull QueryMessage query,
                                                   @Nullable ProcessingContext context);

    /**
     * Registers a handler that will be called when an incoming query is received. The handler should process the query
     * and call the provided {@code ResultCallback} to indicate success or failure.
     *
     * @param handler A lambda that takes a {@link QueryMessage} and a {@link ResultCallback}.
     */
    void onIncomingQuery(@Nonnull Handler handler);

    /**
     * A functional interface representing a handler for incoming query messages. The handler processes the query and
     * uses the provided {@link ResultCallback} to report the result.
     */// TODO unsure whether this is the style we should follow here.
    interface Handler {

        /**
         * Handles the incoming query message.
         *
         * @param query    The query message to handle.
         * @param callback The callback to invoke with the result of handling the query.
         */
        void query(@Nonnull QueryMessage query, @Nonnull ResultCallback callback);

        Publisher<QueryResponseMessage> streamingQuery(@Nonnull QueryMessage query);
    }

    /**
     * A callback interface for handling the result of query processing.
     * <p>
     * It provides methods to indicate success or failure of query handling.
     */// TODO unsure whether this is the style we should follow here.
    interface ResultCallback {

        /**
         * Called when the query processing is successful.
         *
         * @param resultMessage The result message containing the outcome of the query processing. If the message
         *                      handling yielded no result message, a {@code null} should be passed.
         */
        void onSuccess(@Nullable QueryResponseMessage resultMessage);

        /**
         * Called when an error occurs during query processing.
         *
         * @param cause The exception that caused the error.
         */
        void onError(@Nonnull Throwable cause);
    }
}
