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
import io.grpc.stub.StreamObserver;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.UpdateHandler;
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

    private final Logger logger = LoggerFactory.getLogger(AxonHubQueryBus.class);

    private final AxonHubQueryBus axonHubQueryBus;

    private final SubscriptionMessageSerializer serializer;

    private final Map<String, Registration> registrationMap = new ConcurrentHashMap<>();

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
        serializer = new SubscriptionMessageSerializer(configuration,
                                                       messageSerializer,
                                                       genericSerializer);
        axonHubQueryBus.on(SUBSCRIPTION_QUERY, this::onSubscriptionQueryRequest);
        platformConnectionManager.addDisconnectListener(this::onApplicationDisconnected);
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
    public <Q, I, U> Registration subscriptionQuery(SubscriptionQueryMessage<Q, I, U> query,
                                                    UpdateHandler<I, U> updateHandler) {
        String subscriptionId = query.getIdentifier();
        logger.debug("subscriptionQuery request with subscriptionId " + subscriptionId);

        StreamObserver<SubscriptionQuery> requestObserver = this.axonHubQueryBus
                .queryServiceStub()
                .subscription(new SubscriptionResponseHandler<>(updateHandler, serializer));
        requestObserver.onNext(this.serializer.serialize(query));

        return () -> {
            logger.debug("Unsubscribe request for subscriptionId " + subscriptionId);
            requestObserver.onCompleted();
            return true;
        };
    }

    private void onSubscriptionQueryRequest(QueryProviderInbound inbound) {
        SubscriptionQueryRequest subscriptionQuery = inbound.getSubscriptionQuery();
        switch (subscriptionQuery.getResponseCase()) {
            case SUBSCRIBE:
                subscribeLocally(subscriptionQuery.getSubscribe());
                break;
            case UNSUBSCRIBE:
                unsubscribeLocally(subscriptionQuery.getUnsubscribe());
                break;
        }
    }

    private void subscribeLocally(SubscriptionQuery subscribe) {
        String subscriptionId = subscribe.getQueryRequest().getMessageIdentifier();
        UpdateHandler updateHandler = new AxonHubUpdateHandler(subscriptionId, publisher, serializer);
        SubscriptionQueryMessage subscriptionQueryMessage = serializer.deserialize(subscribe);
        Registration registration = this.localSegment.subscriptionQuery(subscriptionQueryMessage, updateHandler);
        registrationMap.put(subscriptionId, registration);
    }

    private void unsubscribeLocally(SubscriptionQuery unsubscribe) {
        registrationMap.remove(unsubscribe.getQueryRequest().getMessageIdentifier()).cancel();
    }

    private void onApplicationDisconnected() {
        registrationMap.clear();
    }
}
