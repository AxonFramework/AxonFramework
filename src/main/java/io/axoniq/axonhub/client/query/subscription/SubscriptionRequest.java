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

import io.axoniq.axonhub.SubscriptionQueryRequest;
import io.axoniq.axonhub.SubscriptionQueryRequest.Builder;
import io.axoniq.axonhub.client.AxonHubConfiguration;
import io.axoniq.axonhub.client.util.GrpcMetaDataConverter;
import io.axoniq.axonhub.client.util.GrpcMetadataSerializer;
import io.axoniq.axonhub.client.util.GrpcObjectSerializer;
import io.axoniq.axonhub.grpc.QueryProviderOutbound;
import io.axoniq.platform.MetaDataValue;
import org.axonframework.messaging.MetaData;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.serialization.MessageSerializer;
import org.axonframework.serialization.Serializer;

import java.util.Map;
import java.util.function.Function;

/**
 * Created by Sara Pellegrini on 11/05/2018.
 * sara.pellegrini@gmail.com
 */
public class SubscriptionRequest implements Function<SubscriptionQueryMessage, QueryProviderOutbound> {

    private final AxonHubConfiguration conf;

    private final Function<Object, io.axoniq.platform.SerializedObject> payloadSerializer;

    private final Function<Object, io.axoniq.platform.SerializedObject> responseTypeSerializer;

    private final Function<MetaData, Map<String, MetaDataValue>> metadataSerializer;

    public SubscriptionRequest(AxonHubConfiguration configuration,
                               Serializer messageSerializer,
                               Serializer genericSerializer) {
        this(configuration,
             new GrpcObjectSerializer(new MessageSerializer(messageSerializer)),
             new GrpcObjectSerializer(genericSerializer),
             new GrpcMetadataSerializer(new GrpcMetaDataConverter(messageSerializer)));
    }

    public SubscriptionRequest(AxonHubConfiguration configuration,
                               Function<Object, io.axoniq.platform.SerializedObject> payloadSerializer,
                               Function<Object, io.axoniq.platform.SerializedObject> responseTypeSerializer,
                               Function<MetaData, Map<String, MetaDataValue>> metadataSerializer) {
        this.conf = configuration;
        this.payloadSerializer = payloadSerializer;
        this.responseTypeSerializer = responseTypeSerializer;
        this.metadataSerializer = metadataSerializer;
    }

    @Override
    public QueryProviderOutbound apply(SubscriptionQueryMessage message) {
        Builder builder = SubscriptionQueryRequest.newBuilder()
                                                  .setTimestamp(System.currentTimeMillis())
                                                  .setMessageIdentifier(message.getIdentifier())
                                                  .setQuery(message.getQueryName())
                                                  .setClientId(conf.getClientName())
                                                  .setComponentName(conf.getComponentName())
                                                  .setPayload(payloadSerializer.apply(message.getPayload()))
                                                  .setResponseType(responseTypeSerializer.apply(message.getResponseType()))
                                                  .setUpdateResponseType(responseTypeSerializer.apply(message.getUpdateResponseType()))
                                                  .putAllMetaData(metadataSerializer.apply(message.getMetaData()));
        if (conf.getContext() != null) builder.setContext(conf.getContext());
        return QueryProviderOutbound.newBuilder().setSubscriptionQueryRequest(builder.build()).build();
    }
}
