/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static io.axoniq.axonserver.grpc.query.QueryProviderInbound.RequestCase.SUBSCRIPTION_QUERY_REQUEST;
import static org.axonframework.axonserver.connector.util.ProcessingInstructionHelper.numberOfResults;
import static org.axonframework.axonserver.connector.util.ProcessingInstructionHelper.priority;

/**
 * AxonServer implementation for the QueryBus. Delegates incoming queries to the specified localSegment.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public class AxonServerQueryBus implements QueryBus {
    private final Logger logger = LoggerFactory.getLogger(AxonServerQueryBus.class);
    private final AxonServerConfiguration configuration;
    private final QueryUpdateEmitter updateEmitter;
    private final QueryBus localSegment;
    private final QuerySerializer serializer;
    private final SubscriptionMessageSerializer subscriptionSerializer;
    private final QueryPriorityCalculator priorityCalculator;
    private final QueryProvider queryProvider;
    private final AxonServerConnectionManager axonServerConnectionManager;
    private final ClientInterceptor[] interceptors;
    private final Collection<String> subscriptions = new CopyOnWriteArraySet<>();
    private final DispatchInterceptors<QueryMessage<?, ?>> dispatchInterceptors = new DispatchInterceptors<>();
    private final Map<RequestCase, Collection<Consumer<QueryProviderInbound>>> queryHandlers = new EnumMap<>(RequestCase.class);


    /**
     * Creates an instance of the AxonServerQueryBus
     *
     * @param axonServerConnectionManager creates connection to AxonServer platform
     * @param configuration             contains client and component names used to identify the application in
     *                                  AxonServer
     * @param updateEmitter             emits incremental updates to subscription queries
     * @param localSegment              handles incoming query requests
     * @param messageSerializer         serializer/deserializer for payload and metadata of query requests and responses
     * @param genericSerializer         serializer for communication of other objects than payload and metadata
     * @param priorityCalculator        calculates the request priority based on the content and adds it to the request
     */
    public AxonServerQueryBus(AxonServerConnectionManager axonServerConnectionManager,
                              AxonServerConfiguration configuration,
                              QueryUpdateEmitter updateEmitter, QueryBus localSegment,
                              Serializer messageSerializer, Serializer genericSerializer,
                              QueryPriorityCalculator priorityCalculator) {
        this.configuration = configuration;
        this.updateEmitter = updateEmitter;
        this.localSegment = localSegment;
        this.serializer = new QuerySerializer(messageSerializer, genericSerializer, configuration);
        this.priorityCalculator = priorityCalculator;
        this.queryProvider = new QueryProvider();
        this.axonServerConnectionManager = axonServerConnectionManager;
        this.axonServerConnectionManager.addReconnectListener(queryProvider::resubscribe);
        this.axonServerConnectionManager.addDisconnectListener(queryProvider::unsubscribeAll);
        interceptors = new ClientInterceptor[]{new TokenAddingInterceptor(configuration.getToken()),
                new ContextAddingInterceptor(configuration.getContext())};
        this.subscriptionSerializer = new SubscriptionMessageSerializer(configuration, messageSerializer, genericSerializer);
        axonServerConnectionManager.addDisconnectListener(this::onApplicationDisconnected);
        axonServerConnectionManager.addReconnectInterceptor(this::interceptReconnectRequest);
        SubscriptionQueryRequestTarget target =
                new SubscriptionQueryRequestTarget(localSegment, this::publish, subscriptionSerializer);
        this.on(SUBSCRIPTION_QUERY_REQUEST, target::onSubscriptionQueryRequest);
        axonServerConnectionManager.addDisconnectListener(target::onApplicationDisconnected);
    }


    @Override
    public <R> Registration subscribe(String queryName, Type responseType,
                                      MessageHandler<? super QueryMessage<?, R>> handler) {
        return new AxonServerRegistration(queryProvider.subscribe(queryName, responseType, configuration.getComponentName(), handler),
                                          () -> queryProvider.unsubscribe(queryName, responseType, configuration.getComponentName()));
    }

    @Override
    public <Q, R> CompletableFuture<QueryResponseMessage<R>> query(QueryMessage<Q, R> queryMessage) {
        QueryMessage<Q, R> interceptedQuery = dispatchInterceptors.intercept(queryMessage);
        CompletableFuture<QueryResponseMessage<R>> completableFuture = new CompletableFuture<>();
        try {
            queryServiceStub()
                    .query(serializer.serializeRequest(interceptedQuery,
                                                       1,
                                                       TimeUnit.HOURS.toMillis(1),
                                                       priorityCalculator.determinePriority(interceptedQuery)),
                           new StreamObserver<QueryResponse>() {
                               @Override
                               public void onNext(QueryResponse queryResponse) {
                                   logger.debug("Received response: {}", queryResponse);
                                   if (queryResponse.hasErrorMessage()) {
                                       AxonServerRemoteQueryHandlingException exception =
                                               new AxonServerRemoteQueryHandlingException(
                                                       queryResponse.getErrorCode(), queryResponse.getErrorMessage()
                                               );
                                       completableFuture.completeExceptionally(exception);
                                   } else {
                                       completableFuture.complete(serializer.deserializeResponse(queryResponse));
                                   }
                               }

                               @Override
                               public void onError(Throwable throwable) {
                                   logger.warn("Received error while waiting for first response: {}",
                                               throwable.getMessage(),
                                               throwable);
                                   completableFuture.completeExceptionally(new AxonServerQueryDispatchException(
                                           ErrorCode.QUERY_DISPATCH_ERROR.errorCode(),
                                           ExceptionSerializer.serialize(configuration.getClientId(), throwable)
                                   ));
                               }

                               @Override
                               public void onCompleted() {
                                   if (!completableFuture.isDone()) {
                                       completableFuture.completeExceptionally(new AxonServerQueryDispatchException(
                                               ErrorCode.QUERY_DISPATCH_ERROR.errorCode(),
                                               ErrorMessage.newBuilder()
                                                           .setMessage("No result from query executor")
                                                           .build()
                                       ));
                                   }
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

    public QueryServiceGrpc.QueryServiceStub queryServiceStub() {
        return QueryServiceGrpc.newStub(axonServerConnectionManager.getChannel()).withInterceptors(interceptors);
    }

    @Override
    public <Q, R> Stream<QueryResponseMessage<R>> scatterGather(QueryMessage<Q, R> queryMessage, long timeout, TimeUnit timeUnit) {
        QueryMessage<Q, R> interceptedQuery = dispatchInterceptors.intercept(queryMessage);
        QueueBackedSpliterator<QueryResponseMessage<R>> resultSpliterator = new QueueBackedSpliterator<>(timeout, timeUnit);
        queryServiceStub()
                        .withDeadlineAfter(timeout, timeUnit)
                        .query(serializer.serializeRequest(interceptedQuery, -1, timeUnit.toMillis(timeout),
                                                           priorityCalculator.determinePriority(interceptedQuery)),
                               new StreamObserver<QueryResponse>() {
                                   @Override
                                   public void onNext(QueryResponse queryResponse) {
                                       logger.debug("Received response: {}", queryResponse);

                                       if( queryResponse.hasErrorMessage()) {
                                           logger.warn("Received exception: {}", queryResponse.getErrorMessage());
                                       } else {
                                           resultSpliterator.put(serializer.deserializeResponse(queryResponse));
                                       }
                                   }

                                   @Override
                                   public void onError(Throwable throwable) {
                                       if (!isDeadlineExceeded(throwable)) {
                                           logger.warn("Received error while waiting for responses: {}", throwable.getMessage(), throwable);
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
        return throwable instanceof StatusRuntimeException && ((StatusRuntimeException) throwable).getStatus().getCode().equals(Status.Code.DEADLINE_EXCEEDED);
    }

    public Registration registerHandlerInterceptor(MessageHandlerInterceptor<? super QueryMessage<?, ?>> interceptor){
        return localSegment.registerHandlerInterceptor(interceptor);
    }

    public Registration registerDispatchInterceptor(MessageDispatchInterceptor<? super QueryMessage<?, ?>> dispatchInterceptor) {
        return dispatchInterceptors.registerDispatchInterceptor(dispatchInterceptor);
    }

    public void disconnect() {
        queryProvider.disconnect();
    }

    class QueryProvider {
        private final ConcurrentMap<QueryDefinition, Set<MessageHandler<? super QueryMessage<?, ?>>>> subscribedQueries = new ConcurrentHashMap<>();
        private final PriorityBlockingQueue<QueryRequest> queryQueue;
        private final ExecutorService executor = Executors.newFixedThreadPool(configuration.getQueryThreads());
        private StreamObserver<QueryProviderOutbound> outboundStreamObserver;
        private volatile boolean subscribing;
        private volatile boolean running = true;

        QueryProvider() {
            queryQueue = new PriorityBlockingQueue<>(1000, Comparator.comparingLong(c -> -priority(c.getProcessingInstructionsList())));
            IntStream.range(0, configuration.getQueryThreads()).forEach(i -> executor.submit(this::queryExecutor));
        }

        private void queryExecutor() {
            logger.debug("Starting Query Executor");
            boolean interrupted = false;

            while (running && !interrupted) {
                try {
                    QueryRequest query = queryQueue.poll(1, TimeUnit.SECONDS);
                    if (query != null) {
                        logger.debug("Received query: {}", query);
                        processQuery(query);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Interrupted queryExecutor", e);
                    interrupted = true;
                }
            }

        }

        private void processQuery(QueryRequest query) {
            String requestId = query.getMessageIdentifier();
            try {
                //noinspection unchecked
                if( numberOfResults(query.getProcessingInstructionsList()) == 1) {
                    QueryResponseMessage<Object> response = localSegment.query(serializer.deserializeRequest(query))
                                                                        .get();
                    outboundStreamObserver.onNext(
                            QueryProviderOutbound.newBuilder()
                                                 .setQueryResponse(serializer.serializeResponse(response,
                                                                                                requestId))
                                                 .build());
                } else {
                    localSegment.scatterGather(serializer.deserializeRequest(query), 0, TimeUnit.SECONDS)
                                .forEach(response -> outboundStreamObserver.onNext(
                                        QueryProviderOutbound.newBuilder()
                                                             .setQueryResponse(serializer.serializeResponse(response,
                                                                                                            requestId))
                                                             .build()));
                }
                outboundStreamObserver.onNext(QueryProviderOutbound.newBuilder().setQueryComplete(
                        QueryComplete.newBuilder().setMessageId(UUID.randomUUID().toString()).setRequestId(requestId)).build());
            } catch (Exception ex) {
                logger.warn("Received error from localSegment: {}", ex.getMessage(), ex);
                outboundStreamObserver.onNext(QueryProviderOutbound.newBuilder()
                                                                   .setQueryResponse(QueryResponse.newBuilder()
                                                                                                  .setMessageIdentifier(UUID.randomUUID().toString())
                                                                                                  .setRequestIdentifier(requestId)
                                                                                                  .setErrorMessage(ExceptionSerializer
                                                                                                                 .serialize(configuration.getClientId(), ex))
                                                                                                  .setErrorCode(ErrorCode.QUERY_EXECUTION_ERROR.errorCode())
                                                                                                  .build())
                                                                   .build());
            }
        }

        @SuppressWarnings("unchecked")
        public <R> Registration subscribe(String queryName, Type responseType, String componentName, MessageHandler<? super QueryMessage<?, R>> handler) {
            subscribing = true;
            Set registrations = subscribedQueries.computeIfAbsent(new QueryDefinition(queryName, responseType.getTypeName(), componentName), k -> new CopyOnWriteArraySet<>());
            registrations.add(handler);

            try {
                getSubscriberObserver().onNext(QueryProviderOutbound.newBuilder()
                                                                    .setSubscribe(QuerySubscription.newBuilder()
                                                                                                   .setMessageId(UUID.randomUUID().toString())
                                                                                                   .setClientId(configuration.getClientId())
                                                                                                   .setComponentName(componentName)
                                                                                                   .setQuery(queryName)
                                                                                                   .setResultName(responseType.getTypeName())
                                                                                                   .setNrOfHandlers(registrations.size())
                                                                                                   .build())
                                                                    .build());
            } catch (Exception ex) {
                logger.warn("Subscribe failed - {}", ex.getMessage());
            } finally {
                subscribing = false;
            }
            return localSegment.subscribe(queryName, responseType, handler);
        }

        private synchronized StreamObserver<QueryProviderOutbound> getSubscriberObserver() {
            if (outboundStreamObserver == null) {
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
                                queryQueue.add(inboundRequest.getQuery());
                                break;
                        }
                    }

                    @Override
                    public void onError(Throwable ex){
                        logger.warn("Received error from server: {}", ex.getMessage());
                        outboundStreamObserver = null;
                        if (ex instanceof StatusRuntimeException && ((StatusRuntimeException) ex).getStatus().getCode().equals(Status.UNAVAILABLE.getCode())) {
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

                StreamObserver<QueryProviderOutbound> stream = axonServerConnectionManager.getQueryStream(queryProviderInboundStreamObserver, interceptors);
                logger.info("Creating new subscriber");
                outboundStreamObserver = new FlowControllingStreamObserver<>(stream,
                                                                             configuration,
                                                                             flowControl -> QueryProviderOutbound.newBuilder().setFlowControl(flowControl).build(),
                                                                             t -> t.getRequestCase().equals(QueryProviderOutbound.RequestCase.QUERY_RESPONSE)).sendInitialPermits();
            }
            return outboundStreamObserver;
        }

        public void unsubscribe(String queryName, Type responseType, String componentName) {
            QueryDefinition queryDefinition = new QueryDefinition(queryName, responseType.getTypeName(), componentName);
            subscribedQueries.remove(queryDefinition);
            try {
                getSubscriberObserver().onNext(QueryProviderOutbound.newBuilder().setUnsubscribe(
                        subscriptionBuilder(queryDefinition, 1)
                ).build());
            } catch (Exception ignored) {

            }
        }

        private void unsubscribeAll() {
            subscribedQueries.forEach((d, count) -> {
                try {
                    getSubscriberObserver().onNext(QueryProviderOutbound.newBuilder().setUnsubscribe(
                            subscriptionBuilder(d, 1)
                    ).build());
                } catch (Exception ignored) {

                }
            });
            outboundStreamObserver = null;
        }

        private void resubscribe() {
            if( subscribedQueries.isEmpty() || subscribing) return;
            try {
                StreamObserver<QueryProviderOutbound> subscriberStreamObserver = getSubscriberObserver();
                subscribedQueries.forEach((queryDefinition, handlers) ->
                                                      subscriberStreamObserver.onNext(QueryProviderOutbound.newBuilder().setSubscribe(
                                                              subscriptionBuilder(queryDefinition, handlers.size())
                                                      ).build())
                );
            } catch (Exception ex) {
                logger.warn("Error while resubscribing - {}", ex.getMessage());
            }
        }

        public void disconnect() {
            if (outboundStreamObserver != null) {
                outboundStreamObserver.onCompleted();
            }

            running = false;
            executor.shutdown();
        }

        private QuerySubscription.Builder subscriptionBuilder(QueryDefinition queryDefinition, int nrHandlers) {
            return QuerySubscription.newBuilder()
                                    .setClientId(configuration.getClientId())
                                    .setMessageId(UUID.randomUUID().toString())
                                    .setComponentName(queryDefinition.componentName)
                                    .setQuery(queryDefinition.queryName)
                                    .setNrOfHandlers(nrHandlers)
                                    .setResultName(queryDefinition.responseName);
        }

        class QueryDefinition {
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
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
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
    }

    public void publish(QueryProviderOutbound providerOutbound){
        this.queryProvider.getSubscriberObserver().onNext(providerOutbound);
    }

    public void on(RequestCase requestCase, Consumer<QueryProviderInbound> consumer) {
        Collection<Consumer<QueryProviderInbound>> consumers =
                queryHandlers.computeIfAbsent(requestCase,rc -> new CopyOnWriteArraySet<>());
        consumers.add(consumer);
    }

    @Override
    public <Q, I, U> SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> subscriptionQuery(
            SubscriptionQueryMessage<Q, I, U> query, SubscriptionQueryBackpressure backPressure, int updateBufferSize) {
        String subscriptionId = query.getIdentifier();

        if (this.subscriptions.contains(subscriptionId)) {
            String errorMessage = "Already exists a subscription query with the same subscriptionId: " + subscriptionId;
            logger.warn(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }
        logger.debug("Subscription Query requested with subscriptionId {}", subscriptionId);
        subscriptions.add(subscriptionId);
        QueryServiceGrpc.QueryServiceStub queryService = this.queryServiceStub();

        AxonServerSubscriptionQueryResult result = new AxonServerSubscriptionQueryResult(
                subscriptionSerializer.serialize(query),
                queryService::subscription,
                configuration,
                backPressure,
                updateBufferSize,
                () -> subscriptions.remove(subscriptionId));

        return new DeserializedResult<>(result.get(), subscriptionSerializer);
    }

    @Override
    public QueryUpdateEmitter queryUpdateEmitter() {
        return updateEmitter;
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

}
