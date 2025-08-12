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

/**
 * A functional interface describing how to decorate a module of type {@code M}.
 * <p>
 * Implementers of this interface can choose to wrap the {@code delegate} into a new instance of type {@code M} or to
 * mutate the state of the {@code delegate}. The former solution is applicable for modules that
 * support decorators. The latter form is suitable when customizing the configuration instance of a module.
 *
 * @param <M> The type of module to be decorated.
 * @param <D> The type of decorated module, which must be the same or a subclass of {@code D}.
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public interface ModuleDecorator<M extends Module, D extends M> {

    /**
     * Decorates the given {@code delegate} into a mutated or replaced instance of type {@code D}, which <b>must be</b>
     * the same or a subclass of {@code M}.
     * <p>
     * Decorating can roughly take two angles. One, it may choose to wrap the {@code delegate} into a new instance of
     * type {@code M}. Second, it could mutate the state of the {@code delegate}.
     * <p>
     * Option one would typically apply to modules that support decorators. An example of this might be
     * wrapping an event processing module with additional capabilities or interceptors.
     * <p>
     * The latter form is suitable when customizing the configuration instance of a module.
     *
     * @param config   The configuration of this Axon application. Provided to support retrieval of other
     *                 {@link Configuration#getComponent(Class) components} for construction or mutation of the given
     *                 {@code delegate}.
     * @param name     The name of the module to be decorated.
     * @param delegate The delegate of type {@code M} to be decorated.
     * @return A decorated module of type {@code M}, typically based on the given {@code delegate}.
     * @throws ClassCastException When this decorator does not return a subclass of {@code M}.
     */
    D decorate(@Nonnull Configuration config,
               @Nullable String name,
               @Nonnull M delegate);
}