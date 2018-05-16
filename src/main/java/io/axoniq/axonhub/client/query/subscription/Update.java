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
import io.axoniq.axonhub.client.util.GrpcMetaDataConverter;
import io.axoniq.axonhub.client.util.GrpcMetadataSerializer;
import io.axoniq.axonhub.client.util.GrpcObjectSerializer;
import io.axoniq.platform.MetaDataValue;
import io.axoniq.platform.SerializedObject;
import io.axoniq.platform.grpc.PlatformInboundInstruction;
import org.axonframework.messaging.MetaData;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.serialization.MessageSerializer;
import org.axonframework.serialization.Serializer;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.axoniq.platform.grpc.PlatformInboundInstruction.newBuilder;

/**
 * Created by Sara Pellegrini on 11/05/2018.
 * sara.pellegrini@gmail.com
 */
public class Update implements BiFunction<SubscriptionQueryUpdateMessage<?>, String, PlatformInboundInstruction> {

    private final AxonHubConfiguration conf;

    private final Function<Object, SerializedObject> payloadSerializer;

    private final Function<MetaData, Map<String, MetaDataValue>> metadataSerializer;

    public Update(AxonHubConfiguration configuration, Serializer messageSerializer) {
        this(configuration,
             new GrpcObjectSerializer(new MessageSerializer(messageSerializer)),
             new GrpcMetadataSerializer(new GrpcMetaDataConverter(messageSerializer)));
    }

    public Update(AxonHubConfiguration configuration,
                  Function<Object, SerializedObject> payloadSerializer,
                  Function<MetaData, Map<String, MetaDataValue>> metadataSerializer) {
        this.conf = configuration;
        this.payloadSerializer = payloadSerializer;
        this.metadataSerializer = metadataSerializer;
    }

    @Override
    public PlatformInboundInstruction apply(SubscriptionQueryUpdateMessage<?> update, String subscriptionId) {
        QueryUpdate.Builder builder = QueryUpdate.newBuilder()
                                                 .setPayload(payloadSerializer.apply(update.getPayload()))
                                                 .putAllMetaData(metadataSerializer.apply(update.getMetaData()))
                                                 .setMessageIdentifier(update.getIdentifier())
                                                 .setSubscriptionIdentifier(subscriptionId)
                                                 .setClientName(conf.getClientName())
                                                 .setComponentName(conf.getComponentName());
        if (conf.getContext() != null) builder.setContext(conf.getContext());
        return newBuilder().setQueryUpdate(builder.build()).build();
    }
}
