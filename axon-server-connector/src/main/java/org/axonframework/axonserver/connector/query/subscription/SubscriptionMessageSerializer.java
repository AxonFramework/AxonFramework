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

package org.axonframework.axonserver.connector.query.subscription;

import io.axoniq.axonserver.grpc.query.QueryRequest;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import io.axoniq.axonserver.grpc.query.QueryUpdate;
import io.axoniq.axonserver.grpc.query.QueryUpdateComplete;
import io.axoniq.axonserver.grpc.query.QueryUpdateCompleteExceptionally;
import io.axoniq.axonserver.grpc.query.SubscriptionQuery;
import io.axoniq.axonserver.grpc.query.SubscriptionQueryResponse;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.query.GrpcBackedResponseMessage;
import org.axonframework.axonserver.connector.util.ExceptionSerializer;
import org.axonframework.axonserver.connector.util.GrpcMetaDataConverter;
import org.axonframework.axonserver.connector.util.GrpcMetadataSerializer;
import org.axonframework.axonserver.connector.util.GrpcObjectSerializer;
import org.axonframework.axonserver.connector.util.GrpcPayloadSerializer;
import io.axoniq.axonserver.grpc.query.QueryProviderOutbound;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.serialization.Serializer;

import static io.axoniq.axonserver.grpc.query.QueryProviderOutbound.newBuilder;

/**
 * Serializer for Subscription Query Messages.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */

public class SubscriptionMessageSerializer {

    private final AxonServerConfiguration conf;

    private final Serializer messageSerializer;

    private final Serializer genericSerializer;

    private final GrpcMetadataSerializer metadataSerializer;

    private final GrpcPayloadSerializer payloadSerializer;

    private final GrpcObjectSerializer<Object> responseTypeSerializer;

    public SubscriptionMessageSerializer(AxonServerConfiguration conf,
                                         Serializer messageSerializer,
                                         Serializer genericSerializer) {
        this.conf = conf;
        this.messageSerializer = messageSerializer;
        this.genericSerializer = genericSerializer;
        this.metadataSerializer = new GrpcMetadataSerializer(new GrpcMetaDataConverter(messageSerializer));
        this.payloadSerializer  = new GrpcPayloadSerializer(messageSerializer);
        this.responseTypeSerializer = new GrpcObjectSerializer<>(genericSerializer);
    }


    QueryProviderOutbound serialize(QueryResponseMessage initialResult, String subscriptionId) {
        QueryResponse response = QueryResponse.newBuilder()
                                              .setPayload(payloadSerializer.apply(initialResult))
                                              .putAllMetaData(metadataSerializer.apply(initialResult.getMetaData()))
                                              .setMessageIdentifier(initialResult.getIdentifier())
                                              .setRequestIdentifier(subscriptionId)
                                              .build();
        return newBuilder().setSubscriptionQueryResponse(
                SubscriptionQueryResponse.newBuilder()
                                         .setSubscriptionIdentifier(subscriptionId)
                                         .setInitialResult(response)).build();
    }

    <I> QueryResponseMessage<I> deserialize(QueryResponse queryResponse){
        return new GrpcBackedResponseMessage<>(queryResponse, messageSerializer);
    }

    QueryProviderOutbound serialize(SubscriptionQueryUpdateMessage<?> update, String subscriptionId) {
        QueryUpdate.Builder builder = QueryUpdate.newBuilder()
                                                 .setPayload(payloadSerializer.apply(update))
                                                 .putAllMetaData(metadataSerializer.apply(update.getMetaData()))
                                                 .setMessageIdentifier(update.getIdentifier())
                                                 .setClientId(conf.getClientId())
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
                .setErrorMessage(ExceptionSerializer.serialize(conf.getClientId(), cause))
                .setErrorCode(ErrorCode.QUERY_EXECUTION_ERROR.errorCode())
                .setClientId(conf.getClientId())
                .setComponentName(conf.getComponentName());
        return newBuilder().setSubscriptionQueryResponse(
                SubscriptionQueryResponse.newBuilder()
                                         .setSubscriptionIdentifier(subscriptionId)
                                         .setCompleteExceptionally(builder.build())).build();
    }


    public SubscriptionQuery serialize(SubscriptionQueryMessage message) {
        QueryRequest queryRequest = QueryRequest.newBuilder().setTimestamp(System.currentTimeMillis())
                                                .setMessageIdentifier(message.getIdentifier())
                                                .setQuery(message.getQueryName())
                                                .setClientId(conf.getClientId())
                                                .setComponentName(conf.getComponentName())
                                                .setPayload(payloadSerializer.apply(message))
                                                .setResponseType(responseTypeSerializer.apply(message.getResponseType()))
                                                .putAllMetaData(metadataSerializer.apply(message.getMetaData())).build();

        SubscriptionQuery.Builder builder = SubscriptionQuery.newBuilder()
                                                             .setSubscriptionIdentifier(message.getIdentifier())
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
                                                                 .setClientId(conf.getClientId())
                                                                 .setComponentName(conf.getComponentName());
        return newBuilder().setSubscriptionQueryResponse(
                SubscriptionQueryResponse.newBuilder()
                                         .setSubscriptionIdentifier(subscriptionId)
                                         .setComplete(builder.build())).build();
    }


}
