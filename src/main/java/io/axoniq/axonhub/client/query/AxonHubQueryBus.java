/*
 * Copyright (c) 2018. AxonIQ
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

package io.axoniq.axonhub.client.query;

import io.axoniq.axonhub.ErrorMessage;
import io.axoniq.axonhub.QueryRequest;
import io.axoniq.axonhub.QueryResponse;
import io.axoniq.axonhub.QuerySubscription;
import io.axoniq.axonhub.client.AxonHubConfiguration;
import io.axoniq.axonhub.client.DispatchInterceptors;
import io.axoniq.axonhub.client.ErrorCode;
import io.axoniq.axonhub.client.PlatformConnectionManager;
import io.axoniq.axonhub.client.command.AxonHubRegistration;
import io.axoniq.axonhub.client.util.ContextAddingInterceptor;
import io.axoniq.axonhub.client.util.ExceptionSerializer;
import io.axoniq.axonhub.client.util.FlowControllingStreamObserver;
import io.axoniq.axonhub.client.util.TokenAddingInterceptor;
import io.axoniq.axonhub.grpc.QueryComplete;
import io.axoniq.axonhub.grpc.QueryProviderInbound;
import io.axoniq.axonhub.grpc.QueryProviderOutbound;
import io.axoniq.axonhub.grpc.QueryServiceGrpc;
import io.grpc.ClientInterceptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.Comparator;
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
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static io.axoniq.axonhub.client.util.ProcessingInstructionHelper.numberOfResults;
import static io.axoniq.axonhub.client.util.ProcessingInstructionHelper.priority;

/**
 * AxonHub implementation for the QueryBus. Delegates incoming queries to the specified localSegment.
 *
 * @author Marc Gathier
 */
public class AxonHubQueryBus implements QueryBus {
    private final Logger logger = LoggerFactory.getLogger(AxonHubQueryBus.class);
    private final AxonHubConfiguration configuration;
    private final QueryBus localSegment;
    private final QuerySerializer serializer;
    private final QueryPriorityCalculator priorityCalculator;
    private final QueryProvider queryProvider;
    private final PlatformConnectionManager platformConnectionManager;
    private final ClientInterceptor[] interceptors;
    private final DispatchInterceptors<QueryMessage<?, ?>> dispatchInterceptors = new DispatchInterceptors<>();


    /**
     * Creates an instance of the AxonHubQueryBus
     *
     * @param platformConnectionManager creates connection to AxonHub platform
     * @param configuration             contains client and component names used to identify the application in AxonHub
     * @param localSegment              handles incoming query requests
     * @param messageSerializer         serializer/deserializer for payload and metadata of query requests and responses
     * @param genericSerializer         serializer for communication of other objects than payload and metadata
     * @param priorityCalculator        calculates the request priority based on the content and adds it to the request
     */
    public
    AxonHubQueryBus(PlatformConnectionManager platformConnectionManager, AxonHubConfiguration configuration, QueryBus localSegment,
                           Serializer messageSerializer, Serializer genericSerializer, QueryPriorityCalculator priorityCalculator) {
        this.configuration = configuration;
        this.localSegment = localSegment;
        this.serializer = new QuerySerializer(messageSerializer, genericSerializer, configuration);
        this.priorityCalculator = priorityCalculator;
        this.queryProvider = new QueryProvider();
        this.platformConnectionManager = platformConnectionManager;
        this.platformConnectionManager.addReconnectListener(queryProvider::resubscribe);
        this.platformConnectionManager.addDisconnectListener(queryProvider::unsubscribeAll);
        interceptors = new ClientInterceptor[]{new TokenAddingInterceptor(configuration.getToken()),
                new ContextAddingInterceptor(configuration.getContext())};

    }


    @Override
    public <R> Registration subscribe(String queryName, Type responseType,
                                      MessageHandler<? super QueryMessage<?, R>> handler) {
        return new AxonHubRegistration(queryProvider.subscribe(queryName, responseType, configuration.getComponentName(), handler),
                                       () -> queryProvider.unsubscribe(queryName, responseType, configuration.getComponentName()));
    }

    @Override
    public <Q, R> CompletableFuture<QueryResponseMessage<R>> query(QueryMessage<Q, R> queryMessage) {
        QueryMessage<Q, R> interceptedQuery = dispatchInterceptors.intercept(queryMessage);
        CompletableFuture<QueryResponseMessage<R>> completableFuture = new CompletableFuture<>();
        QueryServiceGrpc.newStub(platformConnectionManager.getChannel())
                        .withInterceptors(interceptors)
                        .query(serializer.serializeRequest(interceptedQuery, 1,
                                                           TimeUnit.HOURS.toMillis(1), priorityCalculator.determinePriority(interceptedQuery)),
                               new StreamObserver<QueryResponse>() {
                                   @Override
                                   public void onNext(QueryResponse queryResponse) {
                                       logger.debug("Received response: {}", queryResponse);
                                       if( queryResponse.hasMessage()) {
                                           completableFuture.completeExceptionally(new RemoteQueryException(queryResponse.getErrorCode(), queryResponse.getMessage()));
                                       } else {
                                           completableFuture.complete(serializer.deserializeResponse(queryResponse));
                                       }
                                   }

                                   @Override
                                   public void onError(Throwable throwable) {
                                       logger.warn("Received error while waiting for first response: {}", throwable.getMessage(), throwable);
                                       completableFuture.completeExceptionally(throwable);
                                   }

                                   @Override
                                   public void onCompleted() {
                                       if (!completableFuture.isDone()) {
                                           completableFuture.completeExceptionally(new RemoteQueryException(ErrorCode.OTHER.errorCode(), ErrorMessage.newBuilder().setMessage("No result from query executor").build()));
                                       }
                                   }
                               });

        return completableFuture;
    }

    @Override
    public <Q, R> Stream<QueryResponseMessage<R>> scatterGather(QueryMessage<Q, R> queryMessage, long timeout, TimeUnit timeUnit) {
        QueryMessage<Q, R> interceptedQuery = dispatchInterceptors.intercept(queryMessage);
        QueueBackedSpliterator<QueryResponseMessage<R>> resultSpliterator = new QueueBackedSpliterator<>(timeout, timeUnit);
        QueryServiceGrpc.newStub(platformConnectionManager.getChannel())
                        .withInterceptors(interceptors)
                        .withDeadlineAfter(timeout, timeUnit)
                        .query(serializer.serializeRequest(interceptedQuery, -1, timeUnit.toMillis(timeout),
                                                           priorityCalculator.determinePriority(interceptedQuery)),
                               new StreamObserver<QueryResponse>() {
                                   @Override
                                   public void onNext(QueryResponse queryResponse) {
                                       logger.debug("Received response: {}", queryResponse);

                                       if( queryResponse.hasMessage()) {
                                           logger.warn("Received exception: {}", queryResponse.getMessage());
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

    public Registration registerDispatchInterceptor(MessageDispatchInterceptor<? super QueryMessage<?, ?>> dispatchInterceptor) {
        return dispatchInterceptors.registerDispatchInterceptor(dispatchInterceptor);
    }

    class QueryProvider {
        private final ConcurrentMap<QueryDefinition, Set<MessageHandler<? super QueryMessage<?, ?>>>> subscribedQueries = new ConcurrentHashMap<>();
        private final PriorityBlockingQueue<QueryRequest> queryQueue;
        private final ExecutorService executor = Executors.newFixedThreadPool(configuration.getQueryThreads());
        private StreamObserver<QueryProviderOutbound> outboundStreamObserver;
        private volatile boolean subscribing;

        QueryProvider() {
            queryQueue = new PriorityBlockingQueue<>(1000, Comparator.comparingLong(c -> -priority(c.getProcessingInstructionsList())));
            IntStream.range(0, configuration.getQueryThreads()).forEach(i -> executor.submit(this::queryExecutor));
        }

        private void queryExecutor() {
            logger.debug("Starting Query Executor");
            while (true) {
                try {
                    QueryRequest query = queryQueue.poll(10, TimeUnit.SECONDS);
                    if (query != null) {
                        logger.debug("Received query: {}", query);
                        processQuery(query);
                    }
                } catch (InterruptedException e) {
                    logger.warn("Interrupted queryExecutor", e);
                    return;
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
                                                                                                  .setMessage(ExceptionSerializer
                                                                                                                 .serialize(configuration.getClientName(), ex))
                                                                                                    .setErrorCode(ErrorCode.resolve(ex).errorCode())
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
                                                                                                   .setClientName(configuration.getClientName())
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
                logger.info("Create new subscriber");
                StreamObserver<QueryProviderInbound> queryProviderInboundStreamObserver = new StreamObserver<QueryProviderInbound>() {
                    @Override
                    public void onNext(QueryProviderInbound inboundRequest) {
                        switch (inboundRequest.getRequestCase()) {
                            case CONFIRMATION:
                                break;
                            case QUERY:
                                queryQueue.add(inboundRequest.getQuery());
                                break;
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        outboundStreamObserver = null;
                    }

                    @Override
                    public void onCompleted() {
                        outboundStreamObserver = null;
                    }
                };

                StreamObserver<QueryProviderOutbound> stream = platformConnectionManager.getQueryStream(queryProviderInboundStreamObserver, interceptors);
                outboundStreamObserver = new FlowControllingStreamObserver<>(stream,
                                                                             configuration,
                                                                             flowControl -> QueryProviderOutbound.newBuilder().setFlowControl(flowControl).build(),
                                                                             t -> t.getRequestCase().equals(QueryProviderOutbound.RequestCase.QUERYRESPONSE)).sendInitialPermits();
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

        private QuerySubscription.Builder subscriptionBuilder(QueryDefinition queryDefinition, int nrHandlers) {
            return QuerySubscription.newBuilder()
                                    .setClientName(configuration.getClientName())
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

}
