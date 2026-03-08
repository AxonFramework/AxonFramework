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

import org.axonframework.common.annotation.Internal;
import org.axonframework.common.configuration.ComponentBuilder;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private List<NamedBuilder> componentBuilders = new ArrayList<>();

    /**
     * Creates a new empty configurer instance.
     */
    public DefaultEventHandlingComponentsConfigurer() {
        // Default constructor for empty configuration
    }

    @Override
    public EventHandlingComponentsConfigurer.AdditionalComponentPhase declarative(
            String name,
            ComponentBuilder<EventHandlingComponent> handlingComponentBuilder
    ) {
        requireNonNull(name, "The name cannot be null.");
        requireNonNull(handlingComponentBuilder, "The handling component builder cannot be null.");
        componentBuilders.add(new NamedBuilder(name, handlingComponentBuilder));
        return this;
    }

    @Override
    public EventHandlingComponentsConfigurer.CompletePhase decorated(
            BiFunction<Configuration, EventHandlingComponent, EventHandlingComponent> decorator
    ) {
        Objects.requireNonNull(decorator, "decorator may not be null");
        var decoratedBuilders = new ArrayList<NamedBuilder>();
        for (var namedBuilder : componentBuilders) {
            decoratedBuilders.add(new NamedBuilder(
                    namedBuilder.name(),
                    cfg -> {
                        EventHandlingComponent component = namedBuilder.builder().build(cfg);
                        return decorator.apply(cfg, component);
                    }
            ));
        }
        componentBuilders = decoratedBuilders;
        return this;
    }

    /**
     * Returns the names of all configured components, in registration order.
     *
     * @return An unmodifiable list of component names in registration order.
     */
    public List<String> componentNames() {
        return componentBuilders.stream()
                                .map(NamedBuilder::name)
                                .toList();
    }

    /**
     * Builds all configured components using the provided configuration and returns them as an ordered map,
     * keyed by the component name.
     *
     * @param configuration The framework configuration.
     * @return An ordered map of component name to built {@link EventHandlingComponent}.
     * @throws IllegalStateException if duplicate component names are detected.
     */
    public Map<String, EventHandlingComponent> build(Configuration configuration) {
        var result = new LinkedHashMap<String, EventHandlingComponent>();
        for (var entry : componentBuilders) {
            var component = entry.builder().build(configuration);
            var name = entry.name();
            if (result.containsKey(name)) {
                throw new IllegalStateException(
                        "Duplicate EventHandlingComponent name '%s' within the same processor.".formatted(name)
                );
            }
            result.put(name, component);
        }
        return Collections.unmodifiableMap(result);
    }

    private record NamedBuilder(String name, ComponentBuilder<EventHandlingComponent> builder) {

    }
}
