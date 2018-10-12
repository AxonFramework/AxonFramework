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

import io.axoniq.axonserver.grpc.query.QueryUpdate;
import org.axonframework.axonserver.connector.util.GrpcMetadata;
import org.axonframework.axonserver.connector.util.GrpcSerializedObject;
import org.axonframework.messaging.MetaData;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.serialization.LazyDeserializingObject;
import org.axonframework.serialization.Serializer;

import java.util.Map;

/**
 * Wrapper that allows clients to access a GRPC {@link QueryUpdate} Message as a {@link SubscriptionQueryUpdateMessage}
 *
 * @author Sara Pellegrini
 */
class GrpcBackedQueryUpdateMessage<U> implements SubscriptionQueryUpdateMessage<U> {

    private final QueryUpdate queryUpdate;
    private final LazyDeserializingObject<U> payload;
    private final GrpcMetadata metadata;

    public GrpcBackedQueryUpdateMessage(QueryUpdate update, Serializer serializer) {
        this.queryUpdate = update;
        this.payload = new LazyDeserializingObject<>(new GrpcSerializedObject(update.getPayload()), serializer) ;
        this.metadata = new GrpcMetadata(update.getMetaDataMap(), serializer);
    }

    @Override
    public String getIdentifier() {
        return queryUpdate.getMessageIdentifier();
    }

    @Override
    public MetaData getMetaData() {
        return metadata.get();
    }

    @Override
    public U getPayload() {
        return payload.getObject();
    }

    @Override
    public Class<U> getPayloadType() {
        return payload.getType();
    }

    @Override
    public SubscriptionQueryUpdateMessage<U> withMetaData(Map<String, ?> metaData) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SubscriptionQueryUpdateMessage<U> andMetaData(Map<String, ?> metaData) {
        throw new UnsupportedOperationException();
    }
}
