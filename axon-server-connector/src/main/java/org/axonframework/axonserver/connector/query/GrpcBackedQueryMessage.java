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

package org.axonframework.axonserver.connector.query;

import io.axoniq.axonserver.grpc.query.QueryRequest;
import org.axonframework.axonserver.connector.util.GrpcMetadata;
import org.axonframework.axonserver.connector.util.GrpcSerializedObject;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.serialization.LazyDeserializingObject;
import org.axonframework.serialization.Serializer;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Wrapper around GRPC QueryRequest to implement the QueryMessage interface
 *
 * @author Marc Gathier
 */
public class GrpcBackedQueryMessage<Q, R> implements QueryMessage<Q, R> {

    private final QueryRequest query;
    private final Serializer messageSerializer;
    private final LazyDeserializingObject<Q> serializedPayload;
    private final LazyDeserializingObject<ResponseType<R>> serializedResponseType;
    private final Supplier<MetaData> metadata;

    public GrpcBackedQueryMessage(QueryRequest query, Serializer messageSerializer, Serializer genericSerializer) {
        this.query = query;
        this.messageSerializer = messageSerializer;
        this.serializedPayload = new LazyDeserializingObject<>(new GrpcSerializedObject(query.getPayload()), messageSerializer);
        this.serializedResponseType = new LazyDeserializingObject<>(new GrpcSerializedObject(query.getResponseType()), genericSerializer);
        this.metadata = new GrpcMetadata(query.getMetaDataMap(), messageSerializer);
    }

    public GrpcBackedQueryMessage(QueryRequest query, Serializer messageSerializer,
                                  LazyDeserializingObject<Q> serializedPayload,
                                  LazyDeserializingObject<ResponseType<R>> serializedResponseType,
                                  Supplier<MetaData> metadata) {
        this.query = query;
        this.messageSerializer = messageSerializer;
        this.serializedPayload = serializedPayload;
        this.serializedResponseType = serializedResponseType;
        this.metadata = metadata;
    }

    @Override
    public String getQueryName() {
        return query.getQuery();
    }

    @Override
    public ResponseType<R> getResponseType() {
        return serializedResponseType.getObject();
    }

    @Override
    public String getIdentifier() {
        return query.getMessageIdentifier();
    }

    @Override
    public MetaData getMetaData() {
        return metadata.get();
    }

    @Override
    public Q getPayload() {
        return serializedPayload.getObject();
    }

    @Override
    public Class<Q> getPayloadType() {
        return serializedPayload.getType();
    }

    @Override
    public QueryMessage<Q, R> withMetaData(Map<String, ?> metaData) {
        return new GrpcBackedQueryMessage<>(query, messageSerializer, serializedPayload, serializedResponseType,
                                         () -> MetaData.from(metaData));
    }

    @Override
    public QueryMessage<Q, R> andMetaData(Map<String, ?> metaData) {
        return withMetaData(getMetaData().mergedWith(metaData));
    }

}
