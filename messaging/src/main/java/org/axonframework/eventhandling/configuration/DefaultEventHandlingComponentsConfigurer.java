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
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Name alternatives: EventHandlingComponents
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class DefaultEventHandlingComponentsConfigurer
        implements EventHandlingComponentsConfigurer.ComponentsPhase, EventHandlingComponentsConfigurer.CompletePhase {

    private final List<EventHandlingComponent> components;

    public static EventHandlingComponentsConfigurer.ComponentsPhase empty() {
        return new DefaultEventHandlingComponentsConfigurer(List.of());
    }

    private DefaultEventHandlingComponentsConfigurer(@Nonnull List<EventHandlingComponent> components) {
        this.components = components;
    }

    @Nonnull
    @Override
    public EventHandlingComponentsConfigurer.CompletePhase single(
            @Nonnull Function<EventHandlingComponentBuilder.SequencingPolicyPhase, EventHandlingComponentBuilder.Complete> definition) {
        return many(definition.apply(EventHandlingComponentBuilder.component()));
    }

    @SafeVarargs
    @Nonnull
    @Override
    public final EventHandlingComponentsConfigurer.CompletePhase many(
            @Nonnull Function<EventHandlingComponentBuilder.SequencingPolicyPhase, EventHandlingComponentBuilder.Complete> requiredComponent,
            @Nonnull Function<EventHandlingComponentBuilder.SequencingPolicyPhase, EventHandlingComponentBuilder.Complete>... additionalComponents) {
        return many(
                Stream.concat(Stream.of(requiredComponent), Stream.of(additionalComponents))
                      .map(builder -> builder.apply(new DefaultEventHandlingComponentBuilder()).build())
                      .toList()
        );
    }

    @Override
    public EventHandlingComponentsConfigurer.CompletePhase many(@Nonnull List<EventHandlingComponent> components) {
        return new DefaultEventHandlingComponentsConfigurer(components);
    }

    public DefaultEventHandlingComponentsConfigurer decorated(
            @Nonnull UnaryOperator<EventHandlingComponent> decorator
    ) {
        return new DefaultEventHandlingComponentsConfigurer(components.stream().map(decorator).toList());
    }

    public List<EventHandlingComponent> toList() {
        return List.copyOf(components);
    }
}
