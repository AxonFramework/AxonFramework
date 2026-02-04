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

package org.axonframework.common.configuration;

import jakarta.annotation.Nonnull;

/**
 * An exception indicating a {@link Component} is being {@link Configuration#getComponent(Class, String) retrieved} for
 * a type and name combination that resulted in several matches.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class AmbiguousComponentMatchException extends RuntimeException {

    /**
     * Constructs an {@code AmbiguousComponentMatchException} for the given {@code identifier}.
     *
     * @param identifier The identifier for which to create an {@code AmbiguousComponentMatchException}.
     * @param <C>        The {@link Component.Identifier#type()} of the given {@code identifier}.
     */
    public <C> AmbiguousComponentMatchException(@Nonnull Component.Identifier<C> identifier) {
        super("No single instance found for type ["
                      + identifier.typeAsClass()
                      + "] and name ["
                      + identifier.name()
                      + "]. Please try a more specific type-name combination to retrieve components.");
    }
}
