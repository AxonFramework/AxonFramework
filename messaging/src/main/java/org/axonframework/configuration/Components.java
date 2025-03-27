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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.configuration.Component.Identifier;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Wrapper around a {@link Map} of {@link Component Components} stored per {@link Component.Identifier}.
 * <p>
 * Provides a cleaner interface to the {@link ComponentRegistry} and {@link NewConfiguration} when interacting with the
 * configured {@code Components}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class Components {

    private final Map<Identifier<?>, Component<?>> components = new ConcurrentHashMap<>();

    /**
     * Get an {@link Optional} on the {@link Component} registered under the given {@code identifier}.
     *
     * @param identifier The identifier to retrieve a {@link Component} for.
     * @param <C>        The type of the component to retrieve.
     * @return An {@link Optional} on the {@link Component} registered under the given {@code identifier}.
     */
    @Nonnull
    public <C> Optional<Component<C>> get(@Nonnull Identifier<C> identifier) {
        //noinspection unchecked
        return Optional.ofNullable((Component<C>) components.get(identifier));
    }

    /**
     * Puts the given {@code component}, identified by the given {@code identifier}, in this collection.
     *
     * @param component  The component to put in this collection.
     * @param <C>        The type of the component to put.
     * @return A previous component registered under the given {@code identifier}, if present.
     */
    @Nullable
    public <C> Component<C> put(@Nonnull Component<C> component) {
        //noinspection unchecked
        return (Component<C>) components.put(component.identifier(), component);
    }

    /**
     * Computes a {@link Component} for the given {@code identifier} when absent, otherwise returns the
     * {@code Component} {@link #put(Component)} under the {@code identifier}.
     * <p>
     * The given {@code compute} operation is <b>only</b> invoked when there is no {@code Component} present for the
     * given {@code identifier}.
     *
     * @param identifier The identifier for which to check if a {@link Component} is already present.
     * @param compute    The lambda computing the {@link Component} to put into this collection when absent.
     * @param <C>        The type of the component to get and compute if absent.
     * @return The previously {@link #put(Component) put Component} identifier by the given
     * {@code identifier}. When absent, the outcome of the {@code compute} operation is returned
     */
    @Nonnull
    public <C> Component<C> computeIfAbsent(
            @Nonnull Identifier<C> identifier,
            @Nonnull Function<? super Identifier<?>, ? extends Component<?>> compute
    ) {
        //noinspection unchecked
        return (Component<C>) components.computeIfAbsent(identifier, compute);
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

    /**
     * Returns the identifiers of the components currently registered.
     *
     * @return a set with the identifiers of registered components
     */
    public Set<Identifier<?>> listComponents() {
        return Set.copyOf(components.keySet());
    }

    /**
     * Replace the component registered under the given {@code identifier} with the instance returned by given
     * {@code replacement} function. If no component is registered under the given identifier, nothing happens.
     * <p>
     * If the given {@code replacement} function returns null, the component registration is removed.
     *
     * @param identifier  The identifier of the component to replace
     * @param replacement The function providing the replacement value, based on the currently registered component
     * @param <C>         The type of component registered
     * @return {@code true} if a component is present and has been replaced, {@code false} if no component was present,
     * or has been removed by the replacement function
     */
    public <C> boolean replace(Identifier<C> identifier,
                               Function<Component<C>, Component<C>> replacement) {
        //noinspection unchecked
        Component<?> newValue = components.computeIfPresent(identifier,
                                                            (i, c) -> replacement.apply((Component<C>) c));
        return newValue != null;
    }

    /**
     * Invoke the given {@code processor} on all components that are registered in this collection.
     * <p>
     * Exceptions thrown by the processor will be rethrown to the caller before all components have been processed.
     *
     * @param processor The action to invoke for each component
     */
    public void postProcessComponents(Consumer<Component<?>> processor) {
        components.values().forEach(processor);
    }
}
