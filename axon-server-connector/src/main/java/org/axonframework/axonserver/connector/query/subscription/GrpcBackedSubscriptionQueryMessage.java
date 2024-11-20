/*
 * Copyright (c) 2010-2024. Axon Framework
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
import jakarta.annotation.Nonnull;
import org.axonframework.axonserver.connector.query.GrpcBackedQueryMessage;
import org.axonframework.axonserver.connector.util.GrpcSerializedObject;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.axonframework.serialization.LazyDeserializingObject;
import org.axonframework.serialization.Serializer;

import java.util.Map;

/**
 * Wrapper that allows clients to access a gRPC {@link SubscriptionQuery} message as a
 * {@link SubscriptionQueryMessage}.
 *
 * @param <P> A generic specifying the type of the {@link SubscriptionQueryMessage SubscriptionQueryMessage's}
 *            {@link #getPayload() payload}.
 * @param <I> A generic specifying the type of the {@link #getResponseType() initial result} of the
 *            {@link SubscriptionQueryResult}.
 * @param <U> A generic specifying the type of the {@link #getUpdateResponseType() subsequent updates} of the
 *            {@link SubscriptionQueryResult}.
 * @author Sara Pellegrini
 * @since 4.0.0
 */
public class GrpcBackedSubscriptionQueryMessage<P, I, U> implements SubscriptionQueryMessage<P, I, U> {

    private final SubscriptionQuery subscriptionQuery;
    private final GrpcBackedQueryMessage<P, I> grpcBackedQueryMessage;
    private final LazyDeserializingObject<ResponseType<U>> serializedUpdateResponseType;

    /**
     * Instantiate a {@link GrpcBackedSubscriptionQueryMessage} with the given {@code subscriptionQuery}, using the
     * provided {@code messageSerializer} to be able to retrieve the payload and {@link MetaData} from it. The
     * {@code serializer} is solely used to deserialize the response type of the update message.
     *
     * @param subscriptionQuery The {@link SubscriptionQuery} which is being wrapped as a
     *                          {@link SubscriptionQueryMessage}.
     * @param messageSerializer The {@link Serializer} used to deserialize the payload and {@link MetaData} from the
     *                          given {@code queryRequest}.
     * @param serializer        The {@link Serializer} used to deserialize the response type.
     */
    public GrpcBackedSubscriptionQueryMessage(SubscriptionQuery subscriptionQuery, Serializer messageSerializer,
                                              Serializer serializer) {
        this(subscriptionQuery,
             new GrpcBackedQueryMessage<>(subscriptionQuery.getQueryRequest(), messageSerializer, serializer),
             new LazyDeserializingObject<>(new GrpcSerializedObject(subscriptionQuery.getUpdateResponseType()),
                                           serializer));
    }

    private GrpcBackedSubscriptionQueryMessage(SubscriptionQuery subscriptionQuery,
                                               GrpcBackedQueryMessage<P, I> grpcBackedQueryMessage,
                                               LazyDeserializingObject<ResponseType<U>> serializedUpdateResponseType) {
        this.subscriptionQuery = subscriptionQuery;
        this.grpcBackedQueryMessage = grpcBackedQueryMessage;
        this.serializedUpdateResponseType = serializedUpdateResponseType;
    }

    @Override
    public String getIdentifier() {
        return subscriptionQuery.getSubscriptionIdentifier();
    }

    @Nonnull
    @Override
    public QualifiedName type() {
        return grpcBackedQueryMessage.type();
    }

    @Nonnull
    @Override
    public String getQueryName() {
        return grpcBackedQueryMessage.getQueryName();
    }

    @Nonnull
    @Override
    public ResponseType<I> getResponseType() {
        return grpcBackedQueryMessage.getResponseType();
    }

    @Override
    public ResponseType<U> getUpdateResponseType() {
        return serializedUpdateResponseType.getObject();
    }

    @Override
    public P getPayload() {
        return grpcBackedQueryMessage.getPayload();
    }

    @Override
    public MetaData getMetaData() {
        return grpcBackedQueryMessage.getMetaData();
    }

    @Override
    public Class<P> getPayloadType() {
        return grpcBackedQueryMessage.getPayloadType();
    }

    @Override
    public GrpcBackedSubscriptionQueryMessage<P, I, U> withMetaData(@Nonnull Map<String, ?> metaData) {
        return new GrpcBackedSubscriptionQueryMessage<>(subscriptionQuery,
                                                        grpcBackedQueryMessage.withMetaData(metaData),
                                                        serializedUpdateResponseType);
    }

    @Override
    public GrpcBackedSubscriptionQueryMessage<P, I, U> andMetaData(@Nonnull Map<String, ?> metaData) {
        return withMetaData(getMetaData().mergedWith(metaData));
    }
}
