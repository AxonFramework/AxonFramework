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

package org.axonframework.messaging;

import jakarta.annotation.Nonnull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link MessageTypeResolver} implementation that allows registering custom mappings for specific payload types.
 * <p>
 * This resolver provides a fluent API to register type mappings that return a {@link MessageType} for specific payload
 * types.
 * <p>
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class SimpleMessageTypeResolver implements MessageTypeResolver {

    private final Map<Class<?>, MessageType> mappings;

    /**
     * Private constructor used by the builder to create the resolver with the configured mappings and default
     * behavior.
     *
     * @param mappings         The mapping of payload types to their specific {@code MessageType}s.
     * @param fallbackResolver The fallback resolver to use when no specific resolver is found, or null to throw an
     *                         exception.
     */
    public SimpleMessageTypeResolver(
            @Nonnull Map<Class<?>, MessageType> mappings
    ) {
        Objects.requireNonNull(mappings, "Mappings may not be null");
        this.mappings = mappings;
    }

    public SimpleMessageTypeResolver() {
        this(new HashMap<>());
    }

    public SimpleMessageTypeResolver message(@Nonnull Class<?> payloadType, @Nonnull MessageType messageType) {
        if (mappings.containsKey(payloadType)) {
            throw new IllegalArgumentException(
                    "A MessageType is already defined for payload type [" + payloadType.getName() + "]");
        }

        Map<Class<?>, MessageType> newMappings = new HashMap<>(mappings);
        newMappings.put(payloadType, messageType);

        return new SimpleMessageTypeResolver(newMappings);
    }

    @Override
    public MessageType resolve(Class<?> payloadType) {
        var messageType = mappings.get(payloadType);
        if (messageType == null) {
            throw new MessageTypeNotResolvedException(
                    "No MessageType found for payload type [" + payloadType.getName() + "]");
        }
        return messageType;
    }
}