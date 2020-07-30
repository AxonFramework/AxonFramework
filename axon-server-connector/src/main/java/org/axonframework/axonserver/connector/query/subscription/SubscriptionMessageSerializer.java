/*
 * Copyright (c) 2010-2020. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.axonserver.connector.query.subscription;

import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.query.QueryProviderOutbound;
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
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.serialization.Serializer;

import static io.axoniq.axonserver.grpc.query.QueryProviderOutbound.newBuilder;

/**
 * Converter between Axon Framework {@link SubscriptionQueryMessage}, the initial {@link QueryResponseMessage} and the
 * subsequent {@link SubscriptionQueryUpdateMessage}'s and Axon Server gRPC {@link SubscriptionQuery} and {@link
 * SubscriptionQueryResponse}. The latter is serviced by providing a {@link QueryProviderOutbound} wrapping the
 * SubscriptionQueryResponse.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */
public class SubscriptionMessageSerializer {

    private final AxonServerConfiguration configuration;
    private final Serializer messageSerializer;
    private final Serializer serializer;

    private final GrpcPayloadSerializer payloadSerializer;
    private final GrpcMetadataSerializer metadataSerializer;
    private final GrpcObjectSerializer<Object> responseTypeSerializer;

    /**
     * Instantiate a serializer used to convert Axon {@link SubscriptionQueryMessage}s, the initial {@link
     * QueryResponseMessage} and the subsequent {@link SubscriptionQueryUpdateMessage}s into Axon Server gRPC messages
     * and vice versa.
     *
     * @param messageSerializer a {@link Serializer} used to de-/serialize an Axon Server gRPC message into {@link
     *                          SubscriptionQueryMessage}s, {@link QueryResponseMessage}s and {@link
     *                          SubscriptionQueryUpdateMessage}s, and vice versa
     * @param serializer        a {@link Serializer} used to create a dedicated converter for a {@link QueryMessage}
     *                          {@link org.axonframework.messaging.responsetypes.ResponseType}
     * @param configuration     an {@link AxonServerConfiguration} used to set the configurable component id and name in
     *                          the messages
     */
    public SubscriptionMessageSerializer(Serializer messageSerializer,
                                         Serializer serializer,
                                         AxonServerConfiguration configuration) {
        this.configuration = configuration;
        this.messageSerializer = messageSerializer;
        this.serializer = serializer;

        this.payloadSerializer = new GrpcPayloadSerializer(messageSerializer);
        this.metadataSerializer = new GrpcMetadataSerializer(new GrpcMetaDataConverter(messageSerializer));
        this.responseTypeSerializer = new GrpcObjectSerializer<>(serializer);
    }

    /**
     * Serializes the given {@code subscriptionQueryMessage} into a {@link QueryRequest}.
     *
     * @param subscriptionQueryMessage the {@link SubscriptionQueryMessage} to serialize into a {@link QueryRequest}
     * @return a {@link QueryRequest} based on the given {@code subscriptionQueryMessage}
     */
    public QueryRequest serializeQuery(SubscriptionQueryMessage subscriptionQueryMessage) {
        return QueryRequest.newBuilder()
                           .setTimestamp(System.currentTimeMillis())
                           .setMessageIdentifier(subscriptionQueryMessage.getIdentifier())
                           .setQuery(subscriptionQueryMessage.getQueryName())
                           .setClientId(configuration.getClientId())
                           .setComponentName(configuration.getComponentName())
                           .setPayload(payloadSerializer.apply(subscriptionQueryMessage))
                           .setResponseType(responseTypeSerializer.apply(subscriptionQueryMessage.getResponseType()))
                           .putAllMetaData(metadataSerializer.apply(subscriptionQueryMessage.getMetaData()))
                           .build();
    }

    /**
     * Convert a {@link SubscriptionQueryMessage} into a {@link SubscriptionQuery}.
     *
     * @param subscriptionQueryMessage the {@link SubscriptionQueryMessage} to convert into a {@link SubscriptionQuery}
     * @return a {@link SubscriptionQuery} based on the provided {@code subscriptionQueryMessage}
     */
    public SubscriptionQuery serialize(SubscriptionQueryMessage<?, ?, ?> subscriptionQueryMessage) {
        QueryRequest queryRequest =
                QueryRequest.newBuilder()
                            .setTimestamp(System.currentTimeMillis())
                            .setMessageIdentifier(subscriptionQueryMessage.getIdentifier())
                            .setQuery(subscriptionQueryMessage.getQueryName())
                            .setClientId(configuration.getClientId())
                            .setComponentName(configuration.getComponentName())
                            .setPayload(payloadSerializer.apply(subscriptionQueryMessage))
                            .setResponseType(responseTypeSerializer.apply(subscriptionQueryMessage.getResponseType()))
                            .putAllMetaData(metadataSerializer.apply(subscriptionQueryMessage.getMetaData()))
                            .build();

        return SubscriptionQuery.newBuilder()
                                .setSubscriptionIdentifier(subscriptionQueryMessage.getIdentifier())
                                .setNumberOfPermits(configuration.getInitialNrOfPermits())
                                .setUpdateResponseType(
                                        responseTypeSerializer.apply(subscriptionQueryMessage.getUpdateResponseType())
                                )
                                .setQueryRequest(queryRequest).build();
    }

    /**
     * Serializes the given {@code subscriptionQueryMessage} into a {@link SerializedObject}.
     *
     * @param subscriptionQueryMessage the {@link SubscriptionQueryMessage} who's {@link SubscriptionQueryMessage#getUpdateResponseType()}
     *                                 to serialize into a {@link SerializedObject}
     * @return a {@link SerializedObject} based on the given {@code subscriptionQueryMessage} its {@link
     * SubscriptionQueryMessage#getUpdateResponseType()}
     */
    public SerializedObject serializeUpdateType(SubscriptionQueryMessage<?, ?, ?> subscriptionQueryMessage) {
        return responseTypeSerializer.apply(subscriptionQueryMessage.getUpdateResponseType());
    }

    /**
     * Deserializes the given {@code subscriptionQuery} into a {@link SubscriptionQueryMessage}.
     *
     * @param subscriptionQuery the {@link SubscriptionQuery} to deserialize into a {@link SubscriptionQueryMessage}
     * @param <Q>               the query type of the {@link SubscriptionQueryMessage} to return
     * @param <I>               the initial result type of the {@link SubscriptionQueryMessage} to return
     * @param <U>               the update type of the {@link SubscriptionQueryMessage} to return
     * @return the {@link SubscriptionQueryMessage} based on the given {@code subscriptionQuery}
     */
    public <Q, I, U> SubscriptionQueryMessage<Q, I, U> deserialize(SubscriptionQuery subscriptionQuery) {
        return new GrpcBackedSubscriptionQueryMessage<>(subscriptionQuery, messageSerializer, serializer);
    }

    /**
     * @deprecated in through use of the <a href="https://github.com/AxonIQ/axonserver-connector-java">AxonServer java
     * connector</a>
     */
    @Deprecated
    QueryProviderOutbound serialize(QueryResponseMessage initialResult, String subscriptionId) {
        QueryResponse queryResponse =
                QueryResponse.newBuilder()
                             .setPayload(payloadSerializer.apply(initialResult))
                             .putAllMetaData(metadataSerializer.apply(initialResult.getMetaData()))
                             .setMessageIdentifier(initialResult.getIdentifier())
                             .setRequestIdentifier(subscriptionId)
                             .build();

        return newBuilder().setSubscriptionQueryResponse(
                SubscriptionQueryResponse.newBuilder()
                                         .setSubscriptionIdentifier(subscriptionId)
                                         .setInitialResult(queryResponse)
        ).build();
    }

    /**
     * Deserializes the given {@code queryResponse} into a {@link QueryResponseMessage}.
     *
     * @param queryResponse the {@link QueryResponse} to deserialize into a {@link QueryResponseMessage}
     * @param <I>           the response type of the {@link QueryResponseMessage} to return
     * @return a {@link QueryResponseMessage} based on the given {@link QueryResponse}
     */
    public <I> QueryResponseMessage<I> deserialize(QueryResponse queryResponse) {
        return new GrpcBackedResponseMessage<>(queryResponse, messageSerializer);
    }

    /**
     * Serializes the given {@code subscriptionQueryUpdateMessage} into a {@link QueryUpdate}.
     *
     * @param subscriptionQueryUpdateMessage the {@link SubscriptionQueryUpdateMessage} to serialize into a {@link
     *                                       QueryUpdate}
     * @return the {@link QueryUpdate} based on the given {@link SubscriptionQueryUpdateMessage}
     */
    public QueryUpdate serialize(SubscriptionQueryUpdateMessage<?> subscriptionQueryUpdateMessage) {
        return QueryUpdate.newBuilder()
                          .setPayload(payloadSerializer.apply(subscriptionQueryUpdateMessage))
                          .putAllMetaData(metadataSerializer.apply(subscriptionQueryUpdateMessage.getMetaData()))
                          .setMessageIdentifier(subscriptionQueryUpdateMessage.getIdentifier())
                          .setClientId(configuration.getClientId())
                          .setComponentName(configuration.getComponentName())
                          .build();
    }

    /**
     * Deserializes the given {@code queryUpdate} into a {@link SubscriptionQueryUpdateMessage}.
     *
     * @param queryUpdate the {@link QueryUpdate} to deserialize into a {@link SubscriptionQueryUpdateMessage}
     * @param <U>         the update type of the {@link SubscriptionQueryUpdateMessage} to return
     * @return a {@link SubscriptionQueryUpdateMessage} based on the given {@link QueryUpdate}
     */
    public <U> SubscriptionQueryUpdateMessage<U> deserialize(QueryUpdate queryUpdate) {
        return new GrpcBackedQueryUpdateMessage<>(queryUpdate, messageSerializer);
    }

    /**
     * @deprecated in through use of the <a href="https://github.com/AxonIQ/axonserver-connector-java">AxonServer java
     * connector</a>
     */
    @Deprecated
    QueryProviderOutbound serializeComplete(String subscriptionId) {
        QueryUpdateComplete completedQueryUpdate =
                QueryUpdateComplete.newBuilder()
                                   .setClientId(configuration.getClientId())
                                   .setComponentName(configuration.getComponentName())
                                   .build();

        return newBuilder().setSubscriptionQueryResponse(
                SubscriptionQueryResponse.newBuilder()
                                         .setSubscriptionIdentifier(subscriptionId)
                                         .setComplete(completedQueryUpdate)
        ).build();
    }

    /**
     * @deprecated in through use of the <a href="https://github.com/AxonIQ/axonserver-connector-java">AxonServer java
     * connector</a>
     */
    @Deprecated
    QueryProviderOutbound serializeCompleteExceptionally(String subscriptionId, Throwable cause) {
        QueryUpdateCompleteExceptionally exceptionallyCompletedQueryUpdate =
                QueryUpdateCompleteExceptionally.newBuilder()
                                                .setErrorMessage(ExceptionSerializer.serialize(
                                                        configuration.getClientId(), cause
                                                ))
                                                .setErrorCode(ErrorCode.QUERY_EXECUTION_ERROR.errorCode())
                                                .setClientId(configuration.getClientId())
                                                .setComponentName(configuration.getComponentName())
                                                .build();

        return newBuilder().setSubscriptionQueryResponse(
                SubscriptionQueryResponse.newBuilder()
                                         .setSubscriptionIdentifier(subscriptionId)
                                         .setCompleteExceptionally(exceptionallyCompletedQueryUpdate)
        ).build();
    }
}
