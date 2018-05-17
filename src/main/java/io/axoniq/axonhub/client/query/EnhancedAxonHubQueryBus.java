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

import io.axoniq.axonhub.client.AxonHubConfiguration;
import io.axoniq.axonhub.client.PlatformConnectionManager;
import io.axoniq.axonhub.client.query.subscription.AxonHubUpdateDispatcher;
import io.axoniq.axonhub.client.query.subscription.SubscriptionQuerySerializer;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.UpdateHandler;
import org.axonframework.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static io.axoniq.axonhub.grpc.QueryProviderInbound.RequestCase.*;

/**
 * AxonHub implementation for the QueryBus that support subscription queries.
 *
 * @author Sara Pellegrini
 */
public class EnhancedAxonHubQueryBus implements QueryBus {

    private final Logger logger = LoggerFactory.getLogger(AxonHubQueryBus.class);

    private final AxonHubQueryBus axonHubQueryBus;

    private final SubscriptionQuerySerializer subscriptionQuerySerializer;

    private final AxonHubUpdateDispatcher updateDispatcher;

    public EnhancedAxonHubQueryBus(PlatformConnectionManager platformConnectionManager, AxonHubConfiguration configuration, QueryBus localSegment,
                                   Serializer messageSerializer, Serializer genericSerializer, QueryPriorityCalculator priorityCalculator) {
        axonHubQueryBus = new AxonHubQueryBus(platformConnectionManager, configuration, localSegment, messageSerializer, genericSerializer,priorityCalculator);
        subscriptionQuerySerializer = new SubscriptionQuerySerializer(configuration, messageSerializer, genericSerializer);
        updateDispatcher = new AxonHubUpdateDispatcher(axonHubQueryBus::publish, messageSerializer, genericSerializer,configuration);
        axonHubQueryBus.on(QUERY_UPDATE, updateDispatcher::onUpdate);
        axonHubQueryBus.on(QUERY_UPDATE_COMPLETE, updateDispatcher::onUpdate);
        axonHubQueryBus.on(QUERY_UPDATE_COMPLETE_EXCEPTIONALLY, updateDispatcher::onUpdate);
        platformConnectionManager.addDisconnectListener(updateDispatcher::onDisconnect);
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
    public <Q, I, U> Registration subscriptionQuery(SubscriptionQueryMessage<Q, I, U> query, UpdateHandler<I, U> updateHandler) {
        String subscriptionId = query.getIdentifier();
        logger.debug("subscriptionQuery request with subscriptionId " + subscriptionId);
        this.query(query).thenAccept( response -> {
            axonHubQueryBus.publish(subscriptionQuerySerializer.subscriptionRequest(query));
            updateHandler.onInitialResult(response.getPayload());
            this.updateDispatcher.register(subscriptionId, updateHandler);
        }).exceptionally(throwable -> {
            updateHandler.onCompletedExceptionally(throwable);
            return null;
        });

        return () -> {
            logger.debug("Unsubscribe request for subscriptionId " + subscriptionId);
            this.axonHubQueryBus.publish(subscriptionQuerySerializer.subscriptionCancel(subscriptionId));
            this.updateDispatcher.unregister(subscriptionId);
            return true;
        };
    }
}
