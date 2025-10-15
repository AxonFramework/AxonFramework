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
import io.axoniq.axonserver.connector.ReplyChannel;
import io.axoniq.axonserver.connector.impl.CloseAwareReplyChannel;
import io.axoniq.axonserver.connector.query.QueryHandler;
import io.axoniq.axonserver.grpc.ErrorMessage;
import io.axoniq.axonserver.grpc.query.QueryRequest;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import io.axoniq.axonserver.grpc.query.QueryUpdate;
import io.axoniq.axonserver.grpc.query.SubscriptionQuery;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.util.ExceptionSerializer;
import org.axonframework.axonserver.connector.util.ProcessingInstructionHelper;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.lifecycle.ShutdownLatch;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryHandlerName;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.distributed.QueryBusConnector;
import org.axonframework.util.PriorityRunnable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;

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

    private final ShutdownLatch shutdownLatch = new ShutdownLatch();
    private final Duration queryInProgressAwait = Duration.ofSeconds(5);

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
    }

    /**
     * Starts the Axon Server {@link QueryBusConnector} implementation.
     */
    public void start() {
        shutdownLatch.initialize();
        logger.trace("The AxonServerQueryBusConnector started.");
    }

    // TODO Implement
    @Override
    public CompletableFuture<Void> subscribe(@Nonnull QueryHandlerName name) {
        return null;
    }

    // TODO Implement
    @Override
    public boolean unsubscribe(@Nonnull QueryHandlerName name) {
        return false;
    }

    // TODO Implement
    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> query(@Nonnull QueryMessage query,
                                                     @Nullable ProcessingContext context) {
        return null;
    }
    /* AxonServerQueryBus query implementation
    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> query(@Nonnull QueryMessage queryMessage,
                                                     @Nullable ProcessingContext context) {
        Span span = spanFactory.createQuerySpan(queryMessage, true).start();
        try (SpanScope unused = span.makeCurrent()) {
            QueryMessage queryWithContext = spanFactory.propagateContext(queryMessage);
            shutdownLatch.ifShuttingDown("Cannot dispatch new queries as this bus is being shut down");

            QueryMessage interceptedQuery = FluxUtils
                .of(
                    new DefaultMessageDispatchInterceptorChain<>(dispatchInterceptors)
                        .proceed(queryWithContext, null)
                        .first()
                        .<QueryMessage>cast()
                )
                .singleOrEmpty()
                .map(MessageStream.Entry::message)
                .block(); // TODO reintegrate as part of #3079
            //noinspection resource
            ShutdownLatch.ActivityHandle queryInTransit = shutdownLatch.registerActivity();
            CompletableFuture<QueryResponseMessage> queryTransaction = new CompletableFuture<>();
            try {
                if (shouldRunQueryLocally(interceptedQuery.type().name())) {
//                    queryTransaction = localSegment.query(interceptedQuery);
                } else {
                    int priority = priorityCalculator.determinePriority(interceptedQuery);
                    QueryRequest queryRequest = serialize(interceptedQuery, false, priority);
                    ResultStream<QueryResponse> result = sendRequest(interceptedQuery, queryRequest);
                    queryTransaction.whenComplete((r, e) -> result.close());
                    Span responseTaskSpan = spanFactory.createResponseProcessingSpan(interceptedQuery);
                    Runnable responseProcessingTask = new ResponseProcessingTask<>(result,
                                                                                   serializer,
                                                                                   queryTransaction,
                                                                                   // TODO #3488 - Replace Serializer and ResponseType use
                                                                                   null, // queryMessage.responseType(),
                                                                                   responseTaskSpan);

                    result.onAvailable(() -> queryResponseExecutor.execute(new PriorityRunnable(
                            responseProcessingTask,
                            priority,
                            TASK_SEQUENCE.incrementAndGet())));
                }
            } catch (Exception e) {
                logger.debug("There was a problem issuing a query {}.", interceptedQuery, e);
                AxonException exception = ErrorCode.QUERY_DISPATCH_ERROR.convert(configuration.getClientId(), e);
                queryTransaction.completeExceptionally(exception);
                span.recordException(e).end();
            }

            queryTransaction.whenComplete((r, e) -> {
                queryInTransit.end();
                if (e != null) {
                    span.recordException(e);
                }
                if (r != null && r.isExceptional()) {
                    span.recordException(r.exceptionResult());
                }
                span.end();
            });
//            return queryTransaction;
        }
        return MessageStream.empty().cast();
    }

    // TODO I think this switch belongs in the DistributedQueryBus i.o. the Connector implementation.
    private final boolean localSegmentShortCut;

    private boolean shouldRunQueryLocally(String queryName) {
        return localSegmentShortCut && queryHandlerNames.contains(queryName);
    }

    private static class ResponseProcessingTask<R> implements Runnable {

        private final AtomicBoolean singleExecutionCheck = new AtomicBoolean();
        private final ResultStream<QueryResponse> result;
        @SuppressWarnings("unused")
        private final QuerySerializer serializer;
        private final CompletableFuture<QueryResponseMessage> queryTransaction;
        @SuppressWarnings("unused")
        private final Class<R> expectedResponseType;
        private final Span span;

        public ResponseProcessingTask(ResultStream<QueryResponse> result,
                                      QuerySerializer serializer,
                                      CompletableFuture<QueryResponseMessage> queryTransaction,
                                      Class<R> expectedResponseType,
                                      Span responseTaskSpan) {
            this.result = result;
            this.serializer = serializer;
            this.queryTransaction = queryTransaction;
            this.expectedResponseType = expectedResponseType;
            this.span = responseTaskSpan;
        }

        @Override
        public void run() {
            if (singleExecutionCheck.compareAndSet(false, true)) {
                QueryResponse nextAvailable = result.nextIfAvailable();
                if (nextAvailable != null) {
                    span.run(() -> {
                        // TODO #3488 - Replace Serializer and ResponseType use
//                        queryTransaction.complete(serializer.deserializeResponse(nextAvailable, expectedResponseType));
                    });
                } else if (result.isClosed() && !queryTransaction.isDone()) {
                    Exception exception = result.getError()
                                                .map(ErrorCode.QUERY_DISPATCH_ERROR::convert)
                                                .orElse(new AxonServerQueryDispatchException(
                                                        ErrorCode.QUERY_DISPATCH_ERROR.errorCode(),
                                                        "Query did not yield the expected number of results."
                                                ));
                    queryTransaction.completeExceptionally(exception);
                }
            }
        }
    }
     */

    // TODO Implement
    @Nonnull
    @Override
    public Publisher<QueryResponseMessage> streamingQuery(@Nonnull QueryMessage query,
                                                          @Nullable ProcessingContext context) {
        return null;
    }
    /* AxonServerQueryBus streamingQuery implementation
    @Nonnull
    @Override
    public Publisher<QueryResponseMessage> streamingQuery(@Nonnull QueryMessage query,
                                                          @Nullable ProcessingContext context) {
        Span span = spanFactory.createStreamingQuerySpan(query, true).start();
        try (SpanScope unused = span.makeCurrent()) {
            QueryMessage queryWithContext = spanFactory.propagateContext(query);
            int priority = priorityCalculator.determinePriority(queryWithContext);
            AtomicReference<Scheduler> scheduler = new AtomicReference<>(PriorityTaskSchedulers.forPriority(
                    queryResponseExecutor,
                    priority,
                    TASK_SEQUENCE));
            return Mono.fromSupplier(this::registerStreamingQueryActivity)
                .flatMapMany(activity ->
                    FluxUtils.of(
                        new DefaultMessageDispatchInterceptorChain<>(dispatchInterceptors)
                            .proceed(queryWithContext, null)
                            .first()
                            .<QueryMessage>cast()
                    )
                    .singleOrEmpty()
                    .map(MessageStream.Entry::message)
                    .flatMapMany(intercepted -> {
                        if (shouldRunQueryLocally(intercepted.type().name())) {
                            return localSegment.streamingQuery(intercepted, context);
                        }
                        return Mono.just(serializeStreaming(intercepted, priority))
                            .flatMapMany(queryRequest -> new ResultStreamPublisher<>(() -> sendRequest(intercepted, queryRequest)))
                            .concatMap(queryResponse -> deserialize(intercepted, queryResponse));
                        }
                    )
                    .publishOn(scheduler.get())
                    .doOnError(span::recordException)
                    .doFinally(new ActivityFinisher(activity, span))
                    .subscribeOn(scheduler.get())
                );
        }
    }

    private ShutdownLatch.ActivityHandle registerStreamingQueryActivity() {
        shutdownLatch.ifShuttingDown("Cannot dispatch new queries as this bus is being shut down");
        return shutdownLatch.registerActivity();
    }

    private QueryRequest serializeStreaming(QueryMessage query, int priority) {
        return serialize(query, true, priority);
    }

    Ends a streaming query activity.
    <p>
    The reason for this static class to exist at all is the ability of instantiating {@link AxonServerQueryBus} even
    without Project Reactor on the classpath.
    </p>
    <p>
    If we had Project Reactor on the classpath, this class would be replaced with a lambda (which would compile into
    inner class). But, inner classes have a reference to an outer class making a single unit together with it. If an
    inner or outer class had a method with a parameter that belongs to a library which is not on the classpath,
    instantiation would fail.
    </p>
    @author Milan Savic

    private static class ActivityFinisher implements Consumer<SignalType> {

        private final ShutdownLatch.ActivityHandle activity;
        private final Span span;

        private ActivityFinisher(ShutdownLatch.ActivityHandle activity, Span span) {
            this.activity = activity;
            this.span = span;
        }

        @Override
        public void accept(SignalType signalType) {
            span.end();
            activity.end();
        }
    }

    private QueryRequest serialize(QueryMessage query, boolean stream, int priority) {
        return serializer.serializeRequest(query,
                                           1,
                                           TimeUnit.HOURS.toMillis(1),
                                           priority,
                                           stream);
    }

    private ResultStream<QueryResponse> sendRequest(QueryMessage queryMessage, QueryRequest queryRequest) {
        return axonServerConnectionManager.getConnection(targetContextResolver.resolveContext(queryMessage))
                                          .queryChannel()
                                          .query(queryRequest);
    }

    private Publisher<QueryResponseMessage> deserialize(QueryMessage queryMessage,
                                                        QueryResponse queryResponse) {
        // TODO #3488 - Replace Serializer and ResponseType use
        //noinspection unchecked
//        Class<R> expectedResponseType = (Class<R>) queryMessage.responseType().getExpectedResponseType();
        QueryResponseMessage responseMessage = serializer.deserializeResponse(queryResponse);
        if (responseMessage.isExceptional()) {
            return Flux.error(responseMessage.exceptionResult());
        }
//        if (expectedResponseType.isAssignableFrom(responseMessage.payloadType())) {
//            InstanceResponseType<R> instanceResponseType = new InstanceResponseType<>(expectedResponseType);
//            return Flux.just(new ConvertingResponseMessage<>(instanceResponseType, responseMessage));
//        } else {
//            MultipleInstancesResponseType<R> multiResponseType =
//                    new MultipleInstancesResponseType<>(expectedResponseType);
//            ConvertingResponseMessage<List<R>> convertingMessage =
//                    new ConvertingResponseMessage<>(multiResponseType, responseMessage);
//            return Flux.fromStream(convertingMessage.payload()
//                                                    .stream()
//                                                    .map(payload -> singleMessage(responseMessage,
//                                                                                  payload,
//                                                                                  expectedResponseType)));
//        }
        return null;
    }

    private <R> QueryResponseMessage singleMessage(QueryResponseMessage original,
                                                      R newPayload,
                                                      Class<R> expectedPayloadType) {
        GenericMessage delegate = new GenericMessage(original.identifier(),
                                                          original.type(),
                                                          newPayload,
                                                          expectedPayloadType,
                                                          original.metadata());
        return new GenericQueryResponseMessage(delegate);
    }
    */

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
            return connection.queryChannel().prepareDisconnect();
        }
        // TODO Integration with LocalSegmentAdapter, which should integrate with QueryBusConnector.Handler, replacement
        //if (!localSegmentAdapter.awaitTermination(queryInProgressAwait)) {
        //    logger.info(
        //            "Awaited termination of queries in progress without success. Going to cancel remaining queries in progress.");
        //    localSegmentAdapter.cancel();
        //}
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
     * A {@link QueryHandler} implementation serving as a wrapper around the local {@link QueryBus} to push through the
     * message handling and subscription query registration.
     *
     * TODO This should be used on AxonServerQueryBusConnector#subscribe, and the implementation should use the QueryBusConnector.Handler that is TBD.
     */
//    private class LocalSegmentAdapter implements QueryHandler {
//
//        private final Map<String, QueryProcessingTask> queriesInProgress = new ConcurrentHashMap<>();
//
//        @Override
//        public void handle(QueryRequest query, ReplyChannel<QueryResponse> responseHandler) {
//            stream(query, responseHandler).request(Long.MAX_VALUE);
//        }
//
//        @Override
//        public FlowControl stream(QueryRequest query, ReplyChannel<QueryResponse> responseHandler) {
//            Runnable onClose = () -> queriesInProgress.remove(query.getMessageIdentifier());
//            CloseAwareReplyChannel<QueryResponse> closeAwareReplyChannel =
//                    new CloseAwareReplyChannel<>(responseHandler, onClose);
//
//            long priority = ProcessingInstructionHelper.priority(query.getProcessingInstructionsList());
//            QueryProcessingTask processingTask = new QueryProcessingTask(
//                    localSegment, query, closeAwareReplyChannel, serializer, configuration.getClientId(), spanFactory
//            );
//            PriorityRunnable priorityTask = new PriorityRunnable(processingTask,
//                                                                 priority,
//                                                                 TASK_SEQUENCE.incrementAndGet());
//
//            queriesInProgress.put(query.getMessageIdentifier(), processingTask);
//            queryExecutor.execute(priorityTask);
//
//            return new FlowControl() {
//                @Override
//                public void request(long requested) {
//                    queryExecutor.execute(new PriorityRunnable(() -> processingTask.request(requested),
//                                                               priorityTask.priority(),
//                                                               TASK_SEQUENCE.incrementAndGet())
//                    );
//                }
//
//                @Override
//                public void cancel() {
//                    queryExecutor.execute(new PriorityRunnable(processingTask::cancel,
//                                                               priorityTask.priority(),
//                                                               TASK_SEQUENCE.incrementAndGet()));
//                }
//            };
//        }
//
//        @Override
//        public io.axoniq.axonserver.connector.Registration registerSubscriptionQuery(SubscriptionQuery query,
//                                                                                     UpdateHandler sendUpdate) {
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
//        }
//
//        private boolean awaitTermination(Duration timeout) {
//            Instant startAwait = Instant.now();
//            Instant endAwait = startAwait.plusSeconds(timeout.getSeconds());
//            while (Instant.now().isBefore(endAwait) && !queriesInProgress.isEmpty()) {
//                queriesInProgress.values()
//                                 .stream()
//                                 .findFirst()
//                                 .ifPresent(queryInProgress -> {
//                                     while (Instant.now().isBefore(endAwait) && queryInProgress.resultPending()) {
//                                         LockSupport.parkNanos(10_000_000);
//                                     }
//                                 });
//            }
//            return queriesInProgress.isEmpty();
//        }
//
//        private void cancel() {
//            queriesInProgress.values()
//                             .iterator()
//                             .forEachRemaining(QueryProcessingTask::cancel);
//        }
//    }
}
