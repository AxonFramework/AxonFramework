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

package io.axoniq.axonhub.client.query.subscription;

import io.axoniq.axonhub.SubscriptionQuery;
import io.axoniq.axonhub.SubscriptionQueryRequest;
import io.axoniq.axonhub.client.AxonHubConfiguration;
import io.axoniq.axonhub.client.PlatformConnectionManager;
import io.axoniq.axonhub.client.Publisher;
import io.axoniq.axonhub.client.query.AxonHubQueryBus;
import io.axoniq.axonhub.client.query.QueryPriorityCalculator;
import io.axoniq.axonhub.grpc.QueryProviderInbound;
import io.axoniq.axonhub.grpc.QueryProviderOutbound;
import io.axoniq.axonhub.grpc.QueryServiceGrpc;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageHandler;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static io.axoniq.axonhub.grpc.QueryProviderInbound.RequestCase.SUBSCRIPTION_QUERY;

/**
 * AxonHub implementation for the QueryBus that support subscription queries.
 *
 * @author Sara Pellegrini
 */
public class EnhancedAxonHubQueryBus implements QueryBus, QueryUpdateEmitter {

    private final Logger logger = LoggerFactory.getLogger(EnhancedAxonHubQueryBus.class);

    private final AxonHubConfiguration configuration;

    private final AxonHubQueryBus axonHubQueryBus;

    private final SubscriptionMessageSerializer serializer;

    private final Map<String, SubscriptionQueryResult<QueryResponseMessage<Object>, SubscriptionQueryUpdateMessage<Object>>> subscriptions = new ConcurrentHashMap<>();

    private final Publisher<QueryProviderOutbound> publisher;

    private final QueryBus localSegment;

    private final QueryUpdateEmitter updateEmitter;

    public EnhancedAxonHubQueryBus(PlatformConnectionManager platformConnectionManager,
                                   AxonHubConfiguration configuration, QueryBus localSegment,
                                   QueryUpdateEmitter updateEmitter,
                                   Serializer messageSerializer, Serializer genericSerializer,
                                   QueryPriorityCalculator priorityCalculator) {
        axonHubQueryBus = new AxonHubQueryBus(platformConnectionManager,
                                              configuration,
                                              localSegment,
                                              messageSerializer,
                                              genericSerializer,
                                              priorityCalculator);
        this.configuration = configuration;
        serializer = new SubscriptionMessageSerializer(configuration,
                                                       messageSerializer,
                                                       genericSerializer);
        axonHubQueryBus.on(SUBSCRIPTION_QUERY, this::onSubscriptionQueryRequest);
        platformConnectionManager.addDisconnectListener(this::onApplicationDisconnected);
        platformConnectionManager.addReconnectInterceptor(this::interceptReconnectRequest);
        this.publisher = axonHubQueryBus::publish;
        this.localSegment = localSegment;
        this.updateEmitter = updateEmitter;
    }

    @Override
    public <R> Registration subscribe(String queryName, Type responseType,
                                      MessageHandler<? super QueryMessage<?, R>> handler) {
        return axonHubQueryBus.subscribe(queryName, responseType, handler);
    }

    @Override
    public <Q, R> CompletableFuture<QueryResponseMessage<R>> query(QueryMessage<Q, R> query) {
        return axonHubQueryBus.query(query);
    }

    @Override
    public <Q, R> Stream<QueryResponseMessage<R>> scatterGather(QueryMessage<Q, R> query, long timeout, TimeUnit unit) {
        return axonHubQueryBus.scatterGather(query, timeout, unit);
    }

    @Override
    public <Q, I, U> SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> subscriptionQuery(
            SubscriptionQueryMessage<Q, I, U> query, SubscriptionQueryBackpressure backpressure) {
        logger.debug("Subscription Query requested with subscriptionId " + query.getIdentifier());
        QueryServiceGrpc.QueryServiceStub queryService = this.axonHubQueryBus.queryServiceStub();
        return new AxonHubSubscriptionQueryResult<>(query, queryService, configuration, serializer, backpressure).get();
    }

    @Override
    public <U> void emit(Predicate<SubscriptionQueryMessage<?, ?, U>> filter,
                         SubscriptionQueryUpdateMessage<U> update) {
        this.updateEmitter.emit(filter, update);
    }

    @Override
    public void complete(Predicate<SubscriptionQueryMessage<?, ?, ?>> filter) {
        this.updateEmitter.complete(filter);
    }

    @Override
    public void completeExceptionally(Predicate<SubscriptionQueryMessage<?, ?, ?>> filter, Throwable cause) {
        this.updateEmitter.completeExceptionally(filter, cause);
    }

    private void onSubscriptionQueryRequest(QueryProviderInbound inbound) {
        SubscriptionQueryRequest subscriptionQuery = inbound.getSubscriptionQuery();
        switch (subscriptionQuery.getResponseCase()) {
            case SUBSCRIBE_INITIAL:
                subscribeInitialResult(subscriptionQuery.getSubscribeInitial());
                break;
            case SUBSCRIBE_UPDATE:
                subscribeUpdates(subscriptionQuery.getSubscribeUpdate());
                break;
            case UNSUBSCRIBE:
                unsubscribeLocally(subscriptionQuery.getUnsubscribe());
                break;
        }
    }

    private void subscribeInitialResult(SubscriptionQuery query){
        String subscriptionId = query.getQueryRequest().getMessageIdentifier();
        subscribeLocally(query).initialResult().subscribe(
                i -> publisher.publish(serializer.serialize(i, subscriptionId)),
                e -> logger.debug("Error in initial result for subscription id: {}", subscriptionId)
        );
    }

    private void subscribeUpdates(SubscriptionQuery query){
        String subscriptionId = query.getQueryRequest().getMessageIdentifier();
        subscribeLocally(query).updates().subscribe(
                u -> publisher.publish(serializer.serialize(u, subscriptionId)),
                e -> publisher.publish(serializer.serializeCompleteExceptionally(subscriptionId, e)),
                () -> publisher.publish(serializer.serializeComplete(subscriptionId)));
    }

    private SubscriptionQueryResult<QueryResponseMessage<Object>, SubscriptionQueryUpdateMessage<Object>>
    subscribeLocally(SubscriptionQuery subscribe) {
        String subscriptionId = subscribe.getQueryRequest().getMessageIdentifier();
        return subscriptions.computeIfAbsent(subscriptionId,
                                             id ->  localSegment.subscriptionQuery(serializer.deserialize(subscribe)));
    }

    private void unsubscribeLocally(SubscriptionQuery unsubscribe) {
        String subscriptionId = unsubscribe.getQueryRequest().getMessageIdentifier();
        logger.debug("unsubscribe locally subscriptionId " + subscriptionId);
        subscriptions.remove(subscriptionId).updates().subscribe().dispose();
    }

    private void onApplicationDisconnected() {
        subscriptions.clear();
    }

    private Runnable interceptReconnectRequest(Runnable reconnect) {
        if (subscriptions.isEmpty()) {
            return reconnect;
        }
        return () -> logger.info("Reconnect refused because there are active subscription queries.");
    }
}
