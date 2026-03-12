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

package org.axonframework.extension.spring.config;

import org.axonframework.common.Assert;
import org.axonframework.common.annotation.Internal;
import org.axonframework.extension.spring.config.EventProcessorDefinition.ConfigurationStep;
import org.axonframework.extension.spring.config.EventProcessorDefinition.SelectorStep;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorConfiguration;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Internal builder implementation for {@link EventProcessorDefinition}.
 * <p>
 * This class implements all steps of the processor definition fluent API and maintains the state for the definition
 * being built.
 *
 * @param <T> The type of {@link EventProcessorConfiguration} for this processor.
 * @author Allard Buijze
 * @since 5.0.2
 */
@Internal
class EventProcessorDefinitionBuilder<T extends EventProcessorConfiguration>
        implements SelectorStep<T>, ConfigurationStep<T> {

    private final EventProcessorSettings.ProcessorMode mode;
    private final String name;
    @Nullable
    private EventHandlerSelector selector;

    /**
     * Creates a new builder for a processor definition with the given mode and name.
     *
     * @param name the processor name
     * @param mode the processor mode (type)
     */
    public EventProcessorDefinitionBuilder(String name, EventProcessorSettings.ProcessorMode mode) {
        this.mode = Objects.requireNonNull(mode, "Processor mode must not be null");
        this.name = Assert.nonEmpty(name, "Processor name must not be null or empty");
    }

    @Override
    public ConfigurationStep<T> assigningHandlers(EventHandlerSelector selector) {
        this.selector = Objects.requireNonNull(selector, "Selector predicate must not be null");
        return this;
    }

    @Override
    public EventProcessorDefinition customized(Function<T, T> configurer) {
        return new CompletedEventProcessorDefinitionImpl<>(
                mode,
                name,
                Objects.requireNonNull(selector, "Selector predicate must not be null"),
                Objects.requireNonNull(configurer, "Configuration customizer must not be null")
        );
    }

    @Override
    public EventProcessorDefinition notCustomized() {
        return customized(Function.identity());
    }

    private record CompletedEventProcessorDefinitionImpl<T extends EventProcessorConfiguration>(
            @Override EventProcessorSettings.ProcessorMode mode,
            @Override String name,
            Predicate<EventHandlerDescriptor> selector,
            Function<T, T> configurationCustomizer) implements EventProcessorDefinition {

        @Override
        public boolean matchesSelector(EventHandlerDescriptor eventHandlerDescriptor) {
            return selector.test(eventHandlerDescriptor);
        }

        @Override
        public EventProcessorConfiguration applySettings(EventProcessorConfiguration settings) {
            //noinspection unchecked
            return configurationCustomizer.apply((T) settings);
        }
    }
}
