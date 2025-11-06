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

package org.axonframework.messaging.core;

import jakarta.annotation.Nonnull;

import java.util.Objects;
import java.util.Optional;

/**
 * Implementation of {@link MessageTypeResolver} that combines two resolvers in a fallback pattern. When the primary
 * resolver fails to resolve a {@link MessageType} for a payload type, this implementation will delegate to a secondary
 * fallback resolver.
 * <p>
 * This allows for composing resolution strategies where certain payload types might be resolved by specialized
 * resolvers, falling back to more generic approaches when specialized resolution fails.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class HierarchicalMessageTypeResolver implements MessageTypeResolver {

    private final MessageTypeResolver primary;
    private final MessageTypeResolver secondary;

    /**
     * Initializes a new instance with the given primary {@code delegate} and {@code fallback} resolvers.
     *
     * @param primary The primary resolver to attempt resolution with first, not {@code null}.
     * @param secondary The fallback resolver to use when the delegate fails, not {@code null}.
     * @throws NullPointerException When either the {@code delegate} or {@code fallback} is {@code null}.
     */
    public HierarchicalMessageTypeResolver(@Nonnull MessageTypeResolver primary, @Nonnull MessageTypeResolver secondary) {
        Objects.requireNonNull(primary, "Primary may not be null.");
        Objects.requireNonNull(secondary, "Fallback may not be null.");
        this.primary = primary;
        this.secondary = secondary;
    }

    @Override
    public Optional<MessageType> resolve(@Nonnull Class<?> payloadType) {
        return primary.resolve(payloadType)
                      .or(() -> secondary.resolve(payloadType));
    }
}
