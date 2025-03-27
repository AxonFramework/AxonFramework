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

/**
 * A functional interface describing how to decorate a component of type {@code C}.
 * <p>
 * Implementers of this interface can choose to wrap the {@code delegate} into a new instance of type {@code C} or to
 * mutate the state of the {@code delegate}. The former solution is applicable for infrastructure components that
 * support decorators. The latter form is suitable when customizing the configuration instance of an infrastructure
 * component.
 *
 * @param <C> The type of component to be decorated.
 * @param <D> The type of decorated component
 * @author Steven van Beelen
 * @since 5.0.0
 */
@FunctionalInterface
public interface ComponentDecorator<C, D extends C> {

    /**
     * Decorates the given {@code delegate} into a mutated or replaced instance of type {@code D}, which must be the
     * same or a subclass of {@code C}.
     * <p>
     * Decorating can roughly take two angles. One, it may choose to wrap the {@code delegate} into a new instance of
     * type {@code C}. Second, it could mutate the state of the {@code delegate}.
     * <p>
     * Option one would typically apply to infrastructure components that support decorators. An example of this is the
     * {@link org.axonframework.commandhandling.CommandBus}, which can for example be decorated with the
     * {@link org.axonframework.commandhandling.tracing.TracingCommandBus}.
     * <p>
     * The latter form is suitable when customizing the configuration instance of an infrastructure component.
     *
     * @param config   The configuration of this Axon application. Provided to support retrieval of other
     *                 {@link NewConfiguration#getComponent(Class) components} for construction or mutation of the given
     *                 {@code delegate}.
     * @param name The name of the component to decorated
     * @param delegate The delegate of type {@code C} to be decorated.
     * @return A decorated component of type {@code C}, typically based on the given {@code delegate}.
     */
    D decorate(@Nonnull NewConfiguration config,
               @Nonnull String name,
               @Nonnull C delegate);
}
