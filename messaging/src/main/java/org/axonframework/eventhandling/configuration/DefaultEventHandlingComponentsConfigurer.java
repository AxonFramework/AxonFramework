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
import java.util.function.UnaryOperator;

/**
 * Default implementation of {@link EventHandlingComponentsConfigurer} providing immutable
 * component collection management with decoration support.
 * <p>
 * This configurer follows an immutable pattern where each operation returns a new instance
 * rather than modifying the existing one. Components are stored as immutable copies to
 * prevent external modifications.
 * <p>
 * Typically accessed through the static {@link #init()} method to begin configuration.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class DefaultEventHandlingComponentsConfigurer
        implements EventHandlingComponentsConfigurer.ComponentsPhase, EventHandlingComponentsConfigurer.CompletePhase {

    private final List<EventHandlingComponent> components;

    /**
     * Creates a new configurer instance ready for component specification.
     *
     * @return The initial components phase for adding components.
     */
    public static EventHandlingComponentsConfigurer.ComponentsPhase init() {
        return new DefaultEventHandlingComponentsConfigurer(List.of());
    }

    /**
     * Creates a configurer with the given components.
     *
     * @param components The components to configure. Will be copied for immutability.
     */
    private DefaultEventHandlingComponentsConfigurer(@Nonnull List<EventHandlingComponent> components) {
        this.components = List.copyOf(components);
    }

    @Override
    public EventHandlingComponentsConfigurer.CompletePhase many(@Nonnull List<EventHandlingComponent> components) {
        if (components.isEmpty()) {
            throw new IllegalArgumentException("At least one EventHandlingComponent must be provided");
        }
        return new DefaultEventHandlingComponentsConfigurer(components);
    }

    @Override
    public DefaultEventHandlingComponentsConfigurer decorated(
            @Nonnull UnaryOperator<EventHandlingComponent> decorator
    ) {
        return new DefaultEventHandlingComponentsConfigurer(components.stream().map(decorator).toList());
    }

    public List<EventHandlingComponent> toList() {
        return List.copyOf(components);
    }
}
