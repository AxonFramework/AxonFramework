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

package io.axoniq.axonhub.client.query;

import io.axoniq.axonhub.QueryResponse;
import io.axoniq.platform.MetaDataValue;
import io.axoniq.platform.SerializedObject;
import org.axonframework.messaging.MetaData;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.serialization.LazyDeserializingObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Wrapper around GRPC QueryRequest to implement the QueryMessage interface
 *
 * @author Marc Gathier
 */
public class GrpcBackedResponseMessage<R> implements QueryResponseMessage<R> {

    private final QueryResponse queryResponse;
    private final Serializer messageSerializer;
    private final LazyDeserializingObject<R> serializedPayload;
    private volatile MetaData metaData;

    public GrpcBackedResponseMessage(QueryResponse queryResponse, Serializer messageSerializer) {
        this.queryResponse = queryResponse;
        this.messageSerializer = messageSerializer;
        if( queryResponse.hasPayload() && !"empty".equalsIgnoreCase(queryResponse.getPayload().getType())) {
            this.serializedPayload = new LazyDeserializingObject<>(fromGrpcSerializedObject(queryResponse.getPayload()),
                                                                   messageSerializer);
        } else {
            this.serializedPayload = null;
        }
    }

    private GrpcBackedResponseMessage(QueryResponse queryResponse, Serializer messageSerializer, LazyDeserializingObject<R> serializedPayload,
                                      MetaData metaData) {

        this.queryResponse = queryResponse;
        this.messageSerializer = messageSerializer;
        this.serializedPayload = serializedPayload;
        this.metaData = metaData;
    }

    private org.axonframework.serialization.SerializedObject<byte[]> fromGrpcSerializedObject(SerializedObject payload) {
        return new SimpleSerializedObject<>(payload.getData().toByteArray(),
                                            byte[].class,
                                            payload.getType(),
                                            payload.getRevision());
    }

    @Override
    public String getIdentifier() {
        return queryResponse.getMessageIdentifier();
    }

    @Override
    public MetaData getMetaData() {
        if (metaData == null) {
            metaData = deserializeMetaData(queryResponse.getMetaDataMap());
        }
        return metaData;
    }

    @Override
    public R getPayload() {
        if( serializedPayload == null) return null;
        return serializedPayload.getObject();
    }

    @Override
    public Class<R> getPayloadType() {
        if( serializedPayload == null) return null;
        return serializedPayload.getType();
    }

    @Override
    public GrpcBackedResponseMessage<R> withMetaData(Map<String, ?> metaData) {
        return new GrpcBackedResponseMessage<>(queryResponse, messageSerializer, serializedPayload,
                                               MetaData.from(metaData));
    }

    @Override
    public QueryResponseMessage<R> andMetaData(Map<String, ?> var1) {
        return withMetaData(getMetaData().mergedWith(var1));
    }

    private MetaData deserializeMetaData(Map<String, MetaDataValue> metaDataMap) {
        if (metaDataMap.isEmpty()) {
            return MetaData.emptyInstance();
        }
        Map<String, Object> metaData = new HashMap<>(metaDataMap.size());
        metaDataMap.forEach((k, v) -> metaData.put(k, convertFromMetaDataValue(v)));
        return MetaData.from(metaData);
    }

    private Object convertFromMetaDataValue(MetaDataValue value) {
        switch (value.getDataCase()) {
            case TEXT_VALUE:
                return value.getTextValue();
            case BYTES_VALUE:
                return messageSerializer.deserialize(fromGrpcSerializedObject(value.getBytesValue()));
            case DOUBLE_VALUE:
                return value.getDoubleValue();
            case NUMBER_VALUE:
                return value.getNumberValue();
            case BOOLEAN_VALUE:
                return value.getBooleanValue();
            case DATA_NOT_SET:
                return null;
        }
        return null;
    }

}
