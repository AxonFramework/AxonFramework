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

package org.axonframework.messaging.eventhandling.processing.streaming.pooled;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.configuration.ModuleBuilder;
import org.axonframework.messaging.eventhandling.configuration.EventHandlingComponentsConfigurer;
import org.axonframework.messaging.eventhandling.configuration.EventProcessingConfigurer;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.TokenStore;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.inmemory.InMemoryTokenStore;
import org.axonframework.messaging.eventstreaming.StreamableEventSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * A configurer for managing multiple {@link PooledStreamingEventProcessor} instances within an application.
 * <p>
 * The {@code PooledStreamingEventProcessorsModule} provides a centralized way to configure and register multiple pooled
 * streaming event processors. It acts as a container that manages individual
 * {@link PooledStreamingEventProcessorModule} instances, allowing you to set shared defaults that apply to all
 * processors while enabling processor-specific customizations.
 * <p>
 * The main purpose is to simplify the configuration of multiple event processors by providing shared configuration
 * capabilities such as default {@link TokenStore}, {@link StreamableEventSource}, and
 * processor settings that apply to all processors unless explicitly overridden.
 * <p>
 * The configurer automatically configures default components:
 * <ul>
 * <li>Registers an {@link InMemoryTokenStore} if no
 * {@link TokenStore} is present</li>
 * <li>Automatically wires available {@link TokenStore} and {@link StreamableEventSource} components to all processors</li>
 * <li>Applies shared customizations through the {@link #defaults(BiFunction)} and {@link #defaults(UnaryOperator)} methods</li>
 * </ul>
 * <p>
 * This configurer is typically accessed through {@link EventProcessingConfigurer#pooledStreaming(UnaryOperator)}
 * rather than being instantiated directly.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class PooledStreamingEventProcessorsConfigurer {

    private final EventProcessingConfigurer parent;

    private PooledStreamingEventProcessorModule.Customization processorsDefaultCustomization = PooledStreamingEventProcessorModule.Customization.noOp();
    private final List<ModuleBuilder<PooledStreamingEventProcessorModule>> moduleBuilders = new ArrayList<>();

    /**
     * Constructs a new pooled streaming event processors configurer.
     * <p>
     * This constructor is marked as {@link Internal} because the configurer is typically created and managed by the
     * {@link EventProcessingConfigurer}. Users should not instantiate this class directly but instead access it through
     * {@link EventProcessingConfigurer#pooledStreaming(UnaryOperator)}.
     *
     * @param parent The parent {@link EventProcessingConfigurer} that manages this configurer.
     */
    @Internal
    public PooledStreamingEventProcessorsConfigurer(@Nonnull EventProcessingConfigurer parent) {
        this.parent = parent;
    }

    /**
     * Builds and registers all configured pooled streaming event processors.
     * <p>
     * This method is typically called automatically by the framework during configuration building. It registers
     * default components and all configured processor modules.
     */
    @Internal
    public void build() {
        componentRegistry(cr -> cr.registerIfNotPresent(TokenStore.class, cfg -> new InMemoryTokenStore()));
        componentRegistry(
                cr -> cr.registerComponent(
                        PooledStreamingEventProcessorModule.Customization.class,
                        cfg -> PooledStreamingEventProcessorModule.Customization.noOp().andThen(
                                (axonConfig, processorConfig) -> {
                                    cfg.getOptionalComponent(TokenStore.class)
                                       .ifPresent(processorConfig::tokenStore);
                                    cfg.getOptionalComponent(StreamableEventSource.class)
                                       .ifPresent(processorConfig::eventSource);
                                    return processorConfig;
                                }).andThen(processorsDefaultCustomization)
                )
        );
        moduleBuilders.forEach(moduleBuilder -> componentRegistry(cr -> cr.registerModule(moduleBuilder.build())));
    }

    /**
     * Configures default settings that will be applied to all {@link PooledStreamingEventProcessor} instances managed
     * by this configurer.
     * <p>
     * This method allows you to specify configurations that should be shared across all pooled streaming event
     * processors. The provided function receives both the Axon {@link Configuration} and the processor configuration,
     * allowing access to application-wide components when setting defaults.
     * <p>
     * Common use cases include setting default buffer sizes, segment counts, thread pool configurations, or error
     * handling policies that should apply to all processors unless explicitly overridden.
     *
     * @param configureDefaults A function that receives the Axon configuration and processor configuration, returning a
     *                          modified {@link PooledStreamingEventProcessorConfiguration} with the desired defaults
     *                          applied.
     * @return This configurer instance for method chaining.
     */
    @Nonnull
    public PooledStreamingEventProcessorsConfigurer defaults(
            @Nonnull BiFunction<Configuration, PooledStreamingEventProcessorConfiguration, PooledStreamingEventProcessorConfiguration> configureDefaults
    ) {
        Objects.requireNonNull(configureDefaults, "configureDefaults must not be null");
        this.processorsDefaultCustomization = this.processorsDefaultCustomization.andThen(configureDefaults::apply);
        return this;
    }

    /**
     * Configures default settings that will be applied to all {@link PooledStreamingEventProcessor} instances managed
     * by this configurer.
     * <p>
     * This is a simplified version of {@link #defaults(BiFunction)} that provides only the processor configuration for
     * modification, without access to the Axon {@link Configuration}. Use this when your default configurations don't
     * require components from the application configuration.
     *
     * @param configureDefaults A function that modifies the {@link PooledStreamingEventProcessorConfiguration} with
     *                          desired defaults.
     * @return This configurer instance for method chaining.
     */
    @Nonnull
    public PooledStreamingEventProcessorsConfigurer defaults(
            @Nonnull UnaryOperator<PooledStreamingEventProcessorConfiguration> configureDefaults
    ) {
        Objects.requireNonNull(configureDefaults, "configureDefaults must not be null");
        this.processorsDefaultCustomization = this.processorsDefaultCustomization.andThen(
                (axonConfig, pConfig) -> configureDefaults.apply(pConfig)
        );
        return this;
    }

    /**
     * Registers a pooled streaming event processor with the specified name and event handling components. The processor
     * will use the default pooled streaming event processor configuration.
     *
     * @param name                           The unique name for the processor.
     * @param eventHandlingComponentsBuilder Function to configure the event handling components.
     * @return This configurer instance for method chaining.
     */
    @Nonnull
    public PooledStreamingEventProcessorsConfigurer defaultProcessor(
            @Nonnull String name,
            @Nonnull Function<EventHandlingComponentsConfigurer.RequiredComponentPhase, EventHandlingComponentsConfigurer.CompletePhase> eventHandlingComponentsBuilder
    ) {
        processor(
                () -> EventProcessorModule.pooledStreaming(name)
                                          .eventHandlingComponents(eventHandlingComponentsBuilder)
                                          .notCustomized()
                                          .build()
        );
        return this;
    }

    /**
     * Registers a pooled streaming event processor with custom module configuration.
     *
     * @param name             The unique name for the processor.
     * @param moduleCustomizer Function to customize the processor module configuration.
     * @return This configurer instance for method chaining.
     */
    @Nonnull
    public PooledStreamingEventProcessorsConfigurer processor(
            @Nonnull String name,
            @Nonnull Function<EventProcessorModule.EventHandlingPhase<PooledStreamingEventProcessorModule, PooledStreamingEventProcessorConfiguration>, PooledStreamingEventProcessorModule> moduleCustomizer
    ) {
        processor(
                () -> moduleCustomizer.apply(EventProcessorModule.pooledStreaming(name)).build()
        );
        return this;
    }

    /**
     * Registers a {@link PooledStreamingEventProcessorModule} using a {@link ModuleBuilder}.
     *
     * @param moduleBuilder A builder that creates a {@link PooledStreamingEventProcessorModule} instance.
     * @return This module instance for method chaining.
     */
    @Nonnull
    public PooledStreamingEventProcessorsConfigurer processor(
            @Nonnull ModuleBuilder<PooledStreamingEventProcessorModule> moduleBuilder
    ) {
        Objects.requireNonNull(moduleBuilder, "moduleBuilder may not be null");
        moduleBuilders.add(moduleBuilder);
        return this;
    }

    /**
     * Provides access to the component registry for additional component registrations.
     *
     * @param registryAction Action to perform on the component registry.
     * @return This configurer instance for method chaining.
     */
    @Nonnull
    public PooledStreamingEventProcessorsConfigurer componentRegistry(
            @Nonnull Consumer<ComponentRegistry> registryAction
    ) {
        Objects.requireNonNull(registryAction, "registryAction may not be null");
        parent.componentRegistry(registryAction);
        return this;
    }
}
