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
 * An extension of the {@link NewConfigurer} exposing the {@link #hasComponent(Class, String)} operation to ensure
 * {@link NewConfigurerModule} instances can validate if a given component is present before overriding it.
 *
 * @param <S> The type of configurer this implementation returns. This generic allows us to support fluent interfacing.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface ListableConfigurer<S extends ListableConfigurer<S>> extends NewConfigurer<S> {

    /**
     * Check whether there is a {@link Component} registered with this {@link NewConfigurer} for the given
     * {@code type}.
     *
     * @param type The type of the {@link Component} to check if it exists, typically an interface.
     * @return {@code true} when there is a {@link Component} registered under the given {@code type}, {@code false}
     * otherwise.
     */
    default boolean hasComponent(@Nonnull Class<?> type) {
        return hasComponent(type, type.getSimpleName());
    }

    /**
     * Check whether there is a {@link Component} registered with this {@link NewConfigurer} for the given {@code type}
     * and {@code name} combination.
     *
     * @param type The type of the {@link Component} to check if it exists, typically an interface.
     * @param name The name of the {@link Component} to check if it exists.
     * @return {@code true} when there is a {@link Component} registered under the given {@code type} and
     * {@code name combination}, {@code false} otherwise.
     */
    boolean hasComponent(@Nonnull Class<?> type, @Nonnull String name);
}
