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

package org.axonframework.messaging.responsetypes;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.IllegalPayloadAccessException;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;

import java.util.Map;
import java.util.Optional;

/**
 * Implementation of a {@link QueryResponseMessage} that is aware of the requested response type and performs a
 * just-in-time conversion to ensure the response is formatted as requested.
 * <p>
 * The conversion is generally used to accommodate response types that aren't compatible with serialization, such as
 * {@link OptionalResponseType}.
 *
 * @param <R> The type of {@link #getPayload() payload} contained in this {@link QueryResponseMessage}.
 * @author Allard Buijze
 * @since 4.3.0
 */
public class ConvertingResponseMessage<R> implements QueryResponseMessage<R> {

    private final ResponseType<R> expectedResponseType;
    private final QueryResponseMessage<?> responseMessage;

    /**
     * Initialize a response message, using {@code expectedResponseType} to convert the payload from the
     * {@code responseMessage}, if necessary.
     *
     * @param expectedResponseType An instance describing the expected response type.
     * @param responseMessage      The message containing the actual response from the handler.
     */
    public ConvertingResponseMessage(ResponseType<R> expectedResponseType,
                                     QueryResponseMessage<?> responseMessage) {
        this.expectedResponseType = expectedResponseType;
        this.responseMessage = responseMessage;
    }

    @Override
    public <S> SerializedObject<S> serializePayload(Serializer serializer, Class<S> expectedRepresentation) {
        return responseMessage.serializePayload(serializer, expectedRepresentation);
    }

    @Override
    public <T> SerializedObject<T> serializeExceptionResult(Serializer serializer, Class<T> expectedRepresentation) {
        return responseMessage.serializeExceptionResult(serializer, expectedRepresentation);
    }

    @Override
    public <R1> SerializedObject<R1> serializeMetaData(Serializer serializer, Class<R1> expectedRepresentation) {
        return responseMessage.serializeMetaData(serializer, expectedRepresentation);
    }

    @Override
    public boolean isExceptional() {
        return responseMessage.isExceptional();
    }

    @Override
    public Optional<Throwable> optionalExceptionResult() {
        return responseMessage.optionalExceptionResult();
    }

    @Override
    public String getIdentifier() {
        return responseMessage.getIdentifier();
    }

    @Nonnull
    @Override
    public QualifiedName type() {
        return responseMessage.type();
    }

    @Override
    public MetaData getMetaData() {
        return responseMessage.getMetaData();
    }

    @Override
    public R getPayload() {
        if (isExceptional()) {
            throw new IllegalPayloadAccessException(
                    "This result completed exceptionally, payload is not available. "
                            + "Try calling 'exceptionResult' to see the cause of failure.",
                    optionalExceptionResult().orElse(null)
            );
        }
        return expectedResponseType.convert(responseMessage.getPayload());
    }

    @Override
    public Class<R> getPayloadType() {
        return expectedResponseType.responseMessagePayloadType();
    }

    @Override
    public QueryResponseMessage<R> withMetaData(@Nonnull Map<String, ?> metaData) {
        return new ConvertingResponseMessage<>(expectedResponseType, responseMessage.withMetaData(metaData));
    }

    @Override
    public QueryResponseMessage<R> andMetaData(@Nonnull Map<String, ?> additionalMetaData) {
        return new ConvertingResponseMessage<>(expectedResponseType, responseMessage.andMetaData(additionalMetaData));
    }
}
