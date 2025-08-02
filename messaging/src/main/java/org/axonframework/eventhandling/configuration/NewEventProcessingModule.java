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
import org.axonframework.configuration.BaseModule;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.LifecycleRegistry;
import org.axonframework.eventhandling.SubscribingEventProcessorsModule;
import org.axonframework.eventhandling.EventProcessorConfiguration;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessorsModule;
import org.axonframework.messaging.unitofwork.TransactionalUnitOfWorkFactory;

import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

/**
 * A configuration module for event processing that provides a unified way to configure and manage both
 * {@link org.axonframework.eventhandling.EventProcessor} types:
 * <ul>
 * <li>{@link org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor}</li>
 * <li>{@link org.axonframework.eventhandling.SubscribingEventProcessor}</li>
 * </ul>
 * <p>
 * The {@code NewEventProcessingModule} acts as a composite module that delegates event {@link EventProcessorConfiguration} to
 * specialized sub-modules: {@link PooledStreamingEventProcessorsModule} for
 * {@link org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor PooledStreamingEventProcessor}
 * instances and {@link SubscribingEventProcessorsModule} for
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
 *             pooledStreaming.processor("calendar-processor", List.of(weekStartedEventHandler))
 *                        .processor("astrologers-week-symbol-processor", List.of(weekSymbolProclaimedEventHandler)))
 *         .subscribing(subscribing ->
 *             subscribing.processor("creatures-dwelling-readmodel", List.of(dwellingBuiltEventHandler))));
 * }</pre>
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
// TODO #3098 - Rename to EventProcessingModule - I wanted to limit the files changed at once
public class NewEventProcessingModule extends BaseModule<NewEventProcessingModule> {

    public static final String DEFAULT_NAME = "defaultEventProcessingModule";

    private final PooledStreamingEventProcessorsModule pooledStreamingEventProcessorsModule = new PooledStreamingEventProcessorsModule(
            PooledStreamingEventProcessorsModule.DEFAULT_NAME
    );
    private final SubscribingEventProcessorsModule subscribingEventProcessorsModule = new SubscribingEventProcessorsModule(
            SubscribingEventProcessorsModule.DEFAULT_NAME
    );

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
    public NewEventProcessingModule(@Nonnull String name) {
        super(name);
    }

    @Override
    public Configuration build(@Nonnull Configuration parent, @Nonnull LifecycleRegistry lifecycleRegistry) {
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
        componentRegistry(cr -> cr.registerModule(
                pooledStreamingEventProcessorsModule
        ));
        componentRegistry(cr -> cr.registerModule(
                subscribingEventProcessorsModule
        ));
        return super.build(parent, lifecycleRegistry);
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
    public NewEventProcessingModule defaults(
            @Nonnull BiFunction<Configuration, EventProcessorConfiguration, EventProcessorConfiguration> configureDefaults) {
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
    public NewEventProcessingModule defaults(@Nonnull UnaryOperator<EventProcessorConfiguration> configureDefaults) {
        this.processorsDefaultCustomization = this.processorsDefaultCustomization.andThen(
                (axonConfig, pConfig) -> configureDefaults.apply(pConfig)
        );
        return this;
    }

    /**
     * Provides access to configure {@link org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor}
     * instances through the {@link PooledStreamingEventProcessorsModule}.
     * <p>
     * Use this method to define specific pooled streaming event processors, set their configurations, and register
     * event handling components. The provided function receives the module for
     * {@link org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor}s and can register individual
     * processors or configure module-wide settings.
     *
     * @param processorsModuleTask A function that configures the {@link PooledStreamingEventProcessorsModule}.
     * @return This module instance for method chaining.
     */
    public NewEventProcessingModule pooledStreaming(
            @Nonnull UnaryOperator<PooledStreamingEventProcessorsModule> processorsModuleTask
    ) {
        processorsModuleTask.apply(pooledStreamingEventProcessorsModule);
        return this;
    }

    /**
     * Provides access to configure {@link org.axonframework.eventhandling.SubscribingEventProcessor} instances through
     * the {@link SubscribingEventProcessorsModule}.
     * <p>
     * Use this method to define specific subscribing event processors, set their configurations, and register event
     * handling components. The provided function receives the module for
     * {@link org.axonframework.eventhandling.SubscribingEventProcessor}s and can register individual processors or
     * configure module-wide settings.
     *
     * @param processorsModuleTask A function that configures the {@link SubscribingEventProcessorsModule}.
     * @return This module instance for method chaining.
     */
    public NewEventProcessingModule subscribing(
            @Nonnull UnaryOperator<SubscribingEventProcessorsModule> processorsModuleTask
    ) {
        processorsModuleTask.apply(subscribingEventProcessorsModule);
        return this;
    }
}
