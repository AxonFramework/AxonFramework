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

import io.axoniq.axonserver.grpc.query.SubscriptionQuery;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.axonserver.connector.query.GrpcBackedQueryMessage;
import org.axonframework.axonserver.connector.util.GrpcSerializedObject;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.Metadata;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.serialization.Converter;
import org.axonframework.serialization.LazyDeserializingObject;
import org.axonframework.serialization.Serializer;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * Wrapper that allows clients to access a gRPC {@link SubscriptionQuery} message as a
 * {@link SubscriptionQueryMessage}.
 *
 * @author Sara Pellegrini
 * @since 4.0.0
 */
public class GrpcBackedSubscriptionQueryMessage implements SubscriptionQueryMessage {

    private final SubscriptionQuery subscriptionQuery;
    private final GrpcBackedQueryMessage<?, ?> grpcBackedQueryMessage;
    private final LazyDeserializingObject<ResponseType<?>> serializedUpdateResponseType;

    /**
     * Instantiate a {@link GrpcBackedSubscriptionQueryMessage} with the given {@code subscriptionQuery}, using the
     * provided {@code messageSerializer} to be able to retrieve the payload and {@link Metadata} from it. The
     * {@code serializer} is solely used to deserialize the response type of the update message.
     *
     * @param subscriptionQuery The {@link SubscriptionQuery} which is being wrapped as a
     *                          {@link SubscriptionQueryMessage}.
     * @param messageSerializer The {@link Serializer} used to deserialize the payload and {@link Metadata} from the
     *                          given {@code queryRequest}.
     * @param serializer        The {@link Serializer} used to deserialize the response type.
     */
    public GrpcBackedSubscriptionQueryMessage(SubscriptionQuery subscriptionQuery, Serializer messageSerializer,
                                              Serializer serializer) {
        this(subscriptionQuery,
             new GrpcBackedQueryMessage<>(subscriptionQuery.getQueryRequest(), messageSerializer, serializer),
             new LazyDeserializingObject<>(new GrpcSerializedObject(subscriptionQuery.getUpdateResponseType()),
                                           serializer));
    }

    private GrpcBackedSubscriptionQueryMessage(SubscriptionQuery subscriptionQuery,
                                               GrpcBackedQueryMessage<?, ?> grpcBackedQueryMessage,
                                               LazyDeserializingObject<ResponseType<?>> serializedUpdateResponseType) {
        this.subscriptionQuery = subscriptionQuery;
        this.grpcBackedQueryMessage = grpcBackedQueryMessage;
        this.serializedUpdateResponseType = serializedUpdateResponseType;
    }

    @Override
    @Nonnull
    public String identifier() {
        return subscriptionQuery.getSubscriptionIdentifier();
    }

    @Override
    @Nonnull
    public MessageType type() {
        return grpcBackedQueryMessage.type();
    }

    @Nonnull
    @Override
    public ResponseType<?> responseType() {
        return grpcBackedQueryMessage.responseType();
    }

    @Override
    @Nonnull
    public ResponseType<?> updatesResponseType() {
        return serializedUpdateResponseType.getObject();
    }

    @Override
    @Nullable
    public Object payload() {
        return grpcBackedQueryMessage.payload();
    }

    @Override
    @Nullable
    public <T> T payloadAs(@Nonnull Type type, @Nullable Converter converter) {
        // TODO #3488 - Not implementing this, as the GrpcBackedResponseMessage will be removed as part of #3488
        return null;
    }

    @Override
    @Nonnull
    public Metadata metadata() {
        return grpcBackedQueryMessage.metadata();
    }

    @Override
    @Nonnull
    public Class<?> payloadType() {
        return grpcBackedQueryMessage.payloadType();
    }

    @Override
    @Nonnull
    public GrpcBackedSubscriptionQueryMessage withMetadata(@Nonnull Map<String, String> metadata) {
        return new GrpcBackedSubscriptionQueryMessage(subscriptionQuery,
                                                      grpcBackedQueryMessage.withMetadata(metadata),
                                                      serializedUpdateResponseType);
    }

    @Override
    @Nonnull
    public GrpcBackedSubscriptionQueryMessage andMetadata(@Nonnull Map<String, String> metadata) {
        return withMetadata(metadata().mergedWith(metadata));
    }

    @Override
    @Nonnull
    public SubscriptionQueryMessage withConvertedPayload(@Nonnull Type type,
                                                         @Nonnull Converter converter) {
        // TODO #3488 - Not implementing this, as the GrpcBackedResponseMessage will be removed as part of #3488
        return null;
    }
}
