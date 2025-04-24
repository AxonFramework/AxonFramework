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

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link MessageTypeResolver} implementation that allows registering custom resolvers for specific payload types.
 * <p>
 * This resolver provides a fluent API to register type resolvers that return a {@link MessageType} for specific payload
 * types.
 * <p>
 * When a payload type is not explicitly registered, the resolver can either:
 * <ul>
 *     <li>Use a fallback resolver (configured via {@link TypeResolverPhase#onUnknownUse(MessageTypeResolver)})</li>
 *     <li>Throw an exception (configured via {@link TypeResolverPhase#onUnknownFail()}, which is the default behavior)</li>
 * </ul>
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class LambdaBasedMessageTypeResolver implements MessageTypeResolver {

    private final Map<Class<?>, MessageTypeResolver> resolvers;
    private final MessageTypeResolver defaultResolver;

    /**
     * Private constructor used by the builder to create the resolver with the configured resolvers and default
     * behavior.
     *
     * @param resolvers       The map of payload types to their specific resolvers
     * @param defaultResolver The default resolver to use when no specific resolver is found, or null to throw an
     *                        exception
     */
    private LambdaBasedMessageTypeResolver(Map<Class<?>, MessageTypeResolver> resolvers,
                                           MessageTypeResolver defaultResolver) {
        this.resolvers = resolvers;
        this.defaultResolver = defaultResolver;
    }

    /**
     * Starts building a new resolver and registers the first payload type with a custom resolver.
     *
     * @param payloadType The class to register the resolver for
     * @param resolver    The resolver that resolves the payload type class to a {@link MessageType}
     * @return The type resolver phase for further configuration
     */
    public static TypeResolverPhase on(Class<?> payloadType, MessageTypeResolver resolver) {
        Map<Class<?>, MessageTypeResolver> initialResolvers = new HashMap<>();
        initialResolvers.put(payloadType, resolver);
        return new InternalTypeResolverPhase(initialResolvers, null);
    }

    /**
     * Starts building a new resolver and registers the first payload type with a fixed message type.
     *
     * @param payloadType The class to register the fixed message type for
     * @param messageType The message type to use for the given payload type
     * @return The type resolver phase for further configuration
     */
    public static TypeResolverPhase on(Class<?> payloadType, MessageType messageType) {
        return on(payloadType, payloadType1 -> messageType);
    }

    @Override
    public MessageType resolve(Class<?> payloadType) {
        var resolver = resolvers.get(payloadType);
        if (resolver != null) {
            return resolver.resolve(payloadType);
        }
        if (defaultResolver == null) {
            throw new IllegalArgumentException("No resolver found for payload type [" + payloadType.getName() + "]");
        }
        return defaultResolver.resolve(payloadType);
    }

    /**
     * Interface representing the phase where type resolvers are registered.
     */
    public interface TypeResolverPhase {

        /**
         * Registers a resolver that returns a {@link MessageType} for the given payload type.
         *
         * @param payloadType The class to register the resolver for
         * @param resolver    The resolver that produces a {@link MessageType} for the given payload type
         * @return The current phase for further configuration
         * @throws IllegalArgumentException if a resolver is already registered for the given payload type
         */
        TypeResolverPhase on(Class<?> payloadType, MessageTypeResolver resolver);

        /**
         * Registers a fixed {@link MessageType} for the given payload type.
         *
         * @param payloadType The class to register the fixed message type for
         * @param messageType The message type to use for the given payload type
         * @return The current phase for further configuration
         * @throws IllegalArgumentException if a resolver is already registered for the given payload type
         */
        default TypeResolverPhase on(Class<?> payloadType, MessageType messageType) {
            return on(payloadType, __ -> messageType);
        }

        /**
         * Configures the resolver to throw an exception when no specific resolver is found for a payload type. This is
         * the default behavior.
         *
         * @return The completed {@link LambdaBasedMessageTypeResolver}
         */
        LambdaBasedMessageTypeResolver onUnknownFail();

        /**
         * Configures the resolver to use the specified default resolver when no specific resolver is found for a
         * payload type.
         *
         * @param resolver The default resolver to use
         * @return The completed {@link LambdaBasedMessageTypeResolver}
         */
        LambdaBasedMessageTypeResolver onUnknownUse(MessageTypeResolver resolver);
    }

    /**
     * Implementation of the {@link TypeResolverPhase} interface.
     */
    private record InternalTypeResolverPhase(
            Map<Class<?>, MessageTypeResolver> resolvers,
            MessageTypeResolver defaultResolver
    ) implements TypeResolverPhase {

        @Override
        public TypeResolverPhase on(Class<?> payloadType, MessageTypeResolver resolver) {
            if (resolvers.containsKey(payloadType)) {
                throw new IllegalArgumentException(
                        "A resolver is already registered for payload type [" + payloadType.getName() + "]");
            }

            Map<Class<?>, MessageTypeResolver> newResolvers = new HashMap<>(resolvers);
            newResolvers.put(payloadType, resolver);

            return new InternalTypeResolverPhase(newResolvers, defaultResolver);
        }

        @Override
        public LambdaBasedMessageTypeResolver onUnknownFail() {
            return new LambdaBasedMessageTypeResolver(resolvers, null);
        }

        @Override
        public LambdaBasedMessageTypeResolver onUnknownUse(MessageTypeResolver resolver) {
            return new LambdaBasedMessageTypeResolver(resolvers, resolver);
        }
    }
}