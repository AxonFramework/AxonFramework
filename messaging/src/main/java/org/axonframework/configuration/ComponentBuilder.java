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
 * Functional interface describing how to build a component of type {@code C} using the {@link NewConfiguration} during
 * construction.
 *
 * @param <C> The component to be built.
 * @author Steven van Beelen
 * @since 5.0.0
 */
@FunctionalInterface
public interface ComponentBuilder<C> {

    /**
     * Builds a component of type {@code C} using the given {@code config} during construction.
     *
     * @param config The configuration from which other components can be retrieved to build the result.
     * @return A component of type {@code C} using the given {@code config} during construction.
     */
    C build(@Nonnull NewConfiguration config);

    default ComponentBuilder<C> decorate(@Nonnull ComponentDecorator<C> decorator) {
        return this;
    }
}
