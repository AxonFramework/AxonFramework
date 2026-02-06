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

import jakarta.annotation.Nonnull;
import org.axonframework.common.Assert;
import org.axonframework.common.annotation.Internal;
import org.axonframework.extension.spring.config.EventProcessorDefinition.ConfigurationStep;
import org.axonframework.extension.spring.config.EventProcessorDefinition.SelectorStep;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorConfiguration;

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
    private Predicate<EventProcessorDefinition.EventHandlerDescriptor> selector;

    /**
     * Creates a new builder for a processor definition with the given mode and name.
     *
     * @param name The processor name.
     * @param mode The processor mode (type).
     */
    public EventProcessorDefinitionBuilder(@Nonnull String name, @Nonnull EventProcessorSettings.ProcessorMode mode) {
        Assert.notNull(mode, () -> "Processor mode must not be null");
        this.mode = mode;
        this.name = Assert.nonEmpty(name, "Processor name must not be null");
    }

    @Override
    @Nonnull
    public EventProcessorDefinition customized(@Nonnull Function<T, T> configurer) {
        Assert.notNull(configurer, () -> "Configuration customizer must not be null");
        return new CompletedEventProcessorDefinitionImpl<>(mode, name, selector, configurer);
    }

    @Override
    @Nonnull
    public EventProcessorDefinition notCustomized() {
        return customized(Function.identity());
    }

    @Override
    @Nonnull
    public ConfigurationStep<T> assigningHandlers(
            @Nonnull Predicate<EventProcessorDefinition.EventHandlerDescriptor> selector
    ) {
        Assert.notNull(selector, () -> "Selector predicate must not be null");
        this.selector = selector;
        return this;
    }

    private record CompletedEventProcessorDefinitionImpl<T extends EventProcessorConfiguration>(
            @Override EventProcessorSettings.ProcessorMode mode,
            @Override String name,
            Predicate<EventHandlerDescriptor> selector,
            Function<T, T> configurationCustomizer) implements EventProcessorDefinition {

        @Override
        public boolean matchesSelector(@Nonnull EventHandlerDescriptor eventHandlerDescriptor) {
            return selector.test(eventHandlerDescriptor);
        }

        @Nonnull
        @Override
        public EventProcessorConfiguration applySettings(@Nonnull EventProcessorConfiguration settings) {
            //noinspection unchecked
            return configurationCustomizer.apply((T) settings);
        }
    }
}
