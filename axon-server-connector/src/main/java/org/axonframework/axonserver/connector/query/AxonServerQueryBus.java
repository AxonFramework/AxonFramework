/*
 * Copyright (c) 2010-2020. Axon Framework
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

import io.axoniq.axonserver.grpc.ErrorMessage;
import io.axoniq.axonserver.grpc.InstructionAck;
import io.axoniq.axonserver.grpc.query.QueryComplete;
import io.axoniq.axonserver.grpc.query.QueryProviderInbound;
import io.axoniq.axonserver.grpc.query.QueryProviderInbound.RequestCase;
import io.axoniq.axonserver.grpc.query.QueryProviderOutbound;
import io.axoniq.axonserver.grpc.query.QueryRequest;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import io.axoniq.axonserver.grpc.query.QueryServiceGrpc;
import io.axoniq.axonserver.grpc.query.QuerySubscription;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.netty.util.internal.OutOfDirectMemoryError;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.DefaultHandlers;
import org.axonframework.axonserver.connector.DefaultInstructionAckSource;
import org.axonframework.axonserver.connector.DispatchInterceptors;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.Handlers;
import org.axonframework.axonserver.connector.InstructionAckSource;
import org.axonframework.axonserver.connector.TargetContextResolver;
import org.axonframework.axonserver.connector.command.AxonServerRegistration;
import org.axonframework.axonserver.connector.query.subscription.AxonServerSubscriptionQueryResult;
import org.axonframework.axonserver.connector.query.subscription.DeserializedResult;
import org.axonframework.axonserver.connector.query.subscription.SubscriptionMessageSerializer;
import org.axonframework.axonserver.connector.query.subscription.SubscriptionQueryRequestTarget;
import org.axonframework.axonserver.connector.util.BufferingSpliterator;
import org.axonframework.axonserver.connector.util.ExceptionSerializer;
import org.axonframework.axonserver.connector.util.ExecutorServiceBuilder;
import org.axonframework.axonserver.connector.util.FlowControllingStreamObserver;
import org.axonframework.axonserver.connector.util.ResubscribableStreamObserver;
import org.axonframework.axonserver.connector.util.UpstreamAwareStreamObserver;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.AxonException;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.common.Registration;
import org.axonframework.lifecycle.Phase;
import org.axonframework.lifecycle.ShutdownHandler;
import org.axonframework.lifecycle.ShutdownLatch;
import org.axonframework.lifecycle.StartHandler;
import org.axonframework.messaging.Distributed;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SubscriptionQueryBackpressure;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static io.axoniq.axonserver.grpc.query.QueryProviderInbound.RequestCase.*;
import static org.axonframework.axonserver.connector.util.ProcessingInstructionHelper.numberOfResults;
import static org.axonframework.axonserver.connector.util.ProcessingInstructionHelper.priority;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Axon {@link QueryBus} implementation that connects to Axon Server to submit and receive queries and query responses.
 * Delegates incoming queries to the provided {@code localSegment}.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public class AxonServerQueryBus implements QueryBus, Distributed<QueryBus> {

    private static final Logger logger = LoggerFactory.getLogger(AxonServerQueryBus.class);

    private static final int DIRECT_QUERY_NUMBER_OF_RESULTS = 1;
    private static final long DIRECT_QUERY_TIMEOUT_MS = TimeUnit.HOURS.toMillis(1);
    private static final int SCATTER_GATHER_NUMBER_OF_RESULTS = -1;

    private final AxonServerConnectionManager axonServerConnectionManager;
    private final AxonServerConfiguration configuration;
    private final QueryUpdateEmitter updateEmitter;
    private final QueryBus localSegment;
    private final QuerySerializer serializer;
    private final SubscriptionMessageSerializer subscriptionSerializer;
    private final QueryPriorityCalculator priorityCalculator;

    private final QueryProcessor queryProcessor;
    private final DispatchInterceptors<QueryMessage<?, ?>> dispatchInterceptors;
    private final Map<String, Set<String>> subscriptions = new ConcurrentHashMap<>();
    private final Handlers<QueryProviderInbound.RequestCase, BiConsumer<QueryProviderInbound, StreamObserver<QueryProviderOutbound>>> queryHandlers = new DefaultHandlers<>();
    private final TargetContextResolver<? super QueryMessage<?, ?>> targetContextResolver;
    private final InstructionAckSource<QueryProviderOutbound> instructionAckSource;
    private final ShutdownLatch shutdownLatch = new ShutdownLatch();

    /**
     * Creates an instance of the Axon Server {@link QueryBus} client. Will connect to an Axon Server instance to submit
     * and receive queries and query responses. The {@link TargetContextResolver} defaults to a lambda returning the
     * output from {@link AxonServerConfiguration#getContext()}.
     *
     * @param axonServerConnectionManager a {@link AxonServerConnectionManager} which creates the connection to an Axon
     *                                    Server platform
     * @param configuration               the {@link AxonServerConfiguration} containing specifics like the client and
     *                                    component names used to identify the application in Axon Server among others
     * @param updateEmitter               the {@link QueryUpdateEmitter} used to emits incremental updates to
     *                                    subscription queries
     * @param localSegment                a {@link QueryBus} handling the incoming queries for the local application
     * @param messageSerializer           a {@link Serializer} used to de-/serialize the payload and metadata of query
     *                                    messages and responses
     * @param genericSerializer           a {@link Serializer} used for communication of other objects than query
     *                                    message and response, payload and metadata
     * @param priorityCalculator          a {@link QueryPriorityCalculator} calculating the request priority based on
     *                                    the content, and adds this priority to the request
     * @deprecated in favor of using the {@link Builder} (with the convenience {@link #builder()} method) to instantiate
     * an Axon Server Query Bus
     */
    @Deprecated
    public AxonServerQueryBus(AxonServerConnectionManager axonServerConnectionManager,
                              AxonServerConfiguration configuration,
                              QueryUpdateEmitter updateEmitter,
                              QueryBus localSegment,
                              Serializer messageSerializer,
                              Serializer genericSerializer,
                              QueryPriorityCalculator priorityCalculator) {
        this(axonServerConnectionManager,
             configuration,
             updateEmitter,
             localSegment,
             messageSerializer,
             genericSerializer,
             priorityCalculator,
             q -> configuration.getContext()
        );
    }

    /**
     * Creates an instance of the Axon Server {@link QueryBus} client. Will connect to an Axon Server instance to submit
     * and receive queries and query responses.
     *
     * @param axonServerConnectionManager a {@link AxonServerConnectionManager} which creates the connection to an Axon
     *                                    Server platform
     * @param configuration               the {@link AxonServerConfiguration} containing specifics like the client and
     *                                    component names used to identify the application in Axon Server among others
     * @param updateEmitter               the {@link QueryUpdateEmitter} used to emits incremental updates to
     *                                    subscription queries
     * @param localSegment                a {@link QueryBus} handling the incoming queries for the local application
     * @param messageSerializer           a {@link Serializer} used to de-/serialize the payload and metadata of query
     *                                    messages and responses
     * @param genericSerializer           a {@link Serializer} used for communication of other objects than query
     *                                    message and response, payload and metadata
     * @param priorityCalculator          a {@link QueryPriorityCalculator} calculating the request priority based on
     *                                    the content, and adds this priority to the request
     * @param targetContextResolver       resolves the context a given query should be dispatched in
     * @deprecated in favor of using the {@link Builder} (with the convenience {@link #builder()} method) to instantiate
     * an Axon Server Query Bus
     */
    @Deprecated
    public AxonServerQueryBus(AxonServerConnectionManager axonServerConnectionManager,
                              AxonServerConfiguration configuration,
                              QueryUpdateEmitter updateEmitter,
                              QueryBus localSegment,
                              Serializer messageSerializer,
                              Serializer genericSerializer,
                              QueryPriorityCalculator priorityCalculator,
                              TargetContextResolver<? super QueryMessage<?, ?>> targetContextResolver) {
        this.axonServerConnectionManager = axonServerConnectionManager;
        this.configuration = configuration;
        this.updateEmitter = updateEmitter;
        this.localSegment = localSegment;
        this.serializer = new QuerySerializer(messageSerializer, genericSerializer, configuration);
        this.subscriptionSerializer =
                new SubscriptionMessageSerializer(messageSerializer, genericSerializer, configuration);
        this.priorityCalculator = priorityCalculator;
        String context = configuration.getContext();
        this.targetContextResolver = targetContextResolver.orElse(m -> context);

        //noinspection unchecked
        this.queryProcessor = new QueryProcessor(
                context, configuration, ExecutorServiceBuilder.defaultQueryExecutorServiceBuilder(),
                so -> (StreamObserver<QueryProviderOutbound>) so.getRequestStream()
        );
        dispatchInterceptors = new DispatchInterceptors<>();

        this.axonServerConnectionManager.addReconnectListener(context, queryProcessor::publishSubscriptions);
        this.axonServerConnectionManager.addReconnectInterceptor(this::interceptReconnectRequest);

        this.axonServerConnectionManager.addDisconnectListener(context, queryProcessor::unsubscribeAll);
        this.axonServerConnectionManager.addDisconnectListener(this::onApplicationDisconnected);
        SubscriptionQueryRequestTarget target =
                new SubscriptionQueryRequestTarget(localSegment, qpo -> publish(context, qpo), subscriptionSerializer);
        this.on(SUBSCRIPTION_QUERY_REQUEST, target::onSubscriptionQueryRequest);
        this.axonServerConnectionManager.addDisconnectListener(target::onApplicationDisconnected);
        this.instructionAckSource = new DefaultInstructionAckSource<>(ack -> QueryProviderOutbound.newBuilder()
                                                                                                  .setAck(ack)
                                                                                                  .build());
    }

    /**
     * Instantiate a {@link AxonServerQueryBus} based on the fields contained in the {@link Builder}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link AxonServerQueryBus} instance
     */
    public AxonServerQueryBus(Builder builder) {
        builder.validate();
        this.axonServerConnectionManager = builder.axonServerConnectionManager;
        this.configuration = builder.configuration;
        this.updateEmitter = builder.updateEmitter;
        this.localSegment = builder.localSegment;
        this.serializer = builder.buildQuerySerializer();
        this.subscriptionSerializer = builder.buildSubscriptionMessageSerializer();
        this.priorityCalculator = builder.priorityCalculator;
        String context = configuration.getContext();
        this.targetContextResolver = builder.targetContextResolver.orElse(m -> context);

        this.queryProcessor = new QueryProcessor(context,
                                                 configuration,
                                                 builder.executorServiceBuilder,
                                                 builder.requestStreamFactory);
        dispatchInterceptors = new DispatchInterceptors<>();

        this.axonServerConnectionManager.addReconnectListener(context, queryProcessor::publishSubscriptions);
        this.axonServerConnectionManager.addReconnectInterceptor(this::interceptReconnectRequest);

        this.axonServerConnectionManager.addDisconnectListener(context, queryProcessor::unsubscribeAll);
        this.axonServerConnectionManager.addDisconnectListener(this::onApplicationDisconnected);
        SubscriptionQueryRequestTarget target =
                new SubscriptionQueryRequestTarget(localSegment, qpo -> publish(context, qpo), subscriptionSerializer);
        this.on(SUBSCRIPTION_QUERY_REQUEST, target::onSubscriptionQueryRequest);
        this.axonServerConnectionManager.addDisconnectListener(target::onApplicationDisconnected);
        this.instructionAckSource = builder.instructionAckSource;
    }

    /**
     * Instantiate a Builder to be able to create an {@link AxonServerQueryBus}.
     * <p>
     * The {@link QueryPriorityCalculator} is defaulted to {@link QueryPriorityCalculator#defaultQueryPriorityCalculator()}
     * and the {@link TargetContextResolver} defaults to a lambda returning the {@link
     * AxonServerConfiguration#getContext()} as the context. The {@link ExecutorServiceBuilder} defaults to {@link
     * ExecutorServiceBuilder#defaultQueryExecutorServiceBuilder()}. The {@link AxonServerConnectionManager}, the {@link
     * AxonServerConfiguration}, the local {@link QueryBus}, the {@link QueryUpdateEmitter}, and the message and generic
     * {@link Serializer}s are <b>hard requirements</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link AxonServerQueryBus}
     */
    public static Builder builder() {
        return new Builder();
    }

    private Consumer<String> interceptReconnectRequest(Consumer<String> reconnect) {
        if (subscriptions.isEmpty()) {
            return reconnect;
        }
        return c -> logger.info("Reconnect for context [{}] refused because there are active subscription queries.", c);
    }

    private void onApplicationDisconnected(String context) {
        subscriptions.remove(context);
    }

    /**
     * Start the Axon Server {@link QueryBus} implementation.
     */
    @StartHandler(phase = Phase.INBOUND_QUERY_CONNECTOR)
    public void start() {
        shutdownLatch.initialize();
    }

    @Override
    public <R> Registration subscribe(String queryName,
                                      Type responseType,
                                      MessageHandler<? super QueryMessage<?, R>> handler) {
        return new AxonServerRegistration(
                queryProcessor.subscribe(queryName, responseType, configuration.getComponentName(), handler),
                () -> queryProcessor.removeAndUnsubscribe(queryName, responseType, configuration.getComponentName())
        );
    }

    @Override
    public <Q, R> CompletableFuture<QueryResponseMessage<R>> query(QueryMessage<Q, R> queryMessage) {
        shutdownLatch.ifShuttingDown(String.format("Cannot dispatch new %s as this bus is being shut down", "queries"));

        QueryMessage<Q, R> interceptedQuery = dispatchInterceptors.intercept(queryMessage);
        ShutdownLatch.ActivityHandle queryInTransit = shutdownLatch.registerActivity();
        CompletableFuture<QueryResponseMessage<R>> queryTransaction = new CompletableFuture<>();
        try {
            String context = targetContextResolver.resolveContext(interceptedQuery);
            QueryRequest queryRequest =
                    serializer.serializeRequest(interceptedQuery,
                                                DIRECT_QUERY_NUMBER_OF_RESULTS,
                                                DIRECT_QUERY_TIMEOUT_MS,
                                                priorityCalculator.determinePriority(interceptedQuery));

            QueryServiceGrpc.QueryServiceStub queryService = queryService(context);
            queryService.query(queryRequest, new StreamObserver<QueryResponse>() {
                @Override
                public void onNext(QueryResponse queryResponse) {
                    logger.debug("Received query response [{}]", queryResponse);
                    QueryResponseMessage<R> responseMessage =
                            serializer.deserializeResponse(queryResponse, queryMessage.getResponseType());
                    queryTransaction.complete(responseMessage);
                }

                @Override
                public void onError(Throwable throwable) {
                    logger.debug("Received error while waiting for first response", throwable);
                    AxonException exception =
                            ErrorCode.QUERY_DISPATCH_ERROR.convert(configuration.getClientId(), throwable);
                    queryTransaction.completeExceptionally(exception);
                    queryInTransit.end();
                }

                @Override
                public void onCompleted() {
                    if (!queryTransaction.isDone()) {
                        ErrorMessage errorMessage = ErrorMessage.newBuilder()
                                                                .setMessage("No result from query executor")
                                                                .build();
                        AxonException exception = ErrorCode.QUERY_DISPATCH_ERROR.convert(errorMessage);
                        queryTransaction.completeExceptionally(exception);
                    }
                    queryInTransit.end();
                }
            });
        } catch (Exception e) {
            logger.debug("There was a problem issuing a query {}.", interceptedQuery, e);
            AxonException exception = ErrorCode.QUERY_DISPATCH_ERROR.convert(configuration.getClientId(), e);
            queryTransaction.completeExceptionally(exception);
            queryInTransit.end();
        }

        return queryTransaction;
    }

    @Override
    public <Q, R> Stream<QueryResponseMessage<R>> scatterGather(QueryMessage<Q, R> queryMessage,
                                                                long timeout,
                                                                TimeUnit timeUnit) {
        shutdownLatch.ifShuttingDown(String.format(
                "Cannot dispatch new %s as this bus is being shut down", "scatter-gather queries"
        ));

        QueryMessage<Q, R> interceptedQuery = dispatchInterceptors.intercept(queryMessage);
        ShutdownLatch.ActivityHandle queryInTransit = shutdownLatch.registerActivity();
        BufferingSpliterator<QueryResponseMessage<R>> resultSpliterator =
                new BufferingSpliterator<>(Instant.now().plusMillis(timeUnit.toMillis(timeout)));
        try {
            String context = targetContextResolver.resolveContext(interceptedQuery);
            QueryRequest queryRequest =
                    serializer.serializeRequest(interceptedQuery,
                                                SCATTER_GATHER_NUMBER_OF_RESULTS,
                                                timeUnit.toMillis(timeout),
                                                priorityCalculator.determinePriority(interceptedQuery));

            QueryServiceGrpc.QueryServiceStub queryService =
                    queryService(context).withDeadlineAfter(timeout, timeUnit);
            queryService.query(queryRequest, new UpstreamAwareStreamObserver<QueryResponse>() {
                @Override
                public void onNext(QueryResponse queryResponse) {
                    logger.debug("Received query response [{}]", queryResponse);
                    if (queryResponse.hasErrorMessage()) {
                        logger.debug("The received query response has error message [{}]",
                                     queryResponse.getErrorMessage());
                    } else {
                        if (!resultSpliterator.put(serializer.deserializeResponse(
                                queryResponse, queryMessage.getResponseType()
                        ))) {
                            getRequestStream().cancel("Cancellation requested by client", null);
                        }
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    if (!isDeadlineExceeded(throwable)) {
                        logger.info("Received error while waiting for responses", throwable);
                    }
                    resultSpliterator.cancel(throwable);
                    queryInTransit.end();
                }

                @Override
                public void onCompleted() {
                    resultSpliterator.cancel(null);
                    queryInTransit.end();
                }
            });
        } catch (Exception e) {
            logger.debug("There was a problem issuing a scatter-gather query {}.", interceptedQuery, e);
            queryInTransit.end();
            throw e;
        }

        return StreamSupport.stream(resultSpliterator, false).onClose(() -> resultSpliterator.cancel(null));
    }

    private boolean isDeadlineExceeded(Throwable throwable) {
        return throwable instanceof StatusRuntimeException
                && ((StatusRuntimeException) throwable).getStatus().getCode().equals(Status.Code.DEADLINE_EXCEEDED);
    }

    private void publish(String context, QueryProviderOutbound providerOutbound) {
        this.queryProcessor.getSubscriberObserver(context).onNext(providerOutbound);
    }

    @SuppressWarnings("SameParameterValue")
    private void on(RequestCase requestCase, BiConsumer<String, QueryProviderInbound> handler) {
        queryHandlers.register(requestCase, wrapWithResult(handler));
    }

    private BiConsumer<QueryProviderInbound, StreamObserver<QueryProviderOutbound>> wrapWithResult(
            BiConsumer<String, QueryProviderInbound> handler) {
        return (inbound, stream) -> {
            try {
                handler.accept(configuration.getContext(), inbound);
                instructionAckSource.sendSuccessfulAck(inbound.getInstructionId(), stream);
            } catch (Exception e) {
                logger.warn("Error happened while handling query instruction {}.", inbound.getInstructionId());
                ErrorMessage error = ErrorMessage.newBuilder()
                                                 .setErrorCode(ErrorCode.INSTRUCTION_ACK_ERROR.errorCode())
                                                 .addDetails("Error happened while handling query instruction")
                                                 .setLocation(configuration.getClientId())
                                                 .build();
                instructionAckSource.sendUnsuccessfulAck(inbound.getInstructionId(), error, stream);
            }
        };
    }

    @Override
    public <Q, I, U> SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> subscriptionQuery(
            SubscriptionQueryMessage<Q, I, U> query,
            SubscriptionQueryBackpressure backPressure,
            int updateBufferSize) {
        shutdownLatch.ifShuttingDown(String.format(
                "Cannot dispatch new %s as this bus is being shut down", "subscription queries"
        ));

        SubscriptionQueryMessage<Q, I, U> interceptedQuery = dispatchInterceptors.intercept(query);
        String subscriptionId = interceptedQuery.getIdentifier();
        String context = targetContextResolver.resolveContext(interceptedQuery);

        Set<String> contextSubscriptions = subscriptions.computeIfAbsent(context, k -> new ConcurrentSkipListSet<>());
        if (!contextSubscriptions.add(subscriptionId)) {
            String errorMessage =
                    "There already is a subscription query with subscription Id [" +
                            subscriptionId + "] for context [" + context + "]";
            logger.warn(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }
        logger.debug("Subscription Query requested with subscription Id [{}]", subscriptionId);

        AxonServerSubscriptionQueryResult result = new AxonServerSubscriptionQueryResult(
                subscriptionSerializer.serialize(interceptedQuery),
                this.queryService(context)::subscription,
                configuration,
                backPressure,
                updateBufferSize,
                () -> contextSubscriptions.remove(subscriptionId)
        );
        return new DeserializedResult<>(result.get(), subscriptionSerializer);
    }

    QueryServiceGrpc.QueryServiceStub queryService(String context) {
        return QueryServiceGrpc.newStub(axonServerConnectionManager.getChannel(context));
    }

    @Override
    public QueryUpdateEmitter queryUpdateEmitter() {
        return updateEmitter;
    }

    @Override
    public QueryBus localSegment() {
        return localSegment;
    }

    @Override
    public Registration registerHandlerInterceptor(MessageHandlerInterceptor<? super QueryMessage<?, ?>> interceptor) {
        return localSegment.registerHandlerInterceptor(interceptor);
    }

    @Override
    public Registration registerDispatchInterceptor(
            MessageDispatchInterceptor<? super QueryMessage<?, ?>> dispatchInterceptor) {
        return dispatchInterceptors.registerDispatchInterceptor(dispatchInterceptor);
    }

    /**
     * Disconnect the query bus from Axon Server, by unsubscribing all known query handlers. This shutdown operation is
     * performed in the {@link Phase#INBOUND_QUERY_CONNECTOR} phase.
     */
    @ShutdownHandler(phase = Phase.INBOUND_QUERY_CONNECTOR)
    public void disconnect() {
        queryProcessor.unsubscribeAll();
    }

    /**
     * Shutdown the query bus asynchronously for dispatching queries to Axon Server. This process will wait for
     * dispatched queries which have not received a response yet and will close off running subscription queries. This
     * shutdown operation is performed in the {@link Phase#OUTBOUND_QUERY_CONNECTORS} phase.
     *
     * @return a completable future which is resolved once all query dispatching activities are completed
     */
    @ShutdownHandler(phase = Phase.OUTBOUND_QUERY_CONNECTORS)
    public CompletableFuture<Void> shutdownDispatching() {
        return CompletableFuture.runAsync(queryProcessor::disconnect)
                                .thenCompose(r -> shutdownLatch.initiateShutdown())
                                .thenRun(queryProcessor::removeLocalSubscriptions);
    }

    private class QueryProcessor {

        private static final int QUERY_QUEUE_CAPACITY = 1000;
        private static final int DEFAULT_PRIORITY = 0;

        private final String context;
        private final ConcurrentMap<QueryDefinition, Set<MessageHandler<? super QueryMessage<?, ?>>>> subscribedQueries;
        private final ExecutorService queryExecutor;
        private final Function<UpstreamAwareStreamObserver<QueryProviderInbound>, StreamObserver<QueryProviderOutbound>> requestStreamFactory;

        private volatile boolean subscribing;
        private volatile boolean running = true;
        private volatile StreamObserver<QueryProviderOutbound> outboundStreamObserver;

        QueryProcessor(String context,
                       AxonServerConfiguration configuration,
                       ExecutorServiceBuilder executorServiceBuilder,
                       Function<UpstreamAwareStreamObserver<QueryProviderInbound>, StreamObserver<QueryProviderOutbound>> requestStreamFactory) {
            this.context = context;
            this.requestStreamFactory = requestStreamFactory;
            subscribedQueries = new ConcurrentHashMap<>();
            PriorityBlockingQueue<Runnable> queryProcessQueue = new PriorityBlockingQueue<>(
                    QUERY_QUEUE_CAPACITY,
                    Comparator.comparingLong(
                            r -> r instanceof QueryProcessingTask
                                    ? ((QueryProcessingTask) r).getPriority()
                                    : DEFAULT_PRIORITY
                    )
            );
            queryExecutor = executorServiceBuilder.apply(configuration, queryProcessQueue);
            queryHandlers.register(QUERY, (inbound, stream) -> {
                queryExecutor.execute(new QueryProcessor.QueryProcessingTask(inbound.getQuery()));
                instructionAckSource.sendSuccessfulAck(inbound.getInstructionId(), stream);
            });
            queryHandlers.register(ACK, (inbound, stream) -> {
                if (isUnsupportedInstructionErrorResult(inbound.getAck())) {
                    logger.warn("Unsupported query instruction sent to the server. {}", inbound.getAck());
                } else {
                    logger.trace("Received query ack {}.", inbound.getAck());
                }
            });
        }

        private boolean isUnsupportedInstructionErrorResult(InstructionAck instructionResult) {
            return instructionResult.hasError()
                    && instructionResult.getError().getErrorCode().equals(ErrorCode.UNSUPPORTED_INSTRUCTION
                                                                                  .errorCode());
        }

        private void publishSubscriptions() {
            if (subscribedQueries.isEmpty() || subscribing) {
                return;
            }

            try {
                StreamObserver<QueryProviderOutbound> subscriberStreamObserver = getSubscriberObserver(context);
                subscribedQueries.forEach((queryDefinition, handlers) -> subscriberStreamObserver.onNext(
                        QueryProviderOutbound.newBuilder()
                                             .setSubscribe(buildQuerySubscription(queryDefinition, handlers.size()))
                                             .build()
                ));
            } catch (Exception ex) {
                logger.warn("Error while resubscribing query handlers", ex);
            }
        }

        public <R> Registration subscribe(String queryName,
                                          Type responseType,
                                          String componentName,
                                          MessageHandler<? super QueryMessage<?, R>> handler) {
            subscribing = true;
            //noinspection rawtypes - Supressed due to missing generic on `subscribedQueries`
            Set registrations = subscribedQueries.computeIfAbsent(
                    new QueryDefinition(queryName, responseType.getTypeName(), componentName),
                    k -> new CopyOnWriteArraySet<>()
            );
            //noinspection unchecked
            registrations.add(handler);

            try {
                getSubscriberObserver(context).onNext(QueryProviderOutbound.newBuilder().setSubscribe(
                        QuerySubscription.newBuilder()
                                         .setMessageId(UUID.randomUUID().toString())
                                         .setClientId(configuration.getClientId())
                                         .setComponentName(componentName)
                                         .setQuery(queryName)
                                         .setResultName(responseType.getTypeName())
                                         .setNrOfHandlers(registrations.size())
                                         .build()).build()
                );
            } catch (Exception ex) {
                logger.warn("Error subscribing query handler", ex);
            } finally {
                subscribing = false;
            }
            return localSegment.subscribe(queryName, responseType, handler);
        }

        private void processQuery(QueryRequest query) {
            String requestId = query.getMessageIdentifier();
            QueryMessage<Object, Object> queryMessage = serializer.deserializeRequest(query);
            try {
                if (numberOfResults(query.getProcessingInstructionsList()) == 1) {
                    QueryResponseMessage<Object> response = localSegment.query(queryMessage).get();
                    sendResponse(query.getQuery(),
                                 QueryProviderOutbound.newBuilder()
                                                      .setQueryResponse(serializer.serializeResponse(response, requestId))
                                                      .build());
                } else {
                    localSegment.scatterGather(queryMessage, 0, TimeUnit.SECONDS)
                                .forEach(response -> sendResponse(query.getQuery(),
                                                                  QueryProviderOutbound.newBuilder()
                                                                                       .setQueryResponse(serializer.serializeResponse(response, requestId))
                                                                                       .build())
                                );
                }

                sendResponse(query.getQuery(), QueryProviderOutbound.newBuilder()
                                                                    .setQueryComplete(
                                                                            QueryComplete.newBuilder()
                                                                                         .setMessageId(UUID.randomUUID().toString())
                                                                                         .setRequestId(requestId)
                                                                    ).build());
            } catch (Exception e) {
                logger.warn("Failed to dispatch query [{}] locally", queryMessage.getQueryName(), e);
                sendResponse(query.getQuery(), QueryProviderOutbound.newBuilder().setQueryResponse(
                        QueryResponse.newBuilder()
                                     .setMessageIdentifier(UUID.randomUUID().toString())
                                     .setRequestIdentifier(requestId)
                                     .setErrorMessage(
                                             ExceptionSerializer.serialize(configuration.getClientId(), e)
                                     )
                                     .setErrorCode(ErrorCode.QUERY_EXECUTION_ERROR.errorCode())
                                     .build()).build());
            }
        }

        private void sendResponse(String queryName, QueryProviderOutbound response) {
            StreamObserver<QueryProviderOutbound> out = this.outboundStreamObserver;
            if (out != null) {
                out.onNext(response);
            } else {
                logger.info("Unable to send Query Result for query [{}] due to lost connection to AxonServer",
                            queryName);
            }
        }

        private synchronized StreamObserver<QueryProviderOutbound> getSubscriberObserver(String context) {
            StreamObserver<QueryProviderOutbound> out = this.outboundStreamObserver;
            if (out != null) {
                return out;
            }
            StreamObserver<QueryProviderInbound> queryProviderInboundStreamObserver = new UpstreamAwareStreamObserver<QueryProviderInbound>() {
                @Override
                public void onNext(QueryProviderInbound inboundRequest) {
                    RequestCase requestCase = inboundRequest.getRequestCase();
                    Collection<BiConsumer<QueryProviderInbound, StreamObserver<QueryProviderOutbound>>> defaultHandlers = Collections
                            .singleton((qpi, stream) -> instructionAckSource
                                    .sendUnsupportedInstruction(qpi.getInstructionId(),
                                                                configuration.getClientId(),
                                                                stream));
                    queryHandlers.getOrDefault(configuration.getContext(), requestCase, defaultHandlers)
                                 .forEach(consumer -> consumer.accept(inboundRequest,
                                                                      requestStreamFactory.apply(this)));
                }

                @SuppressWarnings("Duplicates")
                @Override
                public void onError(Throwable ex) {
                    logger.warn("Query Inbound Stream closed with error", ex);
                    completeRequestStream();
                    outboundStreamObserver = null;
                }

                @Override
                public void onCompleted() {
                    logger.info("Received completed from server.");
                    completeRequestStream();
                    outboundStreamObserver = null;
                }
            };

            ResubscribableStreamObserver<QueryProviderInbound> resubscribableStreamObserver =
                    new ResubscribableStreamObserver<>(queryProviderInboundStreamObserver, t -> reconnectQueryStream());

            StreamObserver<QueryProviderOutbound> streamObserver = axonServerConnectionManager.getQueryStream(
                    context, resubscribableStreamObserver
            );

            logger.info("Creating new query stream subscriber");

            outboundStreamObserver = new FlowControllingStreamObserver<>(
                    streamObserver,
                    configuration.getClientId(),
                    configuration.getQueryFlowControl(),
                    flowControl -> QueryProviderOutbound.newBuilder().setFlowControl(flowControl).build(),
                    t -> t.getRequestCase().equals(QueryProviderOutbound.RequestCase.QUERY_RESPONSE)
            ).sendInitialPermits();
            return outboundStreamObserver;
        }

        private void reconnectQueryStream() {
            unsubscribeAll();
            if (running) {
                publishSubscriptions();
            }
        }

        private void unsubscribeAll() {
            if (outboundStreamObserver != null) {
                subscribedQueries.keySet().forEach(this::unsubscribe);
            }
        }

        public void removeAndUnsubscribe(String queryName, Type responseType, String componentName) {
            removeAndUnsubscribe(new QueryDefinition(queryName, responseType.getTypeName(), componentName));
        }

        private void removeAndUnsubscribe(QueryDefinition queryDefinition) {
            if (subscribedQueries.remove(queryDefinition) != null) {
                unsubscribe(queryDefinition);
            }
        }

        private void unsubscribe(QueryDefinition queryDefinition) {
            try {
                getSubscriberObserver(context).onNext(
                        QueryProviderOutbound.newBuilder()
                                             .setUnsubscribe(buildQuerySubscription(queryDefinition, 1))
                                             .build()
                );
            } catch (Exception ignored) {
                // This exception is ignored
            }
        }

        private void removeLocalSubscriptions() {
            subscribedQueries.clear();
        }

        void disconnect() {
            running = false;
            queryExecutor.shutdown();
            try {
                if (!queryExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warn("Awaited Query Bus termination for 5 seconds. Wait period extended by 30 seconds.");
                }
                if (!queryExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    logger.warn("Awaited Query Bus termination for 35 seconds. Will shutdown forcefully.");
                    queryExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.warn("Awaiting termination of Query Bus got interrupted. Will shutdown immediately", e);
                queryExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }

            if (outboundStreamObserver != null) {
                outboundStreamObserver.onCompleted();
            }
        }

        private QuerySubscription buildQuerySubscription(QueryDefinition queryDefinition, int nrHandlers) {
            return QuerySubscription.newBuilder()
                                    .setClientId(configuration.getClientId())
                                    .setMessageId(UUID.randomUUID().toString())
                                    .setComponentName(queryDefinition.componentName)
                                    .setQuery(queryDefinition.queryName)
                                    .setNrOfHandlers(nrHandlers)
                                    .setResultName(queryDefinition.responseName)
                                    .build();
        }

        private class QueryDefinition {

            private final String queryName;
            private final String responseName;
            private final String componentName;

            QueryDefinition(String queryName, String responseName, String componentName) {
                this.queryName = queryName;
                this.responseName = responseName;
                this.componentName = componentName;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                QueryDefinition that = (QueryDefinition) o;
                return Objects.equals(queryName, that.queryName) &&
                        Objects.equals(responseName, that.responseName) &&
                        Objects.equals(componentName, that.componentName);
            }

            @Override
            public int hashCode() {
                return Objects.hash(queryName, responseName, componentName);
            }
        }

        /**
         * A {@link Runnable} implementation which is given to a {@link PriorityBlockingQueue} to be consumed by the
         * query {@link ExecutorService}, in order. The {@code priority} is retrieved from the provided {@link
         * QueryRequest} and used to priorities this {@link QueryProcessingTask} among others of it's kind.
         */
        private class QueryProcessingTask implements Runnable {

            private final long priority;
            private final QueryRequest queryRequest;

            private QueryProcessingTask(QueryRequest queryRequest) {
                this.priority = -priority(queryRequest.getProcessingInstructionsList());
                this.queryRequest = queryRequest;
            }

            public long getPriority() {
                return priority;
            }

            @Override
            public void run() {
                try {
                    logger.debug("Will process query [{}]", queryRequest.getQuery());
                    processQuery(queryRequest);
                } catch (RuntimeException | OutOfDirectMemoryError e) {
                    logger.warn("Query Processor had an exception when processing query [{}]",
                                queryRequest.getQuery(),
                                e);
                }
            }
        }
    }

    /**
     * Builder class to instantiate an {@link AxonServerQueryBus}.
     * <p>
     * The {@link QueryPriorityCalculator} is defaulted to {@link QueryPriorityCalculator#defaultQueryPriorityCalculator()}
     * and the {@link TargetContextResolver} defaults to a lambda returning the {@link
     * AxonServerConfiguration#getContext()} as the context. The {@link ExecutorServiceBuilder} defaults to {@link
     * ExecutorServiceBuilder#defaultQueryExecutorServiceBuilder()}. The {@link AxonServerConnectionManager}, the {@link
     * AxonServerConfiguration}, the local {@link QueryBus}, the {@link QueryUpdateEmitter}, and the message and generic
     * {@link Serializer}s are <b>hard requirements</b> and as such should be provided.
     */
    public static class Builder {

        private AxonServerConnectionManager axonServerConnectionManager;
        private AxonServerConfiguration configuration;
        private QueryBus localSegment;
        private QueryUpdateEmitter updateEmitter;
        private Serializer messageSerializer;
        private Serializer genericSerializer;
        private QueryPriorityCalculator priorityCalculator = QueryPriorityCalculator.defaultQueryPriorityCalculator();
        private TargetContextResolver<? super QueryMessage<?, ?>> targetContextResolver =
                q -> configuration.getContext();
        private ExecutorServiceBuilder executorServiceBuilder =
                ExecutorServiceBuilder.defaultQueryExecutorServiceBuilder();
        @SuppressWarnings("unchecked")
        private Function<UpstreamAwareStreamObserver<QueryProviderInbound>, StreamObserver<QueryProviderOutbound>> requestStreamFactory =
                so -> (StreamObserver<QueryProviderOutbound>) so.getRequestStream();
        private InstructionAckSource<QueryProviderOutbound> instructionAckSource = new DefaultInstructionAckSource<>(ack -> QueryProviderOutbound
                .newBuilder().setAck(ack).build());

        /**
         * Sets the {@link AxonServerConnectionManager} used to create connections between this application and an Axon
         * Server instance.
         *
         * @param axonServerConnectionManager an {@link AxonServerConnectionManager} used to create connections between
         *                                    this application and an Axon Server instance
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder axonServerConnectionManager(AxonServerConnectionManager axonServerConnectionManager) {
            assertNonNull(axonServerConnectionManager, "AxonServerConnectionManager may not be null");
            this.axonServerConnectionManager = axonServerConnectionManager;
            return this;
        }

        /**
         * Sets the {@link AxonServerConfiguration} used to configure several components within the Axon Server Query
         * Bus, like setting the client id or the number of query handling threads used.
         *
         * @param configuration an {@link AxonServerConfiguration} used to configure several components within the Axon
         *                      Server Query Bus
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder configuration(AxonServerConfiguration configuration) {
            assertNonNull(configuration, "AxonServerConfiguration may not be null");
            this.configuration = configuration;
            return this;
        }

        /**
         * Sets the local {@link QueryBus} used to dispatch incoming queries to the local environment.
         *
         * @param localSegment a {@link QueryBus} used to dispatch incoming queries to the local environment
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder localSegment(QueryBus localSegment) {
            assertNonNull(localSegment, "Local QueryBus may not be null");
            this.localSegment = localSegment;
            return this;
        }

        /**
         * Sets the {@link QueryUpdateEmitter} which can be used to emit updates to queries. Required to honor the
         * {@link QueryBus#queryUpdateEmitter()} contract.
         *
         * @param updateEmitter a {@link QueryUpdateEmitter} which can be used to emit updates to queries
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder updateEmitter(QueryUpdateEmitter updateEmitter) {
            assertNonNull(updateEmitter, "QueryUpdateEmitter may not be null");
            this.updateEmitter = updateEmitter;
            return this;
        }

        /**
         * Sets the message {@link Serializer} used to de-/serialize incoming and outgoing queries and query responses.
         *
         * @param messageSerializer a {@link Serializer} used to de-/serialize incoming and outgoing queries and query
         *                          responses
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder messageSerializer(Serializer messageSerializer) {
            assertNonNull(messageSerializer, "Message Serializer may not be null");
            this.messageSerializer = messageSerializer;
            return this;
        }

        /**
         * Sets the generic {@link Serializer} used to de-/serialize incoming and outgoing query {@link
         * org.axonframework.messaging.responsetypes.ResponseType} implementations.
         *
         * @param genericSerializer a {@link Serializer} used to de-/serialize incoming and outgoing query {@link
         *                          org.axonframework.messaging.responsetypes.ResponseType} implementations.
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder genericSerializer(Serializer genericSerializer) {
            assertNonNull(genericSerializer, "Generic Serializer may not be null");
            this.genericSerializer = genericSerializer;
            return this;
        }

        /**
         * Sets the {@link QueryPriorityCalculator} used to deduce the priority of an incoming query among other
         * queries, to give precedence over high(er) valued queries for example. Defaults to a {@link
         * QueryPriorityCalculator#defaultQueryPriorityCalculator()}.
         *
         * @param priorityCalculator a {@link QueryPriorityCalculator} used to deduce the priority of an incoming query
         *                           among other queries
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder priorityCalculator(QueryPriorityCalculator priorityCalculator) {
            assertNonNull(targetContextResolver, "QueryPriorityCalculator may not be null");
            this.priorityCalculator = priorityCalculator;
            return this;
        }

        /**
         * Sets the {@link TargetContextResolver} used to resolve the target (bounded) context of an ingested {@link
         * QueryMessage}. Defaults to returning the {@link AxonServerConfiguration#getContext()} on any type of query
         * message being ingested.
         *
         * @param targetContextResolver a {@link TargetContextResolver} used to resolve the target (bounded) context of
         *                              an ingested {@link QueryMessage}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder targetContextResolver(TargetContextResolver<? super QueryMessage<?, ?>> targetContextResolver) {
            assertNonNull(targetContextResolver, "TargetContextResolver may not be null");
            this.targetContextResolver = targetContextResolver;
            return this;
        }

        /**
         * Sets the {@link ExecutorServiceBuilder} which builds an {@link ExecutorService} based on a given {@link
         * AxonServerConfiguration} and {@link BlockingQueue} of {@link Runnable}. This ExecutorService is used to
         * process incoming queries with. Defaults to a {@link ThreadPoolExecutor}, using the {@link
         * AxonServerConfiguration#getQueryThreads()} for the pool size, a keep-alive-time of {@code 100ms}, the given
         * BlockingQueue as the work queue and an {@link AxonThreadFactory}.
         * <p/>
         * Note that it is highly recommended to use the given BlockingQueue if you are to provide you own {@code
         * executorServiceBuilder}, as it ensure the query's priority is taken into consideration. Defaults to {@link
         * ExecutorServiceBuilder#defaultQueryExecutorServiceBuilder()}.
         *
         * @param executorServiceBuilder an {@link ExecutorServiceBuilder} used to build an {@link ExecutorService}
         *                               based on the {@link AxonServerConfiguration} and a {@link BlockingQueue}
         * @return the current Builder instance, for fluent interfacing
         */
        @SuppressWarnings("unused")
        public Builder executorServiceBuilder(ExecutorServiceBuilder executorServiceBuilder) {
            assertNonNull(executorServiceBuilder, "ExecutorServiceBuilder may not be null");
            this.executorServiceBuilder = executorServiceBuilder;
            return this;
        }

        /**
         * Sets the request stream factory that creates a request stream based on upstream. Defaults to {@link
         * UpstreamAwareStreamObserver#getRequestStream()}.
         *
         * @param requestStreamFactory factory that creates a request stream based on upstream
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder requestStreamFactory(
                Function<UpstreamAwareStreamObserver<QueryProviderInbound>, StreamObserver<QueryProviderOutbound>> requestStreamFactory) {
            assertNonNull(requestStreamFactory, "RequestStreamFactory may not be null");
            this.requestStreamFactory = requestStreamFactory;
            return this;
        }

        /**
         * Sets the instruction ack source used to send instruction acknowledgements. Defaults to {@link
         * DefaultInstructionAckSource}.
         *
         * @param instructionAckSource used to send instruction acknowledgements
         * @return the current Builder instance, for fluent interfacing
         */
        @SuppressWarnings("unused")
        public Builder instructionAckSource(InstructionAckSource<QueryProviderOutbound> instructionAckSource) {
            assertNonNull(instructionAckSource, "InstructionAckSource may not be null");
            this.instructionAckSource = instructionAckSource;
            return this;
        }

        /**
         * Initializes a {@link AxonServerQueryBus} as specified through this Builder.
         *
         * @return a {@link AxonServerQueryBus} as specified through this Builder
         */
        public AxonServerQueryBus build() {
            return new AxonServerQueryBus(this);
        }

        /**
         * Build a {@link QuerySerializer} using the configured {@code messageSerializer}, {@code genericSerializer} and
         * {@code configuration}.
         *
         * @return a {@link QuerySerializer} based on the configured {@code messageSerializer}, {@code
         * genericSerializer} and {@code configuration}
         */
        protected QuerySerializer buildQuerySerializer() {
            return new QuerySerializer(messageSerializer, genericSerializer, configuration);
        }

        /**
         * Build a {@link SubscriptionMessageSerializer} using the configured {@code messageSerializer}, {@code
         * genericSerializer} and {@code configuration}.
         *
         * @return a {@link SubscriptionMessageSerializer} based on the configured {@code messageSerializer}, {@code
         * genericSerializer} and {@code configuration}
         */
        protected SubscriptionMessageSerializer buildSubscriptionMessageSerializer() {
            return new SubscriptionMessageSerializer(messageSerializer, genericSerializer, configuration);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(axonServerConnectionManager,
                          "The AxonServerConnectionManager is a hard requirement and should be provided");
            assertNonNull(configuration, "The AxonServerConfiguration is a hard requirement and should be provided");
            assertNonNull(localSegment, "The Local QueryBus is a hard requirement and should be provided");
            assertNonNull(updateEmitter, "The QueryUpdateEmitter is a hard requirement and should be provided");
            assertNonNull(messageSerializer, "The Message Serializer is a hard requirement and should be provided");
            assertNonNull(genericSerializer, "The Generic Serializer is a hard requirement and should be provided");
        }
    }
}
