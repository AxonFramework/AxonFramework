/*
 * Copyright (c) 2010-2025. Axon Framework
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

import io.axoniq.axonserver.grpc.query.QueryResponse;
import jakarta.annotation.Nonnull;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.util.GrpcMetaData;
import org.axonframework.axonserver.connector.util.GrpcSerializedObject;
import org.axonframework.messaging.IllegalPayloadAccessException;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.serialization.LazyDeserializingObject;
import org.axonframework.serialization.SerializedType;
import org.axonframework.serialization.Serializer;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Wrapper that allows clients to access a gRPC {@link QueryResponse} as a {@link QueryResponseMessage}.
 *
 * @param <R> A generic specifying the type of the {@link QueryResponseMessage}.
 * @author Marc Gathier
 * @since 4.0.0
 */
public class GrpcBackedResponseMessage<R> implements QueryResponseMessage<R> {

    private final QueryResponse queryResponse;
    private final LazyDeserializingObject<R> serializedPayload;
    private final Throwable exception;
    private final Supplier<MetaData> metaDataSupplier;
    private final MessageType type;

    /**
     * Instantiate a {@link GrpcBackedResponseMessage} with the given {@code queryResponse}, using the provided
     * {@link Serializer} to be able to retrieve the payload and {@link MetaData} from it.
     *
     * @param queryResponse The {@link QueryResponse} which is being wrapped as a {@link QueryResponseMessage}.
     * @param serializer    The {@link Serializer} used to deserialize the payload and {@link MetaData} from the given
     *                      {@code queryResponse}.
     */
    public GrpcBackedResponseMessage(QueryResponse queryResponse, Serializer serializer) {
        this.queryResponse = queryResponse;
        this.serializedPayload = queryResponse.hasPayload()
                && !SerializedType.emptyType().getName().equalsIgnoreCase(queryResponse.getPayload().getType())
                ? new LazyDeserializingObject<>(new GrpcSerializedObject(queryResponse.getPayload()), serializer)
                : null;
        this.exception = queryResponse.hasErrorMessage()
                ? ErrorCode.getFromCode(queryResponse.getErrorCode())
                           .convert(queryResponse.getErrorMessage(),
                                    serializedPayload == null ? () -> null : serializedPayload::getObject)
                : null;
        this.metaDataSupplier = new GrpcMetaData(queryResponse.getMetaDataMap(), serializer);
        if (serializedPayload != null) {
            this.type = new MessageType(
                    serializer.classForType(new GrpcSerializedObject(queryResponse.getPayload()).getType())
            );
        } else {
            this.type = new MessageType(
                    queryResponse.getErrorCode()
            );
        }
    }

    private GrpcBackedResponseMessage(QueryResponse queryResponse,
                                      LazyDeserializingObject<R> serializedPayload,
                                      Throwable exception,
                                      Supplier<MetaData> metaDataSupplier,
                                      MessageType type) {
        this.queryResponse = queryResponse;
        this.serializedPayload = serializedPayload;
        this.exception = exception;
        this.metaDataSupplier = metaDataSupplier;
        this.type = type;
    }

    @Override
    public String getIdentifier() {
        return queryResponse.getMessageIdentifier();
    }

    @Nonnull
    @Override
    public MessageType type() {
        return this.type;
    }

    @Override
    public MetaData getMetaData() {
        return metaDataSupplier.get();
    }

    @Override
    public R getPayload() {
        if (isExceptional()) {
            throw new IllegalPayloadAccessException(
                    "This result completed exceptionally, payload is not available. "
                            + "Try calling 'exceptionResult' to see the cause of failure.",
                    exception
            );
        }
        return serializedPayload == null ? null : serializedPayload.getObject();
    }

    @Override
    public Class<R> getPayloadType() {
        return serializedPayload == null ? null : serializedPayload.getType();
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
    public GrpcBackedResponseMessage<R> withMetaData(@Nonnull Map<String, ?> metaData) {
        return new GrpcBackedResponseMessage<>(queryResponse,
                                               serializedPayload,
                                               exception,
                                               () -> MetaData.from(metaData),
                                               type);
    }

    @Override
    public GrpcBackedResponseMessage<R> andMetaData(@Nonnull Map<String, ?> metaData) {
        return withMetaData(getMetaData().mergedWith(metaData));
    }
}
