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

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.core.unitofwork.transaction.TransactionManager;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.core.configuration.MessagingConfigurer;
import org.axonframework.messaging.eventhandling.processing.EventProcessor;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessor;
import org.axonframework.messaging.eventhandling.processing.subscribing.SubscribingEventProcessorsConfigurer;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessorsConfigurer;
import org.axonframework.messaging.eventhandling.processing.subscribing.SubscribingEventProcessor;
import org.axonframework.messaging.core.unitofwork.TransactionalUnitOfWorkFactory;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * A configuration module for event processing that provides a unified way to configure and manage both
 * {@link EventProcessor} types:
 * <ul>
 * <li>{@link PooledStreamingEventProcessor}</li>
 * <li>{@link SubscribingEventProcessor}</li>
 * </ul>
 * <p>
 * The {@code EventProcessingConfigurer} acts as a composite module that delegates event {@link EventProcessorConfiguration} to
 * specialized sub-modules: {@link PooledStreamingEventProcessorsConfigurer} for
 * {@link PooledStreamingEventProcessor PooledStreamingEventProcessor}
 * instances and {@link SubscribingEventProcessorsConfigurer} for
 * {@link SubscribingEventProcessor SubscribingEventProcessor} instances.
 * <p>
 * The main purpose is to provide shared configuration capabilities for all event processor types, allowing you to set
 * default configurations like {@link UnitOfWorkFactory} that apply to
 * all processors while still enabling type-specific customizations.
 * <p>
 * The module automatically configures default transaction management by registering a
 * {@link TransactionalUnitOfWorkFactory} when a {@link TransactionManager} is available in the configuration.
 * <p>
 * This module is automatically created and registered by the {@link MessagingConfigurer}
 * when the application starts, so you typically don't need to instantiate it manually. Instead, access it through
 * {@link MessagingConfigurer#eventProcessing(java.util.function.Consumer)}.
 * <p>
 * Example usage:
 * <pre>{@code
 * MessagingConfigurer.create()
 *     .eventProcessing(eventProcessing -> eventProcessing
 *         .defaults(config -> config.unitOfWorkFactory(new SimpleUnitOfWorkFactory()))
 *         .pooledStreaming(pooledStreaming ->
 *             pooledStreaming.processor("calendar-processor", components -> components.declarative(cfg -> weekStartedEventHandler))
 *                        .processor("astrologers-week-symbol-processor", components -> components.declarative(cfg -> weekSymbolProclaimedEventHandler)))
 *         .subscribing(subscribing ->
 *             subscribing.processor("creatures-dwelling-readmodel", components -> components.autodetected(cfg -> dwellingBuiltEventHandler)));
 * }</pre>
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class EventProcessingConfigurer {

    private final MessagingConfigurer parent;
    private final PooledStreamingEventProcessorsConfigurer pooledStreamingEventProcessors;
    private final SubscribingEventProcessorsConfigurer subscribingEventProcessors;

    private EventProcessorCustomization processorsDefaultCustomization = EventProcessorCustomization.noOp();

    /**
     * Constructs a new event processing module with the given name.
     * <p>
     * This constructor is marked as {@link Internal} because the module is automatically created and managed by the
     * {@link MessagingConfigurer}. Typically, users should not instantiate this class
     * directly but instead access it through
     * {@link MessagingConfigurer#eventProcessing(java.util.function.Consumer)}.
     *
     * @param parent The {@code MessagingConfigurer} to enhance with configuration of event processing components.
     */
    @Internal
    public EventProcessingConfigurer(@Nonnull MessagingConfigurer parent) {
        this.parent = Objects.requireNonNull(parent, "parent may not be null");
        this.pooledStreamingEventProcessors = new PooledStreamingEventProcessorsConfigurer(this);
        this.subscribingEventProcessors = new SubscribingEventProcessorsConfigurer(this);
    }

    /**
     * Builds the configurer, registering the necessary modules and customizations.
     * <p>
     * This method is typically called by the {@link MessagingConfigurer} during
     * application startup to finalize the configuration of event processors.
     */
    public void build() {
        componentRegistry(
                cr -> cr.registerComponent(
                        EventProcessorCustomization.class,
                        cfg -> EventProcessorCustomization.noOp().andThen(
                                (axonConfig, processorConfig) -> processorConfig.unitOfWorkFactory(
                                        cfg.getComponent(UnitOfWorkFactory.class))
                        ).andThen(processorsDefaultCustomization)
                )
        );
        pooledStreamingEventProcessors.build();
        subscribingEventProcessors.build();
    }

    /**
     * Configures default settings that will be applied to all event processors managed by this module.
     * <p>
     * This method allows you to specify configurations that should be shared across both pooled streaming and
     * subscribing event processors. The provided function receives both the Axon {@link Configuration} and the
     * {@link EventProcessorConfiguration}, allowing access to application-wide components when setting defaults.
     * <p>
     * Common use cases include setting default {@link UnitOfWorkFactory}, error
     * handlers, or processing policies that should apply to all processors unless explicitly overridden.
     *
     * @param configureDefaults A function that receives the Axon configuration and {@link EventProcessorConfiguration},
     *                          returning a modified {@link EventProcessorConfiguration} with the desired defaults
     *                          applied.
     * @return This module instance for method chaining.
     */
    public EventProcessingConfigurer defaults(
            @Nonnull BiFunction<Configuration, EventProcessorConfiguration, EventProcessorConfiguration> configureDefaults) {
        Objects.requireNonNull(configureDefaults, "configureDefaults may not be null");
        this.processorsDefaultCustomization = this.processorsDefaultCustomization.andThen(configureDefaults::apply);
        return this;
    }

    /**
     * Configures default settings that will be applied to all event processors managed by this module.
     * <p>
     * This is a simplified version of {@link #defaults(BiFunction)} that provides only the
     * {@link EventProcessorConfiguration} for modification, without access to the Axon {@link Configuration}. Use this
     * when your default configurations don't require components from the application {@link Configuration}.
     *
     * @param configureDefaults A function that modifies the {@link EventProcessorConfiguration} with desired defaults.
     * @return This module instance for method chaining.
     */
    public EventProcessingConfigurer defaults(@Nonnull UnaryOperator<EventProcessorConfiguration> configureDefaults) {
        Objects.requireNonNull(configureDefaults, "configureDefaults may not be null");
        this.processorsDefaultCustomization = this.processorsDefaultCustomization.andThen(
                (axonConfig, pConfig) -> configureDefaults.apply(pConfig)
        );
        return this;
    }

    /**
     * Provides access to configure {@link PooledStreamingEventProcessor}
     * instances through the {@link PooledStreamingEventProcessorsConfigurer}.
     * <p>
     * Use this method to define specific pooled streaming event processors, set their configurations, and register
     * event handling components. The provided function receives the module for
     * {@link PooledStreamingEventProcessor}s and can register individual
     * processors or configure module-wide settings.
     *
     * @param processorsModuleTask A function that configures the {@link PooledStreamingEventProcessorsConfigurer}.
     * @return This module instance for method chaining.
     */
    public EventProcessingConfigurer pooledStreaming(
            @Nonnull UnaryOperator<PooledStreamingEventProcessorsConfigurer> processorsModuleTask
    ) {
        Objects.requireNonNull(processorsModuleTask, "processorsModuleTask may not be null");
        processorsModuleTask.apply(pooledStreamingEventProcessors);
        return this;
    }

    /**
     * Provides access to configure {@link SubscribingEventProcessor} instances through
     * the {@link SubscribingEventProcessorsConfigurer}.
     * <p>
     * Use this method to define specific subscribing event processors, set their configurations, and register event
     * handling components. The provided function receives the module for
     * {@link SubscribingEventProcessor}s and can register individual processors or
     * configure module-wide settings.
     *
     * @param processorsModuleTask A function that configures the {@link SubscribingEventProcessorsConfigurer}.
     * @return This module instance for method chaining.
     */
    public EventProcessingConfigurer subscribing(
            @Nonnull UnaryOperator<SubscribingEventProcessorsConfigurer> processorsModuleTask
    ) {
        Objects.requireNonNull(processorsModuleTask, "processorsModuleTask may not be null");
        processorsModuleTask.apply(subscribingEventProcessors);
        return this;
    }

    /**
     * Executes the given {@code componentRegistrar} on the component registry associated with the
     * {@code MessagingConfigurer}, which is the parent of this configurer.
     *
     * @param componentRegistrar The actions to take on the component registry.
     * @return This {@code EventProcessingConfigurer} for a fluent API.
     */
    public EventProcessingConfigurer componentRegistry(@Nonnull Consumer<ComponentRegistry> componentRegistrar) {
        Objects.requireNonNull(componentRegistrar, "componentRegistrar may not be null");
        parent.componentRegistry(componentRegistrar);
        return this;
    }
}
