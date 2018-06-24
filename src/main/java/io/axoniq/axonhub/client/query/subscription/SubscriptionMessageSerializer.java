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

import io.axoniq.axonhub.QueryRequest;
import io.axoniq.axonhub.QueryResponse;
import io.axoniq.axonhub.QueryUpdate;
import io.axoniq.axonhub.QueryUpdateComplete;
import io.axoniq.axonhub.QueryUpdateCompleteExceptionally;
import io.axoniq.axonhub.SubscriptionQuery;
import io.axoniq.axonhub.SubscriptionQueryResponse;
import io.axoniq.axonhub.client.AxonHubConfiguration;
import io.axoniq.axonhub.client.AxonHubException;
import io.axoniq.axonhub.client.ErrorCode;
import io.axoniq.axonhub.client.query.GrpcBackedResponseMessage;
import io.axoniq.axonhub.client.query.RemoteQueryException;
import io.axoniq.axonhub.client.util.ExceptionSerializer;
import io.axoniq.axonhub.client.util.GrpcMetaDataConverter;
import io.axoniq.axonhub.client.util.GrpcMetadataSerializer;
import io.axoniq.axonhub.client.util.GrpcObjectSerializer;
import io.axoniq.axonhub.grpc.QueryProviderOutbound;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.serialization.MessageSerializer;
import org.axonframework.serialization.Serializer;

import static io.axoniq.axonhub.grpc.QueryProviderOutbound.newBuilder;

/**
 * Serializer for Subscription Query Messages
 *
 * @author Sara Pellegrini
 */

public class SubscriptionMessageSerializer {

    private final AxonHubConfiguration conf;

    private final Serializer messageSerializer;

    private final Serializer genericSerializer;

    private final GrpcMetadataSerializer metadataSerializer;

    private final GrpcObjectSerializer payloadSerializer;

    private final GrpcObjectSerializer responseTypeSerializer;

    public SubscriptionMessageSerializer(AxonHubConfiguration conf,
                                         Serializer messageSerializer,
                                         Serializer genericSerializer) {
        this.conf = conf;
        this.messageSerializer = messageSerializer;
        this.genericSerializer = genericSerializer;
        this.metadataSerializer = new GrpcMetadataSerializer(new GrpcMetaDataConverter(messageSerializer));
        this.payloadSerializer  = new GrpcObjectSerializer(new MessageSerializer(messageSerializer));
        this.responseTypeSerializer = new GrpcObjectSerializer(genericSerializer);
    }


    QueryProviderOutbound serialize(QueryResponseMessage initialResult, String subscriptionId) {
        QueryResponse response = QueryResponse.newBuilder()
                                              .setPayload(payloadSerializer.apply(initialResult.getPayload()))
                                              .putAllMetaData(metadataSerializer.apply(initialResult.getMetaData()))
                                              .setMessageIdentifier(initialResult.getIdentifier())
                                              .setRequestIdentifier(subscriptionId)
                                              .build();
        return newBuilder().setSubscriptionQueryResponse(
                SubscriptionQueryResponse.newBuilder()
                                         .setSubscriptionIdentifier(subscriptionId)
                                         .setInitialResponse(response)).build();
    }

    <I> QueryResponseMessage<I> deserialize(QueryResponse queryResponse){
        return new GrpcBackedResponseMessage<>(queryResponse, messageSerializer);
    }

    QueryProviderOutbound serialize(SubscriptionQueryUpdateMessage<?> update, String subscriptionId) {
        QueryUpdate.Builder builder = QueryUpdate.newBuilder()
                                                 .setPayload(payloadSerializer.apply(update.getPayload()))
                                                 .putAllMetaData(metadataSerializer.apply(update.getMetaData()))
                                                 .setMessageIdentifier(update.getIdentifier())
                                                 .setClientName(conf.getClientName())
                                                 .setComponentName(conf.getComponentName());

        return newBuilder().setSubscriptionQueryResponse(
                SubscriptionQueryResponse.newBuilder()
                                         .setSubscriptionIdentifier(subscriptionId)
                                         .setUpdate(builder.build())).build();
    }

    <U> SubscriptionQueryUpdateMessage<U> deserialize(QueryUpdate queryUpdate){
        return new GrpcBackedQueryUpdateMessage<>(queryUpdate, messageSerializer);
    }

    QueryProviderOutbound serializeCompleteExceptionally(String subscriptionId, Throwable cause) {
        QueryUpdateCompleteExceptionally.Builder builder = QueryUpdateCompleteExceptionally
                .newBuilder()
                .setMessage(ExceptionSerializer.serialize(conf.getClientName(), cause))
                .setErrorCode(ErrorCode.resolve(cause).errorCode())
                .setClientName(conf.getClientName())
                .setComponentName(conf.getComponentName());
        return newBuilder().setSubscriptionQueryResponse(
                SubscriptionQueryResponse.newBuilder()
                                         .setSubscriptionIdentifier(subscriptionId)
                                         .setCompleteExceptionally(builder.build())).build();
    }


    SubscriptionQuery serialize(SubscriptionQueryMessage message) {
        QueryRequest queryRequest = QueryRequest.newBuilder().setTimestamp(System.currentTimeMillis())
                                                .setMessageIdentifier(message.getIdentifier())
                                                .setQuery(message.getQueryName())
                                                .setClientId(conf.getClientName())
                                                .setComponentName(conf.getComponentName())
                                                .setPayload(payloadSerializer.apply(message.getPayload()))
                                                .setResponseType(responseTypeSerializer.apply(message.getResponseType()))
                                                .putAllMetaData(metadataSerializer.apply(message.getMetaData())).build();

        SubscriptionQuery.Builder builder = SubscriptionQuery.newBuilder()
                                                             .setNumberOfPermits(conf.getInitialNrOfPermits())
                                                             .setUpdateResponseType(responseTypeSerializer.apply(message.getUpdateResponseType()))
                                                             .setQueryRequest(queryRequest);
        return builder.build();
    }

    <Q, I, U> SubscriptionQueryMessage<Q, I, U> deserialize(SubscriptionQuery query) {
        return new GrpcBackedSubscriptionQueryMessage<>(query, messageSerializer, genericSerializer);
    }


    QueryProviderOutbound serializeComplete(String subscriptionId) {
        QueryUpdateComplete.Builder builder = QueryUpdateComplete.newBuilder()
                                                                 .setClientName(conf.getClientName())
                                                                 .setComponentName(conf.getComponentName());
        return newBuilder().setSubscriptionQueryResponse(
                SubscriptionQueryResponse.newBuilder()
                                         .setSubscriptionIdentifier(subscriptionId)
                                         .setComplete(builder.build())).build();
    }


}
