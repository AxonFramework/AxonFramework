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
import io.axoniq.axonserver.connector.FlowControl;
import io.axoniq.axonserver.connector.Registration;
import io.axoniq.axonserver.connector.ReplyChannel;
import io.axoniq.axonserver.connector.impl.AsyncRegistration;
import io.axoniq.axonserver.connector.query.QueryDefinition;
import io.axoniq.axonserver.connector.query.QueryHandler;
import io.axoniq.axonserver.grpc.query.QueryRequest;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import io.axoniq.axonserver.grpc.query.SubscriptionQuery;
import jakarta.annotation.Nonnull;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.lifecycle.ShutdownLatch;
import org.axonframework.queryhandling.QueryHandlerName;
import org.axonframework.queryhandling.distributed.QueryBusConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;

/**
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class AxonServerQueryBusConnector implements QueryBusConnector {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final AxonServerConnection connection;
    private final String clientId;
    private final String componentName;

    //    private Handler incomingHandler;
    private final Map<QueryHandlerName, Registration> subscriptions = new ConcurrentHashMap<>();
    private final ShutdownLatch shutdownLatch = new ShutdownLatch();

    /**
     *
     * @param connection
     * @param configuration
     */
    public AxonServerQueryBusConnector(@Nonnull AxonServerConnection connection,
                                       @Nonnull AxonServerConfiguration configuration) {
        this.connection = Objects.requireNonNull(connection, "The AxonServerConnection must not be null.");
        Objects.requireNonNull(configuration, "The AxonServerConfiguration must not be null.");
        this.clientId = configuration.getClientId();
        this.componentName = configuration.getComponentName();
    }

    /**
     * Starts the Axon Server {@link QueryBusConnector} implementation.
     */
    public void start() {
        shutdownLatch.initialize();
        logger.trace("The AxonServerQueryBusConnector started.");
    }

    @Override
    public CompletableFuture<Void> subscribe(@Nonnull QueryHandlerName name) {
        logger.debug("Subscribing to query handler [{}] with response type [{}]",
                     name.queryName(), name.responseName());
        QueryDefinition definition = new QueryDefinition(name.queryName().name(), name.responseName().name());
        Registration registration = connection.queryChannel()
                                              .registerQueryHandler(new QueryHandler() {
                                                  @Override
                                                  public void handle(QueryRequest query,
                                                                     ReplyChannel<QueryResponse> responseHandler) {

                                                  }
                                              }, definition);

        // Make sure that when we subscribe and immediately send a command, it can be handled.
        if (registration instanceof AsyncRegistration asyncRegistration) {
            try {
                // Waiting synchronously for the subscription to be acknowledged, this should be improved
                // TODO https://github.com/AxonFramework/AxonFramework/issues/3544
                asyncRegistration.awaitAck(2000, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                throw new RuntimeException(
                        "Timed out waiting for subscription acknowledgment for query: " + name, e
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Thread interrupted while waiting for subscription acknowledgment", e);
            }
        }
        this.subscriptions.put(name, registration);
        return FutureUtils.emptyCompletedFuture();
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
        if (!connection.isConnected()) {
            return FutureUtils.emptyCompletedFuture();
        }
        logger.trace("Disconnecting the AxonServerQueryBusConnector.");
        return connection.queryChannel().prepareDisconnect();
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

        private final Map<String, QueryProcessingTask> queriesInProgress = new ConcurrentHashMap<>();

        @Override
        public void handle(QueryRequest query, ReplyChannel<QueryResponse> responseHandler) {
            stream(query, responseHandler).request(Long.MAX_VALUE);
        }

        @Override
        public FlowControl stream(QueryRequest query, ReplyChannel<QueryResponse> responseHandler) {
//            Runnable onClose = () -> queriesInProgress.remove(query.getMessageIdentifier());
//            CloseAwareReplyChannel<QueryResponse> closeAwareReplyChannel =
//                    new CloseAwareReplyChannel<>(responseHandler, onClose);
//
//            long priority = ProcessingInstructionHelper.priority(query.getProcessingInstructionsList());
////            QueryProcessingTask processingTask = new QueryProcessingTask(
////                    localSegment, query, closeAwareReplyChannel, serializer, configuration.getClientId(), spanFactory
////            );
//            PriorityRunnable priorityTask = new PriorityRunnable(processingTask,
//                                                                 priority,
//                                                                 TASK_SEQUENCE.incrementAndGet());
//
//            queriesInProgress.put(query.getMessageIdentifier(), processingTask);
//            queryExecutor.execute(priorityTask);

            return new FlowControl() {
                @Override
                public void request(long requested) {
//                    queryExecutor.execute(new PriorityRunnable(() -> processingTask.request(requested),
//                                                               priorityTask.priority(),
//                                                               TASK_SEQUENCE.incrementAndGet())
//                    );
                }

                @Override
                public void cancel() {
//                    queryExecutor.execute(new PriorityRunnable(processingTask::cancel,
//                                                               priorityTask.priority(),
//                                                               TASK_SEQUENCE.incrementAndGet()));
                }
            };
        }

        @Override
        public io.axoniq.axonserver.connector.Registration registerSubscriptionQuery(SubscriptionQuery query,
                                                                                     UpdateHandler sendUpdate) {
            return null;
//            org.axonframework.queryhandling.UpdateHandler updateHandler =
//                    localSegment.subscribeToUpdates(subscriptionSerializer.deserialize(query), 1024);
//
//            updateHandler.updates()
//                         .doOnError(e -> {
//                             ErrorMessage error = ExceptionSerializer.serialize(configuration.getClientId(), e);
//                             String errorCode = ErrorCode.getQueryExecutionErrorCode(e).errorCode();
//                             QueryUpdate queryUpdate = QueryUpdate.newBuilder()
//                                                                  .setErrorMessage(error)
//                                                                  .setErrorCode(errorCode)
//                                                                  .build();
//                             sendUpdate.sendUpdate(queryUpdate);
//                             sendUpdate.complete();
//                         })
//                         .doOnComplete(sendUpdate::complete)
//                         .map(subscriptionSerializer::serialize)
//                         .subscribe(sendUpdate::sendUpdate);
//
//            return () -> {
//                updateHandler.cancel();
//                return FutureUtils.emptyCompletedFuture();
//            };
        }

        private boolean awaitTermination(Duration timeout) {
            Instant startAwait = Instant.now();
            Instant endAwait = startAwait.plusSeconds(timeout.getSeconds());
            while (Instant.now().isBefore(endAwait) && !queriesInProgress.isEmpty()) {
                queriesInProgress.values()
                                 .stream()
                                 .findFirst()
                                 .ifPresent(queryInProgress -> {
                                     while (Instant.now().isBefore(endAwait) && queryInProgress.resultPending()) {
                                         LockSupport.parkNanos(10_000_000);
                                     }
                                 });
            }
            return queriesInProgress.isEmpty();
        }

        private void cancel() {
            queriesInProgress.values()
                             .iterator()
                             .forEachRemaining(QueryProcessingTask::cancel);
        }
    }
}
