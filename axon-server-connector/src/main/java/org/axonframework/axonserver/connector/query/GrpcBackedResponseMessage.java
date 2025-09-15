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
import jakarta.annotation.Nullable;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.util.GrpcMetadata;
import org.axonframework.axonserver.connector.util.GrpcSerializedObject;
import org.axonframework.messaging.IllegalPayloadAccessException;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.Metadata;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.serialization.Converter;
import org.axonframework.serialization.LazyDeserializingObject;
import org.axonframework.serialization.SerializedType;
import org.axonframework.serialization.Serializer;

import java.lang.reflect.Type;
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
public class GrpcBackedResponseMessage<R> implements QueryResponseMessage {

    private final QueryResponse queryResponse;
    private final LazyDeserializingObject<R> serializedPayload;
    private final Throwable exception;
    private final Supplier<Metadata> metadataSupplier;
    private final MessageType type;

    /**
     * Instantiate a {@link GrpcBackedResponseMessage} with the given {@code queryResponse}, using the provided
     * {@link Serializer} to be able to retrieve the payload and {@link Metadata} from it.
     *
     * @param queryResponse The {@link QueryResponse} which is being wrapped as a {@link QueryResponseMessage}.
     * @param serializer    The {@link Serializer} used to deserialize the payload and {@link Metadata} from the given
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
        this.metadataSupplier = new GrpcMetadata(queryResponse.getMetaDataMap(), serializer);
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
                                      Supplier<Metadata> metadataSupplier,
                                      MessageType type) {
        this.queryResponse = queryResponse;
        this.serializedPayload = serializedPayload;
        this.exception = exception;
        this.metadataSupplier = metadataSupplier;
        this.type = type;
    }

    @Override
    @Nonnull
    public String identifier() {
        return queryResponse.getMessageIdentifier();
    }

    @Override
    @Nonnull
    public MessageType type() {
        return this.type;
    }

    @Override
    @Nonnull
    public Metadata metadata() {
        return metadataSupplier.get();
    }

    @Override
    @Nullable
    public R payload() {
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
    @Nullable
    public <T> T payloadAs(@Nonnull Type type, @Nullable Converter converter) {
        // TODO #3488 - Not implementing this, as the GrpcBackedResponseMessage will be removed as part of #3488
        return null;
    }

    @Override
    @Nonnull
    public Class<R> payloadType() {
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
    @Nonnull
    public GrpcBackedResponseMessage<R> withMetadata(@Nonnull Map<String, String> metadata) {
        return new GrpcBackedResponseMessage<>(queryResponse,
                                               serializedPayload,
                                               exception,
                                               () -> Metadata.from(metadata),
                                               type);
    }

    @Override
    @Nonnull
    public GrpcBackedResponseMessage<R> andMetadata(@Nonnull Map<String, String> metadata) {
        return withMetadata(metadata().mergedWith(metadata));
    }

    @Override
    @Nonnull
    public QueryResponseMessage withConvertedPayload(@Nonnull Type type, @Nonnull Converter converter) {
        // TODO #3488 - Not implementing this, as the GrpcBackedResponseMessage will be removed as part of #3488
        return null;
    }
}
