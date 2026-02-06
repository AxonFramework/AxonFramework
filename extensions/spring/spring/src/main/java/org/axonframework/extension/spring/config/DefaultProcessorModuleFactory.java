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
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.extension.spring.BeanDefinitionUtils;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory;
import org.axonframework.messaging.eventhandling.configuration.EventHandlingComponentsConfigurer;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorConfiguration;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link ProcessorModuleFactory} that assigns event handlers to processors using
 * {@link EventProcessorDefinition ProcessorDefinitions}.
 * <p>
 * This factory evaluates each event handler against the configured processor definitions. When a handler matches a
 * definition's selector, it is assigned to that processor. If no processor definition matches, the handler is assigned
 * to a processor named after the handler's package (or "default" if the package cannot be determined).
 * <p>
 * The factory uses {@link EventProcessorSettings} to configure each processor according to its type (pooled streaming
 * or subscribing). Custom configuration from processor definitions is applied on top of the base settings.
 *
 * @author Allard Buijze
 * @see ProcessorModuleFactory
 * @see EventProcessorDefinition
 * @since 5.0.2
 */
public class DefaultProcessorModuleFactory implements ProcessorModuleFactory {

    private static final Logger logger = LoggerFactory.getLogger(DefaultProcessorModuleFactory.class);

    private final List<EventProcessorDefinition> eventProcessorDefinitions;
    private final Map<String, EventProcessorSettings> allSettings;
    private final Configuration axonConfiguration;

    /**
     * Creates a new factory with the given processor definitions, settings, and Axon configuration.
     *
     * @param eventProcessorDefinitions The list of processor definitions that define handler assignment rules.
     * @param settings             The map of processor settings, keyed by processor name.
     * @param axonConfiguration    The Axon configuration to retrieve components from.
     */
    public DefaultProcessorModuleFactory(@Nonnull List<EventProcessorDefinition> eventProcessorDefinitions,
                                         @Nonnull Map<String, EventProcessorSettings> settings,
                                         @Nonnull Configuration axonConfiguration) {
        this.eventProcessorDefinitions = eventProcessorDefinitions;
        this.allSettings = settings;
        this.axonConfiguration = axonConfiguration;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation groups handlers by their assigned processor name (determined by
     * {@link #assignedProcessor(EventProcessorDefinition.EventHandlerDescriptor)}), then creates an
     * {@link EventProcessorModule} for each processor with its assigned handlers.
     */
    @Nonnull
    @Override
    public Set<EventProcessorModule> buildProcessorModules(@Nonnull Set<EventProcessorDefinition.EventHandlerDescriptor> handlers) {

        Set<EventProcessorModule> modules = new LinkedHashSet<>();

        var assignments = handlers.stream().collect(Collectors.groupingBy(this::assignedProcessor));

        for (EventProcessorDefinition definition : eventProcessorDefinitions) {
            if (!assignments.containsKey(definition.name())) {
                logger.warn("No handlers assigned to explicitly defined processor: {}", definition.name());
            }
        }

        assignments.forEach((processorName, beanDefs) -> {
            Function<EventHandlingComponentsConfigurer.RequiredComponentPhase, EventHandlingComponentsConfigurer.CompletePhase> componentRegistration = (EventHandlingComponentsConfigurer.RequiredComponentPhase phase) -> {
                EventHandlingComponentsConfigurer.ComponentsPhase resultOfRegistration = phase;
                for (EventProcessorDefinition.EventHandlerDescriptor namedBeanDefinition : beanDefs) {
                    resultOfRegistration = resultOfRegistration.autodetected(namedBeanDefinition.component());
                }
                return (EventHandlingComponentsConfigurer.CompletePhase) resultOfRegistration;
            };

            var processorModuleName = "EventProcessor[" + processorName + "]";

            var settings = Optional.ofNullable(allSettings.get(processorName))
                                   .orElseGet(() -> allSettings.get(EventProcessorSettings.DEFAULT));
            var processorMode = definitionFor(processorName).map(EventProcessorDefinition::mode)
                                                            .orElse(settings.processorMode());
            var module = switch (processorMode) {
                case POOLED -> {
                    var moduleSettings = (EventProcessorSettings.PooledEventProcessorSettings) settings;
                    yield EventProcessorModule
                            .pooledStreaming(processorModuleName)
                            .eventHandlingComponents(componentRegistration)
                            .customized(SpringCustomizations.pooledStreamingCustomizations(processorName,
                                                                                           moduleSettings)
                                                            .andThen(c -> c.unitOfWorkFactory(axonConfiguration.getComponent(
                                                                    UnitOfWorkFactory.class)))
                                                            .andThen(customizeConfiguration(processorName)))
                            .build();
                }
                case SUBSCRIBING -> {
                    var moduleSettings = (EventProcessorSettings.SubscribingEventProcessorSettings) settings;
                    yield EventProcessorModule
                            .subscribing(processorModuleName)
                            .eventHandlingComponents(componentRegistration)
                            .customized(SpringCustomizations.subscribingCustomizations(
                                                                    processorName,
                                                                    moduleSettings
                                                            )
                                                            .andThen(c -> c.unitOfWorkFactory(axonConfiguration.getComponent(
                                                                    UnitOfWorkFactory.class)))
                                                            .andThen(customizeConfiguration(processorName)))
                            .build();
                }
            };

            modules.add(module);
        });
        return modules;
    }

    /**
     * Returns a configuration customizer function for a subscribing processor with the given name.
     * <p>
     * If a processor definition exists for this processor, its configuration is applied. Otherwise, the identity
     * function is returned (no customization).
     *
     * @param processorName The name of the processor.
     * @return A function that customizes the subscribing processor configuration.
     */
    @SuppressWarnings({"unchecked"})
    private <T extends EventProcessorConfiguration> UnaryOperator<T> customizeConfiguration(String processorName) {
        for (EventProcessorDefinition eventProcessorDefinition : eventProcessorDefinitions) {
            if (eventProcessorDefinition.name().equals(processorName)) {
                return c -> (T) eventProcessorDefinition.applySettings(c);
            }
        }
        return UnaryOperator.identity();
    }

    /**
     * Resolves the name of the processor the given event handler should be assigned to.
     * <p>
     * This method evaluates the handler against all processor definitions. If exactly one definition matches, the
     * handler is assigned to that processor. If no definitions match, the handler is assigned to a processor named
     * after its package. If multiple definitions match, an {@link AxonConfigurationException} is thrown.
     *
     * @param handler The event handler descriptor.
     * @return The name of the processor this handler should be assigned to.
     * @throws AxonConfigurationException If multiple processor definitions match the handler.
     */
    private String assignedProcessor(EventProcessorDefinition.EventHandlerDescriptor handler) {
        Set<String> matches = new HashSet<>();
        for (EventProcessorDefinition eventProcessorDefinition : eventProcessorDefinitions) {
            if (eventProcessorDefinition.matchesSelector(handler)) {
                matches.add(eventProcessorDefinition.name());
            }
        }
        if (matches.isEmpty()) {
            // we try to detect the package from the bean definition
            return BeanDefinitionUtils.extractPackageName(handler.beanDefinition());
        }
        if (matches.size() == 1) {
            return matches.iterator().next();
        }
        throw new AxonConfigurationException(
                "Handler [" + handler.beanName() + " (of type " + handler.beanDefinition().getBeanClassName()
                        + ")] matched with multiple processors selectors: " + matches);
    }

    /**
     * Finds the processor definition for the given processor name.
     *
     * @param name The processor name.
     * @return An Optional containing the processor definition if found, or empty if not found.
     */
    private Optional<EventProcessorDefinition> definitionFor(String name) {
        for (EventProcessorDefinition eventProcessorDefinition : eventProcessorDefinitions) {
            if (eventProcessorDefinition.name().equals(name)) {
                return Optional.of(eventProcessorDefinition);
            }
        }
        return Optional.empty();
    }
}
