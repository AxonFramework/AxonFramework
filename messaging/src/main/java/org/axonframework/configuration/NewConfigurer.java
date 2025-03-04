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

/**
 * The starting point when configuring any Axon Framework application.
 * <p>
 * Provides utilities to {@link #registerComponent(Class, ComponentBuilder) register components},
 * {@link #registerDecorator(Class, int, ComponentDecorator) decorators} of these components, set
 * {@link #enhance(ConfigurerEnhancer) enhancers} for the entire configurer, and
 * {@link #registerModule(ModuleBuilder) modules}.
 *
 * @param <S> The type of configurer this implementation returns. This generic allows us to support fluent interfacing.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 3.0.0
 */
// TODO Rename to Configurer once the old Configurer is removed
public interface NewConfigurer<S extends NewConfigurer<S>> extends LifecycleOperations {

    /**
     * Registers a {@link Component} that should be made available to other {@link Component components} or
     * {@link Module modules} in the {@link NewConfiguration} that this {@code Configurer} will result in.
     * <p>
     * The given {@code builder} function gets the {@link NewConfiguration configuration} as input, and is expected to
     * provide the component as output. The component will be registered under an {@link Identifier} based on the given
     * {@code type}.
     * <p>
     * Note that registering a component twice for the same {@code type} will remove the previous registration!
     *
     * @param type    The declared type of the component to build, typically an interface.
     * @param builder The builder function of this component.
     * @param <C>     The type of component the {@code builder} builds.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    default <C> S registerComponent(@Nonnull Class<C> type,
                                    @Nonnull ComponentBuilder<C> builder) {
        return registerComponent(type, type.getSimpleName(), builder);
    }

    /**
     * Registers a {@link Component} that should be made available to other {@link Component components} or
     * {@link Module modules} in the {@link NewConfiguration} that this {@code Configurer} will result in.
     * <p>
     * The given {@code builder} function gets the {@link NewConfiguration configuration} as input, and is expected to
     * provide the component as output. The component will be registered under an {@link Identifier} based on the given
     * {@code type} and {@code name} combination.
     * <p>
     * Note that registering a component twice for the same {@code type} and {@code name} will remove the previous
     * registration!
     *
     * @param type    The declared type of the component to build, typically an interface.
     * @param name    The name of the component to build.
     * @param builder The builder function of this component.
     * @param <C>     The type of component the {@code builder} builds.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    <C> S registerComponent(@Nonnull Class<C> type,
                            @Nonnull String name,
                            @Nonnull ComponentBuilder<C> builder);

    /**
     * Registers a {@link Component} {@link ComponentDecorator decorator} that will act on
     * {@link #registerComponent(Class, ComponentBuilder) registered} components of the given {@code type}.
     * <p>
     * The {@code order} parameter dictates at what point in time the given {@code decorator} is invoked during
     * construction of the {@code Component} it decorators. If a {@code ComponentDecorator} was already present at the
     * given {@code order}, it will be replaced by the given {@code decorator}
     *
     * @param type      The declared type of the component to decorate, typically an interface.
     * @param order     The order of the given {@code decorator} among other decorators. Becomes important whenever
     *                  multiple decorators are present for the given {@code type} <b>and</b> when ordering of these
     *                  decorators is important.
     * @param decorator The decoration function of this component.
     * @param <C>       The type of component the {@code decorator} decorates.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    default <C> S registerDecorator(@Nonnull Class<C> type,
                                    int order,
                                    @Nonnull ComponentDecorator<C> decorator) {
        return registerDecorator(type, type.getSimpleName(), order, decorator);
    }

    /**
     * Registers a {@link Component} {@link ComponentDecorator decorator} that will act on
     * {@link #registerComponent(Class, String, ComponentBuilder) registered} components of the given {@code type} and
     * {@code name} combination.
     * <p>
     * The {@code order} parameter dictates at what point in time the given {@code decorator} is invoked during
     * construction of the {@code Component} it decorators. If a {@code ComponentDecorator} was already present at the
     * given {@code order}, it will be replaced by the given {@code decorator}
     *
     * @param type      The declared type of the component to decorate, typically an interface.
     * @param name      The name of the component to decorate.
     * @param order     The order of the given {@code decorator} among other decorators. Becomes important whenever
     *                  multiple decorators are present for the given {@code type} <b>and</b> when ordering of these
     *                  decorators is important.
     * @param decorator The decoration function of this component.
     * @param <C>       The type of component the {@code decorator} decorates.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    <C> S registerDecorator(@Nonnull Class<C> type,
                            @Nonnull String name,
                            int order,
                            @Nonnull ComponentDecorator<C> decorator);

    /**
     * Registers a {@link Module} {@code builder} with this {@code Configurer}.
     * <p>
     * Note that a {@code Module} is able to access the components of {@code this Configurer} upon construction, but not
     * vice versa. As such, the {@code Module} maintains encapsulation.
     * <p>
     * The given {@code builder} is typically constructed immediately by the {@code Configurer}.
     *
     * @param builder The module builder function to register.
     * @param <M>     The type of {@link Module} constructed by the given {@code builder}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    <M extends Module<M>> S registerModule(@Nonnull ModuleBuilder<M> builder);

    /**
     * Returns the completely initialized {@link NewConfiguration} instance of type {@code C} built using this
     * {@code Configurer} implementation.
     * <p>
     * It is not recommended to change any configuration on {@code this NewConfigurer} once this method is called.
     *
     * @param <C> The {@link NewConfiguration} implementation of type {@code C} returned by this method.
     * @return The fully initialized {@link NewConfiguration} instance of type {@code C}.
     */
    <C extends NewConfiguration> C build();
}
