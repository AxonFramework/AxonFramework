/*
 * Copyright (c) 2010-2019. Axon Framework
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

package org.axonframework.axonserver.connector.query.subscription;

import io.axoniq.axonserver.grpc.query.QueryProviderInbound;
import io.axoniq.axonserver.grpc.query.QueryProviderOutbound;
import io.axoniq.axonserver.grpc.query.SubscriptionQuery;
import io.axoniq.axonserver.grpc.query.SubscriptionQueryRequest;
import org.axonframework.axonserver.connector.Publisher;
import org.axonframework.common.Registration;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SubscriptionQueryBackpressure;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A listener for Subscription Query requests and response, used by the
 * {@link org.axonframework.axonserver.connector.query.AxonServerQueryBus} to support the
 * {@link org.axonframework.axonserver.connector.query.AxonServerQueryBus#subscriptionQuery(SubscriptionQueryMessage,
 * SubscriptionQueryBackpressure, int)}
 * functionality.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */
public class SubscriptionQueryRequestTarget {

    private final Logger logger = LoggerFactory.getLogger(SubscriptionQueryRequestTarget.class);

    private final QueryBus localSegment;
    private final Publisher<QueryProviderOutbound> publisher;
    private final SubscriptionMessageSerializer serializer;
    private final Map<String, SubscriptionQueryResult<QueryResponseMessage<Object>, SubscriptionQueryUpdateMessage<Object>>> subscriptions;

    /**
     * Instantiate a {@link SubscriptionQueryRequestTarget} used to receive Subscription Query requests and response.
     *
     * @param localSegment a {@link QueryBus} used to subscribe incoming Subscription Query Requests to the local
     *                     instance
     * @param publisher    a {@link Publisher} of type {@link QueryProviderOutbound} to send the initial result and the
     *                     subsequent updates on
     * @param serializer   a {@link SubscriptionMessageSerializer} used to convert the Axon Server gRPC based messages
     *                     into Axon Framework based Query messages and vice versa
     */
    public SubscriptionQueryRequestTarget(QueryBus localSegment,
                                          Publisher<QueryProviderOutbound> publisher,
                                          SubscriptionMessageSerializer serializer) {
        this.localSegment = localSegment;
        this.publisher = publisher;
        this.serializer = serializer;
        this.subscriptions = new ConcurrentHashMap<>();
    }

    /**
     * Decides, based on the wrapped {@link SubscriptionQueryRequest} returned from the given
     * {@link QueryProviderInbound}, what operation to perform. Can switch between subscribing in general, retrieving
     * the initial result and unsubscribing from future results.
     *
     * @param queryProviderInbound a {@link QueryProviderInbound} from which the {@link SubscriptionQueryRequest} will
     *                             be retrieved to perform a follow up operation with
     */
    public void onSubscriptionQueryRequest(QueryProviderInbound queryProviderInbound) {
        SubscriptionQueryRequest subscriptionQuery = queryProviderInbound.getSubscriptionQueryRequest();
        try {
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
        } catch (Exception e) {
            logger.warn("Error handling SubscriptionQueryRequest.", e);
        }
    }

    private void subscribe(SubscriptionQuery subscriptionQuery) {
        String subscriptionId = subscriptionQuery.getSubscriptionIdentifier();
        SubscriptionQueryResult<QueryResponseMessage<Object>, SubscriptionQueryUpdateMessage<Object>> result =
                localSegment.subscriptionQuery(serializer.deserialize(subscriptionQuery));

        Disposable disposable = result.updates().subscribe(
                u -> publisher.publish(serializer.serialize(u, subscriptionId)),
                e -> publisher.publish(serializer.serializeCompleteExceptionally(subscriptionId, e)),
                () -> publisher.publish(serializer.serializeComplete(subscriptionId))
        );

        Registration registration = () -> {
            disposable.dispose();
            return true;
        };

        subscriptions.computeIfAbsent(subscriptionId, id -> new DisposableResult<>(result, registration));
    }

    private void getInitialResult(SubscriptionQuery query) {
        String subscriptionId = query.getSubscriptionIdentifier();
        subscriptions.get(subscriptionId).initialResult().subscribe(
                i -> publisher.publish(serializer.serialize(i, subscriptionId)),
                e -> logger.debug("Error in initial result for subscription id: {}", subscriptionId)
        );
    }

    private void unsubscribe(SubscriptionQuery unsubscribe) {
        String subscriptionId = unsubscribe.getSubscriptionIdentifier();
        logger.debug("unsubscribe locally subscriptionId {}", subscriptionId);
        subscriptions.remove(subscriptionId).cancel();
    }

    /**
     * Cancels all the subscription query {@link Registration}s which are contained by this
     * {@link SubscriptionQueryRequestTarget}.
     */
    public void onApplicationDisconnected() {
        subscriptions.values().forEach(Registration::cancel);
        subscriptions.clear();
    }
}
