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

package org.axonframework.axonserver.connector.query;

import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.ErrorCategory;
import io.axoniq.axonserver.connector.FlowControl;
import io.axoniq.axonserver.connector.Registration;
import io.axoniq.axonserver.connector.ReplyChannel;
import io.axoniq.axonserver.connector.ResultStream;
import io.axoniq.axonserver.connector.impl.AsyncRegistration;
import io.axoniq.axonserver.connector.query.QueryDefinition;
import io.axoniq.axonserver.connector.query.QueryHandler;
import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.query.QueryRequest;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import io.axoniq.axonserver.grpc.query.SubscriptionQuery;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.lifecycle.ShutdownLatch;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.queryhandling.QueryHandlerName;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.distributed.QueryBusConnector;
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
 * TODO Implement methods and fine tune JavaDoc
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class AxonServerQueryBusConnector implements QueryBusConnector {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final AxonServerConnection connection;
    private final String clientId;
    private final String componentName;
    private final LocalSegmentAdapter localSegmentAdapter;

    private final Map<QueryHandlerName, Registration> subscriptions = new ConcurrentHashMap<>();
    private final ShutdownLatch shutdownLatch = new ShutdownLatch();
    private final Duration queryInProgressAwait = Duration.ofSeconds(5);

    private Handler incomingHandler;

    /**
     * Creates a QueryBusConnector implementation that connects to AxonServer for dispatching and receiving queries.
     *
     * @param connection The connection to AxonServer
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
    public CompletableFuture<Void> subscribe(@Nonnull QueryHandlerName name) {
        logger.debug("Subscribing to query handler [{}] with response type [{}]",
                     name.queryName(), name.responseName());
        QueryDefinition definition = new QueryDefinition(name.queryName().name(), name.responseName().name());
        Registration registration = connection.queryChannel()
                                              .registerQueryHandler(localSegmentAdapter, definition);

        CompletableFuture<Void> registrationComplete = new CompletableFuture<>();
        // Make sure that when we subscribe and immediately send a command, it can be handled.
        if (registration instanceof AsyncRegistration asyncRegistration) {
            asyncRegistration.onAck(() -> registrationComplete.complete(null));
        } else {
            registrationComplete.complete(null);
        }
        this.subscriptions.put(name, registration);
        return registrationComplete;
    }

    @Override
    public boolean unsubscribe(@Nonnull QueryHandlerName name) {
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
    public MessageStream<QueryResponseMessage> subscriptionQuery(@Nonnull SubscriptionQueryMessage query,
                                                                 @Nullable ProcessingContext context,
                                                                 int updateBufferSize) {
        shutdownLatch.ifShuttingDown("Cannot dispatch new queries as this bus is being shut down");

        try (ShutdownLatch.ActivityHandle queryInTransit = shutdownLatch.registerActivity()) {
            var result = connection.queryChannel()
                                   .subscriptionQuery(QueryConverter.convertQueryMessage(query,
                                                                                         clientId,
                                                                                         componentName),
                                                      // TODO legacy requirement. Should be removed from connector
                                                      SerializedObject.getDefaultInstance(),
                                                      updateBufferSize,
                                                      Math.min(updateBufferSize / 4, 8));
            return MessageStream.fromFuture(result.initialResult()
                                                  .thenApply(QueryConverter::convertQueryResponse))
                                .concatWith(new QueryUpdateMessageStream(result.updates()))
                                .onClose(queryInTransit::end);
        }
    }

    // endregion

    /**
     * Disconnect the query bus for receiving queries from Axon Server, by unsubscribing all registered query handlers.
     * <p>
     * This shutdown operation is performed in the {@link org.axonframework.lifecycle.Phase#INBOUND_QUERY_CONNECTOR}
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
     * performed in the {@link org.axonframework.lifecycle.Phase#OUTBOUND_QUERY_CONNECTORS} phase.
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
            result.onClose(queriesInProgress.remove(query.getMessageIdentifier()))
                  .onAvailable(() -> {
                      // TODO #3808 - Check if sufficient permits from flow control
                      // This logic should be executed by tasks with scheduling gates on the response thread pool
                      while (result.hasNextAvailable()) {
                          var next = result.next();
                          next.ifPresent(i -> responseHandler.send(QueryConverter.convertQueryResponseMessage(query.getMessageIdentifier(),
                                                                                                              i.message())));
                      }
                      if (result.isCompleted()) {
                          result.error()
                                .ifPresentOrElse(error ->
                                                         responseHandler.completeWithError(ErrorCategory.QUERY_EXECUTION_ERROR,
                                                                                           error.getMessage()),
                                                 responseHandler::complete);
                      }
                  });
            return new FlowControl() {
                @Override
                public void request(long requested) {
                    // TODO #3808 - Implement flow control on responses
                }

                @Override
                public void cancel() {
                    result.close();
                }
            };
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
            updateHandler.sendUpdate(QueryConverter.convertQueryUpdate(clientId, error));
            updateHandler.complete();
            return FutureUtils.emptyCompletedFuture();
        }
    }
}
