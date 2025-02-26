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
 * {@link #registerDecorator(Class, ComponentDecorator) decorators} of these components, and
 * {@link #registerModule(ModuleBuilder) modules}.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 3.0.0
 */
// TODO Rename to Configurer once the old Configurer is removed
public interface NewConfigurer extends LifecycleOperations {

    /**
     * Registers a {@link Component} that should be made available to other {@link Component components} or
     * {@link Module modules} in the {@link NewConfiguration} that this {@code NewConfigurer} will result in.
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
     * @return The current instance of the {@code NewConfigurer} for a fluent API.
     */
    default <C> NewConfigurer registerComponent(@Nonnull Class<C> type,
                                                @Nonnull ComponentBuilder<C> builder) {
        return registerComponent(new Identifier<>(type), builder);
    }

    /**
     * Registers a {@link Component} that should be made available to other {@link Component components} or
     * {@link Module modules} in the {@link NewConfiguration} that this {@code NewConfigurer} will result in.
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
     * @return The current instance of the {@code NewConfigurer} for a fluent API.
     */
    default <C> NewConfigurer registerComponent(@Nonnull Class<C> type,
                                                @Nonnull String name,
                                                @Nonnull ComponentBuilder<C> builder) {
        return registerComponent(new Identifier<>(type, name), builder);
    }

    /**
     * Registers a {@link Component} that should be made available to other {@link Component components} or
     * {@link Module modules} in the {@link NewConfiguration} that this {@code NewConfigurer} will result in.
     * <p>
     * The given {@code builder} function gets the {@link NewConfiguration configuration} as input, and is expected to
     * provide the component as output. The component will be registered under the given {@code identifier}.
     * <p>
     * Note that registering a component twice for the same {@code identifier} will remove the previous registration!
     *
     * @param identifier The identifier of the component to build.
     * @param builder    The builder function of this component.
     * @param <C>        The identifier of component the {@code builder} builds.
     * @return The current instance of the {@code NewConfigurer} for a fluent API.
     */
    <C> NewConfigurer registerComponent(@Nonnull Identifier<C> identifier,
                                        @Nonnull ComponentBuilder<C> builder);


    /**
     * Registers a {@link Component} {@link ComponentDecorator decorator} that will act on
     * {@link #registerComponent(Class, ComponentBuilder) registered} components of the given {@code type}.
     * <p>
     * Multiple Invocations of this method will attach the given decorators in the invocation order.
     *
     * @param type      The declared type of the component to decorate, typically an interface.
     * @param decorator The decoration function of this component.
     * @param <C>       The type of component the {@code decorator} decorates.
     * @return The current instance of the {@code NewConfigurer} for a fluent API.
     */
    default <C> NewConfigurer registerDecorator(@Nonnull Class<C> type,
                                                @Nonnull ComponentDecorator<C> decorator) {
        return registerDecorator(new Identifier<>(type), decorator);
    }

    /**
     * Registers a {@link Component} {@link ComponentDecorator decorator} that will act on
     * {@link #registerComponent(Class, String, ComponentBuilder) registered} components of the given {@code type} and
     * {@code name} combination.
     * <p>
     * Multiple Invocations of this method will attach the given decorators in the invocation order.
     *
     * @param type      The declared type of the component to decorate, typically an interface.
     * @param name      The name of the component to decorate.
     * @param decorator The decoration function of this component.
     * @param <C>       The type of component the {@code decorator} decorates.
     * @return The current instance of the {@code NewConfigurer} for a fluent API.
     */
    default <C> NewConfigurer registerDecorator(@Nonnull Class<C> type,
                                                @Nonnull String name,
                                                @Nonnull ComponentDecorator<C> decorator) {
        return registerDecorator(new Identifier<>(type, name), decorator);
    }

    /**
     * Registers a {@link Component} {@link ComponentDecorator decorator} that will act on
     * {@link #registerComponent(Class, String, ComponentBuilder) registered} components of the given
     * {@code identifier}.
     * <p>
     * Multiple Invocations of this method will attach the given decorators in the invocation order.
     *
     * @param identifier The identifier of the component to decorate.
     * @param decorator  The decoration function of this component.
     * @param <C>        The identifier of component the {@code decorator} decorates.
     * @return The current instance of the {@code NewConfigurer} for a fluent API.
     */
    <C> NewConfigurer registerDecorator(@Nonnull Identifier<C> identifier,
                                        @Nonnull ComponentDecorator<C> decorator);

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
     * @return The current instance of the {@code NewConfigurer} for a fluent API.
     */
    default <C> NewConfigurer registerDecorator(@Nonnull Class<C> type,
                                                int order,
                                                @Nonnull ComponentDecorator<C> decorator) {
        return registerDecorator(new Identifier<>(type), order, decorator);
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
     * @return The current instance of the {@code NewConfigurer} for a fluent API.
     */
    default <C> NewConfigurer registerDecorator(@Nonnull Class<C> type,
                                                @Nonnull String name,
                                                int order,
                                                @Nonnull ComponentDecorator<C> decorator) {
        return registerDecorator(new Identifier<>(type, name), order, decorator);
    }

    /**
     * Registers a {@link Component} {@link ComponentDecorator decorator} that will act on
     * {@link #registerComponent(Class, ComponentBuilder) registered} components of the given {@code identifier}.
     * <p>
     * The {@code order} parameter dictates at what point in time the given {@code decorator} is invoked during
     * construction of the {@code Component} it decorators. If a {@code ComponentDecorator} was already present at the
     * given {@code order}, it will be replaced by the given {@code decorator}
     *
     * @param identifier The identifier of the component to decorate.
     * @param order      The order of the given {@code decorator} among other decorators. Becomes important whenever
     *                   multiple decorators are present for the given {@code identifier} <b>and</b> when ordering of
     *                   these decorators is important.
     * @param decorator  The decoration function of this component.
     * @param <C>        The identifier of component the {@code decorator} decorates.
     * @return The current instance of the {@code NewConfigurer} for a fluent API.
     */
    <C> NewConfigurer registerDecorator(@Nonnull Identifier<C> identifier,
                                        int order,
                                        @Nonnull ComponentDecorator<C> decorator);

    /**
     * Registers a {@code moduleBuilder} with this {@code NewConfigurer}. The {@code moduleBuilder} is typically
     * constructed immediately by the {@code NewConfigurer}.
     *
     * @param moduleBuilder The module builder function to register.
     * @return The current instance of the {@code NewConfigurer} for a fluent API.
     */
    NewConfigurer registerModule(@Nonnull ModuleBuilder moduleBuilder);
}
