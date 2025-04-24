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
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * A {@link MessageTypeResolver} implementation that allows registering custom resolvers for specific payload types.
 * <p>
 * This resolver provides a fluent API to register type resolvers that return a {@link MessageType} for specific payload types.
 * <p>
 * When a payload type is not explicitly registered, the resolver can either:
 * <ul>
 *     <li>Use a fallback resolver (configured via {@link Customization#defaultResolverTo(MessageTypeResolver)})</li>
 *     <li>Throw an exception (default behavior)</li>
 * </ul>
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class LambdaBasedMessageTypeResolver implements MessageTypeResolver {

    private final Customization customization;

    /**
     * Constructs a {@link LambdaBasedMessageTypeResolver} with default settings. By default, this resolver
     * will throw an exception when asked to resolve an unregistered payload type.
     */
    public LambdaBasedMessageTypeResolver() {
        this(c -> c);
    }

    /**
     * Constructs a {@link LambdaBasedMessageTypeResolver} with custom configuration.
     *
     * @param customization A function to customize the resolver behavior
     */
    public LambdaBasedMessageTypeResolver(UnaryOperator<Customization> customization) {
        this.customization = customization.apply(new Customization());
    }

    @Override
    public MessageType resolve(Class<?> payloadType) {
        var resolver = customization.resolvers.get(payloadType);
        if (resolver == null) {
            if (customization.defaultResolver != null) {
                return customization.defaultResolver.resolve(payloadType);
            }
            throw new IllegalArgumentException("No resolver found for payload type [" + payloadType.getName() + "]");
        }
        return resolver.resolve(payloadType);
    }

    /**
     * Customization class for configuring the {@link LambdaBasedMessageTypeResolver}.
     */
    public class Customization {

        private final Map<Class<?>, MessageTypeResolver> resolvers = new HashMap<>();
        private MessageTypeResolver defaultResolver;

        private Customization() {
            // Empty constructor
        }

        /**
         * Registers a resolver that returns a {@link MessageType} for the given payload type.
         *
         * @param <T>         The payload type
         * @param payloadType The class to register the resolver for
         * @param resolver    The function that resolves the payload type class to a {@link MessageType}
         * @return The customization for method chaining
         * @throws IllegalArgumentException if a resolver is already registered for the given payload type
         */
        public <T> Customization on(Class<T> payloadType, Function<Class<?>, MessageType> resolver) {
            if (resolvers.containsKey(payloadType)) {
                throw new IllegalArgumentException("A resolver is already registered for payload type [" + payloadType.getName() + "]");
            }
            resolvers.put(payloadType, resolver::apply);
            return this;
        }

        /**
         * Sets a default resolver to use when no specific resolver is registered for a payload type.
         * If not set, the resolver will throw an exception when asked to resolve an unregistered type.
         *
         * @param resolver The default resolver to use
         * @return The customization for method chaining
         */
        public Customization defaultResolverTo(MessageTypeResolver resolver) {
            this.defaultResolver = resolver;
            return this;
        }
    }
}