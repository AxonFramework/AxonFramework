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

package org.axonframework.common.infra;

import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.Map;

/**
 * Contract towards describing the properties an (infrastructure) component might have.
 * <p>
 * The {@link ComponentDescriptor} implementation passed to a component by
 * {@link DescribableComponent#describeTo(ComponentDescriptor)} determines both the structure and
 * format of the output. This means the same component can be rendered in different formats (like JSON, XML, or a
 * filesystem-like tree) depending on which {@link ComponentDescriptor} implementation is used. The component only needs
 * to provide its properties and structure to the descriptor, which then handles the formatting details.
 * <p>
 * <strong>Handling Circular References:</strong> Component hierarchies may contain circular references where a
 * {@link DescribableComponent} refers to another component that eventually references back to the original.
 * {@link ComponentDescriptor} implementations must handle these circular dependencies to prevent infinite recursion.
 *
 * @author Allard Buijze
 * @author Mitchel Herrijgers
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface ComponentDescriptor {

    /**
     * Describe the given {@code object} with {@code this} descriptor for the given {@code name}.
     * <p>
     * If the {@code object} is a {@link DescribableComponent},
     * {@link DescribableComponent#describeTo(ComponentDescriptor)} is invoked with {@code this} descriptor.
     *
     * @param name   The name for the {@code object} to describe.
     * @param object The object to describe with {@code this} descriptor.
     */
    void describeProperty(@Nonnull String name, @Nonnull Object object);

    /**
     * Describe the given {@code collection} with {@code this} descriptor for the given {@code name}.
     * <p>
     * The formatting of the {@code collection} typically takes a regular array structure.
     *
     * @param name       The name for the {@code collection} to describe.
     * @param collection The collection to describe with {@code this} descriptor.
     */
    void describeProperty(@Nonnull String name, @Nonnull Collection<?> collection);

    /**
     * Describe the given {@code map} with {@code this} descriptor for the given {@code name}.
     * <p>
     * The formatting of the {@code map} typically takes a regular key-value structure based on the
     * {@link Map.Entry entries} of the {@code map}.
     *
     * @param name The name for the {@code map} to describe.
     * @param map  The map to describe with {@code this} descriptor.
     */
    void describeProperty(@Nonnull String name, @Nonnull Map<?, ?> map);

    /**
     * Describe the given {@code value} with {@code this} descriptor for the given {@code name}.
     *
     * @param name  The name for the {@code value} to describe.
     * @param value The value to describe with {@code this} descriptor.
     */
    void describeProperty(@Nonnull String name, @Nonnull String value);

    /**
     * Describe the given {@code value} with {@code this} descriptor for the given {@code name}.
     *
     * @param name  The name for the {@code value} to describe.
     * @param value The value to describe with {@code this} descriptor.
     */
    void describeProperty(@Nonnull String name, long value);

    /**
     * Describe the given {@code value} with {@code this} descriptor for the given {@code name}.
     *
     * @param name  The name for the {@code value} to describe.
     * @param value The value to describe with {@code this} descriptor.
     */
    void describeProperty(@Nonnull String name, boolean value);

    /**
     * Describe the given {@code delegate} with {@code this} descriptor under the name {@code "delegate"}.
     * <p>
     * If the {@code delegate} is a {@link DescribableComponent},
     * {@link DescribableComponent#describeTo(ComponentDescriptor)} is invoked with {@code this} descriptor.
     *
     * @param delegate The object to describe with {@code this} descriptor.
     */
    default void describeWrapperOf(Object delegate) {
        describeProperty("delegate", delegate);
    }

    /**
     * Provides the description of {@code this ComponentDescriptor}.
     *
     * @return The description result of {@code this ComponentDescriptor}.
     */
    String describe();
}
