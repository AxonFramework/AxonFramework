/*
 * Copyright (c) 2010-2018. Axon Framework
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

import io.axoniq.axonserver.grpc.query.QueryRequest;
import io.axoniq.axonserver.grpc.query.QueryUpdate;
import io.axoniq.axonserver.grpc.query.SubscriptionQuery;
import org.axonframework.axonserver.connector.query.GrpcBackedQueryMessage;
import org.axonframework.axonserver.connector.util.GrpcSerializedObject;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.serialization.LazyDeserializingObject;
import org.axonframework.serialization.Serializer;

import java.util.Map;

/**
 * Wrapper that allows clients to access a GRPC {@link SubscriptionQuery} Message as a {@link SubscriptionQueryMessage}.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */
public class GrpcBackedSubscriptionQueryMessage<Q,I,U> implements SubscriptionQueryMessage<Q,I,U> {

    private final SubscriptionQuery subscriptionQuery;
    private final GrpcBackedQueryMessage<Q, I> grpcBackedQueryMessage;
    private final LazyDeserializingObject<ResponseType<U>> updateType;


    public GrpcBackedSubscriptionQueryMessage(SubscriptionQuery subscriptionQuery, Serializer messageSerializer, Serializer genericSerializer) {
        this.subscriptionQuery = subscriptionQuery;
        QueryRequest query = subscriptionQuery.getQueryRequest();
        this.updateType = new LazyDeserializingObject<>(new GrpcSerializedObject(query.getResponseType()), genericSerializer);
        grpcBackedQueryMessage = new GrpcBackedQueryMessage<>(query, messageSerializer, genericSerializer);
    }

    @Override
    public ResponseType<U> getUpdateResponseType() {
        return updateType.getObject();
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
    public SubscriptionQueryMessage<Q, I, U> withMetaData(Map<String, ?> metaData) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SubscriptionQueryMessage<Q, I, U> andMetaData(Map<String, ?> additionalMetaData) {
        throw new UnsupportedOperationException();
    }
}
