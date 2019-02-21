/*
 * Copyright (c) 2010-2019. Axon Framework
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
import io.axoniq.axonserver.grpc.query.QueryComplete;
import io.axoniq.axonserver.grpc.query.QueryProviderInbound;
import io.axoniq.axonserver.grpc.query.QueryProviderInbound.RequestCase;
import io.axoniq.axonserver.grpc.query.QueryProviderOutbound;
import io.axoniq.axonserver.grpc.query.QueryRequest;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import io.axoniq.axonserver.grpc.query.QueryServiceGrpc;
import io.axoniq.axonserver.grpc.query.QuerySubscription;
import io.grpc.ClientInterceptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.netty.util.internal.OutOfDirectMemoryError;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.DispatchInterceptors;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.command.AxonServerRegistration;
import org.axonframework.axonserver.connector.query.subscription.AxonServerSubscriptionQueryResult;
import org.axonframework.axonserver.connector.query.subscription.DeserializedResult;
import org.axonframework.axonserver.connector.query.subscription.SubscriptionMessageSerializer;
import org.axonframework.axonserver.connector.query.subscription.SubscriptionQueryRequestTarget;
import org.axonframework.axonserver.connector.util.ContextAddingInterceptor;
import org.axonframework.axonserver.connector.util.ExceptionSerializer;
import org.axonframework.axonserver.connector.util.FlowControllingStreamObserver;
import org.axonframework.axonserver.connector.util.TokenAddingInterceptor;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.common.Registration;
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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static io.axoniq.axonserver.grpc.query.QueryProviderInbound.RequestCase.SUBSCRIPTION_QUERY_REQUEST;
import static org.axonframework.axonserver.connector.util.ProcessingInstructionHelper.numberOfResults;
import static org.axonframework.axonserver.connector.util.ProcessingInstructionHelper.priority;

/**
 * Axon {@link QueryBus} implementation that connects to Axon Server to submit and receive queries and query responses.
 * Delegates incoming queries to the provided {@code localSegment}.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public class AxonServerQueryBus implements QueryBus {

    private final Logger logger = LoggerFactory.getLogger(AxonServerQueryBus.class);

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

    private final QueryHandlerProvider queryProvider;
    private final ClientInterceptor[] interceptors;
    private final DispatchInterceptors<QueryMessage<?, ?>> dispatchInterceptors;
    private final Collection<String> subscriptions;
    private final Map<RequestCase, Collection<Consumer<QueryProviderInbound>>> queryHandlers;

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
     */
    public AxonServerQueryBus(AxonServerConnectionManager axonServerConnectionManager,
                              AxonServerConfiguration configuration,
                              QueryUpdateEmitter updateEmitter,
                              QueryBus localSegment,
                              Serializer messageSerializer,
                              Serializer genericSerializer,
                              QueryPriorityCalculator priorityCalculator) {
        this.axonServerConnectionManager = axonServerConnectionManager;
        this.configuration = configuration;
        this.updateEmitter = updateEmitter;
        this.localSegment = localSegment;
        this.serializer = new QuerySerializer(messageSerializer, genericSerializer, configuration);
        this.subscriptionSerializer =
                new SubscriptionMessageSerializer(messageSerializer, genericSerializer, configuration);
        this.priorityCalculator = priorityCalculator;

        this.queryProvider = new QueryHandlerProvider();
        interceptors = new ClientInterceptor[]{
                new TokenAddingInterceptor(configuration.getToken()),
                new ContextAddingInterceptor(configuration.getContext())
        };
        dispatchInterceptors = new DispatchInterceptors<>();
        subscriptions = new CopyOnWriteArraySet<>();
        queryHandlers = new EnumMap<>(RequestCase.class);

        this.axonServerConnectionManager.addReconnectListener(queryProvider::resubscribe);
        this.axonServerConnectionManager.addReconnectInterceptor(this::interceptReconnectRequest);

        this.axonServerConnectionManager.addDisconnectListener(queryProvider::unsubscribeAll);
        this.axonServerConnectionManager.addDisconnectListener(this::onApplicationDisconnected);
        SubscriptionQueryRequestTarget target =
                new SubscriptionQueryRequestTarget(localSegment, this::publish, subscriptionSerializer);
        this.on(SUBSCRIPTION_QUERY_REQUEST, target::onSubscriptionQueryRequest);
        this.axonServerConnectionManager.addDisconnectListener(target::onApplicationDisconnected);
    }

    private Runnable interceptReconnectRequest(Runnable reconnect) {
        if (subscriptions.isEmpty()) {
            return reconnect;
        }
        return () -> logger.info("Reconnect refused because there are active subscription queries.");
    }

    private void onApplicationDisconnected() {
        subscriptions.clear();
    }

    @Override
    public <R> Registration subscribe(String queryName,
                                      Type responseType,
                                      MessageHandler<? super QueryMessage<?, R>> handler) {
        return new AxonServerRegistration(
                queryProvider.subscribe(queryName, responseType, configuration.getComponentName(), handler),
                () -> queryProvider.unsubscribe(queryName, responseType, configuration.getComponentName())
        );
    }

    @Override
    public <Q, R> CompletableFuture<QueryResponseMessage<R>> query(QueryMessage<Q, R> queryMessage) {
        QueryMessage<Q, R> interceptedQuery = dispatchInterceptors.intercept(queryMessage);
        CompletableFuture<QueryResponseMessage<R>> completableFuture = new CompletableFuture<>();
        try {
            QueryRequest queryRequest = serializer.serializeRequest(
                    interceptedQuery, DIRECT_QUERY_NUMBER_OF_RESULTS, DIRECT_QUERY_TIMEOUT_MS,
                    priorityCalculator.determinePriority(interceptedQuery)
            );

            queryService()
                    .query(queryRequest,
                           new StreamObserver<QueryResponse>() {
                               @Override
                               public void onNext(QueryResponse queryResponse) {
                                   logger.debug("Received query response [{}]", queryResponse);
                                   completableFuture.complete(serializer.deserializeResponse(queryResponse));
                               }

                               @Override
                               public void onError(Throwable throwable) {
                                   if (logger.isDebugEnabled()) {
                                       logger.warn("Received error while waiting for first response: {}",
                                                   throwable.getMessage(),
                                                   throwable);
                                   } else {
                                       logger.warn("Received error while waiting for first response: {}",
                                                   throwable.getMessage());
                                   }
                                   completableFuture.completeExceptionally(
                                           ErrorCode.QUERY_DISPATCH_ERROR.convert(
                                                   configuration.getClientId(), throwable
                                           )
                                   );
                               }

                               @Override
                               public void onCompleted() {
                                   if (completableFuture.isDone()) {
                                       return;
                                   }

                                   completableFuture.completeExceptionally(
                                           ErrorCode.QUERY_DISPATCH_ERROR.convert(
                                                   ErrorMessage.newBuilder()
                                                               .setMessage("No result from query executor")
                                                               .build()
                                           ));
                               }
                           });
        } catch (Exception e) {
            logger.warn("There was a problem issuing a query {}.", interceptedQuery, e);
            completableFuture.completeExceptionally(
                    ErrorCode.QUERY_DISPATCH_ERROR.convert(configuration.getClientId(), e)
            );
        }
        return completableFuture;
    }

    @Override
    public <Q, R> Stream<QueryResponseMessage<R>> scatterGather(QueryMessage<Q, R> queryMessage,
                                                                long timeout,
                                                                TimeUnit timeUnit) {
        QueryMessage<Q, R> interceptedQuery = dispatchInterceptors.intercept(queryMessage);
        QueueBackedSpliterator<QueryResponseMessage<R>> resultSpliterator =
                new QueueBackedSpliterator<>(timeout, timeUnit);
        QueryRequest queryRequest = serializer.serializeRequest(interceptedQuery,
                                                                SCATTER_GATHER_NUMBER_OF_RESULTS,
                                                                timeUnit.toMillis(timeout),
                                                                priorityCalculator.determinePriority(interceptedQuery));
        queryService()
                .withDeadlineAfter(timeout, timeUnit)
                .query(queryRequest,
                       new StreamObserver<QueryResponse>() {
                           @Override
                           public void onNext(QueryResponse queryResponse) {
                               logger.debug("Received query response [{}]", queryResponse);
                               if (queryResponse.hasErrorMessage()) {
                                   logger.warn("The received query response has error message [{}]",
                                               queryResponse.getErrorMessage());
                               } else {
                                   resultSpliterator.put(serializer.deserializeResponse(queryResponse));
                               }
                           }

                           @Override
                           public void onError(Throwable throwable) {
                               if (!isDeadlineExceeded(throwable)) {
                                   logger.warn("Received error while waiting for responses: {}",
                                               throwable.getMessage(), throwable);
                               }
                               resultSpliterator.cancel(throwable);
                           }

                           @Override
                           public void onCompleted() {
                               resultSpliterator.cancel(null);
                           }
                       });

        return StreamSupport.stream(resultSpliterator, false);
    }

    private boolean isDeadlineExceeded(Throwable throwable) {
        return throwable instanceof StatusRuntimeException
                && ((StatusRuntimeException) throwable).getStatus().getCode().equals(Status.Code.DEADLINE_EXCEEDED);
    }

    public void disconnect() {
        queryProvider.disconnect();
    }

    /**
     * Returns the local segment configured for this instance. The local segment is responsible for publishing queries
     * to handlers in the local JVM.
     *
     * @return the local segment of the Query Bus
     */
    public QueryBus localSegment() {
        return localSegment;
    }

    private void publish(QueryProviderOutbound providerOutbound) {
        this.queryProvider.getSubscriberObserver().onNext(providerOutbound);
    }

    public void on(RequestCase requestCase, Consumer<QueryProviderInbound> consumer) {
        Collection<Consumer<QueryProviderInbound>> consumers =
                queryHandlers.computeIfAbsent(requestCase, rc -> new CopyOnWriteArraySet<>());
        consumers.add(consumer);
    }

    @Override
    public <Q, I, U> SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> subscriptionQuery(
            SubscriptionQueryMessage<Q, I, U> query,
            SubscriptionQueryBackpressure backPressure,
            int updateBufferSize
    ) {
        String subscriptionId = query.getIdentifier();

        if (subscriptions.contains(subscriptionId)) {
            String errorMessage = "There already is a subscription query with subscription Id [" + subscriptionId + "]";
            logger.warn(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }
        logger.debug("Subscription Query requested with subscription Id [{}]", subscriptionId);

        subscriptions.add(subscriptionId);

        AxonServerSubscriptionQueryResult result = new AxonServerSubscriptionQueryResult(
                subscriptionSerializer.serialize(query),
                this.queryService()::subscription,
                configuration,
                backPressure,
                updateBufferSize,
                () -> subscriptions.remove(subscriptionId)
        );
        return new DeserializedResult<>(result.get(), subscriptionSerializer);
    }

    QueryServiceGrpc.QueryServiceStub queryService() {
        return QueryServiceGrpc.newStub(axonServerConnectionManager.getChannel())
                               .withInterceptors(interceptors);
    }

    @Override
    public QueryUpdateEmitter queryUpdateEmitter() {
        return updateEmitter;
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

    private class QueryHandlerProvider {

        private static final int QUERY_QUEUE_CAPACITY = 1000;
        private static final int DEFAULT_PRIORITY = 0;
        private static final long THREAD_KEEP_ALIVE_TIME = 100L;

        private final ConcurrentMap<QueryDefinition, Set<MessageHandler<? super QueryMessage<?, ?>>>> subscribedQueries;
        private final ExecutorService queryExecutor;

        private volatile boolean subscribing;
        private volatile boolean running = true;
        private volatile StreamObserver<QueryProviderOutbound> outboundStreamObserver;

        QueryHandlerProvider() {
            subscribedQueries = new ConcurrentHashMap<>();
            PriorityBlockingQueue<Runnable> queryProcessQueue =
                    new PriorityBlockingQueue<>(QUERY_QUEUE_CAPACITY, Comparator.comparingLong(
                            r -> r instanceof QueryProcessor ? ((QueryProcessor) r).getPriority() : DEFAULT_PRIORITY
                    ));
            int queryThreads = configuration.getQueryThreads();
            queryExecutor = new ThreadPoolExecutor(
                    queryThreads,
                    queryThreads,
                    THREAD_KEEP_ALIVE_TIME,
                    TimeUnit.MILLISECONDS,
                    queryProcessQueue,
                    new AxonThreadFactory("AxonServerQueryProvider")
            );
        }

        private void resubscribe() {
            if (subscribedQueries.isEmpty() || subscribing) {
                return;
            }

            try {
                StreamObserver<QueryProviderOutbound> subscriberStreamObserver = getSubscriberObserver();
                subscribedQueries.forEach((queryDefinition, handlers) -> subscriberStreamObserver.onNext(
                        QueryProviderOutbound.newBuilder()
                                             .setSubscribe(buildQuerySubscription(queryDefinition, handlers.size()))
                                             .build()
                ));
            } catch (Exception ex) {
                logger.warn("Error while resubscribing - {}", ex.getMessage());
            }
        }

        @SuppressWarnings("unchecked")
        public <R> Registration subscribe(String queryName,
                                          Type responseType,
                                          String componentName,
                                          MessageHandler<? super QueryMessage<?, R>> handler) {
            subscribing = true;
            Set registrations = subscribedQueries.computeIfAbsent(
                    new QueryDefinition(queryName, responseType.getTypeName(), componentName),
                    k -> new CopyOnWriteArraySet<>()
            );
            registrations.add(handler);

            try {
                getSubscriberObserver().onNext(QueryProviderOutbound.newBuilder().setSubscribe(
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
                logger.warn("Subscribe failed - {}", ex.getMessage());
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
                    outboundStreamObserver.onNext(
                            QueryProviderOutbound.newBuilder()
                                                 .setQueryResponse(serializer.serializeResponse(response, requestId))
                                                 .build()
                    );
                } else {
                    localSegment.scatterGather(queryMessage, 0, TimeUnit.SECONDS)
                                .forEach(response -> outboundStreamObserver.onNext(
                                        QueryProviderOutbound.newBuilder()
                                                             .setQueryResponse(
                                                                     serializer.serializeResponse(response, requestId)
                                                             )
                                                             .build())
                                );
                }

                outboundStreamObserver.onNext(
                        QueryProviderOutbound.newBuilder()
                                             .setQueryComplete(
                                                     QueryComplete.newBuilder()
                                                                  .setMessageId(UUID.randomUUID().toString())
                                                                  .setRequestId(requestId)
                                             ).build()
                );
            } catch (Exception e) {
                logger.warn("Failed to dispatch query [{}] locally - Cause: {}",
                            queryMessage.getQueryName(), e.getMessage(), e);

                if (outboundStreamObserver == null) {
                    return;
                }

                outboundStreamObserver.onNext(
                        QueryProviderOutbound.newBuilder().setQueryResponse(
                                QueryResponse.newBuilder()
                                             .setMessageIdentifier(UUID.randomUUID().toString())
                                             .setRequestIdentifier(requestId)
                                             .setErrorMessage(
                                                     ExceptionSerializer.serialize(configuration.getClientId(), e)
                                             )
                                             .setErrorCode(ErrorCode.QUERY_EXECUTION_ERROR.errorCode())
                                             .build()).build()
                );
            }
        }

        private synchronized StreamObserver<QueryProviderOutbound> getSubscriberObserver() {
            if (outboundStreamObserver != null) {
                return outboundStreamObserver;
            }

            StreamObserver<QueryProviderInbound> queryProviderInboundStreamObserver = new StreamObserver<QueryProviderInbound>() {
                @Override
                public void onNext(QueryProviderInbound inboundRequest) {
                    RequestCase requestCase = inboundRequest.getRequestCase();
                    queryHandlers.getOrDefault(requestCase, Collections.emptySet())
                                 .forEach(consumer -> consumer.accept(inboundRequest));

                    switch (requestCase) {
                        case CONFIRMATION:
                            break;
                        case QUERY:
                            queryExecutor.execute(new QueryProcessor(inboundRequest.getQuery()));
//                            queryProcessQueue.add(new QueryProcessor(inboundRequest.getQuery()));
                            break;
                    }
                }

                @SuppressWarnings("Duplicates")
                @Override
                public void onError(Throwable ex) {
                    logger.warn("Received error from server: {}", ex.getMessage());
                    outboundStreamObserver = null;
                    if (ex instanceof StatusRuntimeException
                            && ((StatusRuntimeException) ex).getStatus().getCode()
                                                            .equals(Status.UNAVAILABLE.getCode())) {
                        return;
                    }
                    resubscribe();
                }

                @Override
                public void onCompleted() {
                    logger.debug("Received completed from server");
                    outboundStreamObserver = null;
                }
            };

            StreamObserver<QueryProviderOutbound> streamObserver =
                    axonServerConnectionManager.getQueryStream(queryProviderInboundStreamObserver, interceptors);

            logger.info("Creating new query stream subscriber");

            outboundStreamObserver = new FlowControllingStreamObserver<>(
                    streamObserver,
                    configuration,
                    flowControl -> QueryProviderOutbound.newBuilder().setFlowControl(flowControl).build(),
                    t -> t.getRequestCase().equals(QueryProviderOutbound.RequestCase.QUERY_RESPONSE)
            ).sendInitialPermits();
            return outboundStreamObserver;
        }

        public void unsubscribe(String queryName, Type responseType, String componentName) {
            QueryDefinition queryDefinition = new QueryDefinition(queryName, responseType.getTypeName(), componentName);
            subscribedQueries.remove(queryDefinition);
            try {
                getSubscriberObserver().onNext(
                        QueryProviderOutbound.newBuilder()
                                             .setUnsubscribe(buildQuerySubscription(queryDefinition, 1))
                                             .build()
                );
            } catch (Exception ignored) {
                // This exception is ignored
            }
        }

        private void unsubscribeAll() {
            subscribedQueries.forEach((queryDefinition, handlerSet) -> {
                try {
                    getSubscriberObserver().onNext(
                            QueryProviderOutbound.newBuilder()
                                                 .setUnsubscribe(buildQuerySubscription(queryDefinition, 1))
                                                 .build()
                    );
                } catch (Exception ignored) {
                    // This exception is ignored
                }
            });
            outboundStreamObserver = null;
        }

        void disconnect() {
            if (outboundStreamObserver != null) {
                outboundStreamObserver.onCompleted();
            }
            running = false;
            queryExecutor.shutdown();
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
         * query {@link ExecutorService}, in order. The {@code priority} is retrieved from the provided
         * {@link QueryRequest} and used to priorities this {@link QueryProcessor} among others of it's kind.
         */
        private class QueryProcessor implements Runnable {

            private final long priority;
            private final QueryRequest queryRequest;

            private QueryProcessor(QueryRequest queryRequest) {
                this.priority = priority(queryRequest.getProcessingInstructionsList());
                this.queryRequest = queryRequest;
            }

            public long getPriority() {
                return priority;
            }

            @Override
            public void run() {
                if (!running) {
                    logger.debug("Query Handler Provider has stopped running, "
                                         + "hence query [{}] will no longer be processed");
                    return;
                }

                try {
                    logger.debug("Will process query [{}]", queryRequest);
                    processQuery(queryRequest);
                } catch (RuntimeException | OutOfDirectMemoryError e) {
                    logger.warn("Query Processor had an exception when processing query [{}]", queryRequest, e);
                }
            }
        }
    }
}
