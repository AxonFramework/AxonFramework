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

/**
 * Contract allowing components {@link #describeTo(ComponentDescriptor) to describe} themselves with a given
 * {@link ComponentDescriptor}.
 * <p>
 * Components should focus on providing their internal state and structure to the
 * {@link org.axonframework.configuration.Component} without concern for how that information will be serialized or
 * displayed. This separation of concerns allows for multiple output formats from the same component hierarchy.
 *
 * @author Allard Buijze
 * @author Mitchel Herrijgers
 * @author Steven van Beelen
 * @since 5.0.0
 */
@FunctionalInterface
public interface DescribableComponent {

    /**
     * Describe the properties of {@code this DescribableComponent} with the given {@code descriptor}.
     * <p>
     * Components should call the appropriate {@code describeProperty} methods on the descriptor to register their
     * properties. The descriptor is responsible for determining how these properties are formatted and structured
     * in the final output.
     * <p>
     * <strong>Best Practices:</strong> As a general rule, all relevant fields of a {@code DescribableComponent}
     * implementation should be described in this method. However, developers have discretion to include only the fields
     * that make sense in the context. Not every field may be meaningful for description purposes, especially internal
     * implementation details. Furthermore, components might want to expose different information based on their current
     * state. The final decision on what properties to include lies with the person implementing the {@code describeTo}
     * method, who should focus on providing information that is useful for understanding the component's configuration
     * and state.
     * <p>
     * Example implementation:
     * <pre>
     * public void describeTo(ComponentDescriptor descriptor) {
     *     descriptor.describeProperty("name", this.name);
     *     descriptor.describeProperty("enabled", this.enabled);
     *     descriptor.describeProperty("configuration", this.configuration); // A nested component
     *     descriptor.describeProperty("handlers", this.eventHandlers);      // A collection
     * }
     * </pre>
     *
     * @param descriptor The component descriptor to describe {@code this DescribableComponent}n its properties in.
     */
    void describeTo(@Nonnull ComponentDescriptor descriptor);
}
