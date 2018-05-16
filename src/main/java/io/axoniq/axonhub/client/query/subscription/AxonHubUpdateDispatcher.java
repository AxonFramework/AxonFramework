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

import io.axoniq.axonhub.QueryUpdate;
import io.axoniq.axonhub.QueryUpdateComplete;
import io.axoniq.axonhub.QueryUpdateCompleteExceptionally;
import io.axoniq.axonhub.client.AxonHubConfiguration;
import io.axoniq.axonhub.client.AxonHubException;
import io.axoniq.axonhub.client.Publisher;
import io.axoniq.axonhub.grpc.QueryProviderInbound;
import io.axoniq.axonhub.grpc.QueryProviderOutbound;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.UpdateHandler;
import org.axonframework.serialization.Serializer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Sara Pellegrini on 11/05/2018.
 * sara.pellegrini@gmail.com
 */
public class AxonHubUpdateDispatcher {

    private final Map<String, UpdateHandler> updateHandlers = new ConcurrentHashMap<>();

    private final Publisher<QueryProviderOutbound> publisher;

    private final SubscriptionQuerySerializer messageMapping;

    public AxonHubUpdateDispatcher(Publisher<QueryProviderOutbound> publisher,
                                   Serializer messageSerializer, Serializer genericSerializer,
                                   AxonHubConfiguration configuration) {
        this(publisher, new SubscriptionQuerySerializer(configuration, messageSerializer, genericSerializer));
    }

    public AxonHubUpdateDispatcher(
            Publisher<QueryProviderOutbound> publisher,
            SubscriptionQuerySerializer messageMapping) {
        this.publisher = publisher;
        this.messageMapping = messageMapping;
    }

    public void register(String subscriptionId, UpdateHandler updateHandler){
        this.updateHandlers.put(subscriptionId, updateHandler);
    }

    public void unregister(String subscriptionId){
        this.updateHandlers.remove(subscriptionId);
    }


    public void onDisconnect(){
        AxonHubException cause = new AxonHubException("Client disconnected form AxonHub.");
        updateHandlers.values().forEach(updateHandler -> updateHandler.onCompletedExceptionally(cause));
    }

    public void onUpdate(QueryProviderInbound queryProviderInbound){
        QueryUpdate update = queryProviderInbound.getQueryUpdate();
        SubscriptionQueryUpdateMessage<Object> message = messageMapping.subscriptionQueryUpdateMessage(update);
        getUpdateHandlerFor(update.getSubscriptionIdentifier()).onUpdate(message.getPayload());
    }

    public void onUpdateComplete(QueryProviderInbound queryProviderInbound) {
        QueryUpdateComplete complete = queryProviderInbound.getQueryUpdateComplete();
        String subscriptionId = complete.getSubscriptionIdentifier();
        getUpdateHandlerFor(subscriptionId).onCompleted();
        publisher.publish(messageMapping.subscriptionCancel(subscriptionId));
    }

    public void onUpdateCompleteExceptionally(QueryProviderInbound queryProviderInbound) {
        QueryUpdateCompleteExceptionally exceptionally = queryProviderInbound.getQueryUpdateCompleteExceptionally();
        String subscriptionId = exceptionally.getSubscriptionIdentifier();
        AxonHubException cause = new AxonHubException(exceptionally.getErrorCode(), exceptionally.getMessage());
        getUpdateHandlerFor(subscriptionId).onCompletedExceptionally(cause);
        publisher.publish(messageMapping.subscriptionCancel(subscriptionId));
    }

    private UpdateHandler getUpdateHandlerFor(String subscriptionId){
        return updateHandlers.getOrDefault(subscriptionId, new MissingUpdateHandler(subscriptionId));
    }
}
