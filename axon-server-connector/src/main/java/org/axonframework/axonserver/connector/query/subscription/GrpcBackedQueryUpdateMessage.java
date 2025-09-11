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

package org.axonframework.axonserver.connector.query.subscription;

import io.axoniq.axonserver.grpc.query.QueryUpdate;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.util.GrpcMetadata;
import org.axonframework.axonserver.connector.util.GrpcSerializedObject;
import org.axonframework.messaging.IllegalPayloadAccessException;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.Metadata;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.serialization.Converter;
import org.axonframework.serialization.LazyDeserializingObject;
import org.axonframework.serialization.Serializer;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Wrapper that allows clients to access a gRPC {@link QueryUpdate} as a {@link SubscriptionQueryUpdateMessage}.
 *
 * @param <U> A generic specifying the type of the updates contained in the {@link SubscriptionQueryUpdateMessage}.
 * @author Sara Pellegrini
 * @since 4.0.0
 */
class GrpcBackedQueryUpdateMessage implements SubscriptionQueryUpdateMessage {

    private final QueryUpdate queryUpdate;
    private final LazyDeserializingObject<?> serializedPayload;
    private final Throwable exception;
    private final Supplier<Metadata> metadataSupplier;
    private final MessageType type;

    /**
     * Instantiate a {@link GrpcBackedQueryUpdateMessage} with the given {@code queryUpdate}, using the provided
     * {@code serializer} to be able to retrieve the payload and {@link Metadata} from it.
     *
     * @param queryUpdate A {@link QueryUpdate} which is being wrapped as a {@link SubscriptionQueryUpdateMessage}.
     * @param serializer  A {@link Serializer} used to deserialize the payload and {@link Metadata} from the given
     *                    {@code queryUpdate}.
     */
    public GrpcBackedQueryUpdateMessage(QueryUpdate queryUpdate, Serializer serializer) {
        this.queryUpdate = queryUpdate;
        this.serializedPayload = queryUpdate.hasPayload()
                ? new LazyDeserializingObject<>(new GrpcSerializedObject(queryUpdate.getPayload()), serializer)
                : null;
        Supplier<Object> exceptionDetails = serializedPayload == null
                ? () -> null
                : serializedPayload::getObject;
        this.exception = queryUpdate.hasErrorMessage()
                ? ErrorCode.getFromCode(queryUpdate.getErrorCode())
                           .convert(queryUpdate.getErrorMessage(), exceptionDetails)
                : null;
        this.metadataSupplier = new GrpcMetadata(queryUpdate.getMetaDataMap(), serializer);
        if (serializedPayload != null) {
            this.type = new MessageType(
                    serializer.classForType(new GrpcSerializedObject(queryUpdate.getPayload()).getType())
            );
        } else {
            this.type = new MessageType(
                    queryUpdate.getErrorCode()
            );
        }
    }

    private GrpcBackedQueryUpdateMessage(QueryUpdate queryUpdate,
                                         LazyDeserializingObject<?> serializedPayload,
                                         Throwable exception,
                                         Supplier<Metadata> metadataSupplier,
                                         MessageType type) {
        this.queryUpdate = queryUpdate;
        this.serializedPayload = serializedPayload;
        this.exception = exception;
        this.metadataSupplier = metadataSupplier;
        this.type = type;
    }

    @Override
    @Nonnull
    public String identifier() {
        return queryUpdate.getMessageIdentifier();
    }

    @Nonnull
    @Override
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
    public Object payload() {
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
    public Class<?> payloadType() {
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
    @Nonnull
    public GrpcBackedQueryUpdateMessage withMetadata(@Nonnull Map<String, String> metadata) {
        return new GrpcBackedQueryUpdateMessage(queryUpdate,
                                                  serializedPayload,
                                                  exception,
                                                  () -> Metadata.from(metadata),
                                                  type);
    }

    @Override
    @Nonnull
    public GrpcBackedQueryUpdateMessage andMetadata(@Nonnull Map<String, String> metadata) {
        return withMetadata(metadata().mergedWith(metadata));
    }

    @Override
    @Nonnull
    public SubscriptionQueryUpdateMessage withConvertedPayload(@Nonnull Type type,
                                                                      @Nonnull Converter converter) {
        // TODO #3488 - Not implementing this, as the GrpcBackedResponseMessage will be removed as part of #3488
        return null;
    }
}
