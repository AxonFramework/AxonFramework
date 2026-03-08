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

/**
 * Functional interface describing how to build a named component of type {@code C} using the component's
 * {@code name} and the {@link Configuration} during construction.
 * <p>
 * This is an extension of the {@link ComponentBuilder} concept for cases where the component needs to know
 * its own name during construction. The name is provided by the framework, eliminating the need for the caller
 * to duplicate the name in both the registration call and the component constructor.
 *
 * @param <C> The component to be built.
 * @author Mateusz Nowak
 * @since 5.1.0
 * @see ComponentBuilder
 */
@FunctionalInterface
public interface NamedComponentBuilder<C> {

    /**
     * Builds a component of type {@code C} using the given {@code name} and {@code config} during construction.
     *
     * @param name   The name assigned to the component by the framework.
     * @param config The configuration from which other components can be retrieved to build the result.
     * @return A component of type {@code C} using the given {@code name} and {@code config} during construction.
     */
    C build(String name, Configuration config);
}
