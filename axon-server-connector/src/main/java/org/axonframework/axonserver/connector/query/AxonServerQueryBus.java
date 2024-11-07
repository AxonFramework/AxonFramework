/*
 * Copyright (c) 2010-2024. Axon Framework
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

import io.axoniq.axonserver.connector.FlowControl;
import io.axoniq.axonserver.connector.ReplyChannel;
import io.axoniq.axonserver.connector.ResultStream;
import io.axoniq.axonserver.connector.ResultStreamPublisher;
import io.axoniq.axonserver.connector.impl.CloseAwareReplyChannel;
import io.axoniq.axonserver.connector.query.QueryDefinition;
import io.axoniq.axonserver.connector.query.QueryHandler;
import io.axoniq.axonserver.grpc.ErrorMessage;
import io.axoniq.axonserver.grpc.query.QueryProviderInbound;
import io.axoniq.axonserver.grpc.query.QueryProviderOutbound;
import io.axoniq.axonserver.grpc.query.QueryRequest;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import io.axoniq.axonserver.grpc.query.QueryUpdate;
import io.axoniq.axonserver.grpc.query.SubscriptionQuery;
import io.grpc.stub.StreamObserver;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.DefaultInstructionAckSource;
import org.axonframework.axonserver.connector.DispatchInterceptors;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.InstructionAckSource;
import org.axonframework.axonserver.connector.PriorityRunnable;
import org.axonframework.axonserver.connector.TargetContextResolver;
import org.axonframework.axonserver.connector.command.AxonServerRegistration;
import org.axonframework.axonserver.connector.query.subscription.AxonServerSubscriptionQueryResult;
import org.axonframework.axonserver.connector.query.subscription.SubscriptionMessageSerializer;
import org.axonframework.axonserver.connector.util.ExceptionSerializer;
import org.axonframework.axonserver.connector.util.ExecutorServiceBuilder;
import org.axonframework.axonserver.connector.util.PriorityTaskSchedulers;
import org.axonframework.axonserver.connector.util.ProcessingInstructionHelper;
import org.axonframework.axonserver.connector.util.UpstreamAwareStreamObserver;
import org.axonframework.common.Assert;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.AxonException;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.common.Registration;
import org.axonframework.common.StringUtils;
import org.axonframework.lifecycle.Lifecycle;
import org.axonframework.lifecycle.Phase;
import org.axonframework.lifecycle.ShutdownLatch;
import org.axonframework.messaging.Distributed;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.responsetypes.ConvertingResponseMessage;
import org.axonframework.messaging.responsetypes.InstanceResponseType;
import org.axonframework.messaging.responsetypes.MultipleInstancesResponseType;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.DefaultQueryBusSpanFactory;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryBusSpanFactory;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.StreamingQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryBackpressure;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.UpdateHandlerRegistration;
import org.axonframework.serialization.Serializer;
import org.axonframework.tracing.NoOpSpanFactory;
import org.axonframework.tracing.Span;
import org.axonframework.tracing.SpanFactory;
import org.axonframework.tracing.SpanScope;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Scheduler;

import javax.annotation.Nonnull;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.String.format;
import static org.axonframework.common.BuilderUtils.assertNonEmpty;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Axon {@link QueryBus} implementation that connects to Axon Server to submit and receive queries and query responses.
 * Delegates incoming queries to the provided {@code localSegment}.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public class AxonServerQueryBus implements QueryBus, Distributed<QueryBus>, Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final AtomicLong TASK_SEQUENCE = new AtomicLong(Long.MIN_VALUE);

    private static final int DIRECT_QUERY_NUMBER_OF_RESULTS = 1;
    private static final long DIRECT_QUERY_TIMEOUT_MS = TimeUnit.HOURS.toMillis(1);
    private static final int SCATTER_GATHER_NUMBER_OF_RESULTS = -1;

    private static final int QUERY_QUEUE_CAPACITY = 1000;

    private final AxonServerConnectionManager axonServerConnectionManager;
    private final AxonServerConfiguration configuration;
    private final QueryUpdateEmitter updateEmitter;
    private final QueryBus localSegment;
    private final QuerySerializer serializer;
    private final SubscriptionMessageSerializer subscriptionSerializer;
    private final QueryPriorityCalculator priorityCalculator;

    private final DispatchInterceptors<QueryMessage<?, ?>> dispatchInterceptors;
    private final TargetContextResolver<? super QueryMessage<?, ?>> targetContextResolver;
    private final ShutdownLatch shutdownLatch = new ShutdownLatch();
    private final ExecutorService queryExecutor;
    private final LocalSegmentAdapter localSegmentAdapter;
    private final String context;
    private final QueryBusSpanFactory spanFactory;
    private final boolean localSegmentShortCut;
    private final Duration queryInProgressAwait;

    private final Set<String> queryHandlerNames = new CopyOnWriteArraySet<>();

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
        this.context = StringUtils.nonEmptyOrNull(builder.defaultContext) ? builder.defaultContext : configuration.getContext();
        this.targetContextResolver = builder.targetContextResolver.orElse(m -> context);
        this.spanFactory = builder.spanFactory;
        this.queryInProgressAwait = builder.queryInProgressAwait;

        dispatchInterceptors = new DispatchInterceptors<>();

        PriorityBlockingQueue<Runnable> queryProcessQueue = new PriorityBlockingQueue<>(QUERY_QUEUE_CAPACITY);
        queryExecutor = builder.executorServiceBuilder.apply(configuration, queryProcessQueue);
        localSegmentAdapter = new LocalSegmentAdapter();
        this.localSegmentShortCut = builder.localSegmentShortCut;
    }

    @Override
    public <Q, R> Publisher<QueryResponseMessage<R>> streamingQuery(StreamingQueryMessage<Q, R> query) {
        Span span = spanFactory.createStreamingQuerySpan(query, true).start();
        try (SpanScope unused = span.makeCurrent()) {
            StreamingQueryMessage<Q, R> queryWithContext = spanFactory.propagateContext(query);
            int priority = priorityCalculator.determinePriority(queryWithContext);
            AtomicReference<Scheduler> scheduler = new AtomicReference<>(PriorityTaskSchedulers.forPriority(
                    queryExecutor,
                    priority,
                    TASK_SEQUENCE));
            return Mono.fromSupplier(this::registerStreamingQueryActivity).flatMapMany(
                    activity -> Mono.just(dispatchInterceptors.intercept(queryWithContext))
                                    .flatMapMany(intercepted -> {
                                                     if (shouldRunQueryLocally(intercepted.getQueryName())) {
                                                         return localSegment.streamingQuery(intercepted);
                                                     }
                                                     return Mono.just(serializeStreaming(intercepted, priority))
                                                                .flatMapMany(queryRequest -> new ResultStreamPublisher<>(
                                                                        () -> sendRequest(intercepted, queryRequest)))
                                                                .concatMap(queryResponse -> deserialize(intercepted,
                                                                                                        queryResponse));
                                                 }
                                    )
                                    .publishOn(scheduler.get())
                                    .doOnError(span::recordException)
                                    .doFinally(new ActivityFinisher(activity, span))
                                    .subscribeOn(scheduler.get()));
        }
    }


    /**
     * Instantiate a Builder to be able to create an {@link AxonServerQueryBus}.
     * <p>
     * The {@link QueryPriorityCalculator} is defaulted to
     * {@link QueryPriorityCalculator#defaultQueryPriorityCalculator()}, the {@link TargetContextResolver} defaults to a
     * lambda returning the {@link AxonServerConfiguration#getContext()} as the context, the
     * {@link ExecutorServiceBuilder} defaults to {@link ExecutorServiceBuilder#defaultQueryExecutorServiceBuilder()}.
     * The {@link AxonServerConnectionManager} and the {@link QueryBusSpanFactory} defaults to a
     * {@link DefaultQueryBusSpanFactory} backed by a {@link NoOpSpanFactory}. The {@link AxonServerConfiguration}, the
     * local {@link QueryBus}, the {@link QueryUpdateEmitter}, and the message and generic {@link Serializer}s are
     * <b>hard requirements</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link AxonServerQueryBus}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void registerLifecycleHandlers(@Nonnull LifecycleRegistry lifecycle) {
        lifecycle.onStart(Phase.INBOUND_QUERY_CONNECTOR, this::start);
        lifecycle.onShutdown(Phase.INBOUND_QUERY_CONNECTOR, this::disconnect);
        lifecycle.onShutdown(Phase.OUTBOUND_QUERY_CONNECTORS, this::shutdownDispatching);
    }

    /**
     * Start the Axon Server {@link QueryBus} implementation.
     */
    public void start() {
        shutdownLatch.initialize();
    }

    @Override
    public <R> Registration subscribe(@Nonnull String queryName,
                                      @Nonnull Type responseType,
                                      @Nonnull MessageHandler<? super QueryMessage<?, R>> handler) {
        Registration localRegistration = localSegment.subscribe(queryName, responseType, handler);
        QueryDefinition queryDefinition = new QueryDefinition(queryName, responseType);
        io.axoniq.axonserver.connector.Registration serverRegistration =
                axonServerConnectionManager.getConnection(context)
                                           .queryChannel()
                                           .registerQueryHandler(localSegmentAdapter, queryDefinition);

        queryHandlerNames.add(queryName);
        return new AxonServerRegistration(() -> unsubscribe(queryName, localRegistration), serverRegistration::cancel);
    }

    private boolean unsubscribe(String queryName, Registration localSegmentRegistration) {
        boolean result = localSegmentRegistration.cancel();
        if (result) {
            queryHandlerNames.remove(queryName);
        }
        return result;
    }

    @Override
    public <Q, R> CompletableFuture<QueryResponseMessage<R>> query(@Nonnull QueryMessage<Q, R> queryMessage) {

        Span span = spanFactory.createQuerySpan(queryMessage, true).start();
        try (SpanScope unused = span.makeCurrent()) {
            QueryMessage<Q, R> queryWithContext = spanFactory.propagateContext(queryMessage);
            Assert.isFalse(Publisher.class.isAssignableFrom(queryMessage.getResponseType().getExpectedResponseType()),
                           () -> "The direct query does not support Flux as a return type.");
            shutdownLatch.ifShuttingDown("Cannot dispatch new queries as this bus is being shut down");

            QueryMessage<Q, R> interceptedQuery = dispatchInterceptors.intercept(queryWithContext);
            //noinspection resource
            ShutdownLatch.ActivityHandle queryInTransit = shutdownLatch.registerActivity();
            CompletableFuture<QueryResponseMessage<R>> queryTransaction = new CompletableFuture<>();
            try {
                if (shouldRunQueryLocally(interceptedQuery.getQueryName())) {
                    queryTransaction = localSegment.query(interceptedQuery);
                } else {
                    int priority = priorityCalculator.determinePriority(interceptedQuery);
                    QueryRequest queryRequest = serialize(interceptedQuery, false, priority);
                    ResultStream<QueryResponse> result = sendRequest(interceptedQuery, queryRequest);
                    queryTransaction.whenComplete((r, e) -> result.close());
                    Span responseTaskSpan = spanFactory.createResponseProcessingSpan(interceptedQuery);
                    Runnable responseProcessingTask = new ResponseProcessingTask<>(result,
                                                                                   serializer,
                                                                                   queryTransaction,
                                                                                   queryMessage.getResponseType(),
                                                                                   responseTaskSpan);

                    result.onAvailable(() -> queryExecutor.execute(new PriorityRunnable(
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
            return queryTransaction;
        }
    }

    private boolean shouldRunQueryLocally(String queryName) {
        return localSegmentShortCut && queryHandlerNames.contains(queryName);
    }

    private QueryRequest serializeStreaming(QueryMessage<?, ?> query, int priority) {
        return serialize(query, true, priority);
    }

    /**
     * Ends a streaming query activity.
     * <p>
     * The reason for this static class to exist at all is the ability of instantiating {@link AxonServerQueryBus} even
     * without Project Reactor on the classpath.
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

    private ShutdownLatch.ActivityHandle registerStreamingQueryActivity() {
        shutdownLatch.ifShuttingDown("Cannot dispatch new queries as this bus is being shut down");
        return shutdownLatch.registerActivity();
    }

    private QueryRequest serialize(QueryMessage<?, ?> query, boolean stream, int priority) {
        return serializer.serializeRequest(query,
                                           DIRECT_QUERY_NUMBER_OF_RESULTS,
                                           DIRECT_QUERY_TIMEOUT_MS,
                                           priority,
                                           stream);
    }

    private ResultStream<QueryResponse> sendRequest(QueryMessage<?, ?> queryMessage, QueryRequest queryRequest) {
        return axonServerConnectionManager.getConnection(targetContextResolver.resolveContext(queryMessage))
                                          .queryChannel()
                                          .query(queryRequest);
    }

    private <R> Publisher<QueryResponseMessage<R>> deserialize(StreamingQueryMessage<?, R> queryMessage,
                                                               QueryResponse queryResponse) {
        //noinspection unchecked
        Class<R> expectedResponseType = (Class<R>) queryMessage.getResponseType().getExpectedResponseType();
        QueryResponseMessage<?> responseMessage = serializer.deserializeResponse(queryResponse);
        if (responseMessage.isExceptional()) {
            return Flux.error(responseMessage.exceptionResult());
        }
        if (expectedResponseType.isAssignableFrom(responseMessage.getPayloadType())) {
            InstanceResponseType<R> instanceResponseType = new InstanceResponseType<>(expectedResponseType);
            return Flux.just(new ConvertingResponseMessage<>(instanceResponseType, responseMessage));
        } else {
            MultipleInstancesResponseType<R> multiResponseType =
                    new MultipleInstancesResponseType<>(expectedResponseType);
            ConvertingResponseMessage<List<R>> convertingMessage =
                    new ConvertingResponseMessage<>(multiResponseType, responseMessage);
            return Flux.fromStream(convertingMessage.getPayload()
                                                    .stream()
                                                    .map(payload -> singleMessage(responseMessage,
                                                                                  payload,
                                                                                  expectedResponseType)));
        }
    }

    private <R> QueryResponseMessage<R> singleMessage(QueryResponseMessage<?> original,
                                                      R newPayload,
                                                      Class<R> expectedPayloadType) {
        GenericMessage<R> delegate = new GenericMessage<>(original.getIdentifier(),
                                                          expectedPayloadType,
                                                          newPayload,
                                                          original.getMetaData());
        return new GenericQueryResponseMessage<>(delegate);
    }

    @Override
    public <Q, R> Stream<QueryResponseMessage<R>> scatterGather(@Nonnull QueryMessage<Q, R> queryMessage,
                                                                long timeout,
                                                                @Nonnull TimeUnit timeUnit) {
        Assert.isFalse(Publisher.class.isAssignableFrom(queryMessage.getResponseType().getExpectedResponseType()),
                       () -> "The scatter-Gather query does not support Flux as a return type.");
        shutdownLatch.ifShuttingDown(format(
                "Cannot dispatch new %s as this bus is being shut down", "scatter-gather queries"
        ));
        ShutdownLatch.ActivityHandle queryInTransit = shutdownLatch.registerActivity();

        Span span = spanFactory.createScatterGatherSpan(queryMessage, true).start();
        try(SpanScope unused = span.makeCurrent()) {
            QueryMessage<Q, R> interceptedQuery = dispatchInterceptors.intercept(spanFactory.propagateContext(queryMessage));
            long deadline = System.currentTimeMillis() + timeUnit.toMillis(timeout);
            String targetContext = targetContextResolver.resolveContext(interceptedQuery);
            QueryRequest queryRequest =
                    serializer.serializeRequest(interceptedQuery,
                                                SCATTER_GATHER_NUMBER_OF_RESULTS,
                                                timeUnit.toMillis(timeout),
                                                priorityCalculator.determinePriority(interceptedQuery));

            ResultStream<QueryResponse> queryResult = axonServerConnectionManager.getConnection(targetContext)
                                                                                 .queryChannel()
                                                                                 .query(queryRequest);

            AtomicBoolean closed = new AtomicBoolean(false);
            Runnable closeHandler = () -> {
                if (closed.compareAndSet(false, true)) {
                    queryInTransit.end();
                    span.end();
                }
            };
            return StreamSupport.stream(
                    new QueryResponseSpliterator<>(queryMessage,
                                                   queryResult,
                                                   deadline,
                                                   serializer,
                                                   closeHandler),
                    false
            ).onClose(closeHandler);
        } catch (Exception e) {
            logger.debug("There was a problem issuing a scatter-gather query {}.", queryMessage, e);
            queryInTransit.end();
            span.recordException(e).end();
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated in favor of using the {{@link #subscriptionQuery(SubscriptionQueryMessage, int)}}
     */
    @Deprecated
    @Override
    public <Q, I, U> SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> subscriptionQuery(
            @Nonnull SubscriptionQueryMessage<Q, I, U> query,
            SubscriptionQueryBackpressure backPressure,
            int updateBufferSize
    ) {
        return subscriptionQuery(query, updateBufferSize);
    }

    @Override
    public <Q, I, U> SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> subscriptionQuery(
            @Nonnull SubscriptionQueryMessage<Q, I, U> query,
            int updateBufferSize
    ) {
        Assert.isFalse(Publisher.class.isAssignableFrom(query.getResponseType().getExpectedResponseType()),
                       () -> "The subscription Query query does not support Flux as a return type.");
        Assert.isFalse(Publisher.class.isAssignableFrom(query.getUpdateResponseType().getExpectedResponseType()),
                       () -> "The subscription Query query does not support Flux as an update type.");
        shutdownLatch.ifShuttingDown(format(
                "Cannot dispatch new %s as this bus is being shut down", "subscription queries"
        ));

        Span span = spanFactory.createSubscriptionQuerySpan(query, true).start();
        try (SpanScope unused = span.makeCurrent()) {
            SubscriptionQueryMessage<Q, I, U> interceptedQuery = dispatchInterceptors.intercept(
                    spanFactory.propagateContext(query)
            );
            String subscriptionId = interceptedQuery.getIdentifier();
            String targetContext = targetContextResolver.resolveContext(interceptedQuery);

            logger.debug("Subscription Query requested with subscription Id [{}]", subscriptionId);

            io.axoniq.axonserver.connector.query.SubscriptionQueryResult result =
                    axonServerConnectionManager.getConnection(targetContext)
                                               .queryChannel()
                                               .subscriptionQuery(
                                                       subscriptionSerializer.serializeQuery(interceptedQuery),
                                                       subscriptionSerializer.serializeUpdateType(interceptedQuery),
                                                       configuration.getQueryFlowControl().getPermits(),
                                                       configuration.getQueryFlowControl().getNrOfNewPermits()
                                               );
            return new AxonServerSubscriptionQueryResult<>(
                    interceptedQuery,
                    result,
                    subscriptionSerializer,
                    spanFactory,
                    span);
        }
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
    public Registration registerHandlerInterceptor(
            @Nonnull MessageHandlerInterceptor<? super QueryMessage<?, ?>> interceptor) {
        return localSegment.registerHandlerInterceptor(interceptor);
    }

    @Override
    public @Nonnull
    Registration registerDispatchInterceptor(
            @Nonnull MessageDispatchInterceptor<? super QueryMessage<?, ?>> dispatchInterceptor) {
        return dispatchInterceptors.registerDispatchInterceptor(dispatchInterceptor);
    }

    /**
     * Disconnect the query bus from Axon Server, by unsubscribing all known query handlers and aborting all queries in progress.
     */
    public void disconnect() {
        if (axonServerConnectionManager.isConnected(context)) {
            axonServerConnectionManager.getConnection(context)
                                       .queryChannel()
                                       .prepareDisconnect();
        }
        if (!localSegmentAdapter.awaitTermination(queryInProgressAwait)) {
            logger.info("Awaited termination of queries in progress without success. Going to cancel remaining queries in progress.");
            localSegmentAdapter.cancel();
        }
    }

    /**
     * Shutdown the query bus asynchronously for dispatching queries to Axon Server. This process will wait for
     * dispatched queries which have not received a response yet and will close off running subscription queries. This
     * shutdown operation is performed in the {@link Phase#OUTBOUND_QUERY_CONNECTORS} phase.
     *
     * @return a completable future which is resolved once all query dispatching activities are completed
     */
    public CompletableFuture<Void> shutdownDispatching() {
        return shutdownLatch.initiateShutdown();
    }

    /**
     * Builder class to instantiate an {@link AxonServerQueryBus}.
     * <p>
     * The {@link QueryPriorityCalculator} is defaulted to
     * {@link QueryPriorityCalculator#defaultQueryPriorityCalculator()} and the {@link TargetContextResolver} defaults
     * to a lambda returning the {@link AxonServerConfiguration#getContext()} as the context. The
     * {@link ExecutorServiceBuilder} defaults to {@link ExecutorServiceBuilder#defaultQueryExecutorServiceBuilder()}.
     * The {@link QueryBusSpanFactory} defaults to a {@link DefaultQueryBusSpanFactory} backed by a
     * {@link NoOpSpanFactory}. The {@link AxonServerConnectionManager}, the {@link AxonServerConfiguration}, the local
     * {@link QueryBus}, the {@link QueryUpdateEmitter}, and the message and generic {@link Serializer}s are <b>hard
     * requirements</b> and as such should be provided.
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
        private String defaultContext;
        private QueryBusSpanFactory spanFactory = DefaultQueryBusSpanFactory.builder()
                                                                            .spanFactory(NoOpSpanFactory.INSTANCE)
                                                                            .build();
        private boolean localSegmentShortCut;
        private Duration queryInProgressAwait = Duration.ofSeconds(5);

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
         * Sets the generic {@link Serializer} used to de-/serialize incoming and outgoing query
         * {@link org.axonframework.messaging.responsetypes.ResponseType} implementations.
         *
         * @param genericSerializer a {@link Serializer} used to de-/serialize incoming and outgoing query
         *                          {@link org.axonframework.messaging.responsetypes.ResponseType} implementations.
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder genericSerializer(Serializer genericSerializer) {
            assertNonNull(genericSerializer, "Generic Serializer may not be null");
            this.genericSerializer = genericSerializer;
            return this;
        }

        /**
         * Sets the {@link QueryPriorityCalculator} used to deduce the priority of an incoming query among other
         * queries, to give precedence over high(er) valued queries for example. Defaults to a
         * {@link QueryPriorityCalculator#defaultQueryPriorityCalculator()}.
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
         * Sets the {@link TargetContextResolver} used to resolve the target (bounded) context of an ingested
         * {@link QueryMessage}. Defaults to returning the {@link AxonServerConfiguration#getContext()} on any type of
         * query message being ingested.
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
         * Sets the {@link ExecutorServiceBuilder} which builds an {@link ExecutorService} based on a given
         * {@link AxonServerConfiguration} and {@link BlockingQueue} of {@link Runnable}. This ExecutorService is used
         * to process incoming queries with. Defaults to a {@link ThreadPoolExecutor}, using the
         * {@link AxonServerConfiguration#getQueryThreads()} for the pool size, a keep-alive-time of {@code 100ms}, the
         * given BlockingQueue as the work queue and an {@link AxonThreadFactory}.
         * <p/>
         * Note that it is highly recommended to use the given BlockingQueue if you are to provide you own
         * {@code executorServiceBuilder}, as it ensure the query's priority is taken into consideration. Defaults to
         * {@link ExecutorServiceBuilder#defaultQueryExecutorServiceBuilder()}.
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
         * Sets the request stream factory that creates a request stream based on upstream. Defaults to
         * {@link UpstreamAwareStreamObserver#getRequestStream()}.
         *
         * @param requestStreamFactory factory that creates a request stream based on upstream
         * @return the current Builder instance, for fluent interfacing
         * @deprecated in through use of the <a href="https://github.com/AxonIQ/axonserver-connector-java">AxonServer
         * java connector</a>
         */
        @Deprecated
        public Builder requestStreamFactory(
                Function<UpstreamAwareStreamObserver<QueryProviderInbound>, StreamObserver<QueryProviderOutbound>> requestStreamFactory
        ) {
            return this;
        }

        /**
         * Sets the instruction ack source used to send instruction acknowledgements. Defaults to
         * {@link DefaultInstructionAckSource}.
         *
         * @param instructionAckSource used to send instruction acknowledgements
         * @return the current Builder instance, for fluent interfacing
         * @deprecated in through use of the <a href="https://github.com/AxonIQ/axonserver-connector-java">AxonServer
         * java connector</a>
         */
        @Deprecated
        public Builder instructionAckSource(InstructionAckSource<QueryProviderOutbound> instructionAckSource) {
            return this;
        }

        /**
         * Sets the default context for this event store to connect to.
         *
         * @param defaultContext for this bus to connect to.
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder defaultContext(String defaultContext) {
            assertNonEmpty(defaultContext, "The context may not be null or empty");
            this.defaultContext = defaultContext;
            return this;
        }

        /**
         * Sets the {@link SpanFactory} implementation to use for providing tracing capabilities. Defaults to a
         * {@link NoOpSpanFactory} by default, which provides no tracing capabilities.
         *
         * @param spanFactory The {@link SpanFactory} implementation
         * @return The current Builder instance, for fluent interfacing.
         * @deprecated Use {@link #spanFactory(QueryBusSpanFactory)} instead as it provides more configurability.
         */
        @Deprecated
        public Builder spanFactory(@Nonnull SpanFactory spanFactory) {
            assertNonNull(spanFactory, "The SpanFactory may not be null or empty");
            this.spanFactory = DefaultQueryBusSpanFactory.builder().spanFactory(spanFactory).build();
            return this;
        }

        /**
         * Sets the {@link QueryBusSpanFactory} implementation to use for providing tracing capabilities. Defaults to a
         * {@link DefaultQueryBusSpanFactory} backed by a {@link NoOpSpanFactory} by default, which provides no tracing
         * capabilities.
         *
         * @param spanFactory The {@link QueryBusSpanFactory} implementation.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder spanFactory(@Nonnull QueryBusSpanFactory spanFactory) {
            assertNonNull(spanFactory, "SpanFactory may not be null");
            this.spanFactory = spanFactory;
            return this;
        }

        /**
         * Enables shortcut to local {@link QueryBus}. If query handlers are registered in the local environment they
         * will be invoked directly instead of sending request to axon server.
         *
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder enabledLocalSegmentShortCut() {
            this.localSegmentShortCut = true;
            return this;
        }

        /**
         * Sets the {@link Duration query in progress await timeout} used to await the successful termination of queries
         * in progress. When this timeout is exceeded, the query in progress will be canceled.
         * <p>
         * Defaults to a {@code Duration} of 5 seconds.
         *
         * @param queryInProgressAwait The {@link Duration query in progress await timeout} used to await the successful
         *                             termination of queries in progress
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder queryInProgressAwait(@Nonnull Duration queryInProgressAwait) {
            assertNonNull(queryInProgressAwait, "Query in progress await timeout may not be null");
            this.queryInProgressAwait = queryInProgressAwait;
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
         * @return a {@link QuerySerializer} based on the configured {@code messageSerializer},
         * {@code genericSerializer} and {@code configuration}
         */
        protected QuerySerializer buildQuerySerializer() {
            return new QuerySerializer(messageSerializer, genericSerializer, configuration);
        }

        /**
         * Build a {@link SubscriptionMessageSerializer} using the configured {@code messageSerializer},
         * {@code genericSerializer} and {@code configuration}.
         *
         * @return a {@link SubscriptionMessageSerializer} based on the configured {@code messageSerializer},
         * {@code genericSerializer} and {@code configuration}
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

    private static class QueryResponseSpliterator<Q, R> implements Spliterator<QueryResponseMessage<R>> {

        private final QueryMessage<Q, R> queryMessage;
        private final ResultStream<QueryResponse> queryResult;
        private final long deadline;
        private final QuerySerializer serializer;
        private final Runnable closeHandler;

        public QueryResponseSpliterator(QueryMessage<Q, R> queryMessage,
                                        ResultStream<QueryResponse> queryResult,
                                        long deadline,
                                        QuerySerializer serializer,
                                        Runnable closeHandler) {
            this.queryMessage = queryMessage;
            this.queryResult = queryResult;
            this.deadline = deadline;
            this.serializer = serializer;
            this.closeHandler = closeHandler;
        }

        @Override
        public boolean tryAdvance(Consumer<? super QueryResponseMessage<R>> action) {
            long remaining = deadline - System.currentTimeMillis();
            QueryResponse next;
            if (remaining > 0) {
                try {
                    next = queryResult.nextIfAvailable(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    closeHandler.run();
                    return false;
                }
            } else {
                next = queryResult.nextIfAvailable();
            }
            if (next != null) {
                action.accept(serializer.deserializeResponse(next, queryMessage.getResponseType()));
                return true;
            }
            queryResult.close();
            closeHandler.run();
            return false;
        }

        @Override
        public Spliterator<QueryResponseMessage<R>> trySplit() {
            return null;
        }

        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        @Override
        public int characteristics() {
            return Spliterator.DISTINCT & Spliterator.IMMUTABLE & Spliterator.NONNULL;
        }
    }

    private static class ResponseProcessingTask<R> implements Runnable {

        private final AtomicBoolean singleExecutionCheck = new AtomicBoolean();
        private final ResultStream<QueryResponse> result;
        private final QuerySerializer serializer;
        private final CompletableFuture<QueryResponseMessage<R>> queryTransaction;
        private final ResponseType<R> expectedResponseType;
        private final Span span;

        public ResponseProcessingTask(ResultStream<QueryResponse> result,
                                      QuerySerializer serializer,
                                      CompletableFuture<QueryResponseMessage<R>> queryTransaction,
                                      ResponseType<R> expectedResponseType, Span responseTaskSpan) {
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
                        queryTransaction.complete(serializer.deserializeResponse(nextAvailable, expectedResponseType));
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

    /**
     * A {@link QueryHandler} implementation serving as a wrapper around the local {@link QueryBus} to push through the
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
            Runnable onClose = () -> queriesInProgress.remove(query.getMessageIdentifier());
            CloseAwareReplyChannel<QueryResponse> closeAwareReplyChannel =
                    new CloseAwareReplyChannel<>(responseHandler, onClose);

            long priority = ProcessingInstructionHelper.priority(query.getProcessingInstructionsList());
            QueryProcessingTask processingTask = new QueryProcessingTask(
                    localSegment, query, closeAwareReplyChannel, serializer, configuration.getClientId(), spanFactory
            );
            PriorityRunnable priorityTask = new PriorityRunnable(processingTask,
                                                                 priority,
                                                                 TASK_SEQUENCE.incrementAndGet());

            queriesInProgress.put(query.getMessageIdentifier(), processingTask);
            queryExecutor.execute(priorityTask);

            return new FlowControl() {
                @Override
                public void request(long requested) {
                    queryExecutor.execute(new PriorityRunnable(() -> processingTask.request(requested),
                                                               priorityTask.priority(),
                                                               TASK_SEQUENCE.incrementAndGet())
                    );
                }

                @Override
                public void cancel() {
                    queryExecutor.execute(new PriorityRunnable(processingTask::cancel,
                                                               priorityTask.priority(),
                                                               TASK_SEQUENCE.incrementAndGet()));
                }
            };
        }

        @Override
        public io.axoniq.axonserver.connector.Registration registerSubscriptionQuery(SubscriptionQuery query,
                                                                                     UpdateHandler sendUpdate) {
            UpdateHandlerRegistration<Object> updateHandler =
                    updateEmitter.registerUpdateHandler(subscriptionSerializer.deserialize(query), 1024);

            updateHandler.getUpdates()
                         .doOnError(e -> {
                             ErrorMessage error = ExceptionSerializer.serialize(configuration.getClientId(), e);
                             String errorCode = ErrorCode.getQueryExecutionErrorCode(e).errorCode();
                             QueryUpdate queryUpdate = QueryUpdate.newBuilder()
                                                                  .setErrorMessage(error)
                                                                  .setErrorCode(errorCode)
                                                                  .build();
                             sendUpdate.sendUpdate(queryUpdate);
                             sendUpdate.complete();
                         })
                         .doOnComplete(sendUpdate::complete)
                         .map(subscriptionSerializer::serialize)
                         .subscribe(sendUpdate::sendUpdate);

            return () -> {
                updateHandler.getRegistration().close();
                return CompletableFuture.completedFuture(null);
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
                                     while (Instant.now().isBefore(endAwait) && queryInProgress.resultPending()) {
                                         LockSupport.parkUntil(10);
                                     }
                                 });
            }
            return Instant.now().isBefore(endAwait);
        }

        private void cancel() {
            queriesInProgress.values()
                             .iterator()
                             .forEachRemaining(QueryProcessingTask::cancel);
        }
    }
}
