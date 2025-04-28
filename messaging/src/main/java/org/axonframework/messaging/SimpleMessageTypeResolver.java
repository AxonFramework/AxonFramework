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
 * When a payload type is not explicitly registered, the resolver can either:
 * <ul>
 *     <li>Use a fallback resolver (configured via {@link TypeResolverPhase#fallback(MessageTypeResolver)}).</li>
 *     <li>Throw an exception (configured via {@link TypeResolverPhase#throwsIfUnknown()}, which is the default behavior).</li>
 * </ul>
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
    private SimpleMessageTypeResolver(
            @Nonnull Map<Class<?>, MessageType> mappings
    ) {
        Objects.requireNonNull(mappings, "Mappings may not be null");
        this.mappings = mappings;
    }

    /**
     * Starts building a new resolver and registers the first payload type with a fixed message type.
     *
     * @param payloadType The class to register the fixed message type for.
     * @param messageType The message type to use for the given payload type.
     * @return The type resolver phase for further configuration.
     */
    public static TypeResolverPhase message(@Nonnull Class<?> payloadType, @Nonnull MessageType messageType) {
        Map<Class<?>, MessageType> initialMappings = new HashMap<>();
        initialMappings.put(payloadType, messageType);
        return new InternalTypeResolverPhase(initialMappings, null);
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

    /**
     * Interface representing the phase where type mappings are registered.
     */
    public interface TypeResolverPhase {

        /**
         * Registers a fixed {@link MessageType} for the given payload type.
         *
         * @param payloadType The class to register the fixed message type for.
         * @param messageType The message type to use for the given payload type.
         * @return The current phase for further configuration.
         * @throws IllegalArgumentException if a resolver is already registered for the given payload type.
         */
        TypeResolverPhase message(@Nonnull Class<?> payloadType, @Nonnull MessageType messageType);

        /**
         * Configures the resolver to throw an exception when no specific resolver is found for a payload type.
         *
         * @return The completed {@link SimpleMessageTypeResolver}.
         */
        MessageTypeResolver throwsIfUnknown();

        /**
         * Configures the resolver to use the specified default resolver when no specific resolver is found for a
         * payload type.
         *
         * @param resolver The default resolver to use.
         * @return The completed {@link SimpleMessageTypeResolver}.
         */
        MessageTypeResolver fallback(MessageTypeResolver resolver);
    }

    private record InternalTypeResolverPhase(
            Map<Class<?>, MessageType> mappings,
            MessageTypeResolver defaultResolver
    ) implements TypeResolverPhase {

        @Override
        public TypeResolverPhase message(@Nonnull Class<?> payloadType, @Nonnull MessageType messageType) {
            if (mappings.containsKey(payloadType)) {
                throw new IllegalArgumentException(
                        "A MessageType is already defined for payload type [" + payloadType.getName() + "]");
            }

            Map<Class<?>, MessageType> newMappings = new HashMap<>(mappings);
            newMappings.put(payloadType, messageType);

            return new InternalTypeResolverPhase(newMappings, defaultResolver);
        }

        @Override
        public MessageTypeResolver throwsIfUnknown() {
            return new SimpleMessageTypeResolver(mappings);
        }

        @Override
        public MessageTypeResolver fallback(MessageTypeResolver resolver) {
            return new FallbackMessageTypeResolver(throwsIfUnknown(), resolver);
        }
    }
}