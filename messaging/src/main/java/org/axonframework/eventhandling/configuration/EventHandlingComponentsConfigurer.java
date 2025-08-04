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
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Name alternatives: EventHandlingComponents
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class EventHandlingComponentsConfigurer {

    private final List<EventHandlingComponent> components;

    private EventHandlingComponentsConfigurer(@Nonnull List<EventHandlingComponent> components) {
        this.components = components;
    }

    public static EventHandlingComponentsConfigurer single(
            @Nonnull EventHandlingComponentConfigurer.Complete definition
    ) {
        return new EventHandlingComponentsConfigurer(List.of(definition.toComponent()));
    }

    public static EventHandlingComponentsConfigurer single(@Nonnull EventHandlingComponent component) {
        return new EventHandlingComponentsConfigurer(List.of(component));
    }

    public static EventHandlingComponentsConfigurer many(
            @Nonnull EventHandlingComponent requiredComponent,
            @Nonnull EventHandlingComponent... additionalComponents
    ) {
        var components = Stream.concat(
                Stream.of(requiredComponent),
                Stream.of(additionalComponents)
        ).filter(Objects::nonNull).toList();
        return new EventHandlingComponentsConfigurer(components);
    }

    public static EventHandlingComponentsConfigurer many(
            @Nonnull EventHandlingComponentConfigurer.Complete requiredDefinition,
            @Nonnull EventHandlingComponentConfigurer.Complete... additionalDefinitions
    ) {
        var components = Stream.concat(
                Stream.of(requiredDefinition.toComponent()),
                Stream.of(additionalDefinitions).map(EventHandlingComponentConfigurer.Complete::toComponent)
        ).filter(Objects::nonNull).toList();
        return new EventHandlingComponentsConfigurer(components);
    }

    public EventHandlingComponentsConfigurer decorated(@Nonnull UnaryOperator<EventHandlingComponent> decorator) {
        return new EventHandlingComponentsConfigurer(components.stream().map(decorator).toList());
    }

    public List<EventHandlingComponent> toList() {
        return List.copyOf(components);
    }
}
