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
import org.axonframework.common.configuration.ComponentBuilder;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.eventhandling.configuration.EventHandlingComponentsConfigurer;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessorConfiguration;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Internal descriptor for an Axon Framework 4 Saga that supplies an assembled {@link EventHandlingComponent} instead
 * of a Spring bean whose annotated methods should be discovered.
 * <p>
 * This contract is package-private because it only connects the legacy Saga integration to Spring's internal event
 * processor assembly.
 *
 * @author Mateusz Nowak
 * @since 5.4.0
 */
@Internal
interface LegacySagaEventHandlerDescriptor extends EventProcessorDefinition.EventHandlerDescriptor {

    /**
     * Returns the assembled Saga handling component.
     *
     * @return the Saga handling component builder
     */
    ComponentBuilder<EventHandlingComponent> handlingComponent();

    /**
     * Registers the assembled Saga component without applying annotation discovery a second time.
     *
     * @param components the components already assigned to the processor
     * @return the phase accepting another component or completing registration
     */
    default EventHandlingComponentsConfigurer.AdditionalComponentPhase registerWith(
            EventHandlingComponentsConfigurer.ComponentsPhase components
    ) {
        return components.declarative(beanName(), handlingComponent());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default ComponentBuilder<Object> component() {
        return handlingComponent()::build;
    }

    /**
     * Returns the processor name to use when no processor definition or namespace assigns this Saga.
     *
     * @return the preferred fallback processor name, or empty to use the component's package
     */
    default Optional<String> preferredProcessorName() {
        return Optional.empty();
    }

    /**
     * Returns defaults to apply when this Saga is assigned to an otherwise unconfigured pooled streaming processor.
     *
     * @return the pooled streaming processor defaults
     */
    default UnaryOperator<PooledStreamingEventProcessorConfiguration> pooledStreamingDefaults() {
        return UnaryOperator.identity();
    }
}
