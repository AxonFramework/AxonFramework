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

package org.axonframework.common.configuration;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.common.configuration.Component.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

/**
 * Wrapper around a {@link Map} of {@link Component Components} stored per {@link Component.Identifier}.
 * <p>
 * Provides a cleaner interface to the {@link ComponentRegistry} and {@link Configuration} when interacting with the
 * configured {@code Components}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
public class Components implements DescribableComponent {

    private final Map<Identifier<?>, Component<?>> components = new ConcurrentHashMap<>();

    /**
     * Get an {@link Optional} on the {@link Component} registered under the given {@code identifier}.
     * <p>
     * When no exact match is found with the given {@code identifier}, a {@link Class#isAssignableFrom(Class)} check is
     * done between the stored {@link Identifier#type() types} and the type of the given {@code identifier}.
     *
     * @param identifier The identifier to retrieve a {@link Component} for.
     * @param <C>        The type of the component to retrieve.
     * @return An {@link Optional} on the {@link Component} registered under the given {@code identifier}.
     * @throws AmbiguousComponentMatchException When multiple matching {@link Component Components} are found for the
     *                                          given {@code identifier}.
     */
    @Nonnull
    public <C> Optional<Component<C>> get(@Nonnull Identifier<C> identifier) {
        //noinspection unchecked
        return Optional.ofNullable((Component<C>) components.get(identifier))
                       .or(() -> {
                           List<Component<C>> matches = getComponentsAssignableTo(identifier);
                           return Optional.ofNullable(matches.isEmpty() ? null : matches.getFirst());
                       });
    }

    private <C> List<Component<C>> getComponentsAssignableTo(Identifier<C> identifier) {
        //noinspection unchecked
        List<Component<C>> matches = components.entrySet().stream()
                                               .filter(entry -> identifier.matches(entry.getKey()))
                                               .map(Map.Entry::getValue)
                                               .map(component -> (Component<C>) component)
                                               .toList();

        if (matches.size() > 1) {
            throw new AmbiguousComponentMatchException(identifier);
        }
        return matches;
    }

    /**
     * Puts the given {@code component}, identified by the given {@code identifier}, in this collection.
     *
     * @param component The component to put in this collection.
     * @param <C>       The type of the component to put.
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
     * @return The previously {@link #put(Component) put Component} identifier by the given {@code identifier}. When
     * absent, the outcome of the {@code compute} operation is returned
     */
    @Nonnull
    public <C> Component<C> computeIfAbsent(
            @Nonnull Identifier<C> identifier,
            @Nonnull Supplier<Component<C>> compute
    ) {
        //noinspection unchecked
        return (Component<C>) components.computeIfAbsent(identifier, i -> compute.get());
    }

    /**
     * Check whether there is a {@link Component} present for the given {@code identifier}.
     * <p>
     * If the given {@code identifier} has a nullable {@link Identifier#name() name}, <b>all</b> identifiers of this
     * collection trigger a match if their {@link Identifier#type() type} is assignable to the given
     * {@code identifier's} type.
     *
     * @param identifier The identifier for which to check if there is a {@link Component} present.
     * @return {@code true} if this collection contains a {@link Component} identified by the given {@code identifier},
     * {@code false} otherwise.
     */
    public boolean contains(@Nonnull Identifier<?> identifier) {
        return identifier.name() != null
                ? components.containsKey(identifier)
                : components.keySet()
                            .stream()
                            .anyMatch(identifier::matchesType);
    }

    /**
     * Returns the identifiers of the components currently registered.
     *
     * @return A set with the identifiers of registered components.
     */
    public Set<Identifier<?>> identifiers() {
        return Set.copyOf(components.keySet());
    }

    /**
     * Replace the component registered under the given {@code identifier} with the instance returned by given
     * {@code replacement} function. If no component is registered under the given identifier, nothing happens.
     * <p>
     * If the given {@code replacement} function returns null, the component registration is removed.
     *
     * @param identifier  The identifier of the component to replace.
     * @param replacement The function providing the replacement value, based on the currently registered component.
     * @param <C>         The type of component registered.
     * @return {@code true} if a component is present and has been replaced, {@code false} if no component was present,
     * or has been removed by the replacement function.
     */
    public <C> boolean replace(@Nonnull Identifier<C> identifier,
                               @Nonnull UnaryOperator<Component<C>> replacement) {
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
     * @param processor The action to invoke for each component.
     */
    public void postProcessComponents(@Nonnull Consumer<Component<?>> processor) {
        requireNonNull(processor, "The component post processor must be null.");
        components.values().forEach(processor);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("components", components);
    }
}
