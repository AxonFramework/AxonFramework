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

package org.axonframework.eventhandling.annotation;

import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

/**
 * Factory for creating and configuring {@link SequencingPolicyResolver} instances.
 * This factory provides the default chain of resolvers that handles all built-in
 * sequencing policy annotations.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class SequencingPolicyResolverFactory {

    /**
     * Creates the default {@link SequencingPolicyResolver} chain that includes support
     * for all built-in sequencing policy annotations.
     * <p>
     * This method uses {@link ServiceLoader} to discover all available {@link SequencingPolicyResolver}
     * implementations. The resolvers are loaded in their natural order as defined by the ServiceLoader.
     * <p>
     * The built-in resolvers include:
     * <ul>
     *   <li>{@link DirectSequencingPolicyResolver} - handles {@code @SequencingPolicy} annotations</li>
     *   <li>{@link SequencingByPropertyResolver} - handles {@code @SequencingByProperty} meta-annotations</li>
     * </ul>
     * <p>
     * Additional resolvers can be registered by implementing {@link SequencingPolicyResolver}
     * and adding the implementation to {@code META-INF/services/org.axonframework.eventhandling.annotation.SequencingPolicyResolver}.
     *
     * @return A configured {@link SequencingPolicyResolver} that can handle all discovered annotations
     */
    public static SequencingPolicyResolver createDefaultResolver() {
        var resolvers = ServiceLoader.load(SequencingPolicyResolver.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .collect(Collectors.toList());

        if (resolvers.isEmpty()) {
            // Fallback to hard-coded defaults if no services are found
            resolvers = List.of(
                    new DirectSequencingPolicyResolver(),
                    new SequencingByPropertyResolver()
            );
        }

        return new ChainedSequencingPolicyResolver(resolvers);
    }

    /**
     * Creates a custom {@link SequencingPolicyResolver} chain with the specified resolvers.
     * This method allows for customization of the resolution chain, for example to add
     * support for custom meta-annotations.
     *
     * @param resolvers The resolvers to include in the chain, in order of precedence
     * @return A configured {@link SequencingPolicyResolver} that uses the specified resolvers
     * @throws IllegalArgumentException if the resolvers list is null or empty
     */
    public static SequencingPolicyResolver createCustomResolver(List<SequencingPolicyResolver> resolvers) {
        return new ChainedSequencingPolicyResolver(resolvers);
    }

    private SequencingPolicyResolverFactory() {
        // Utility class - prevent instantiation
    }
}