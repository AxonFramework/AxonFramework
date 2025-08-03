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
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.Module;
import org.axonframework.configuration.ModuleBuilder;
import org.axonframework.eventhandling.EventProcessorConfiguration;
import org.axonframework.eventhandling.SubscribingEventProcessorConfiguration;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessorConfiguration;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessorModule;
import org.axonframework.eventhandling.subscribing.SubscribingEventProcessorModule;

import java.util.function.UnaryOperator;

/**
 * Interface for configuring individual {@link org.axonframework.eventhandling.EventProcessor} modules.
 * <p>
 * This interface is typically not implemented or used directly. Instead, use the provided factory methods to create
 * specific processor modules, or access existing processors through parent module configurations like
 * {@link org.axonframework.eventhandling.SubscribingEventProcessorsModule} or
 * {@link org.axonframework.eventhandling.pooled.PooledStreamingEventProcessorsModule}.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Create a subscribing event processor
 * EventProcessorModule subscribingModule = EventProcessorModule
 *     .subscribing("notification-processor")
 *     .customize(config -> processorConfig -> processorConfig
 *         .eventHandlingComponents(List.of(notificationHandler))
 *         .messageSource(customMessageSource)
 *     );
 *
 * // Create a pooled streaming event processor
 * EventProcessorModule streamingModule = EventProcessorModule
 *     .pooledStreaming("order-processor")
 *     .customize(config -> processorConfig -> processorConfig
 *         .eventHandlingComponents(List.of(orderHandler))
 *         .bufferSize(2048)
 *         .initialSegmentCount(8)
 *     );
 * }</pre>
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public interface EventProcessorModule extends Module, ModuleBuilder<EventProcessorModule> {

    /**
     * Start building a {@link SubscribingEventProcessorModule} with the given processor processorName. The subscribing
     * event processor will register with a message source to receive events.
     *
     * @param processorName The processor processorName, must not be null or empty.
     * @return A builder phase to configure a subscribing event processor.
     */
    static EventHandlingPhase<SubscribingEventProcessorModule, SubscribingEventProcessorConfiguration> subscribing(
            String processorName) {
        return new SubscribingEventProcessorModule(processorName);
    }

    /**
     * Start building a {@link PooledStreamingEventProcessorModule} with the given processor name. The pooled streaming
     * processor manages multiple segments to process events from a stream.
     *
     * @param processorName The processor name, must not be null or empty.
     * @return A builder phase to configure a pooled streaming event processor.
     */
    static EventHandlingPhase<PooledStreamingEventProcessorModule, PooledStreamingEventProcessorConfiguration> pooledStreaming(
            String processorName) {
        return new PooledStreamingEventProcessorModule(processorName);
    }

    interface EventHandlingPhase<P extends EventProcessorModule, C extends EventProcessorConfiguration> {

        default CustomizationPhase<P, C> eventHandlingComponents(EventHandlingComponents eventHandlingComponents) {
            return eventHandlingComponents(cfg -> eventHandlingComponents);
        }

        CustomizationPhase<P, C> eventHandlingComponents(
                ComponentBuilder<EventHandlingComponents> eventHandlingComponentsBuilder);
    }

    /**
     * Configuration phase interface that provides methods for setting up event processor configurations.
     * <p>
     * This interface offers two approaches for configuring event processors:
     * <ul>
     * <li>{@link #configure(ComponentBuilder)} - Complete configuration replacement, ignoring parent defaults</li>
     * <li>{@link #customize(ComponentBuilder)} - Incremental customization on top of parent defaults</li>
     * </ul>
     * <p>
     * The customization approach is generally preferred as it preserves shared configurations from parent modules
     * while allowing processor-specific overrides.
     *
     * @param <P> The specific type of {@link EventProcessorModule} being configured.
     * @param <C> The specific type of {@link EventProcessorConfiguration} for the processor.
     * @author Mateusz Nowak
     * @since 5.0.0
     */
    interface CustomizationPhase<P extends EventProcessorModule, C extends EventProcessorConfiguration> {

        /**
         * Configures the processor with a complete configuration, ignoring any parent module defaults.
         * <p>
         * This method provides direct control over the processor configuration but bypasses shared defaults from parent
         * modules. Use {@link #customize(ComponentBuilder)} instead to preserve shared configurations while applying
         * processor-specific customizations.
         *
         * @param configurationBuilder A builder that creates the complete processor configuration.
         * @return The configured processor module.
         */
        P configure(@Nonnull ComponentBuilder<C> configurationBuilder);

        /**
         * Customizes the processor configuration by applying modifications to the default configuration.
         * <p>
         * This method applies processor-specific customizations on top of shared defaults from parent modules,
         * providing the recommended approach for most configuration scenarios.
         *
         * @param customizationBuilder A builder that creates a customization function for the processor configuration.
         * @return The configured processor module.
         */
        P customize(@Nonnull ComponentBuilder<UnaryOperator<C>> customizationBuilder);
    }
}
