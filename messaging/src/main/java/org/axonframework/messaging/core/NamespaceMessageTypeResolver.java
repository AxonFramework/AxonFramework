/*
 * Copyright (c) 2010-2026. Axon Framework
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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementation of the {@link MessageTypeResolver} that maintains a mapping of payload types to their corresponding
 * {@link MessageType}s. This resolver organizes message types under a common namespace.
 * <p>
 * Use the {@link #namespace(String)} static method to start building a resolver with a default namespace.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class NamespaceMessageTypeResolver implements MessageTypeResolver {

    private final Map<Class<?>, MessageType> mappings;

    private NamespaceMessageTypeResolver(
            @Nonnull Map<Class<?>, MessageType> mappings
    ) {
        Objects.requireNonNull(mappings, "Mappings may not be null.");
        this.mappings = mappings;
    }

    /**
     * Sets a new namespace for subsequent message mappings.
     *
     * @param namespace The namespace to use for message types created after this call.
     * @return The builder instance for method chaining.
     */
    public static Builder namespace(@Nonnull String namespace) {
        Objects.requireNonNull(namespace, "Namespace may not be null.");
        return new Builder(namespace);
    }

    @Override
    public Optional<MessageType> resolve(@Nonnull Class<?> payloadType) {
        Objects.requireNonNull(payloadType, "Payload Type may not be null.");
        var messageType = mappings.get(payloadType);
        return Optional.ofNullable(messageType);
    }

    /**
     * Builder interface for constructing a {@link NamespaceMessageTypeResolver}.
     * <p>
     * Allows for a fluent API to configure message type mappings under a common namespace.
     */
    public static class Builder {

        private String namespace;
        private final Map<Class<?>, MessageType> mappings = new HashMap<>();

        private Builder(String namespace) {
            this.namespace = namespace;
        }

        /**
         * Sets a new namespace for subsequent message mappings.
         *
         * @param namespace The namespace to use for message types created after this call.
         * @return The builder instance for method chaining.
         */
        public Builder namespace(@Nonnull String namespace) {
            Objects.requireNonNull(namespace, "Namespace may not be null.");
            this.namespace = namespace;
            return this;
        }

        /**
         * Registers a mapping for the given {@code payloadType} to a {@link MessageType} with the specified
         * attributes.
         *
         * @param payloadType The class of the payload to register a mapping for.
         * @param localName   The local name component of the resulting {@link MessageType}.
         * @param version     The version component of the resulting {@link MessageType}.
         * @return The builder instance for method chaining.
         * @throws IllegalArgumentException If a mapping for the given {@code payloadType} already exists. Mappings are
         *                                  global, not in the scope of certain namespace.
         */
        public Builder message(@Nonnull Class<?> payloadType, @Nonnull String localName, @Nonnull String version) {
            Objects.requireNonNull(payloadType, "Payload Type may not be null.");
            Objects.requireNonNull(localName, "Local Name may not be null.");
            Objects.requireNonNull(version, "Version may not be null.");

            if (mappings.containsKey(payloadType)) {
                throw new IllegalArgumentException(
                        "A MessageType is already defined for payload type [" + payloadType.getName() + "]");
            }

            mappings.put(payloadType, new MessageType(namespace, localName, version));

            return this;
        }

        /**
         * Finalizes the builder and returns a {@link MessageTypeResolver} that:
         * <ul>
         *     <li>for {@link MessageTypeResolver#resolveOrThrow} throws a {@link MessageTypeNotResolvedException} when encountering unmapped payload types</li>
         *     <li>for {@link MessageTypeResolver#resolve} returns an {@link Optional#empty()} when encountering unmapped payload types</li>
         * </ul>
         *
         * @return A {@link MessageTypeResolver} without fallback behavior.
         */
        public MessageTypeResolver noFallback() {
            return new NamespaceMessageTypeResolver(mappings);
        }

        /**
         * Finalizes the builder and returns a {@link MessageTypeResolver} that delegates to the given {@code resolver}
         * when encountering unmapped payload types.
         *
         * @param resolver The resolver to use as fallback when this resolver cannot resolve a payload type.
         * @return A {@link MessageTypeResolver} with fallback behavior.
         */
        public MessageTypeResolver fallback(@Nonnull MessageTypeResolver resolver) {
            Objects.requireNonNull(resolver, "Message Type Resolver may not be null.");
            if (mappings.isEmpty()) {
                return resolver;
            }
            return new HierarchicalMessageTypeResolver(noFallback(), resolver);
        }
    }
}