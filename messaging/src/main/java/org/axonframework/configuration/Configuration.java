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
import org.axonframework.common.infra.DescribableComponent;

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
public interface Configuration extends DescribableComponent {

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
        return getComponent(type, type.getSimpleName());
    }

    /**
     * Returns the component declared under the given {@code type} and {@code name} or throws a
     * {@link NullPointerException} if it does not exist.
     *
     * @param type The type of component, typically the interface the component implements.
     * @param name The name of the component to retrieve.
     * @param <C>  The type of component.
     * @return The component registered for the given {@code type} and {@code name}.
     * @throws ComponentNotFoundException Whenever there is no component present for the given {@code type} and
     *                                    {@code name}.
     */
    @Nonnull
    default <C> C getComponent(@Nonnull Class<C> type,
                               @Nonnull String name) {
        return getOptionalComponent(type, name)
                .orElseThrow(() -> new ComponentNotFoundException(type, name));
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
        return getOptionalComponent(type, type.getSimpleName());
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
    <C> Optional<C> getOptionalComponent(@Nonnull Class<C> type,
                                         @Nonnull String name);

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
        return getComponent(type, type.getSimpleName(), defaultImpl);
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
    <C> C getComponent(@Nonnull Class<C> type,
                       @Nonnull String name,
                       @Nonnull Supplier<C> defaultImpl);

    /**
     * Returns all {@code Configurations} from the {@link Module Modules} that have been
     * {@link ComponentRegistry#registerModule(Module) registered} with this {@code Configuration}.
     *
     * @return The resulting {@code Configuration} from each
     * {@link ComponentRegistry#registerModule(Module) registered module} with this {@code Configuration}.
     */
    List<Configuration> getModuleConfigurations();

    /**
     * Returns the {@code Configuration} from the {@link Module} with the given {@code name}.
     * <p>
     * The module must have been {@link ComponentRegistry#registerModule(Module) registered} with this
     * {@code Configuration} directly.
     *
     * @param name The name of the {@link Module} to get the configuration for.
     * @return An {@code Optional} with the {@code Configuration} for a {@link Module} with the given {@code name} or an
     * empty optional if no module exists with that name.
     */
    Optional<Configuration> getModuleConfiguration(@Nonnull String name);

    /**
     * Returns the parent configuration of this configuration, if the parent configuration exists. Components can use
     * this to build hierarchical components, which prefer components from a child configuration over components from a
     * parent configuration.
     *
     * @return The parent configuration of this configuration, or {@code null} if no parent configuration exists.
     */
    @Nullable
    Configuration getParent();
}
