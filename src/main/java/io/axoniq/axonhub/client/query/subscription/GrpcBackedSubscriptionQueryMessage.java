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
import io.axoniq.axonhub.client.util.GrpcMetadata;
import io.axoniq.axonhub.client.util.GrpcSerializedObject;
import io.axoniq.platform.grpc.PlatformOutboundInstruction;
import org.axonframework.messaging.MetaData;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.responsetypes.ResponseType;
import org.axonframework.serialization.LazyDeserializingObject;
import org.axonframework.serialization.Serializer;

import java.util.Map;

/**
 * Created by Sara Pellegrini on 04/05/2018.
 * sara.pellegrini@gmail.com
 */
public class GrpcBackedSubscriptionQueryMessage<Q,I,U> implements SubscriptionQueryMessage<Q,I,U> {

    private final SubscriptionQueryRequest subscriptionQuery;
    private final LazyDeserializingObject<Q> payload;
    private final LazyDeserializingObject<ResponseType<I>> responseType;
    private final LazyDeserializingObject<ResponseType<U>> updateType;
    private final GrpcMetadata metadata;

    public GrpcBackedSubscriptionQueryMessage(PlatformOutboundInstruction instruction, Serializer serializer){
        this(instruction.getSubscribeQuery(), serializer);
    }

    public GrpcBackedSubscriptionQueryMessage(SubscriptionQueryRequest query, Serializer serializer) {
        this.subscriptionQuery = query;
        this.payload = new LazyDeserializingObject<>(new GrpcSerializedObject(query.getPayload()), serializer);
        this.responseType = new LazyDeserializingObject<>(new GrpcSerializedObject(query.getResponseType()), serializer);
        this.updateType = new LazyDeserializingObject<>(new GrpcSerializedObject(query.getResponseType()), serializer);
        this.metadata = new GrpcMetadata(query.getMetaDataMap(), serializer);
    }

    @Override
    public ResponseType<U> getUpdateResponseType() {
        return updateType.getObject();
    }

    @Override
    public String getQueryName() {
        return subscriptionQuery.getQuery();
    }

    @Override
    public ResponseType<I> getResponseType() {
        return responseType.getObject();
    }

    @Override
    public String getIdentifier() {
        return subscriptionQuery.getMessageIdentifier();
    }

    @Override
    public MetaData getMetaData() {
        return metadata.get();
    }

    @Override
    public Q getPayload() {
        return payload.getObject();
    }

    @Override
    public Class<Q> getPayloadType() {
        return payload.getType();
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
