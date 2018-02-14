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

import io.axoniq.axonhub.client.AxonIQPlatformConfiguration;
import io.axoniq.axonhub.client.PlatformConnectionManager;
import io.axoniq.axonhub.client.command.AxonIQRegistration;
import io.axoniq.axonhub.client.util.ContextAddingInterceptor;
import io.axoniq.axonhub.client.util.FlowControllingStreamObserver;
import io.axoniq.axonhub.client.util.TokenAddingInterceptor;
import io.axoniq.axonhub.QueryResponse;
import io.axoniq.axonhub.QuerySubscription;
import io.axoniq.axonhub.grpc.QueryComplete;
import io.axoniq.axonhub.grpc.QueryProviderInbound;
import io.axoniq.axonhub.grpc.QueryProviderOutbound;
import io.axoniq.axonhub.grpc.QueryServiceGrpc;
import io.grpc.ClientInterceptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * AxonHub implementation for the QueryBus. Delegates incoming queries to the specified localSegment.
 * @author Marc Gathier
 */
public class AxonIQQueryBus implements QueryBus {
    private final Logger logger = LoggerFactory.getLogger(AxonIQQueryBus.class);
    private final AxonIQPlatformConfiguration configuration;
    private final QueryBus localSegment;
    private final QuerySerializer serializer;
    private final QueryPriorityCalculator priorityCalculator;
    private final QueryProvider queryProvider;
    private final PlatformConnectionManager platformConnectionManager;
    private final ClientInterceptor[] interceptors;


    /**
     * Creates an instance of the AxonIQQueryBus
     * @param platformConnectionManager creates connection to AxonHub platform
     * @param configuration contains client and component names used to identify the application in AxonHub
     * @param localSegment handles incoming query requests
     * @param serializer serializer/deserializer for query requests and responses
     * @param priorityCalculator calculates the request priority based on the content and adds it to the request
     */
    public AxonIQQueryBus(PlatformConnectionManager platformConnectionManager, AxonIQPlatformConfiguration configuration, QueryBus localSegment,
                          Serializer serializer, QueryPriorityCalculator priorityCalculator) {
        this.configuration = configuration;
        this.localSegment = localSegment;
        this.serializer = new QuerySerializer(serializer);
        this.priorityCalculator = priorityCalculator;
        this.queryProvider = new QueryProvider();
        this.platformConnectionManager = platformConnectionManager;
        this.platformConnectionManager.addReconnectListener(queryProvider::resubscribe);
        this.platformConnectionManager.addDisconnectListener(queryProvider::unsubscribeAll);
        interceptors = new ClientInterceptor[]{ new TokenAddingInterceptor(configuration.getToken()),
                new ContextAddingInterceptor(configuration.getContext())};

    }


    @Override
    public <R> Registration subscribe(String queryName, Class<R> responseType, MessageHandler<? super QueryMessage<?, R>> handler) {
        return new AxonIQRegistration(queryProvider.subscribe(queryName, responseType, configuration.getComponentName(), handler),
                () -> queryProvider.unsubscribe(queryName, responseType, configuration.getComponentName()));
    }

    @Override
    public <Q, R> CompletableFuture<R> query(QueryMessage<Q, R> queryMessage) {
        CompletableFuture<R> completableFuture = new CompletableFuture<>();
        QueryServiceGrpc.newStub(platformConnectionManager.getChannel())
                .withInterceptors(interceptors)
                .query(serializer.serializeRequest(queryMessage, 1,
                TimeUnit.HOURS.toMillis(1), priorityCalculator.determinePriority(queryMessage)),
                new StreamObserver<QueryResponse>() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public void onNext(QueryResponse commandResponse) {
                        logger.debug("Received response: {}", commandResponse);
                        completableFuture.complete((R)serializer.deserializeResponse(commandResponse));
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        logger.warn("Received error while waiting for first response: {}", throwable.getMessage());
                        completableFuture.completeExceptionally(throwable);
                    }

                    @Override
                    public void onCompleted() {
                        if( ! completableFuture.isDone())
                            completableFuture.complete(null);
                    }
                });

        return completableFuture;
    }

    @Override
    public <Q, R> Stream<R> queryAll(QueryMessage<Q, R> queryMessage, long timeout, TimeUnit timeUnit) {
        QueueBackedSpliterator<R> resultSpliterator = new QueueBackedSpliterator<>(timeout, timeUnit);
        QueryServiceGrpc.newStub(platformConnectionManager.getChannel())
                .withInterceptors(interceptors)
                .withDeadlineAfter(timeout, timeUnit)
                .query(serializer.serializeRequest(queryMessage, -1, timeUnit.toMillis(timeout),
                        priorityCalculator.determinePriority(queryMessage)),
                    new StreamObserver<QueryResponse>() {
                        @Override
                        @SuppressWarnings("unchecked")
                        public void onNext(QueryResponse commandResponse) {
                            logger.debug("Received response: {}", commandResponse);
                            resultSpliterator.put((R)serializer.deserializeResponse(commandResponse));
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            if(! isDeadlineExceeded(throwable)) {
                                logger.warn("Received error while waiting for responses: {}", throwable.getMessage());
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
        return throwable instanceof StatusRuntimeException && ((StatusRuntimeException)throwable).getStatus().getCode().equals(Status.Code.DEADLINE_EXCEEDED);
    }

    class QueryProvider {
        private StreamObserver<QueryProviderOutbound> outboundStreamObserver;
        private final ConcurrentMap<QueryDefinition, Set<MessageHandler<? super QueryMessage<?, ?>>>> subscribedQueries = new ConcurrentHashMap<>();

        @SuppressWarnings("unchecked")
        public <R> Registration subscribe(String queryName, Class<R> responseType, String componentName, MessageHandler<? super QueryMessage<?, R>> handler) {
            Set registrations = subscribedQueries.computeIfAbsent( new QueryDefinition(queryName, responseType.getName(), componentName), k -> new CopyOnWriteArraySet<>());
            registrations.add( handler);

            try {
                getSubscriberObserver().onNext(QueryProviderOutbound.newBuilder()
                        .setSubscribe(QuerySubscription.newBuilder()
                                .setMessageId(UUID.randomUUID().toString())
                                .setClientName(configuration.getClientName())
                                .setComponentName(componentName)
                                .setQuery(queryName)
                                .setResultName(responseType.getName())
                                .setNrOfHandlers(registrations.size())
                                .build())
                        .build());
            } catch( Exception ex) {
                logger.warn("Subscribe failed - {}", ex.getMessage());
            }
            return localSegment.subscribe(queryName, responseType, handler);
        }

        private synchronized StreamObserver<QueryProviderOutbound> getSubscriberObserver() {
            if( outboundStreamObserver == null) {
                StreamObserver<QueryProviderInbound> queryProviderInboundStreamObserver = new StreamObserver<QueryProviderInbound>() {
                    @Override
                    public void onNext(QueryProviderInbound inboundRequest) {
                        switch( inboundRequest.getRequestCase()) {
                            case CONFIRMATION:
                                break;
                            case QUERY:
                                String messageId = inboundRequest.getQuery().getMessageIdentifier();
                                try {
                                    //noinspection unchecked
                                    localSegment.queryAll(serializer.deserializeRequest(inboundRequest.getQuery()), 0, TimeUnit.SECONDS).forEach(response ->
                                            outboundStreamObserver.onNext(
                                                    QueryProviderOutbound.newBuilder()
                                                            .setQueryResponse(serializer.serializeResponse(response, messageId))
                                                            .build()
                                            )
                                    );
                                    outboundStreamObserver.onNext(QueryProviderOutbound.newBuilder().setQueryComplete(
                                            QueryComplete.newBuilder().setMessageId(messageId)).build());
                                } catch( Exception ex) {
                                    logger.warn("Received error from localSegment: {}", ex.getMessage());
                                    outboundStreamObserver.onNext(QueryProviderOutbound.newBuilder()
                                            .setQueryResponse(QueryResponse.newBuilder()
                                                    .setMessageIdentifier(messageId)
                                                    .setMessage(ex.getMessage())
                                                    .setSuccess(false)
                                                    .build())
                                            .build());
                                }
                                break;
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        outboundStreamObserver = null;
                        platformConnectionManager.scheduleReconnect();
                    }

                    @Override
                    public void onCompleted() {
                        outboundStreamObserver = null;
                        platformConnectionManager.scheduleReconnect();
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

        public void unsubscribe(String queryName, Class responseType, String componentName) {
            QueryDefinition queryDefinition = new QueryDefinition(queryName, responseType.getName(), componentName);
            subscribedQueries.remove(queryDefinition);
            try {
                getSubscriberObserver().onNext(QueryProviderOutbound.newBuilder().setUnsubscribe(
                        subscriptionBuilder(queryDefinition, 1)
                ).build());
            } catch (Exception ignored) {

            }
        }
        public void unsubscribeAll() {
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

        public void resubscribe() {
            try {
                StreamObserver<QueryProviderOutbound> subscriberStreamObserver = getSubscriberObserver();
                synchronized (subscriberStreamObserver) {
                    subscribedQueries.forEach((queryDefinition, handlers) ->
                            subscriberStreamObserver.onNext(QueryProviderOutbound.newBuilder().setSubscribe(
                                    subscriptionBuilder(queryDefinition, handlers.size())
                            ).build())
                    );
                }
            } catch (Exception ignored) {

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

            QueryDefinition(String queryName, String responseName, String componentName)  {
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
