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

package org.axonframework.configuration;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import jakarta.annotation.Nonnull;
import org.axonframework.configuration.Component.Identifier;

/**
 * Wrapper around a {@link Map} of {@link Component Components} stored per {@link Class} type.
 * <p>
 * Provides a cleaner interface to the {@link NewConfigurer} and {@link NewConfiguration} when interacting with the
 * configured {@code Components}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class Components {

    private final Map<Identifier<?>, Component<?>> components = new ConcurrentHashMap<>();

    @Nonnull
    public <C> C get(@Nonnull Identifier<C> type) {
        return getOptional(type).orElseThrow(() -> new NullPointerException("No component found for type: " + type));
    }

    public <C> Optional<C> getOptional(@Nonnull Identifier<C> type) {
        return getOptionalComponent(type).map(Component::get);
    }

    @Nonnull
    public <C> Component<C> getComponent(@Nonnull Identifier<C> type) {
        return getOptionalComponent(type)
                .orElseThrow(() -> new NullPointerException("No component found for type: " + type));
    }

    @Nonnull
    public <C> Optional<Component<C>> getOptionalComponent(@Nonnull Identifier<C> type) {
        //noinspection unchecked
        return Optional.ofNullable((Component<C>) components.get(type));
    }

    public <C> Component<C> put(@Nonnull Identifier<C> type, @Nonnull Component<C> component) {
        //noinspection unchecked
        return (Component<C>) components.put(type, component);
    }

    public <T> Component<T> computeIfAbsent(
            @Nonnull Identifier<T> type,
            @Nonnull Function<? super Identifier<?>, ? extends Component<?>> mappingFunction
    ) {
        //noinspection unchecked
        return (Component<T>) components.computeIfAbsent(type, mappingFunction);
    }

    /**
     * Check whether there is a {@link Component} present for the given {@code identifier}.
     *
     * @param identifier The identifier for which to check if there is a {@link Component} present.
     * @return {@code true} if this collection contains a {@link Component} identified by the given {@code identifier},
     * {@code false} otherwise.
     */
    public boolean contains(Identifier<?> identifier) {
        return components.containsKey(identifier);
    }
}
