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

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.annotations.SequencingPolicy;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * {@link SequencingPolicyResolver} implementation that chains multiple resolvers together
 * using the Chain of Responsibility pattern.
 * <p>
 * This resolver iterates through a list of {@link SequencingPolicyResolver} instances
 * in order and returns the result from the first resolver that can handle the method.
 * This allows for extensible sequencing policy resolution where new annotation types
 * can be supported by simply adding new resolvers to the chain.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class ChainedSequencingPolicyResolver implements SequencingPolicyResolver {

    private final List<SequencingPolicyResolver> resolvers;

    /**
     * Creates a new {@link ChainedSequencingPolicyResolver} with the given list of resolvers.
     *
     * @param resolvers The list of resolvers to chain together. Must not be null or empty.
     * @throws IllegalArgumentException if the resolvers list is null or empty
     */
    public ChainedSequencingPolicyResolver(@Nonnull List<SequencingPolicyResolver> resolvers) {
        this.resolvers = requireNonNull(resolvers, "Resolvers list must not be null");
        if (resolvers.isEmpty()) {
            throw new IllegalArgumentException("Resolvers list must not be empty");
        }
    }

    @Override
    public Optional<SequencingPolicy> resolve(Method method) {
        return resolvers.stream()
                       .map(resolver -> resolver.resolve(method))
                       .filter(Optional::isPresent)
                       .map(Optional::get)
                       .findFirst();
    }
}