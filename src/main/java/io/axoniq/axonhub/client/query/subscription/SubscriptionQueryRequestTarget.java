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
import io.axoniq.axonhub.client.Publisher;
import io.axoniq.axonhub.grpc.QueryProviderInbound;
import io.axoniq.axonhub.grpc.QueryProviderOutbound;
import org.axonframework.common.Registration;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Sara Pellegrini on 14/06/2018.
 * sara.pellegrini@gmail.com
 */
class SubscriptionQueryRequestTarget {

    private final Logger logger = LoggerFactory.getLogger(SubscriptionQueryRequestTarget.class);

    private final QueryBus localSegment;

    private final Publisher<QueryProviderOutbound> publisher;

    private final SubscriptionMessageSerializer serializer;

    private final Map<String, SubscriptionQueryResult<QueryResponseMessage<Object>, SubscriptionQueryUpdateMessage<Object>>> subscriptions = new ConcurrentHashMap<>();

    SubscriptionQueryRequestTarget(QueryBus localSegment,
                                          Publisher<QueryProviderOutbound> publisher,
                                          SubscriptionMessageSerializer serializer) {
        this.localSegment = localSegment;
        this.publisher = publisher;
        this.serializer = serializer;
    }


    void onSubscriptionQueryRequest(QueryProviderInbound inbound) {
        SubscriptionQueryRequest subscriptionQuery = inbound.getSubscriptionQueryRequest();
        switch (subscriptionQuery.getRequestCase()) {
            case SUBSCRIBE:
                subscribe(subscriptionQuery.getSubscribe());
                break;
            case GET_INITIAL_RESULT:
                getInitialResult(subscriptionQuery.getGetInitialResult());
                break;
            case UNSUBSCRIBE:
                unsubscribe(subscriptionQuery.getUnsubscribe());
                break;
        }
    }

    private void subscribe(SubscriptionQuery query) {
        String subscriptionId = query.getQueryRequest().getMessageIdentifier();
        subscriptions
                .computeIfAbsent(subscriptionId, id -> localSegment.subscriptionQuery(serializer.deserialize(query)))
                .updates()
                .subscribe(
                        u -> publisher.publish(serializer.serialize(u, subscriptionId)),
                        e -> publisher.publish(serializer.serializeCompleteExceptionally(subscriptionId, e)),
                        () -> publisher.publish(serializer.serializeComplete(subscriptionId)));
    }


    private void getInitialResult(SubscriptionQuery query) {
        String subscriptionId = query.getQueryRequest().getMessageIdentifier();
        subscriptions.get(subscriptionId).initialResult().subscribe(
                i -> publisher.publish(serializer.serialize(i, subscriptionId)),
                e -> logger.debug("Error in initial result for subscription id: {}", subscriptionId)
        );
    }

    private void unsubscribe(SubscriptionQuery unsubscribe) {
        String subscriptionId = unsubscribe.getQueryRequest().getMessageIdentifier();
        logger.debug("unsubscribe locally subscriptionId " + subscriptionId);
        subscriptions.remove(subscriptionId).cancel();
    }

    void onApplicationDisconnected() {
        subscriptions.values().forEach(Registration::cancel);
        subscriptions.clear();
    }


}
