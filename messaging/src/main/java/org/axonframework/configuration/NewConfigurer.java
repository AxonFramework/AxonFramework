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
import org.axonframework.common.Assert;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.configuration.Component.Identifier;

import java.util.function.Consumer;

/**
 * The starting point when configuring any Axon Framework application.
 * <p>
 * Provides utilities to {@link #registerComponent(Class, ComponentFactory) register components},
 * {@link #registerDecorator(Class, int, ComponentDecorator) decorators} of these components, check if a component
 * {@link #hasComponent(Class) exists}, register {@link #registerEnhancer(ConfigurationEnhancer) enhancers} for the
 * entire configurer, and {@link #registerModule(Module) modules}.
 *
 * @param <S> The type of configurer this implementation returns. This generic allows us to support fluent interfacing.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 3.0.0
 */
// TODO Rename to Configurer once the old Configurer is removed
public interface NewConfigurer<S extends NewConfigurer<S>> extends LifecycleOperations, DescribableComponent {

    /**
     * Registers a {@link Component} that should be made available to other {@link Component components} or
     * {@link Module modules} in the {@link NewConfiguration} that this {@code Configurer} will result in.
     * <p>
     * The given {@code factory} function gets the {@link NewConfiguration configuration} as input, and is expected to
     * provide the component as output. The component will be registered under an {@link Identifier} based on the given
     * {@code type}.
     * <p>
     * Note that registering a component twice for the same {@code type} will remove the previous registration!
     *
     * @param type    The declared type of the component to build, typically an interface.
     * @param factory The factory building the component.
     * @param <C>     The type of component the {@code factory} builds.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    default <C> S registerComponent(@Nonnull Class<C> type,
                                    @Nonnull ComponentFactory<C> factory) {
        return registerComponent(type, type.getSimpleName(), factory);
    }

    /**
     * Registers a {@link Component} that should be made available to other {@link Component components} or
     * {@link Module modules} in the {@link NewConfiguration} that this {@code Configurer} will result in.
     * <p>
     * The given {@code factory} function gets the {@link NewConfiguration configuration} as input, and is expected to
     * provide the component as output. The component will be registered under an {@link Identifier} based on the given
     * {@code type} and {@code name} combination.
     * <p>
     * Note that registering a component twice for the same {@code type} and {@code name} will remove the previous
     * registration!
     *
     * @param type    The declared type of the component to build, typically an interface.
     * @param name    The name of the component to build.
     * @param factory The factory building the component.
     * @param <C>     The type of component the {@code factory} builds.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    <C> S registerComponent(@Nonnull Class<C> type,
                            @Nonnull String name,
                            @Nonnull ComponentFactory<C> factory);

    /**
     * Registers a {@link Component} {@link ComponentDecorator decorator} that will act on <b>all</b>
     * {@link #registerComponent(Class, ComponentFactory) registered} components of the given {@code type}, regardless
     * of component name.
     * <p>
     * Decorators are invoked based on the given {@code order}. Decorators with a lower {@code order} will be executed
     * before those with a higher one. If decorators depend on the result of another decorator, their {@code order} must
     * be strictly higher than the one they depend on.
     * <p>
     * The order in which components are decorated by decorators with the same {@code order} is undefined.
     *
     * @param type      The declared type of the component to decorate, typically an interface.
     * @param order     The order of the given {@code decorator} among other decorators.
     * @param decorator The decoration function for a component of type {@code C}.
     * @param <C>       The type of component the {@code decorator} decorates.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    <C> S registerDecorator(@Nonnull Class<C> type,
                            int order,
                            @Nonnull ComponentDecorator<C> decorator);

    /**
     * Registers a {@link Component} {@link ComponentDecorator decorator} that will act on
     * {@link #registerComponent(Class, String, ComponentFactory) registered} components of the given {@code type}
     * <b>and</b> {@code name} combination.
     * <p>
     * Decorators are invoked based on the given {@code order}. Decorators with a lowe {@code order} will be executed
     * before those with a higher one. If decorators depend on the result of another decorator, their {@code order} must
     * be strictly higher than the one they depend on.
     * <p>
     * The order in which components are decorated by decorators with the same {@code order} is undefined.
     *
     * @param type      The declared type of the component to decorate, typically an interface.
     * @param name      The name of the component to decorate.
     * @param order     The order of the given {@code decorator} among other decorators.
     * @param decorator The decoration function for a component of type {@code C}.
     * @param <C>       The type of component the {@code decorator} decorates.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    default <C> S registerDecorator(@Nonnull Class<C> type,
                                    @Nonnull String name,
                                    int order,
                                    @Nonnull ComponentDecorator<C> decorator) {
        Assert.nonEmpty(name, "The name cannot be empty or null.");
        return registerDecorator(
                type, order,
                (config, n, delegate) -> name.equals(n) ? decorator.decorate(config, n, delegate) : delegate
        );
    }

    /**
     * Check whether there is a {@link Component} registered with this {@code Configurer} for the given {@code type}.
     *
     * @param type The type of the {@link Component} to check if it exists, typically an interface.
     * @return {@code true} when there is a {@link Component} registered under the given {@code type}, {@code false}
     * otherwise.
     */
    default boolean hasComponent(@Nonnull Class<?> type) {
        return hasComponent(type, type.getSimpleName());
    }

    /**
     * Check whether there is a {@link Component} registered with this {@code Configurer} for the given {@code type} and
     * {@code name} combination.
     *
     * @param type The type of the {@link Component} to check if it exists, typically an interface.
     * @param name The name of the {@link Component} to check if it exists.
     * @return {@code true} when there is a {@link Component} registered under the given {@code type} and
     * {@code name combination}, {@code false} otherwise.
     */
    boolean hasComponent(@Nonnull Class<?> type,
                         @Nonnull String name);

    /**
     * Registers an {@link ConfigurationEnhancer} with {@code this Configurer}.
     * <p>
     * An {@code enhancer} is able to invoke <em>any</em> of the method on this {@code Configurer}, allowing it to add
     * (sensible) defaults, decorate {@link Component components}, or replace components entirely.
     * <p>
     * An enhancer's {@link ConfigurationEnhancer#enhance(NewConfigurer)} method is invoked during the
     * {@link ApplicationConfigurer#build()} of {@code this Configurer}. This right before the configurer resolves to a
     * {@link NewConfiguration}. When multiple enhancers have been provided, their {@link ConfigurationEnhancer#order()}
     * dictates the enhancement order. For enhancer with the same order, the insert order is leading.
     *
     * @param enhancer The configuration enhancer to enhance {@code this Configurer} during
     *                 {@link ApplicationConfigurer#build()}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    S registerEnhancer(@Nonnull ConfigurationEnhancer enhancer);

    /**
     * Registers a {@link Module} {@code builder} with this {@code Configurer}.
     * <p>
     * Note that a {@code Module} is able to access the components of {@code this Configurer} upon construction, but not
     * vice versa. As such, the {@code Module} maintains encapsulation.
     * <p>
     * The given {@code builder} is typically constructed immediately by the {@code Configurer}.
     *
     * @param module The module builder function to register.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    S registerModule(@Nonnull Module<?> module);

    /**
     * Invokes the given {@code configureTask} if (1) this configurer has a delegate configurer and (2) that delegate is
     * of the given {@code type}.
     * <p>
     * Enables more specific {@code Configurer} implementations to invoke registration methods on its delegate
     * configurer. Through this approach, {@code this Configurer} does not have to override all methods from the
     * configurer it is delegating too.
     * <p>
     * Note that this method is typically not used directly, but instead used by more specific delegation methods like
     * {@link MessagingConfigurer#application(Consumer)}, for example.
     *
     * @param type          The delegate type to invoke the given {@code configureTask} on, if it matches with this
     *                      configurers delegate.
     * @param configureTask Lambda consuming the delegate configurer if it matches the given {@code type}.
     * @param <C>           The expected type of the delegate {@code Configurer}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     * @see MessagingConfigurer#application(Consumer)
     */
    <C extends NewConfigurer<C>> S delegate(@Nonnull Class<C> type,
                                            @Nonnull Consumer<C> configureTask);
}
