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

package org.axonframework.axonserver.connector.query;

import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.FlowControl;
import io.axoniq.axonserver.connector.Registration;
import io.axoniq.axonserver.connector.ReplyChannel;
import io.axoniq.axonserver.connector.ResultStream;
import io.axoniq.axonserver.connector.query.QueryDefinition;
import io.axoniq.axonserver.connector.query.QueryHandler;
import io.axoniq.axonserver.grpc.query.QueryRequest;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import io.axoniq.axonserver.grpc.query.SubscriptionQuery;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.lifecycle.Phase;
import org.axonframework.common.lifecycle.ShutdownLatch;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;
import org.axonframework.messaging.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.messaging.queryhandling.distributed.QueryBusConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;

import static java.util.Objects.requireNonNull;

/**
 * AxonServerQueryBusConnector is an implementation of {@link QueryBusConnector} that connects to AxonServer to enable
 * the dispatching and receiving of queries. It manages the registration of handlers, query subscriptions, and
 * disconnection from the AxonServer query bus.
 * <p/>
 * This class facilitates interaction with AxonServer, handles incoming query requests, manages active subscriptions,
 * and oversees lifecycle phases related to query dispatching and receiving.
 *
 * @author Steven van Beelen, Allard Buijze, Jan Galinski
 * @since 5.0.0
 */
public class AxonServerQueryBusConnector implements QueryBusConnector {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final AxonServerConnection connection;
    private final String clientId;
    private final String componentName;
    private final LocalSegmentAdapter localSegmentAdapter;

    private final Map<QualifiedName, Registration> subscriptions = new ConcurrentHashMap<>();
    private final ShutdownLatch shutdownLatch = new ShutdownLatch();
    private final Duration queryInProgressAwait = Duration.ofSeconds(5);

    private Handler incomingHandler;

    /**
     * Creates a QueryBusConnector implementation that connects to AxonServer for dispatching and receiving queries.
     *
     * @param connection    The connection to AxonServer
     * @param configuration The configuration containing local settings for this connector
     */
    public AxonServerQueryBusConnector(@Nonnull AxonServerConnection connection,
                                       @Nonnull AxonServerConfiguration configuration) {
        this.connection = requireNonNull(connection, "The AxonServerConnection must not be null.");
        requireNonNull(configuration, "The AxonServerConfiguration must not be null.");

        this.clientId = configuration.getClientId();
        this.componentName = configuration.getComponentName();
        this.localSegmentAdapter = new LocalSegmentAdapter();
    }

    /**
     * Starts the Axon Server {@link QueryBusConnector} implementation.
     */
    public void start() {
        shutdownLatch.initialize();
        logger.trace("The AxonServerQueryBusConnector started.");
    }

    // region [Connector]
    @Override
    public CompletableFuture<Void> subscribe(@Nonnull QualifiedName name) {
        logger.debug("Subscribing to query handler [{}].",
                     name);
        QueryDefinition definition = new QueryDefinition(name.fullName(), "");
        Registration registration = connection.queryChannel()
                                              .registerQueryHandler(localSegmentAdapter, definition);

        this.subscriptions.put(name, registration);

        CompletableFuture<Void> completion = new CompletableFuture<>();
        registration.onAck(() -> completion.complete(null));
        return completion;
    }

    @Override
    public boolean unsubscribe(@Nonnull QualifiedName name) {
        Registration subscription = subscriptions.remove(name);
        if (subscription != null) {
            subscription.cancel();
            return true;
        }
        return false;
    }

    @Override
    public void onIncomingQuery(@Nonnull Handler handler) {
        this.incomingHandler = requireNonNull(handler, "The incoming query handler must not be null.");
    }

    // endregion

    // region [QueryBus]
    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> query(@Nonnull QueryMessage query,
                                                     @Nullable ProcessingContext context) {
        shutdownLatch.ifShuttingDown("Cannot dispatch new queries as this bus is being shut down");

        try (ShutdownLatch.ActivityHandle queryInTransit = shutdownLatch.registerActivity()) {
            ResultStream<QueryResponse> resultStream = connection.queryChannel()
                                                                 .query(QueryConverter.convertQueryMessage(
                                                                         query,
                                                                         clientId,
                                                                         componentName)
                                                                 );
            return new QueryResponseMessageStream(resultStream).onClose(queryInTransit::end);
        }
    }

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> subscriptionQuery(@Nonnull QueryMessage query,
                                                                 @Nullable ProcessingContext context,
                                                                 int updateBufferSize) {
        shutdownLatch.ifShuttingDown("Cannot dispatch new queries as this bus is being shut down");

        try (ShutdownLatch.ActivityHandle queryInTransit = shutdownLatch.registerActivity()) {
            var result = connection.queryChannel()
                                   .subscriptionQuery(QueryConverter.convertQueryMessage(query,
                                                                                         clientId,
                                                                                         componentName),
                                                      updateBufferSize,
                                                      Math.min(updateBufferSize / 4, 8));
            return new QueryResponseMessageStream(result.initialResults())
                    .concatWith(new QueryUpdateMessageStream(result.updates()))
                    .onClose(queryInTransit::end);
        }
    }

    // endregion

    /**
     * Disconnect the query bus for receiving queries from Axon Server, by unsubscribing all registered query handlers.
     * <p>
     * This shutdown operation is performed in the {@link Phase#INBOUND_QUERY_CONNECTOR}
     * phase.
     *
     * @return A completable future that resolves once the {@link AxonServerConnection#queryChannel()} has prepared
     * disconnecting.
     */
    public CompletableFuture<Void> disconnect() {
        if (connection.isConnected()) {
            logger.trace("Disconnecting the AxonServerQueryBusConnector.");
            connection.queryChannel().prepareDisconnect();
        }
        if (!localSegmentAdapter.awaitTermination(queryInProgressAwait)) {
            logger.info("Awaited termination of queries in progress without success. "
                                + "Going to cancel remaining queries in progress.");
            localSegmentAdapter.cancel();
        }
        return FutureUtils.emptyCompletedFuture();
    }

    /**
     * Shutdown the query bus asynchronously for dispatching query to Axon Server.
     * <p>
     * This process will wait for dispatched queries which have not received a response yet. This shutdown operation is
     * performed in the {@link Phase#OUTBOUND_QUERY_CONNECTORS} phase.
     *
     * @return A completable future which is resolved once all query dispatching activities are completed.
     */
    public CompletableFuture<Void> shutdownDispatching() {
        logger.trace("Shutting down dispatching of AxonServerQueryBusConnector.");
        return shutdownLatch.initiateShutdown();
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("connection", connection);
        descriptor.describeProperty("clientId", clientId);
        descriptor.describeProperty("componentName", componentName);
    }

    /**
     * A {@link QueryHandler} implementation serving as a wrapper around the local {@code QueryBus} to push through the
     * message handling and subscription query registration.
     */
    private class LocalSegmentAdapter implements QueryHandler {

        private final Map<String, Runnable> queriesInProgress = new ConcurrentHashMap<>();

        @Override
        public void handle(QueryRequest query, ReplyChannel<QueryResponse> responseHandler) {
            stream(query, responseHandler).request(Long.MAX_VALUE);
        }

        @Override
        public FlowControl stream(QueryRequest query, ReplyChannel<QueryResponse> responseHandler) {
            var result = incomingHandler.query(QueryConverter.convertQueryRequest(query));
            var previous = queriesInProgress.put(query.getMessageIdentifier(), result::close);
            if (previous != null) {
                previous.run();
            }
            return new FlowControlledResponseSender(clientId, query.getMessageIdentifier(),
                                                    result.onClose(queriesInProgress.remove(query.getMessageIdentifier())),
                                                    responseHandler);
        }

        @Override
        public Registration registerSubscriptionQuery(SubscriptionQuery query, UpdateHandler sendUpdate) {
            var registration = incomingHandler.registerUpdateHandler(QueryConverter.convertSubscriptionQueryMessage(
                    query), new AxonServerUpdateCallback(sendUpdate));
            return () -> {
                registration.cancel();
                return FutureUtils.emptyCompletedFuture();
            };
        }

        private boolean awaitTermination(Duration timeout) {
            Instant startAwait = Instant.now();
            Instant endAwait = startAwait.plusSeconds(timeout.getSeconds());
            while (Instant.now().isBefore(endAwait) && !queriesInProgress.isEmpty()) {
                queriesInProgress.values()
                                 .stream()
                                 .findFirst()
                                 .ifPresent(queryInProgress -> {
                                     while (Instant.now().isBefore(endAwait)) {
                                         LockSupport.parkNanos(10_000_000);
                                     }
                                 });
            }
            return queriesInProgress.isEmpty();
        }

        private void cancel() {
            queriesInProgress.values()
                             .iterator()
                             .forEachRemaining(Runnable::run);
        }
    }

    class AxonServerUpdateCallback implements UpdateCallback {

        private final QueryHandler.UpdateHandler updateHandler;

        public AxonServerUpdateCallback(@Nonnull QueryHandler.UpdateHandler updateHandler) {
            this.updateHandler = updateHandler;
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> sendUpdate(@Nonnull SubscriptionQueryUpdateMessage update) {
            updateHandler.sendUpdate(QueryConverter.convertQueryUpdate(update));
            return FutureUtils.emptyCompletedFuture();
        }

        @Override
        public CompletableFuture<Void> complete() {
            updateHandler.complete();
            return FutureUtils.emptyCompletedFuture();
        }

        @Override
        public CompletableFuture<Void> completeExceptionally(@Nonnull Throwable error) {
            updateHandler.sendUpdate(QueryConverter.convertQueryUpdate(clientId, ErrorCode.QUERY_EXECUTION_ERROR, error));
            updateHandler.complete();
            return FutureUtils.emptyCompletedFuture();
        }
    }
}
