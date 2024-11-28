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

package org.axonframework.axonserver.connector.query;

import io.axoniq.axonserver.grpc.query.QueryRequest;
import jakarta.annotation.Nonnull;
import org.axonframework.axonserver.connector.util.GrpcMetaData;
import org.axonframework.axonserver.connector.util.GrpcSerializedObject;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.QualifiedNameUtils;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.serialization.LazyDeserializingObject;
import org.axonframework.serialization.Serializer;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Wrapper that allows clients to access a gRPC {@link QueryRequest} as a {@link QueryMessage}.
 *
 * @param <P> A generic specifying the type of the {@link QueryMessage QueryMessage's} {@link #getPayload() payload}.
 * @param <R> A generic specifying the expected {@link #getResponseType() response type} of the {@link QueryMessage}.
 * @author Marc Gathier
 * @since 4.0.0
 */
public class GrpcBackedQueryMessage<P, R> implements QueryMessage<P, R> {

    private final QueryRequest query;
    private final LazyDeserializingObject<P> serializedPayload;
    private final LazyDeserializingObject<ResponseType<R>> serializedResponseType;
    private final Supplier<MetaData> metaDataSupplier;
    private final QualifiedName type;

    /**
     * Instantiate a {@link GrpcBackedResponseMessage} with the given {@code queryRequest}, using the provided
     * {@code messageSerializer} to be able to retrieve the payload and {@link MetaData} from it. The {@code serializer}
     * is solely used to deserialize the response type.
     *
     * @param queryRequest      The {@link QueryRequest} which is being wrapped as a {@link QueryMessage}.
     * @param messageSerializer The {@link Serializer} used to deserialize the payload and {@link MetaData} from the
     *                          given {@code queryRequest}.
     * @param serializer        The {@link Serializer} used to deserialize the response type.
     */
    public GrpcBackedQueryMessage(QueryRequest queryRequest,
                                  Serializer messageSerializer,
                                  Serializer serializer) {
        this(
                queryRequest,
                new LazyDeserializingObject<>(new GrpcSerializedObject(queryRequest.getPayload()), messageSerializer),
                new LazyDeserializingObject<>(new GrpcSerializedObject(queryRequest.getResponseType()), serializer),
                new GrpcMetaData(queryRequest.getMetaDataMap(), messageSerializer),
                createType(new GrpcSerializedObject(queryRequest.getResponseType()), serializer)
        );
    }

    private static QualifiedName createType(GrpcSerializedObject serializedObject, Serializer serializer) {
        Class<?> payloadClass = serializer.classForType(serializedObject.getType());
        String revision = serializedObject.getType().getRevision();
        return new QualifiedName(payloadClass.getPackageName(),
                                 payloadClass.getSimpleName(),
                                 revision != null ? revision : QualifiedNameUtils.DEFAULT_REVISION);
    }

    private GrpcBackedQueryMessage(QueryRequest queryRequest,
                                   LazyDeserializingObject<P> serializedPayload,
                                   LazyDeserializingObject<ResponseType<R>> serializedResponseType,
                                   Supplier<MetaData> metaDataSupplier,
                                   QualifiedName type) {
        this.query = queryRequest;
        this.serializedPayload = serializedPayload;
        this.serializedResponseType = serializedResponseType;
        this.metaDataSupplier = metaDataSupplier;
        this.type = type;
    }

    @Override
    public String getIdentifier() {
        return query.getMessageIdentifier();
    }

    @Nonnull
    @Override
    public QualifiedName type() {
        return this.type;
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
    public P getPayload() {
        return serializedPayload.getObject();
    }

    @Override
    public MetaData getMetaData() {
        return metaDataSupplier.get();
    }

    @Override
    public Class<P> getPayloadType() {
        return serializedPayload.getType();
    }

    @Override
    public GrpcBackedQueryMessage<P, R> withMetaData(@Nonnull Map<String, ?> metaData) {
        return new GrpcBackedQueryMessage<>(query,
                                            serializedPayload,
                                            serializedResponseType,
                                            () -> MetaData.from(metaData),
                                            type);
    }

    @Override
    public GrpcBackedQueryMessage<P, R> andMetaData(@Nonnull Map<String, ?> metaData) {
        return withMetaData(getMetaData().mergedWith(metaData));
    }
}
