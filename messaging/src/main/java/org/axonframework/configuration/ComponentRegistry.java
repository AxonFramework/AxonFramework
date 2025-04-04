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
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.configuration.Component.Identifier;

import java.util.Objects;

/**
 * The starting point when configuring any Axon Framework application.
 * <p>
 * Provides utilities to {@link #registerComponent(Class, ComponentFactory) register components},
 * {@link #registerDecorator(Class, int, ComponentDecorator) decorators} of these components, check if a component
 * {@link #hasComponent(Class) exists}, register {@link #registerEnhancer(ConfigurationEnhancer) enhancers} for the
 * entire configurer, and {@link #registerModule(Module) modules}.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface ComponentRegistry extends DescribableComponent {

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
    default <C> ComponentRegistry registerComponent(@Nonnull Class<C> type,
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
    default <C> ComponentRegistry registerComponent(@Nonnull Class<C> type,
                                                    @Nonnull String name,
                                                    @Nonnull ComponentFactory<? extends C> factory) {
        return registerComponent(ComponentDefinition.ofTypeAndName(type, name).withFactory(factory));
    }

    /**
     * Registers a component based on given {@code componentDefinition}.
     *
     * @param componentDefinition The definition of the component to register.
     * @param <C>                 The declared type of the component.
     * @return The current instance of the {@code Configurer} for a fluent API.
     * @throws ComponentOverrideException if the override policy is set to
     *                                    {@link org.axonframework.configuration.OverridePolicy#REJECT} and a component
     *                                    with the same type and name is already defined.
     */
    <C> ComponentRegistry registerComponent(@Nonnull ComponentDefinition<? extends C> componentDefinition);

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
     * @param <D>       The type of component the {@code decorator} returns.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    default <C, D extends C> ComponentRegistry registerDecorator(@Nonnull Class<C> type,
                                                                 int order,
                                                                 @Nonnull ComponentDecorator<C, D> decorator) {
        Objects.requireNonNull(type, "The type must not be null");
        Objects.requireNonNull(decorator, "The component decorator must not be null");
        return registerDecorator(DecoratorDefinition.forType(type).with(decorator).order(order));
    }

    /**
     * Registers a {@link ComponentDecorator decorator} that will act on
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
     * @param <D>       The type of component the {@code decorator} returns.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    default <C, D extends C> ComponentRegistry registerDecorator(@Nonnull Class<C> type,
                                                                 @Nonnull String name,
                                                                 int order,
                                                                 @Nonnull ComponentDecorator<C, D> decorator) {
        return registerDecorator(DecoratorDefinition.forTypeAndName(type, name).with(decorator).order(order));
    }

    /**
     * Registers a decorated based on the given {@code decoratorDefinition}.
     *
     * @param decoratorDefinition The definition of the decorator to apply to components.
     * @param <C>                 The declared type of component.
     * @return The current instance of the {@code Configurer} for a fluent API.
     * @see DecoratorDefinition
     */
    <C> ComponentRegistry registerDecorator(@Nonnull DecoratorDefinition<C, ? extends C> decoratorDefinition);

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
     * Registers an {@link ConfigurationEnhancer} with this ComponentRegistry.
     * <p>
     * An {@code enhancer} is able to invoke <em>any</em> of the method on this {@code ComponentRegistry}, allowing it
     * to add (sensible) defaults, decorate {@link Component components}, or replace components entirely.
     * <p>
     * An enhancer's {@link ConfigurationEnhancer#enhance(ComponentRegistry)} method is invoked during the initialization
     * phase when all components have been defined. This is right before the ComponentRegistry creates its
     * {@link NewConfiguration}.
     * <p>
     * When multiple enhancers have been provided, their {@link ConfigurationEnhancer#order()}
     * dictates the enhancement order. For enhancer with the same order, the order of execution is undefined.
     *
     * @param enhancer The configuration enhancer to enhance ComponentRegistry during
     *                 {@link ApplicationConfigurer#build()}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    ComponentRegistry registerEnhancer(@Nonnull ConfigurationEnhancer enhancer);

    /**
     * Registers a {@link Module} with this registry.
     * <p>
     * Note that a {@code Module} is able to access the components defined in this ComponentRegistry upon construction,
     * but not vice versa. As such, the {@code Module} maintains encapsulation.
     *
     * @param module The module builder function to register.
     * @return The current instance of the {@code ComponentRegistry} for a fluent API.
     * @throws ComponentOverrideException if a module with the same name already exists.
     */
    ComponentRegistry registerModule(@Nonnull Module module);

    /**
     * Sets the {@link OverridePolicy} for this component registry. This policy dictates what should happen when
     * components are registered with an identifier for which another component is already present.
     *
     * @param overridePolicy The override policy for components defined in this registry.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    ComponentRegistry setOverridePolicy(OverridePolicy overridePolicy);
}
