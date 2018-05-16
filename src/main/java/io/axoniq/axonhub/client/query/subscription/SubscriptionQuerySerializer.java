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
import io.axoniq.axonhub.client.AxonHubConfiguration;
import io.axoniq.axonhub.grpc.QueryProviderOutbound;
import io.axoniq.platform.grpc.PlatformInboundInstruction;
import io.axoniq.platform.grpc.PlatformOutboundInstruction;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.serialization.Serializer;

/**
 * Created by Sara Pellegrini on 14/05/2018.
 * sara.pellegrini@gmail.com
 */
public class SubscriptionQuerySerializer {

    private final AxonHubConfiguration configuration;

    private final Serializer messageSerializer;

    private final Serializer genericSerializer;

    public SubscriptionQuerySerializer(AxonHubConfiguration configuration,
                                       Serializer messageSerializer,
                                       Serializer genericSerializer) {
        this.configuration = configuration;
        this.messageSerializer = messageSerializer;
        this.genericSerializer = genericSerializer;
    }

    public QueryProviderOutbound subscriptionRequest(SubscriptionQueryMessage message) {
        return new SubscriptionRequest(configuration, messageSerializer, genericSerializer).apply(message);
    }

    public QueryProviderOutbound subscriptionCancel(String subscriptionId) {
        return new SubscriptionCancel(configuration).apply(subscriptionId);
    }

    PlatformInboundInstruction serializeUpdate(SubscriptionQueryUpdateMessage<?> update, String subscriptionId) {
        return new Update(configuration, messageSerializer).apply(update, subscriptionId);
    }

    PlatformInboundInstruction serializeComplete(String subscriptionId) {
        return new UpdateComplete(configuration).apply(subscriptionId);
    }

    PlatformInboundInstruction serializeCompleteExceptionally(String subscriptionId, Throwable cause) {
        return new UpdateCompleteExceptionally(configuration).apply(subscriptionId, cause);
    }

    SubscriptionQueryMessage subscriptionQueryMessage(PlatformOutboundInstruction instruction){
        return new GrpcBackedSubscriptionQueryMessage(instruction, messageSerializer);
    }

    <U> SubscriptionQueryUpdateMessage<U> subscriptionQueryUpdateMessage(QueryUpdate queryUpdate) {
        return new GrpcBackedQueryUpdateMessage<>(queryUpdate, messageSerializer);
    }






}
