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

import io.axoniq.axonserver.grpc.query.SubscriptionQuery;
import org.axonframework.axonserver.connector.query.GrpcBackedQueryMessage;
import org.axonframework.axonserver.connector.util.GrpcSerializedObject;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.axonframework.serialization.LazyDeserializingObject;
import org.axonframework.serialization.Serializer;

import java.util.Map;

/**
 * Wrapper that allows clients to access a gRPC {@link SubscriptionQuery} message as a {@link
 * SubscriptionQueryMessage}.
 *
 * @param <Q> a generic specifying the type of the {@link SubscriptionQueryMessage}'s payload
 * @param <I> a generic specifying the type of the initial result of the {@link SubscriptionQueryResult}
 * @param <U> a generic specifying the type of the subsequent updates of the {@link SubscriptionQueryResult}
 * @author Sara Pellegrini
 * @since 4.0
 */
public class GrpcBackedSubscriptionQueryMessage<Q, I, U> implements SubscriptionQueryMessage<Q, I, U> {

    private final SubscriptionQuery subscriptionQuery;
    private final GrpcBackedQueryMessage<Q, I> grpcBackedQueryMessage;
    private final LazyDeserializingObject<ResponseType<U>> serializedUpdateResponseType;

    /**
     * Instantiate a {@link GrpcBackedSubscriptionQueryMessage} with the given {@code subscriptionQuery}, using the
     * provided {@code messageSerializer} to be able to retrieve the payload and {@link MetaData} from it. The {@code
     * serializer} is solely used to deserialize the response type of the update message.
     *
     * @param subscriptionQuery the {@link SubscriptionQuery} which is being wrapped as a {@link
     *                          SubscriptionQueryMessage}
     * @param messageSerializer the {@link Serializer} used to deserialize the payload and {@link MetaData} from the
     *                          given {@code queryRequest}
     * @param serializer        the {@link Serializer} used to deserialize the response type
     */
    public GrpcBackedSubscriptionQueryMessage(SubscriptionQuery subscriptionQuery,
                                              Serializer messageSerializer,
                                              Serializer serializer) {
        this(
                subscriptionQuery,
                new GrpcBackedQueryMessage<>(subscriptionQuery.getQueryRequest(), messageSerializer, serializer),
                new LazyDeserializingObject<>(
                        new GrpcSerializedObject(subscriptionQuery.getUpdateResponseType()), serializer
                )
        );
    }

    private GrpcBackedSubscriptionQueryMessage(SubscriptionQuery subscriptionQuery,
                                               GrpcBackedQueryMessage<Q, I> grpcBackedQueryMessage,
                                               LazyDeserializingObject<ResponseType<U>> serializedUpdateResponseType) {
        this.subscriptionQuery = subscriptionQuery;
        this.grpcBackedQueryMessage = grpcBackedQueryMessage;
        this.serializedUpdateResponseType = serializedUpdateResponseType;
    }

    @Override
    public ResponseType<U> getUpdateResponseType() {
        return serializedUpdateResponseType.getObject();
    }

    @Override
    public String getQueryName() {
        return grpcBackedQueryMessage.getQueryName();
    }

    @Override
    public ResponseType<I> getResponseType() {
        return grpcBackedQueryMessage.getResponseType();
    }

    @Override
    public String getIdentifier() {
        return subscriptionQuery.getSubscriptionIdentifier();
    }

    @Override
    public MetaData getMetaData() {
        return grpcBackedQueryMessage.getMetaData();
    }

    @Override
    public Q getPayload() {
        return grpcBackedQueryMessage.getPayload();
    }

    @Override
    public Class<Q> getPayloadType() {
        return grpcBackedQueryMessage.getPayloadType();
    }

    @Override
    public GrpcBackedSubscriptionQueryMessage<Q, I, U> withMetaData(Map<String, ?> metaData) {
        return new GrpcBackedSubscriptionQueryMessage<>(
                subscriptionQuery, grpcBackedQueryMessage.withMetaData(metaData), serializedUpdateResponseType
        );
    }

    @Override
    public GrpcBackedSubscriptionQueryMessage<Q, I, U> andMetaData(Map<String, ?> metaData) {
        return withMetaData(getMetaData().mergedWith(metaData));
    }
}
