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

package org.axonframework.eventhandling.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventHandlingComponent;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Builder interface for configuring collections of {@link EventHandlingComponent} instances.
 * <p>
 * Provides a fluent API for specifying single or multiple components and applying decorations
 * to all components in the collection.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public interface EventHandlingComponentsConfigurer {

    /**
     * Initial phase for specifying event handling components.
     */
    interface ComponentsPhase {

        /**
         * Configures a single event handling component.
         *
         * @param component The component to configure.
         * @return The complete phase for decoration and finalization.
         */
        @Nonnull
        default CompletePhase single(@Nonnull EventHandlingComponent component) {
            return many(List.of(component));
        }

        /**
         * Configures multiple event handling components with varargs syntax.
         *
         * @param requiredComponent     The first required component.
         * @param additionalComponents Additional components (can be empty).
         * @return The complete phase for decoration and finalization.
         */
        @Nonnull
        default CompletePhase many(
                @Nonnull EventHandlingComponent requiredComponent,
                @Nonnull EventHandlingComponent... additionalComponents
        ) {
            var components = Stream.concat(
                    Stream.of(requiredComponent),
                    Stream.of(additionalComponents)
            ).filter(Objects::nonNull).toList();
            return many(components);
        }

        /**
         * Configures multiple event handling components from a list.
         *
         * @param components The list of components to configure.
         * @return The complete phase for decoration and finalization.
         */
        CompletePhase many(@Nonnull List<EventHandlingComponent> components);
    }

    /**
     * Final phase for applying decorations and building the component list.
     */
    interface CompletePhase {

        /**
         * Applies a decorator to all components in the collection.
         *
         * @param decorator Function to decorate each component.
         * @return This phase for further decoration or finalization.
         */
        CompletePhase decorated(@Nonnull UnaryOperator<EventHandlingComponent> decorator);

        /**
         * Returns the configured list of event handling components.
         *
         * @return The immutable list of configured components.
         */
        List<EventHandlingComponent> toList();
    }
}

