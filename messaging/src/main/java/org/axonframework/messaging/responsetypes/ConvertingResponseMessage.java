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

package org.axonframework.messaging.responsetypes;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.IllegalPayloadAccessException;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.Metadata;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.serialization.Converter;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of a {@link QueryResponseMessage} that is aware of the requested response type and performs a
 * just-in-time conversion to ensure the response is formatted as requested.
 * <p>
 * The conversion is generally used to accommodate response types that aren't compatible with serialization, such as
 * {@link OptionalResponseType}.
 *
 * @param <R> The type of {@link #payload() payload} contained in this {@link QueryResponseMessage}.
 * @author Allard Buijze
 * @since 4.3.0
 */
public class ConvertingResponseMessage<R> implements QueryResponseMessage {

    private final ResponseType<R> expectedResponseType;
    private final QueryResponseMessage responseMessage;

    /**
     * Initialize a response message, using {@code expectedResponseType} to convert the payload from the
     * {@code responseMessage}, if necessary.
     *
     * @param expectedResponseType An instance describing the expected response type.
     * @param responseMessage      The message containing the actual response from the handler.
     */
    public ConvertingResponseMessage(ResponseType<R> expectedResponseType,
                                     QueryResponseMessage responseMessage) {
        this.expectedResponseType = expectedResponseType;
        this.responseMessage = responseMessage;
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
    @Nonnull
    public String identifier() {
        return responseMessage.identifier();
    }

    @Override
    @Nonnull
    public MessageType type() {
        return responseMessage.type();
    }

    @Override
    @Nonnull
    public Metadata metadata() {
        return responseMessage.metadata();
    }

    @Override
    @Nullable
    public R payload() {
        if (isExceptional()) {
            throw new IllegalPayloadAccessException(
                    "This result completed exceptionally, payload is not available. "
                            + "Try calling 'exceptionResult' to see the cause of failure.",
                    optionalExceptionResult().orElse(null)
            );
        }
        return expectedResponseType.convert(responseMessage.payload());
    }

    @Override
    @Nullable
    public <T> T payloadAs(@Nonnull Type type, @Nullable Converter converter) {
        if (isExceptional()) {
            throw new IllegalPayloadAccessException(
                    "This result completed exceptionally, payload is not available. "
                            + "Try calling 'exceptionResult' to see the cause of failure.",
                    optionalExceptionResult().orElse(null)
            );
        }
        return responseMessage.payloadAs(type, converter);
    }

    @Override
    @Nonnull
    public Class<R> payloadType() {
        return expectedResponseType.responseMessagePayloadType();
    }

    @Override
    @Nonnull
    public QueryResponseMessage withMetadata(@Nonnull Map<String, String> metadata) {
        return new ConvertingResponseMessage<>(expectedResponseType, responseMessage.withMetadata(metadata));
    }

    @Override
    @Nonnull
    public QueryResponseMessage andMetadata(@Nonnull Map<String, String> additionalMetadata) {
        return new ConvertingResponseMessage<>(expectedResponseType, responseMessage.andMetadata(additionalMetadata));
    }

    @Override
    @Nonnull
    public QueryResponseMessage withConvertedPayload(@Nonnull Type type, @Nonnull Converter converter) {
        return responseMessage.withConvertedPayload(type, converter);
    }
}
