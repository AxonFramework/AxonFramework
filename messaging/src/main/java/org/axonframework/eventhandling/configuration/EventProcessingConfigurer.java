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
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.MessagingConfigurer;
import org.axonframework.eventhandling.SubscribingEventProcessorsConfigurer;
import org.axonframework.eventhandling.EventProcessorConfiguration;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessorsConfigurer;
import org.axonframework.messaging.unitofwork.TransactionalUnitOfWorkFactory;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * A configuration module for event processing that provides a unified way to configure and manage both
 * {@link org.axonframework.eventhandling.EventProcessor} types:
 * <ul>
 * <li>{@link org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor}</li>
 * <li>{@link org.axonframework.eventhandling.SubscribingEventProcessor}</li>
 * </ul>
 * <p>
 * The {@code EventProcessingConfigurer} acts as a composite module that delegates event {@link EventProcessorConfiguration} to
 * specialized sub-modules: {@link PooledStreamingEventProcessorsConfigurer} for
 * {@link org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor PooledStreamingEventProcessor}
 * instances and {@link SubscribingEventProcessorsConfigurer} for
 * {@link org.axonframework.eventhandling.SubscribingEventProcessor SubscribingEventProcessor} instances.
 * <p>
 * The main purpose is to provide shared configuration capabilities for all event processor types, allowing you to set
 * default configurations like {@link org.axonframework.messaging.unitofwork.UnitOfWorkFactory} that apply to
 * all processors while still enabling type-specific customizations.
 * <p>
 * The module automatically configures default transaction management by registering a
 * {@link TransactionalUnitOfWorkFactory} when a {@link TransactionManager} is available in the configuration.
 * <p>
 * This module is automatically created and registered by the {@link org.axonframework.configuration.MessagingConfigurer}
 * when the application starts, so you typically don't need to instantiate it manually. Instead, access it through
 * {@link org.axonframework.configuration.MessagingConfigurer#eventProcessing(java.util.function.Consumer)}.
 * <p>
 * Example usage:
 * <pre>{@code
 * MessagingConfigurer.create()
 *     .eventProcessing(eventProcessing -> eventProcessing
 *         .defaults(config -> config.bufferSize(512))
 *         .pooledStreaming(pooledStreaming ->
 *             pooledStreaming.processor("calendar-processor", components -> components.declarative(cfg -> weekStartedEventHandler))
 *                        .processor("astrologers-week-symbol-processor", components -> components.declarative(cfg -> weekSymbolProclaimedEventHandler)))
 *         .subscribing(subscribing ->
 *             subscribing.processor("creatures-dwelling-readmodel", components -> components.annotated(cfg -> dwellingBuiltEventHandler)));
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
     * {@link org.axonframework.configuration.MessagingConfigurer}. Typically, users should not instantiate this class
     * directly but instead access it through
     * {@link org.axonframework.configuration.MessagingConfigurer#eventProcessing(java.util.function.Consumer)}.
     *
     * @param name The name of this event processing module.
     */
    @Internal
    public EventProcessingConfigurer(@Nonnull MessagingConfigurer parent) {
        this.parent = Objects.requireNonNull(parent, "parent may not be null");
        this.pooledStreamingEventProcessors = new PooledStreamingEventProcessorsConfigurer(this);
        this.subscribingEventProcessors = new SubscribingEventProcessorsConfigurer(this);
    }

    public void build() {
        componentRegistry(
                cr -> cr.registerComponent(
                        EventProcessorCustomization.class,
                        cfg -> EventProcessorCustomization.noOp().andThen(
                                (axonConfig, processorConfig) -> {
                                    cfg.getOptionalComponent(TransactionManager.class)
                                       .map(TransactionalUnitOfWorkFactory::new)
                                       .ifPresent(processorConfig::unitOfWorkFactory);
                                    return processorConfig;
                                }).andThen(processorsDefaultCustomization)
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
     * Common use cases include setting default {@link org.axonframework.messaging.unitofwork.UnitOfWorkFactory}, error
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
     * Provides access to configure {@link org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor}
     * instances through the {@link PooledStreamingEventProcessorsConfigurer}.
     * <p>
     * Use this method to define specific pooled streaming event processors, set their configurations, and register
     * event handling components. The provided function receives the module for
     * {@link org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor}s and can register individual
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
     * Provides access to configure {@link org.axonframework.eventhandling.SubscribingEventProcessor} instances through
     * the {@link SubscribingEventProcessorsConfigurer}.
     * <p>
     * Use this method to define specific subscribing event processors, set their configurations, and register event
     * handling components. The provided function receives the module for
     * {@link org.axonframework.eventhandling.SubscribingEventProcessor}s and can register individual processors or
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

    public EventProcessingConfigurer componentRegistry(@Nonnull Consumer<ComponentRegistry> registryAction) {
        Objects.requireNonNull(registryAction, "registryAction may not be null");
        parent.componentRegistry(registryAction);
        return this;
    }
}
