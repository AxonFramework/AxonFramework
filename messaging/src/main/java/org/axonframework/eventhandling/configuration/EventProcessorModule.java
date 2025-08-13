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
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.Module;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventProcessorConfiguration;
import org.axonframework.eventhandling.SubscribingEventProcessorConfiguration;
import org.axonframework.eventhandling.SubscribingEventProcessorsConfigurer;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessorConfiguration;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessorModule;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessorsConfigurer;
import org.axonframework.eventhandling.subscribing.SubscribingEventProcessorModule;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Interface for configuring individual {@link org.axonframework.eventhandling.EventProcessor} modules.
 * <p>
 * This interface is typically not implemented or used directly. Instead, use the provided factory methods to create
 * specific processor modules, or access existing processors through parent module configurations like
 * {@link SubscribingEventProcessorsConfigurer} or {@link PooledStreamingEventProcessorsConfigurer}.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Create a subscribing event processor
 * EventProcessorModule subscribingModule = EventProcessorModule
 *     .subscribing("notification-processor")
 *     .eventHandlingComponent(notificationHandler)
 *     .customize((config, processorConfig) -> processorConfig
 *         .messageSource(customMessageSource)
 *     );
 *
 * // Create a pooled streaming event processor
 * EventProcessorModule streamingModule = EventProcessorModule
 *     .pooledStreaming("order-processor")
 *     .eventHandlingComponent(orderHandler)
 *     .notCustomized();
 * }</pre>
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public interface EventProcessorModule extends Module {

    /**
     * Creates a {@link SubscribingEventProcessorModule} with the given name.
     *
     * @param processorName The processor name.
     * @return A builder phase to configure the subscribing event processor.
     */
    static EventHandlingPhase<SubscribingEventProcessorModule, SubscribingEventProcessorConfiguration> subscribing(
            @Nonnull String processorName
    ) {
        return new SubscribingEventProcessorModule(processorName);
    }

    /**
     * Creates a {@link PooledStreamingEventProcessorModule} with the given name.
     *
     * @param processorName The processor name.
     * @return A builder phase to configure the pooled streaming event processor.
     */
    static EventHandlingPhase<PooledStreamingEventProcessorModule, PooledStreamingEventProcessorConfiguration> pooledStreaming(
            @Nonnull String processorName
    ) {
        return new PooledStreamingEventProcessorModule(processorName);
    }

    /**
     * Builder phase for configuring event handling components.
     *
     * @param <P> The processor module type.
     * @param <C> The processor configuration type.
     */
    interface EventHandlingPhase<P extends EventProcessorModule, C extends EventProcessorConfiguration> {

        /**
         * Configures event handling components using a configurer function.
         *
         * @param eventHandlingComponentsConfigurer The configurer function.
         * @return The customization phase for further configuration.
         */
        CustomizationPhase<P, C> eventHandlingComponents(
                @Nonnull Function<EventHandlingComponentsConfigurer.RequiredComponentPhase, EventHandlingComponentsConfigurer.CompletePhase> configurerTask
        );
    }

    /**
     * Configuration phase interface that provides methods for setting up event processor configurations.
     * <p>
     * This interface offers two approaches for finishing event processors setup:
     * <ul>
     * <li>{@link #notCustomized()} - Just accepts the shared and type-specific configuration without customizing it</li>
     * <li>{@link #customized(BiFunction)} - Incremental customization on top of shared and type-specific defaults</li>
     * </ul>
     *
     * @param <P> The specific type of {@link EventProcessorModule} being configured.
     * @param <C> The specific type of {@link EventProcessorConfiguration} for the processor.
     * @author Mateusz Nowak
     * @since 5.0.0
     */
    interface CustomizationPhase<P extends EventProcessorModule, C extends EventProcessorConfiguration> {

        /**
         * Customizes the processor configuration by applying modifications to the default configuration.
         * <p>
         * This method applies processor-specific customizations on top of shared defaults from parent modules,
         * providing the recommended approach for most configuration scenarios.
         *
         * @param instanceCustomization A function that receives the configuration and default processor config,
         *                              returning the customized processor configuration.
         * @return The configured processor module.
         */
        P customized(@Nonnull BiFunction<Configuration, C, C> instanceCustomization);

        /**
         * Builds the processor module with the current configuration.
         *
         * @return The configured processor module.
         */
        default P notCustomized() {
            return customized((cfg, processorConfig) -> processorConfig);
        }
    }
}
