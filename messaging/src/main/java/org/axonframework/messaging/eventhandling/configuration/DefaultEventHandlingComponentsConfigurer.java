/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.configuration.ComponentBuilder;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

import static java.util.Objects.requireNonNull;

/**
 * Default implementation of {@link EventHandlingComponentsConfigurer} providing {@link EventHandlingComponent}s`
 * builders management with decoration support.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@Internal
public class DefaultEventHandlingComponentsConfigurer
        implements EventHandlingComponentsConfigurer.RequiredComponentPhase,
        EventHandlingComponentsConfigurer.AdditionalComponentPhase, EventHandlingComponentsConfigurer.CompletePhase {

    private List<ComponentBuilder<EventHandlingComponent>> componentBuilders = new ArrayList<>();

    /**
     * Creates a new empty configurer instance.
     */
    public DefaultEventHandlingComponentsConfigurer() {
        // Default constructor for empty configuration
    }

    @Nonnull
    @Override
    public EventHandlingComponentsConfigurer.AdditionalComponentPhase declarative(
            @Nonnull ComponentBuilder<EventHandlingComponent> handlingComponentBuilder
    ) {
        requireNonNull(handlingComponentBuilder, "The handling component builder cannot be null.");
        componentBuilders.add(handlingComponentBuilder);
        return this;
    }

    @Nonnull
    @Override
    public EventHandlingComponentsConfigurer.CompletePhase decorated(
            @Nonnull BiFunction<Configuration, EventHandlingComponent, EventHandlingComponent> decorator
    ) {
        Objects.requireNonNull(decorator, "decorator may not be null");
        var decoratedBuilders = new ArrayList<ComponentBuilder<EventHandlingComponent>>();
        for (var builder : componentBuilders) {
            decoratedBuilders.add(cfg -> {
                EventHandlingComponent component = builder.build(cfg);
                return decorator.apply(cfg, component);
            });
        }
        componentBuilders = decoratedBuilders;
        return this;
    }

    @Nonnull
    @Override
    public List<ComponentBuilder<EventHandlingComponent>> toList() {
        return List.copyOf(componentBuilders);
    }
}
