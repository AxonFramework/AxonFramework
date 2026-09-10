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

import org.axonframework.common.StringUtils;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.configuration.EventHandlingComponentsConfigurer;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorConfiguration;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessorConfiguration;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessorModule;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Assembles a single named {@link EventProcessorModule}, resolving its mode and settings from a matching
 * {@link EventProcessorDefinition} (by name) or the applicable {@link EventProcessorSettings}, the same way regardless
 * of whether the caller is assigning a group of regular event handlers or a single, dedicated handler such as a Saga.
 * <p>
 * Shared by {@link DefaultProcessorModuleFactory} and the Saga processor wiring, so both keep resolving a processor's
 * mode, its settings, and a matching {@link EventProcessorDefinition}'s override the same way.
 *
 * @author Mateusz Nowak
 * @since 5.4.0
 */
@Internal
final class EventProcessorModuleAssembler {

    private EventProcessorModuleAssembler() {
        // Utility class.
    }

    /**
     * Assembles the processor named {@code processorName}.
     *
     * @param processorName             the name of the processor
     * @param settings                  the settings to apply absent a matching {@link EventProcessorDefinition}
     * @param eventProcessorDefinitions the application's explicit processor definitions, searched by name
     * @param extensionsCustomizations  additional customizations (e.g. dead-lettering) applied only to pooled
     *                                  streaming processors, ahead of a matching definition's own customization
     * @param handlerDefaults           defaults applied to a pooled streaming processor before anything else,
     *                                  so that both settings and a matching definition can still override them
     * @param componentRegistration     registers the processor's event handling components
     * @return the assembled processor module
     */
    static EventProcessorModule assemble(
            String processorName,
            EventProcessorSettings settings,
            List<EventProcessorDefinition> eventProcessorDefinitions,
            List<PooledStreamingEventProcessorModule.Customization> extensionsCustomizations,
            UnaryOperator<PooledStreamingEventProcessorConfiguration> handlerDefaults,
            Function<EventHandlingComponentsConfigurer.RequiredComponentPhase, EventHandlingComponentsConfigurer.CompletePhase> componentRegistration
    ) {
        Optional<EventProcessorDefinition> matchingDefinition = definitionFor(eventProcessorDefinitions, processorName);
        var processorMode = matchingDefinition.map(EventProcessorDefinition::mode).orElse(settings.processorMode());
        return switch (processorMode) {
            case POOLED -> assemblePooled(
                    processorName,
                    (EventProcessorSettings.PooledEventProcessorSettings) settings,
                    matchingDefinition,
                    extensionsCustomizations,
                    handlerDefaults,
                    componentRegistration
            );
            case SUBSCRIBING -> assembleSubscribing(
                    processorName,
                    (EventProcessorSettings.SubscribingEventProcessorSettings) settings,
                    matchingDefinition,
                    componentRegistration
            );
        };
    }

    private static EventProcessorModule assemblePooled(
            String processorName,
            EventProcessorSettings.PooledEventProcessorSettings settings,
            Optional<EventProcessorDefinition> matchingDefinition,
            List<PooledStreamingEventProcessorModule.Customization> extensionsCustomizations,
            UnaryOperator<PooledStreamingEventProcessorConfiguration> handlerDefaults,
            Function<EventHandlingComponentsConfigurer.RequiredComponentPhase, EventHandlingComponentsConfigurer.CompletePhase> componentRegistration
    ) {
        var baseCustomization = SpringCustomizations.pooledStreamingCustomizations(processorName, settings);
        UnaryOperator<PooledStreamingEventProcessorConfiguration> definitionCustomization =
                customizeConfiguration(matchingDefinition);
        PooledStreamingEventProcessorModule.Customization customization = (axonConfig, processorConfig) -> {
            // Applied ahead of the settings so that anything stated in properties overrules it.
            var result = handlerDefaults.apply(processorConfig);
            result = baseCustomization.apply(axonConfig, result);
            result = definitionCustomization.apply(result);
            for (var extension : extensionsCustomizations) {
                result = extension.apply(axonConfig, result);
            }
            SpringCustomizations.requireResolvedTokenStore(processorName, result);
            return result;
        };
        return EventProcessorModule
                .pooledStreaming(processorName)
                .eventHandlingComponents(componentRegistration)
                .customized(customization)
                .build();
    }

    private static EventProcessorModule assembleSubscribing(
            String processorName,
            EventProcessorSettings.SubscribingEventProcessorSettings settings,
            Optional<EventProcessorDefinition> matchingDefinition,
            Function<EventHandlingComponentsConfigurer.RequiredComponentPhase, EventHandlingComponentsConfigurer.CompletePhase> componentRegistration
    ) {
        return EventProcessorModule
                .subscribing(processorName)
                .eventHandlingComponents(componentRegistration)
                .customized(SpringCustomizations.subscribingCustomizations(processorName, settings)
                                                .andThen(customizeConfiguration(matchingDefinition)))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static <T extends EventProcessorConfiguration> UnaryOperator<T> customizeConfiguration(
            Optional<EventProcessorDefinition> matchingDefinition
    ) {
        return matchingDefinition
                .<UnaryOperator<T>>map(definition -> configuration -> (T) definition.applySettings(configuration))
                .orElseGet(UnaryOperator::identity);
    }

    /**
     * Finds the processor definition named {@code name}.
     *
     * @param definitions the application's explicit processor definitions
     * @param name        the processor name
     * @return the matching definition, or empty if none of the {@code definitions} is named {@code name}
     */
    static Optional<EventProcessorDefinition> definitionFor(List<EventProcessorDefinition> definitions, String name) {
        for (EventProcessorDefinition definition : definitions) {
            if (definition.name().equals(name)) {
                return Optional.of(definition);
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves the {@link Namespace} value declared on {@code type}, searched for on the type itself, its enclosing
     * classes (innermost to outermost), its package, and its module, in that order.
     *
     * @param type the type to search
     * @return the namespace value, or empty if {@code type} carries no {@link Namespace}
     */
    static Optional<String> resolveNamespace(Class<?> type) {
        return AnnotationUtils.findAnnotationAttributesOnType(
                                      type,
                                      Namespace.class,
                                      attrs -> !StringUtils.emptyOrNull((String) attrs.get("namespace"))
                              )
                              .map(attrs -> (String) attrs.get("namespace"));
    }
}
