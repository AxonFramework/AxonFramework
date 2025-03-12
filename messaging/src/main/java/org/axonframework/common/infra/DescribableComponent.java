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
     *
     * @param descriptor The component descriptor to describe {@code this DescribableComponent}n its properties in.
     */
    void describeTo(@Nonnull ComponentDescriptor descriptor);
}
