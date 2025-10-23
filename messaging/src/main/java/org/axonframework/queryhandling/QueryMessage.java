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
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;
import org.axonframework.serialization.Converter;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.OptionalInt;

/**
 * A {@link Message} type that carries a Query: a request for information.
 * <p>
 * Besides a {@link #payload() payload}, {@link QueryMessage QueryMessages} also carry the expected
 * {@link #responseType() response type}. This is the type of result expected by the caller.
 * <p>
 * Handlers should only answer a query if they can respond with the appropriate response type.
 *
 * @author Marc Gathier
 * @since 3.1.0
 */
public interface QueryMessage extends Message {

    /**
     * Returns the query {@link MessageType response type} of this {@code QueryMessage}.
     *
     * @return The query {@link MessageType response type} of this {@code QueryMessage}.
     */
    @Nonnull
    MessageType responseType();

    @Override
    @Nonnull
    QueryMessage withMetadata(@Nonnull Map<String, String> metadata);

    @Override
    @Nonnull
    QueryMessage andMetadata(@Nonnull Map<String, String> additionalMetadata);

    @Override
    @Nonnull
    default QueryMessage withConvertedPayload(@Nonnull Class<?> type, @Nonnull Converter converter) {
        return withConvertedPayload((Type) type, converter);
    }

    @Override
    @Nonnull
    default QueryMessage withConvertedPayload(@Nonnull TypeReference<?> type, @Nonnull Converter converter) {
        return withConvertedPayload(type.getType(), converter);
    }

    @Override
    @Nonnull
    QueryMessage withConvertedPayload(@Nonnull Type type, @Nonnull Converter converter);

    /**
     * Returns the priority of this {@link QueryMessage}, if any is applicable.
     * <p>
     * Queries with a higher priority should be handled before queries with a lower priority. Queries without a priority
     * are considered to have the lowest priority.
     *
     * @return The priority of this query message, or an empty {@link OptionalInt} if no priority is set.
     */
    OptionalInt priority();
}
