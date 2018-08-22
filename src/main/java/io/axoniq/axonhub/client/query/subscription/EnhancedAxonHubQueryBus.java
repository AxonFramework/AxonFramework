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

import io.axoniq.axonhub.client.AxonHubConfiguration;
import io.axoniq.axonhub.client.PlatformConnectionManager;
import io.axoniq.axonhub.client.query.AxonHubQueryBus;
import io.axoniq.axonhub.client.query.QueryPriorityCalculator;
import io.axoniq.axonhub.grpc.QueryServiceGrpc;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static io.axoniq.axonhub.grpc.QueryProviderInbound.RequestCase.SUBSCRIPTION_QUERY_REQUEST;

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

    private final Collection<String> subscriptions = new CopyOnWriteArraySet<>();

    private final QueryUpdateEmitter updateEmitter;

    public EnhancedAxonHubQueryBus(PlatformConnectionManager platformConnectionManager,
                                   AxonHubConfiguration configuration, QueryBus localSegment,
                                   QueryUpdateEmitter updateEmitter,
                                   Serializer messageSerializer, Serializer genericSerializer,
                                   QueryPriorityCalculator priorityCalculator) {

        this.configuration = configuration;
        this.axonHubQueryBus = new AxonHubQueryBus(platformConnectionManager, configuration, localSegment,
                                                   messageSerializer, genericSerializer, priorityCalculator);
        this.serializer = new SubscriptionMessageSerializer(configuration, messageSerializer, genericSerializer);
        platformConnectionManager.addDisconnectListener(this::onApplicationDisconnected);
        platformConnectionManager.addReconnectInterceptor(this::interceptReconnectRequest);

        SubscriptionQueryRequestTarget target =
                new SubscriptionQueryRequestTarget(localSegment, axonHubQueryBus::publish, serializer);
        axonHubQueryBus.on(SUBSCRIPTION_QUERY_REQUEST, target::onSubscriptionQueryRequest);
        platformConnectionManager.addDisconnectListener(target::onApplicationDisconnected);

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

    @Override
    public <Q, I, U> SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> subscriptionQuery(
            SubscriptionQueryMessage<Q, I, U> query, SubscriptionQueryBackpressure backPressure, int updateBufferSize) {
        String subscriptionId = query.getIdentifier();

        if (this.subscriptions.contains(subscriptionId)) {
            String errorMessage = "Already exists a subscription query with the same subscriptionId: " + subscriptionId;
            logger.warn(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }
        logger.debug("Subscription Query requested with subscriptionId " + subscriptionId);
        subscriptions.add(subscriptionId);
        QueryServiceGrpc.QueryServiceStub queryService = this.axonHubQueryBus.queryServiceStub();

        AxonHubSubscriptionQueryResult result = new AxonHubSubscriptionQueryResult(
                serializer.serialize(query),
                queryService::subscription,
                configuration,
                backPressure,
                updateBufferSize,
                () -> subscriptions.remove(subscriptionId));

        return new DeserializedResult<>(result.get(), serializer);
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
    public Registration registerDispatchInterceptor(
            MessageDispatchInterceptor<? super QueryMessage<?, ?>> dispatchInterceptor) {
        return axonHubQueryBus.registerDispatchInterceptor(dispatchInterceptor);
    }

    @Override
    public Registration registerHandlerInterceptor(
            MessageHandlerInterceptor<? super QueryMessage<?, ?>> handlerInterceptor) {
        return axonHubQueryBus.registerHandlerInterceptor(handlerInterceptor);
    }
}
