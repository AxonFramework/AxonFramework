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

import io.axoniq.platform.MetaDataValue;
import io.axoniq.axonhub.QueryRequest;
import org.axonframework.messaging.MetaData;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.responsetypes.InstanceResponseType;
import org.axonframework.queryhandling.responsetypes.ResponseType;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Wrapper around GRPC QueryRequest to implement the QueryMessage interface
 * @author Marc Gathier
 */
public class GrpcBackedQueryMessage<T, R> implements QueryMessage<T, R> {

    private final QueryRequest query;
    private final Serializer serializer;
    private SerializedObject serializedObject;
    private MetaData metaData;

    public GrpcBackedQueryMessage(QueryRequest query, Serializer serializer) {
        this.query = query;
        this.serializer = serializer;
    }

    @Override
    public String getQueryName() {
        return  query.getQuery();
    }

    @Override
    public ResponseType<R> getResponseType() {

        try {
            Class<R> klass;
            if( "int".equals(query.getResultName())) klass = (Class<R>) int.class;
            else if( "float".equals(query.getResultName())) klass = (Class<R>) float.class;
            else klass = (Class<R>) Class.forName(query.getResultName());
            return new InstanceResponseType<>(klass);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Illegal response type", e);
        }
    }

    @Override
    public String getIdentifier() {
        return query.getMessageIdentifier();
    }

    @Override
    public MetaData getMetaData() {
        if( metaData == null) {
            metaData = deserializeMetaData(query.getMetaDataMap());
        }
        return metaData;
    }

    @Override
    public T getPayload() {
        if( serializedObject == null) {
            String revision = query.getPayload().getRevision();
            serializedObject =  new SimpleSerializedObject<>(query.getPayload().getData().toByteArray(),
                    byte[].class, query.getPayload().getType(),
                    "".equals(revision) ? null : revision);
        }
        return (T)serializer.deserialize(serializedObject);
    }

    @Override
    public Class<T> getPayloadType() {
        try {
            return (Class<T>) Class.forName(query.getPayload().getType());
        } catch (ClassNotFoundException e) {
            //throw new RuntimeException(e);
            return (Class<T>) getPayload().getClass();
        }
    }

    @Override
    public QueryMessage<T, R> withMetaData(Map<String, ?> var1) {
        return null;
    }

    @Override
    public QueryMessage<T, R> andMetaData(Map<String, ?> var1) {
        return null;
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
                io.axoniq.platform.SerializedObject bytesValue = value.getBytesValue();
                return serializer.deserialize(new SimpleSerializedObject<>(bytesValue.getData().toByteArray(),
                        byte[].class,
                        bytesValue.getType(),
                        bytesValue.getRevision()));

            case DATA_NOT_SET:
                return null;
            case DOUBLE_VALUE:
                return value.getDoubleValue();
            case NUMBER_VALUE:
                return value.getNumberValue();
            case BOOLEAN_VALUE:
                return value.getBooleanValue();
        }
        return null;
    }

}
