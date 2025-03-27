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
 * A {@link RuntimeException} thrown whenever a {@link Component} has been overridden in a {@link ComponentRegistry}.
 * <p>
 * Is typically only thrown whenever the {@link OverrideBehavior} is set to {@link OverrideBehavior#THROW}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class ComponentOverrideException extends RuntimeException {

    /**
     * Constructs a {@code ComponentOverrideException} with the given {@code type} and {@code name} as the unique
     * identifier of the {@link Component} that has been overridden in a {@link ApplicationConfigurer}.
     *
     * @param type The type of the component this object identifiers, typically an interface.
     * @param name The name of the component this object identifiers.
     */
    public ComponentOverrideException(@Nonnull Class<?> type, @Nonnull String name) {
        super("Cannot override Component with type [" + type + "] and name [" + name + "]; it is already registered.");
    }
}
