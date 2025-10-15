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
import io.axoniq.axonserver.connector.ResultStream;
import io.axoniq.axonserver.connector.impl.AsyncRegistration;
import io.axoniq.axonserver.connector.impl.CloseAwareReplyChannel;
import io.axoniq.axonserver.connector.query.QueryDefinition;
import io.axoniq.axonserver.connector.query.QueryHandler;
import io.axoniq.axonserver.grpc.ErrorMessage;
import io.axoniq.axonserver.grpc.query.QueryRequest;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import io.axoniq.axonserver.grpc.query.SubscriptionQuery;
import io.grpc.netty.shaded.io.netty.util.internal.OutOfDirectMemoryError;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.util.ExceptionSerializer;
import org.axonframework.axonserver.connector.util.ProcessingInstructionHelper;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.lifecycle.ShutdownLatch;
import org.axonframework.messaging.FluxUtils;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.queryhandling.QueryHandlerName;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.distributed.QueryBusConnector;
import org.axonframework.queryhandling.tracing.QueryBusSpanFactory;
import org.axonframework.util.ClasspathResolver;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.axonframework.axonserver.connector.util.ProcessingInstructionHelper.*;

/**
 * TODO Implement methods and fine tune JavaDoc
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class AxonServerQueryBusConnector implements QueryBusConnector {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final boolean NON_STREAMING = false;

    private final AxonServerConnection connection;
    private final String clientId;
    private final String componentName;
    private final LocalSegmentAdapter localSegmentAdapter;

    private final Map<QueryHandlerName, Registration> subscriptions = new ConcurrentHashMap<>();
    private final ShutdownLatch shutdownLatch = new ShutdownLatch();
    private final Duration queryInProgressAwait = Duration.ofSeconds(5);

    private Handler incomingHandler;

    /**
     * TODO add JavaDoc
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
        this.localSegmentAdapter = new LocalSegmentAdapter();
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
                                              .registerQueryHandler(localSegmentAdapter, definition);

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

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> query(@Nonnull QueryMessage query,
                                                     @Nullable ProcessingContext context) {
        shutdownLatch.ifShuttingDown("Cannot dispatch new queries as this bus is being shut down");
        try (ShutdownLatch.ActivityHandle queryInTransit = shutdownLatch.registerActivity()) {
            return doQuery(query).whenComplete(queryInTransit::end);
            // TODO old approach used an executor on onAvailable -
            //  I don't see why we would need that with the current MessageStream, as that's async by nature.
        }
    }

    private MessageStream<QueryResponseMessage> doQuery(@Nonnull QueryMessage query) {
        ResultStream<QueryResponse> resultStream =
                connection.queryChannel()
                          .query(QueryConverter.convertQueryMessage(query, clientId, componentName, NON_STREAMING));
        return new QueryResponseMessageStream(resultStream);
    }

    // TODO I think this switch belongs in the DistributedQueryBus i.o. the Connector implementation.
//    private final boolean localSegmentShortCut;
//    private boolean shouldRunQueryLocally(String queryName) {
//        return localSegmentShortCut && queryHandlerNames.contains(queryName);
//    }

    @Nonnull
    @Override
    public Publisher<QueryResponseMessage> streamingQuery(@Nonnull QueryMessage query,
                                                          @Nullable ProcessingContext context) {
        // TODO missing use of ExecutorService here. Should the DistributedQueryBus do the threading or is that an Axon Server concern?
        return Mono.fromSupplier(this::registerStreamingQueryActivity)
                   .flatMapMany(activity -> FluxUtils.of(doQuery(query))
                                                     .doFinally(new ActivityFinisher(activity)))
                   .map(MessageStream.Entry::message);
    }

    private ShutdownLatch.ActivityHandle registerStreamingQueryActivity() {
        shutdownLatch.ifShuttingDown("Cannot dispatch new queries as this bus is being shut down");
        return shutdownLatch.registerActivity();
    }

    /**
     * Ends a streaming query activity.
     * <p>
     * The reason for this static class to exist at all is the ability of instantiating
     * {@link AxonServerQueryBusConnector} even without Project Reactor on the classpath.
     * </p>
     * <p>
     * If we had Project Reactor on the classpath, this class would be replaced with a lambda (which would compile into
     * inner class). But, inner classes have a reference to an outer class making a single unit together with it. If an
     * inner or outer class had a method with a parameter that belongs to a library which is not on the classpath,
     * instantiation would fail.
     * </p>
     *
     * @author Milan Savic
     */
    private static class ActivityFinisher implements Consumer<SignalType> {

        private final ShutdownLatch.ActivityHandle activity;

        private ActivityFinisher(ShutdownLatch.ActivityHandle activity) {
            this.activity = activity;
        }

        @Override
        public void accept(SignalType signalType) {
            activity.end();
        }
    }

    /* AxonServerQueryBus subscriptionQuery implementation
    @Nonnull
    @Override
    public SubscriptionQueryResponseMessages subscriptionQuery(
            @Nonnull SubscriptionQueryMessage query,
            @Nullable ProcessingContext context,
            int updateBufferSize
    ) {
        shutdownLatch.ifShuttingDown(format(
                "Cannot dispatch new %s as this bus is being shut down", "subscription queries"
        ));

        Span span = spanFactory.createSubscriptionQuerySpan(query, true).start();
        try (SpanScope unused = span.makeCurrent()) {

            SubscriptionQueryMessage interceptedQuery = FluxUtils.of(
                new DefaultMessageDispatchInterceptorChain<>(dispatchInterceptors)
                    .proceed(spanFactory.propagateContext(spanFactory.propagateContext(query)), null)
                    .first()
                    .<SubscriptionQueryMessage>cast()
                )
                .singleOrEmpty()
                .map(MessageStream.Entry::message)
                .block(); // TODO reintegrate as part of #3079
            String subscriptionId = interceptedQuery.identifier();
            String targetContext = targetContextResolver.resolveContext(interceptedQuery);

            logger.debug("Subscription Query requested with subscription Id [{}]", subscriptionId);

            @SuppressWarnings("unused")
            io.axoniq.axonserver.connector.query.SubscriptionQueryResult result =
                    axonServerConnectionManager.getConnection(targetContext)
                                               .queryChannel()
                                               .subscriptionQuery(
                                                       subscriptionSerializer.serializeQuery(interceptedQuery),
                                                       subscriptionSerializer.serializeUpdateType(interceptedQuery),
                                                       Math.max(32, updateBufferSize),
                                                       Math.max(4, updateBufferSize >> 3)
                                               );
            // TODO #3488 Pick up when picking up AxonServerQueryBus
//            return new AxonServerSubscriptionQueryResult(
//                    interceptedQuery,
//                    result,
//                    subscriptionSerializer,
//                    spanFactory,
//                    span);
            return null;
        }
    }
     */
    @Nonnull
    @Override
    public void onIncomingQuery(@Nonnull Handler handler) {
        this.incomingHandler = Objects.requireNonNull(handler, "The incoming query handler must not be null.");
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
     * <p>
     * TODO This should be used on AxonServerQueryBusConnector#subscribe, and the implementation should use the QueryBusConnector.Handler that is TBD.
     */
    private class LocalSegmentAdapter implements QueryHandler {

        private final Map<String, QueryProcessingTask> queriesInProgress = new ConcurrentHashMap<>();

        @Override
        public void handle(QueryRequest query, ReplyChannel<QueryResponse> responseHandler) {
            stream(query, responseHandler).request(Long.MAX_VALUE);
        }

        @Override
        public FlowControl stream(QueryRequest query, ReplyChannel<QueryResponse> responseHandler) {
            Runnable onClose = () -> queriesInProgress.remove(query.getMessageIdentifier());
            CloseAwareReplyChannel<QueryResponse> replyChannel =
                    new CloseAwareReplyChannel<>(responseHandler, onClose);
            long priority = ProcessingInstructionHelper.priority(query.getProcessingInstructionsList());
            QueryProcessingTask processingTask = new QueryProcessingTask(query, replyChannel, clientId, null);
            queriesInProgress.put(query.getMessageIdentifier(), processingTask);
            return new FlowControl() {
                @Override
                public void request(long requested) {
                    // TODO guess we need a way to request and cancel on the QueryBusConnector.Handle
//                    queryExecutor.execute(new PriorityRunnable(() -> processingTask.request(requested),
//                                                               priority,
//                                                               TASK_SEQUENCE.incrementAndGet()));
                }

                @Override
                public void cancel() {
//                    queryExecutor.execute(new PriorityRunnable(processingTask::cancel,
//                                                               priority,
//                                                               TASK_SEQUENCE.incrementAndGet()));
                }
            };
        }

        @Override
        public io.axoniq.axonserver.connector.Registration registerSubscriptionQuery(SubscriptionQuery query,
                                                                                     UpdateHandler sendUpdate) {
            return null;
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

    class QueryProcessingTask implements Runnable, FlowControl {

        private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

        private static final int DIRECT_QUERY_NUMBER_OF_RESULTS = 1;

        private final QueryRequest queryRequest;
        private final ReplyChannel<QueryResponse> responseHandler;
        //        private final QuerySerializer serializer;
        private final String clientId;
        private final AtomicReference<StreamableResponse> streamableResultRef = new AtomicReference<>();
        private final AtomicLong requestedBeforeInit = new AtomicLong();
        private final AtomicBoolean cancelledBeforeInit = new AtomicBoolean();
        private final boolean supportsStreaming;

        private final Supplier<Boolean> reactorOnClassPath;
        private final QueryBusSpanFactory spanFactory;

        /**
         * Instantiates a query processing task.
         *
         * @param queryRequest    The request received from Axon Server.
         * @param responseHandler The {@link ReplyChannel} used for sending items to the Axon Server.
         * @param clientId        The identifier of the client.
         */
        QueryProcessingTask(QueryRequest queryRequest,
                            ReplyChannel<QueryResponse> responseHandler,
                            String clientId,
                            QueryBusSpanFactory spanFactory) {
            this(queryRequest,
                 responseHandler,
                 clientId,
                 ClasspathResolver::projectReactorOnClasspath,
                 spanFactory);
        }

        /**
         * Instantiates a query processing task.
         *
         * @param queryRequest       The request received from Axon Server.
         * @param responseHandler    The {@link ReplyChannel} used for sending items to the Axon Server.
         * @param clientId           The identifier of the client.
         * @param reactorOnClassPath Indicates whether Project Reactor is on the classpath.
         * @param spanFactory        The {@link QueryBusSpanFactory} implementation to use to provide tracing
         *                           capabilities.
         */
        QueryProcessingTask(QueryRequest queryRequest,
                            ReplyChannel<QueryResponse> responseHandler,
                            String clientId,
                            Supplier<Boolean> reactorOnClassPath,
                            QueryBusSpanFactory spanFactory) {
            this.queryRequest = queryRequest;
            this.responseHandler = responseHandler;
            this.clientId = clientId;
            this.supportsStreaming = supportsStreaming(queryRequest);
            this.reactorOnClassPath = reactorOnClassPath;
            this.spanFactory = spanFactory;
        }

        @Override
        public void run() {
            try {
                logger.debug("Will process query [{}]", queryRequest.getQuery());
                QueryMessage queryMessage = null; // serializer.deserializeRequest(queryRequest);
                spanFactory.createQueryProcessingSpan(queryMessage).run(() -> {
                    if (numberOfResults(queryRequest.getProcessingInstructionsList())
                            == DIRECT_QUERY_NUMBER_OF_RESULTS) {
                        if (supportsStreaming && reactorOnClassPath.get()) {
                            streamingQuery(queryMessage);
                        } else {
                            directQuery(queryMessage);
                        }
                    }
                });
            } catch (RuntimeException | OutOfDirectMemoryError e) {
                sendError(e);
                logger.warn("Query Processor had an exception when processing query [{}]", queryRequest.getQuery(), e);
            }
        }

        @Override
        public void request(long requested) {
            if (requested <= 0) {
                return;
            }
            if (!requestIfInitialized(requested)) {
                requestedBeforeInit.getAndUpdate(current -> {
                    try {
                        return Math.addExact(requested, current);
                    } catch (ArithmeticException e) {
                        return Long.MAX_VALUE;
                    }
                });
                requestIfInitialized(requestedBeforeInit.get());
            }
        }

        @Override
        public void cancel() {
            StreamableResponse result = streamableResultRef.get();
            if (result != null) {
                result.cancel();
            } else {
                cancelledBeforeInit.set(true);
            }
        }

        /**
         * Returns {@code true} if this task is still waiting for a result, and {@code false} otherwise.
         * <p>
         * Note that this would this return {@code true}, even if the streamable result has not been canceled yet!
         *
         * @return {@code true} if this task is still waiting for a result, and {@code false} otherwise.
         */
        public boolean resultPending() {
            return streamableResultRef.get() == null;
        }

        private void streamingQuery(QueryMessage queryMessage) {
            Publisher<QueryResponseMessage> resultPublisher = incomingHandler.streamingQuery(queryMessage);
            setResponse(streamableResponseFrom(resultPublisher));
        }

        private void directQuery(@Nonnull QueryMessage queryMessage) {
            incomingHandler.query(queryMessage, new ResultCallback() {
                @Override
                public void onSuccess(@Nullable QueryResponseMessage resultMessage) {
                    setResponse(streamableResponseFrom(resultMessage));
                }

                @Override
                public void onError(@Nonnull Throwable cause) {
                    sendError(cause);
                }
            });
        }

        private void setResponse(StreamableResponse result) {
            streamableResultRef.set(result);
            if (cancelledBeforeInit.get()) {
                cancel();
            } else {
                request(requestedBeforeInit.get());
            }
        }

        private StreamableResponse streamableResponseFrom(Publisher<QueryResponseMessage> resultPublisher) {
            return new StreamableFluxResponse(Flux.from(resultPublisher),
                                              responseHandler,
                                              queryRequest.getMessageIdentifier(),
                                              clientId);
        }


        private StreamableInstanceResponse streamableResponseFrom(QueryResponseMessage response) {
            return new StreamableInstanceResponse(response,
                                                  responseHandler,
                                                  queryRequest.getMessageIdentifier());
        }

        private boolean supportsStreaming(QueryRequest queryRequest) {
            boolean axonServerSupportsStreaming = axonServerSupportsQueryStreaming(queryRequest.getProcessingInstructionsList());
            boolean clientSupportsStreaming = clientSupportsQueryStreaming(queryRequest.getProcessingInstructionsList());
            return axonServerSupportsStreaming && clientSupportsStreaming;
        }

        private boolean requestIfInitialized(long requested) {
            StreamableResponse result = streamableResultRef.get();
            if (result != null) {
                result.request(requested);
                return true;
            }
            return false;
        }

        private void sendError(Throwable t) {
            ErrorMessage ex = ExceptionSerializer.serialize(clientId, t);
            QueryResponse response =
                    QueryResponse.newBuilder()
                                 .setErrorCode(ErrorCode.getQueryExecutionErrorCode(t).errorCode())
                                 .setErrorMessage(ex)
                                 .setRequestIdentifier(queryRequest.getMessageIdentifier())
                                 .build();
            responseHandler.sendLast(response);
        }
    }
}
