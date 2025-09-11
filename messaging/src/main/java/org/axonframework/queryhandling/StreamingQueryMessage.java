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

package org.axonframework.queryhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.common.TypeReference;
import org.axonframework.messaging.responsetypes.PublisherResponseType;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.serialization.Converter;
import org.reactivestreams.Publisher;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * A {@link QueryMessage} used for initiating streaming queries.
 * <p>
 * It hard codes the {@link #responseType() response type} to an {@link PublisherResponseType} implementation.
 *
 * @author Milan Savic
 * @author Stefan Dragisic
 * @since 4.6.0
 */
public interface StreamingQueryMessage extends QueryMessage {

    @Override
    @Nonnull
    ResponseType<Publisher<?>> responseType();

    @Override
    @Nonnull
    StreamingQueryMessage withMetadata(@Nonnull Map<String, String> metadata);

    @Override
    @Nonnull
    StreamingQueryMessage andMetadata(@Nonnull Map<String, String> additionalMetadata);

    @Override
    @Nonnull
    default StreamingQueryMessage withConvertedPayload(@Nonnull Class<?> type, @Nonnull Converter converter) {
        return withConvertedPayload((Type) type, converter);
    }

    @Override
    @Nonnull
    default StreamingQueryMessage withConvertedPayload(@Nonnull TypeReference<?> type,
                                                       @Nonnull Converter converter) {
        return withConvertedPayload(type.getType(), converter);
    }

    @Override
    @Nonnull
    StreamingQueryMessage withConvertedPayload(@Nonnull Type type, @Nonnull Converter converter);
}
