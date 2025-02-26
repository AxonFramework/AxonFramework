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
import org.axonframework.configuration.Component.Identifier;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Interface providing access to all configured {@link Component components} in an Axon Framework application.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 3.0.0
 */
// TODO rename to Configuration once the old Configuration is removed
public interface NewConfiguration {

    /**
     * Returns the component declared under the given {@code type} or throws a {@link NullPointerException} if it does
     * not exist.
     *
     * @param type The type of component, typically the interface the component implements.
     * @param <C>  The type of component.
     * @return The component registered for the given type.
     * @throws NullPointerException Whenever there is no component present for the given {@code type}.
     */
    @Nonnull
    default <C> C getComponent(@Nonnull Class<C> type) {
        return getComponent(new Identifier<>(type));
    }

    /**
     * Returns the component declared under the given {@code type} and {@code name} or throws a
     * {@link NullPointerException} if it does not exist.
     *
     * @param type The type of component, typically the interface the component implements.
     * @param name The name of the component to retrieve.
     * @param <C>  The type of component.
     * @return The component registered for the given {@code type} and {@code name}.
     * @throws NullPointerException Whenever there is no component present for the given {@code type} and {@code name}.
     */
    @Nonnull
    default <C> C getComponent(@Nonnull Class<C> type,
                               @Nonnull String name) {
        return getComponent(new Identifier<>(type, name));
    }

    /**
     * Returns the component declared under the given {@code identifier} or throws a {@link NullPointerException} if it
     * does not exist.
     *
     * @param identifier The identifier of the component, typically the interface the component implements.
     * @param <C>        The type of component.
     * @return The component registered for the given identifier.
     * @throws NullPointerException Whenever there is no component present for the given {@code identifier}.
     */
    @Nonnull
    default <C> C getComponent(@Nonnull Identifier<C> identifier) {
        return getOptionalComponent(identifier)
                .orElseThrow(() -> new NullPointerException("No component found for [" + identifier + "]"));
    }

    /**
     * Returns the component declared under the given {@code type} within an {@code Optional}.
     *
     * @param type The type of component, typically the interface the component implements.
     * @param <C>  The type of component.
     * @return An {@code Optional} wrapping the component registered for the given {@code type}. Might be empty when
     * there is no component present for the given {@code type}.
     */
    default <C> Optional<C> getOptionalComponent(@Nonnull Class<C> type) {
        return getOptionalComponent(new Identifier<>(type));
    }

    /**
     * Returns the component declared under the given {@code type} and {@code name} within an {@code Optional}.
     *
     * @param type The type of component, typically the interface the component implements.
     * @param name The name of the component to retrieve.
     * @param <C>  The type of component.
     * @return An {@code Optional} wrapping the component registered for the given {@code type} and {@code name}. Might
     * be empty when there is no component present for the given {@code type} and {@code name}.
     */
    default <C> Optional<C> getOptionalComponent(@Nonnull Class<C> type,
                                                 @Nonnull String name) {
        return getOptionalComponent(new Identifier<>(type, name));
    }

    /**
     * Returns the component declared under the given {@code identifier} within an {@code Optional}.
     *
     * @param identifier The identifier of component, typically the interface the component implements.
     * @param <C>        The type of component.
     * @return An {@code Optional} wrapping the component registered for the given {@code identifier}. Might be empty
     * when there is no component present for the given {@code identifier}.
     */
    <C> Optional<C> getOptionalComponent(@Nonnull Identifier<C> identifier);

    /**
     * Returns the component declared under the given {@code type}, reverting to the given {@code defaultImpl} if no
     * such component is defined.
     * <p>
     * When no component was previously registered, the default is then configured as the component for the given type.
     *
     * @param type        The type of component, typically the interface the component implements.
     * @param defaultImpl The supplier of the default component to return if it was not registered.
     * @param <C>         The type of component.
     * @return The component declared under the given {@code type}, reverting to the given {@code defaultImpl} if no
     * such component is defined.
     */
    @Nonnull
    default <C> C getComponent(@Nonnull Class<C> type,
                               @Nonnull Supplier<C> defaultImpl) {
        return getComponent(new Identifier<>(type), defaultImpl);
    }

    /**
     * Returns the component declared under the given {@code type} and {@code name}, reverting to the given
     * {@code defaultImpl} if no such component is defined.
     * <p>
     * When no component was previously registered, the default is then configured as the component for the given type.
     *
     * @param type        The type of component, typically the interface the component implements.
     * @param name        The name of the component to retrieve.
     * @param defaultImpl The supplier of the default component to return if it was not registered.
     * @param <C>         The type of component.
     * @return The component declared under the given {@code type} and {@code name}, reverting to the given
     * {@code defaultImpl} if no such component is defined.
     */
    @Nonnull
    default <C> C getComponent(@Nonnull Class<C> type,
                               @Nonnull String name,
                               @Nonnull Supplier<C> defaultImpl) {
        return getComponent(new Identifier<>(type, name), defaultImpl);
    }

    /**
     * Returns the component declared under the given {@code identifier}, reverting to the given {@code defaultImpl} if
     * no such component is defined.
     * <p>
     * When no component was previously registered, the default is then configured as the component for the given type.
     *
     * @param identifier  The identifier of component, typically the interface the component implements.
     * @param defaultImpl The supplier of the default component to return if it was not registered.
     * @param <C>         The type of component.
     * @return The component declared under the given {@code identifier}, reverting to the given {@code defaultImpl} if
     * no such component is defined.
     */
    @Nonnull
    <C> C getComponent(@Nonnull Identifier<C> identifier,
                       @Nonnull Supplier<C> defaultImpl);

    /**
     * Finds all configuration modules of given {@code type} within this configuration.
     *
     * @param type The type of the {@link Module Modules} to retrieve.
     * @param <M>  The type of the {@link Module}.
     * @return The {@code Modules} matching the given {@code type} that are defined in this configuration.
     */
    @SuppressWarnings("unchecked")
    default <M extends Module> List<M> getModulesFor(@Nonnull Class<M> type) {
        return getModules().stream()
                           .filter(m -> m.isType(type))
                           .map(m -> (M) m)
                           .toList();
    }

    /**
     * Returns all modules that have been registered with this configuration.
     *
     * @return All modules that have been registered with this configuration.
     */
    List<Module> getModules();
}
