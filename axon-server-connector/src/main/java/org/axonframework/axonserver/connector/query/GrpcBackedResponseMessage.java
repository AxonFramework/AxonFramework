/*
 * Copyright (c) 2010-2019. Axon Framework
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

import io.axoniq.axonserver.grpc.query.QueryResponse;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.util.GrpcMetadata;
import org.axonframework.axonserver.connector.util.GrpcSerializedObject;
import org.axonframework.messaging.MetaData;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.serialization.LazyDeserializingObject;
import org.axonframework.serialization.Serializer;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Wrapper that allows clients to access a gRPC {@link QueryResponse} as a {@link QueryResponseMessage}.
 *
 * @param <R> a generic specifying the type of the {@link QueryResponseMessage}
 * @author Marc Gathier
 * @since 4.0
 */
public class GrpcBackedResponseMessage<R> implements QueryResponseMessage<R> {

    private final QueryResponse queryResponse;
    private final Serializer messageSerializer;

    private final LazyDeserializingObject<R> serializedPayload;
    private final Throwable exception;
    private final Supplier<MetaData> metadata;

    /**
     * Instantiate a {@link GrpcBackedResponseMessage} with the given {@code queryResponse}, using the provided
     * {@link Serializer} to be able to retrieve the payload and {@link MetaData} from it.
     *
     * @param queryResponse the {@Link QueryResponse} which is being wrapped as a {@link QueryResponseMessage}
     * @param serializer    the {@link Serializer} used to deserialize the payload and {@link MetaData} from the given
     *                      {@code queryResponse}
     */
    public GrpcBackedResponseMessage(QueryResponse queryResponse, Serializer serializer) {
        this.queryResponse = queryResponse;
        this.messageSerializer = serializer;
        this.metadata = new GrpcMetadata(queryResponse.getMetaDataMap(), serializer);
        if (queryResponse.hasErrorMessage()) {
            this.exception = ErrorCode.getFromCode(queryResponse.getErrorCode())
                                      .convert(queryResponse.getErrorMessage());
        } else {
            this.exception = null;
        }
        if (queryResponse.hasPayload() && !"empty".equalsIgnoreCase(queryResponse.getPayload().getType())) {
            this.serializedPayload = new LazyDeserializingObject<>(
                    new GrpcSerializedObject(queryResponse.getPayload()), serializer
            );
        } else {
            this.serializedPayload = null;
        }
    }

    private GrpcBackedResponseMessage(QueryResponse queryResponse,
                                      Serializer messageSerializer,
                                      LazyDeserializingObject<R> serializedPayload,
                                      Throwable exception,
                                      Supplier<MetaData> metadata) {
        this.queryResponse = queryResponse;
        this.messageSerializer = messageSerializer;
        this.serializedPayload = serializedPayload;
        this.exception = exception;
        this.metadata = metadata;
    }

    @Override
    public String getIdentifier() {
        return queryResponse.getMessageIdentifier();
    }

    @Override
    public MetaData getMetaData() {
        return metadata.get();
    }

    @Override
    public R getPayload() {
        if (serializedPayload == null) {
            return null;
        }
        return serializedPayload.getObject();
    }

    @Override
    public Class<R> getPayloadType() {
        if (serializedPayload == null) {
            return null;
        }
        return serializedPayload.getType();
    }

    @Override
    public boolean isExceptional() {
        return exception != null;
    }

    @Override
    public Optional<Throwable> optionalExceptionResult() {
        return Optional.ofNullable(exception);
    }

    @Override
    public GrpcBackedResponseMessage<R> withMetaData(Map<String, ?> metaData) {
        return new GrpcBackedResponseMessage<>(queryResponse,
                                               messageSerializer,
                                               serializedPayload,
                                               exception,
                                               () -> MetaData.from(metaData));
    }

    @Override
    public QueryResponseMessage<R> andMetaData(Map<String, ?> var1) {
        return withMetaData(getMetaData().mergedWith(var1));
    }
}
