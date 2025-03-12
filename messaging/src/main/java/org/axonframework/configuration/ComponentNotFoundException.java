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
 * A {@code RuntimeException} dedicated when a {@link Component} cannot be found in the {@link NewConfiguration}.
 *
 * @author Steven van Beelen
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class ComponentNotFoundException extends RuntimeException {

    /**
     * Constructs a {@code ComponentNotFoundException} with a default message describing a {@link Component} couldn't be
     * found for the given {@code type} and {@code name}.
     *
     * @param type The type of the component that could not be found, typically an interface.
     * @param name The name of the component that could not be found.
     */
    public ComponentNotFoundException(@Nonnull Class<?> type, @Nonnull String name) {
        super("No component found for type [" + type + "] name [" + name + "]");
    }
}
