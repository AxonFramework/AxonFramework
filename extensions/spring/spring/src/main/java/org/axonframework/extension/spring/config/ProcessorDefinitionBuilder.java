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

import org.axonframework.common.annotation.Internal;
import org.axonframework.extension.spring.config.ProcessorDefinition.ProcessorDefinitionConfigurationStep;
import org.axonframework.extension.spring.config.ProcessorDefinition.ProcessorDefinitionSelectorStep;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorConfiguration;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Internal builder implementation for {@link ProcessorDefinition}.
 * <p>
 * This class implements all steps of the processor definition fluent API and maintains the state for the definition
 * being built.
 *
 * @param <T> the type of {@link EventProcessorConfiguration} for this processor
 * @author Allard Buijze
 * @since 5.0.0
 */
@Internal
public class ProcessorDefinitionBuilder<T extends EventProcessorConfiguration>
        implements ProcessorDefinitionSelectorStep<T>, ProcessorDefinitionConfigurationStep<T>, ProcessorDefinition {

    private final EventProcessorSettings.ProcessorMode mode;
    private final String name;
    private Predicate<ProcessorDefinition.EventHandlerDescriptor> selector;
    private Function<T, T> configurationCustomizer;

    /**
     * Creates a new builder for a processor definition with the given mode and name.
     *
     * @param name the processor name
     * @param mode the processor mode (type)
     */
    public ProcessorDefinitionBuilder(String name, EventProcessorSettings.ProcessorMode mode) {
        this.mode = mode;
        this.name = name;
    }

    @Override
    public ProcessorDefinition withConfiguration(Function<T, T> configurer) {
        this.configurationCustomizer = configurer;
        return this;
    }

    @Override
    public ProcessorDefinition withDefaultSettings() {
        this.configurationCustomizer = Function.identity();
        return this;
    }

    @Override
    public ProcessorDefinitionConfigurationStep<T> assigningHandlers(
            Predicate<ProcessorDefinition.EventHandlerDescriptor> selector) {
        this.selector = selector;
        return this;
    }

    @Override
    public boolean matchesSelector(EventHandlerDescriptor eventHandlerDescriptor) {
        return selector.test(eventHandlerDescriptor);
    }

    @SuppressWarnings("unchecked")
    @Override
    public EventProcessorConfiguration applySettings(EventProcessorConfiguration settings) {
        return configurationCustomizer.apply((T) settings);
    }

    @Override
    public EventProcessorSettings.ProcessorMode mode() {
        return mode;
    }

    @Override
    public String name() {
        return name;
    }
}
