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

package org.axonframework.eventhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.ModuleBuilder;
import org.axonframework.eventhandling.configuration.EventHandlingComponentsConfigurer;
import org.axonframework.eventhandling.configuration.EventProcessingConfigurer;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventhandling.subscribing.SubscribingEventProcessorModule;
import org.axonframework.messaging.SubscribableMessageSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * A configuration module for managing multiple {@link org.axonframework.eventhandling.SubscribingEventProcessor}
 * instances within an application.
 * <p>
 * The {@code SubscribingEventProcessorsModule} provides a centralized way to configure and register multiple
 * subscribing event processors. It acts as a container module that manages individual
 * {@link SubscribingEventProcessorModule} instances, allowing you to set shared defaults that apply to all processors
 * while enabling processor-specific customizations.
 * <p>
 * The main purpose is to simplify the configuration of multiple event processors by providing shared configuration
 * capabilities such as default {@link org.axonframework.messaging.SubscribableMessageSource} and processor settings
 * that apply to all processors unless explicitly overridden.
 * <p>
 * The module automatically configures default components:
 * <ul>
 * <li>Automatically wires available {@link org.axonframework.messaging.SubscribableMessageSource} components to all processors</li>
 * <li>Applies shared customizations through the {@link #defaults(BiFunction)} and {@link #defaults(UnaryOperator)} methods</li>
 * </ul>
 * <p>
 * This module is typically accessed through {@link EventProcessingConfigurer#subscribing(UnaryOperator)}
 * rather than being instantiated directly.
 * <p>
 * Example usage:
 * <pre>{@code
 * MessagingConfigurer.create()
 *     .eventProcessing(eventProcessing -> eventProcessing
 *         .subscribing(subscribing -> subscribing
 *             .defaults(config -> config.errorHandler(myErrorHandler))
 *             .processor("projection-processor", List.of(projectionHandler))
 *             .processor("notification-processor", List.of(notificationHandler),
 *                       (cfg, config) -> config.messageSource(customMessageSource)) // Processor-specific override
 *         )
 *     );
 * }</pre>
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */

public class SubscribingEventProcessorsConfigurer {

    private final EventProcessingConfigurer parent;
    private SubscribingEventProcessorModule.Customization processorsDefaultCustomization = SubscribingEventProcessorModule.Customization.noOp();
    private final List<ModuleBuilder<SubscribingEventProcessorModule>> moduleBuilders = new ArrayList<>();

    /**
     * Constructs a new subscribing event processors module.
     * <p>
     * This constructor is marked as {@link Internal} because the module is typically created and managed by the
     * {@link EventProcessingConfigurer}. Users should not instantiate this
     * class directly but instead access it through
     * {@link EventProcessingConfigurer#subscribing(UnaryOperator)}.
     *
     * @param parent The parent {@link EventProcessingConfigurer} that manages this module.
     */
    @Internal
    public SubscribingEventProcessorsConfigurer(@Nonnull EventProcessingConfigurer parent) {
        this.parent = parent;
    }

    /**
     * Builds and registers all configured subscribing event processors.
     * <p>
     * This method is typically called automatically by the framework during configuration building.
     * It registers default components and all configured processor modules.
     */
    @Internal
    public void build() {
        componentRegistry(
                cr -> cr.registerComponent(
                        SubscribingEventProcessorModule.Customization.class,
                        "subscribingEventProcessorCustomization",
                        cfg ->
                                SubscribingEventProcessorModule.Customization.noOp().andThen(
                                        (axonConfig, processorConfig) -> {
                                            cfg.getOptionalComponent(SubscribableMessageSource.class)
                                               .ifPresent(processorConfig::messageSource);
                                            return processorConfig;
                                        }).andThen(processorsDefaultCustomization)
                )
        );
        moduleBuilders.forEach(moduleBuilder ->
                                       componentRegistry(cr -> cr.registerModule(
                                               moduleBuilder.build()
                                       ))
        );
    }

    /**
     * Configures default settings that will be applied to all
     * {@link org.axonframework.eventhandling.SubscribingEventProcessor} instances managed by this module.
     * <p>
     * This method allows you to specify configurations that should be shared across all subscribing event processors.
     * The provided function receives both the Axon {@link Configuration} and the processor configuration, allowing
     * access to application-wide components when setting defaults.
     * <p>
     * Common use cases include setting default error handling policies, message interceptors, or processing
     * configurations that should apply to all processors unless explicitly overridden.
     *
     * @param configureDefaults A function that receives the Axon configuration and processor configuration, returning a
     *                          modified {@link SubscribingEventProcessorConfiguration} with the desired defaults
     *                          applied.
     * @return This module instance for method chaining.
     */
    public SubscribingEventProcessorsConfigurer defaults(
            @Nonnull BiFunction<Configuration, SubscribingEventProcessorConfiguration, SubscribingEventProcessorConfiguration> configureDefaults
    ) {
        this.processorsDefaultCustomization = this.processorsDefaultCustomization.andThen(configureDefaults::apply);
        return this;
    }

    /**
     * Configures default settings that will be applied to all
     * {@link org.axonframework.eventhandling.SubscribingEventProcessor} instances managed by this module.
     * <p>
     * This is a simplified version of {@link #defaults(BiFunction)} that provides only the processor configuration for
     * modification, without access to the Axon {@link Configuration}. Use this when your default configurations don't
     * require components from the application configuration.
     *
     * @param configureDefaults A function that modifies the {@link SubscribingEventProcessorConfiguration} with desired
     *                          defaults.
     * @return This module instance for method chaining.
     */
    public SubscribingEventProcessorsConfigurer defaults(
            @Nonnull UnaryOperator<SubscribingEventProcessorConfiguration> configureDefaults
    ) {
        this.processorsDefaultCustomization = this.processorsDefaultCustomization.andThen(
                (axonConfig, pConfig) -> configureDefaults.apply(pConfig)
        );
        return this;
    }

    /**
     * Registers a subscribing event processor with the specified name and event handling components.
     *
     * @param name                         The unique name for the processor.
     * @param eventHandlingComponentsBuilder Function to configure the event handling components.
     * @return This configurer instance for method chaining.
     */
    public SubscribingEventProcessorsConfigurer processor(
            @Nonnull String name,
            @Nonnull BiFunction<Configuration, EventHandlingComponentsConfigurer.ComponentsPhase, EventHandlingComponentsConfigurer.CompletePhase> eventHandlingComponentsBuilder
    ) {
        processor(
                () -> EventProcessorModule.subscribing(name)
                                          .eventHandlingComponents(eventHandlingComponentsBuilder)
                                          .notCustomized()
        );
        return this;
    }

    /**
     * Registers a subscribing event processor with custom module configuration.
     *
     * @param name             The unique name for the processor.
     * @param moduleCustomizer Function to customize the processor module configuration.
     * @return This configurer instance for method chaining.
     */
    public SubscribingEventProcessorsConfigurer processor(
            @Nonnull String name,
            @Nonnull Function<EventProcessorModule.EventHandlingPhase<SubscribingEventProcessorModule, SubscribingEventProcessorConfiguration>, SubscribingEventProcessorModule> moduleCustomizer
    ) {
        processor(
                () -> moduleCustomizer.apply(EventProcessorModule.subscribing(name))
        );
        return this;
    }

    /**
     * Registers a {@link SubscribingEventProcessorModule} using a {@link ModuleBuilder}.
     *
     * @param moduleBuilder A builder that creates a {@link SubscribingEventProcessorModule} instance.
     * @return This module instance for method chaining.
     */
    public SubscribingEventProcessorsConfigurer processor(
            ModuleBuilder<SubscribingEventProcessorModule> moduleBuilder
    ) {
        moduleBuilders.add(moduleBuilder);
        return this;
    }

    /**
     * Provides access to the component registry for additional component registrations.
     *
     * @param registryAction Action to perform on the component registry.
     * @return This configurer instance for method chaining.
     */
    public SubscribingEventProcessorsConfigurer componentRegistry(@Nonnull Consumer<ComponentRegistry> registryAction) {
        parent.componentRegistry(registryAction);
        return this;
    }
}
