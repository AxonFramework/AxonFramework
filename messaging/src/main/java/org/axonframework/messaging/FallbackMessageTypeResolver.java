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

import java.util.Objects;

/**
 * Implementation of {@link MessageTypeResolver} that combines two resolvers in a fallback pattern. When the primary
 * resolver fails to resolve a {@link MessageType} for a payload type, this implementation will delegate to a secondary
 * fallback resolver.
 * <p>
 * This allows for composing resolution strategies where certain payload types might be resolved by specialized resolvers,
 * falling back to more generic approaches when specialized resolution fails.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class FallbackMessageTypeResolver implements MessageTypeResolver {

    private final MessageTypeResolver delegate;
    private final MessageTypeResolver fallback;

    /**
     * Initializes a new instance with the given primary {@code delegate} and {@code fallback}
     * resolvers.
     *
     * @param delegate The primary resolver to attempt resolution with first, not {@code null}.
     * @param fallback The fallback resolver to use when the delegate fails, not {@code null}.
     * @throws NullPointerException When either the {@code delegate} or {@code fallback} is {@code null}.
     */
    public FallbackMessageTypeResolver(@Nonnull MessageTypeResolver delegate, @Nonnull MessageTypeResolver fallback) {
        Objects.requireNonNull(delegate, "Delegate may not be null");
        Objects.requireNonNull(fallback, "Fallback may not be null");
        this.delegate = delegate;
        this.fallback = fallback;
    }

    /**
     * {@inheritDoc}
     * <p>
     * First attempts to resolve the {@link MessageType} using the delegate resolver. If the delegate throws a
     * {@link MessageTypeNotResolvedException}, resolution is delegated to the fallback resolver.
     */
    @Override
    public MessageType resolve(Class<?> payloadType) {
        try {
            return delegate.resolve(payloadType);
        } catch (MessageTypeNotResolvedException e) {
            return fallback.resolve(payloadType);
        }
    }
}
