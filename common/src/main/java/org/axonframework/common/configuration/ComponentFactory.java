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

package org.axonframework.common.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.common.infra.DescribableComponent;

import java.util.Optional;

/**
 * A factory of components of type generic {@code C}.
 * <p>
 * It is <b>strongly</b> recommended to invoke start operations during
 * {@link #construct(String, Configuration) construction} of the components, since the {@link Configuration} is always
 * {@link AxonConfiguration#start() started} before a factory is consulted. Hence, failing to invoke start operations
 * results in components in a faulty state.
 * <p>
 * When {@link ComponentRegistry#registerFactory(ComponentFactory) registered} with a {@link ComponentRegistry}, the
 * registry will consult the factory <b>only</b> when there is no registered component for a given type and name.
 *
 * @param <C> The component this factory builds on request.
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface ComponentFactory<C> extends DescribableComponent {

    /**
     * Returns the {@code Class} this factory constructs.
     * <p>
     * Useful when the generic type is lost due to grouping multiple factories in a collection.
     *
     * @return The {@code Class} this factory constructs.
     */
    @Nonnull
    Class<C> forType();

    /**
     * Constructs a {@link Component} containing an implementation of the generic type {@code C}.
     * <p>
     * Implementations of this method may choose to reject the construction by returning an
     * {@link Optional#empty() empty Optional} if the {@code name} is not of an expected format, or when the given
     * {@code config} does not contain the required components to construct a new instance with.
     * <p>
     * Be certain to invoke <b>any</b> start operation on the new instance of type {@code C} before returning. This is
     * mandatory since factories are only consulted once an application is {@link AxonConfiguration#start() started}.
     *
     * @param name   The name that's used to request a new instance from this factory. This parameter can be a basis to
     *               reject construction.
     * @param config The configuration to retrieve components from to use during the construction by this factory. A
     *               factory may return an {@link Optional#empty() empty Optional} if the configuration does not contain
     *               the necessary components.
     * @return An optional of a {@link Component} containing an implementation of the generic type {@code C}.
     */
    @Nonnull
    Optional<Component<C>> construct(@Nonnull String name, @Nonnull Configuration config);

    /**
     * Registers this factory's shutdown process with the given {@code registry}.
     * <p>
     * This typically means the factory registers the shutdown operations of all
     * {@link #construct(String, Configuration) constructed} components of type {@code C}. This operation might do
     * nothing with the {@code registry} when the component of type {@code C} does not have any shutdown operations, for
     * example.
     * <p>
     * Since a component factory is consulted <b>after</b> {@link AxonConfiguration#start() start-up} of a
     * {@link Configuration}, only {@link LifecycleRegistry#onShutdown(Runnable) registered shutdown handlers} will take
     * effect.
     *
     * @param registry The life cycle registry to
     *                 {@link LifecycleRegistry#onShutdown(Runnable) register shutdown handlers} with.
     */
    void registerShutdownHandlers(@Nonnull LifecycleRegistry registry);
}
