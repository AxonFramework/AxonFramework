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
import org.axonframework.configuration.BaseModule;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.LifecycleRegistry;
import org.axonframework.configuration.ModuleBuilder;
import org.axonframework.eventhandling.configuration.EventHandlingComponents;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventhandling.subscribing.SubscribingEventProcessorModule;
import org.axonframework.messaging.SubscribableMessageSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
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
 * This module is typically accessed through {@link org.axonframework.eventhandling.configuration.NewEventProcessingModule#subscribing(UnaryOperator)}
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
public class SubscribingEventProcessorsModule extends BaseModule<SubscribingEventProcessorsModule> {

    public static final String DEFAULT_NAME = "defaultSubscribingEventProcessors";

    private SubscribingEventProcessorModule.Customization processorsDefaultCustomization = SubscribingEventProcessorModule.Customization.noOp();
    private final List<ModuleBuilder<SubscribingEventProcessorModule>> moduleBuilders = new ArrayList<>();

    /**
     * Constructs a new subscribing event processors module with the given name.
     * <p>
     * This constructor is marked as {@link Internal} because the module is typically created and managed by the
     * {@link org.axonframework.eventhandling.configuration.NewEventProcessingModule}. Users should not instantiate this
     * class directly but instead access it through
     * {@link org.axonframework.eventhandling.configuration.NewEventProcessingModule#subscribing(UnaryOperator)}.
     *
     * @param name The name of this subscribing event processors module.
     */
    @Internal
    public SubscribingEventProcessorsModule(@Nonnull String name) {
        super(name);
    }

    @Override
    public Configuration build(@Nonnull Configuration parent, @Nonnull LifecycleRegistry lifecycleRegistry) {
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
        return super.build(parent, lifecycleRegistry);
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
    public SubscribingEventProcessorsModule defaults(
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
    public SubscribingEventProcessorsModule defaults(
            @Nonnull UnaryOperator<SubscribingEventProcessorConfiguration> configureDefaults
    ) {
        this.processorsDefaultCustomization = this.processorsDefaultCustomization.andThen(
                (axonConfig, pConfig) -> configureDefaults.apply(pConfig)
        );
        return this;
    }

    /**
     * Registers a pre-configured {@link SubscribingEventProcessorModule} with this module.
     * <p>
     * Use this method when you have a fully configured processor module that you want to register directly. The module
     * will inherit the shared defaults configured through {@link #defaults(BiFunction)} or
     * {@link #defaults(UnaryOperator)} methods.
     *
     * @param module A pre-configured {@link SubscribingEventProcessorModule} to register.
     * @return This module instance for method chaining.
     */
    public SubscribingEventProcessorsModule processor(SubscribingEventProcessorModule module) {
        moduleBuilders.add(() -> module);
        return this;
    }

    /**
     * Registers a {@link SubscribingEventProcessorModule} using a {@link ModuleBuilder}.
     *
     * @param moduleBuilder A builder that creates a {@link SubscribingEventProcessorModule} instance.
     * @return This module instance for method chaining.
     */
    public SubscribingEventProcessorsModule processor(
            ModuleBuilder<SubscribingEventProcessorModule> moduleBuilder
    ) {
        moduleBuilders.add(moduleBuilder);
        return this;
    }

    /**
     * Registers a new {@link org.axonframework.eventhandling.SubscribingEventProcessor} with the given name and event
     * handling components.
     * <p>
     * This is the simplest way to register a subscribing event processor. The processor will be created with default
     * configuration plus any shared defaults configured through {@link #defaults(BiFunction)} or
     * {@link #defaults(UnaryOperator)} methods.
     *
     * @param name                    The unique name for the event processor.
     * @param eventHandlingComponents The list of {@link EventHandlingComponent} instances that this processor should
     *                                handle events for.
     * @return This module instance for method chaining.
     */
    public SubscribingEventProcessorsModule processor(
            @Nonnull String name,
            @Nonnull EventHandlingComponents eventHandlingComponents
    ) {
        return processor(
                name,
                eventHandlingComponents,
                (cfg, c) -> c
        );
    }

    /**
     * Registers a new {@link org.axonframework.eventhandling.SubscribingEventProcessor} with the given name and event
     * handling components.
     * <p>
     * This is the simplest way to register a subscribing event processor. The processor will be created with default
     * configuration plus any shared defaults configured through {@link #defaults(BiFunction)} or
     * {@link #defaults(UnaryOperator)} methods.
     *
     * @param name                    The unique name for the event processor.
     * @param eventHandlingComponents The list of {@link EventHandlingComponent} instances that this processor should
     *                                handle events for.
     * @return This module instance for method chaining.
     */
    public SubscribingEventProcessorsModule processor(
            @Nonnull String name,
            @Nonnull ComponentBuilder<EventHandlingComponents> eventHandlingComponents
    ) {
        return processor(
                name,
                eventHandlingComponents,
                (cfg, c) -> c
        );
    }

    /**
     * Registers a new {@link org.axonframework.eventhandling.SubscribingEventProcessor} with the given name, event
     * handling components, and custom configuration.
     * <p>
     * This method allows you to specify processor-specific customizations that will be applied in addition to any
     * shared defaults. The customization function receives both the Axon {@link Configuration} and the processor
     * configuration, allowing access to application-wide components.
     *
     * @param name                    The unique name for the event processor.
     * @param eventHandlingComponents The list of {@link EventHandlingComponent} instances that this processor should
     *                                handle events for.
     * @param customize               A function that customizes the {@link SubscribingEventProcessorConfiguration} for
     *                                this specific processor.
     * @return This module instance for method chaining.
     */
    public SubscribingEventProcessorsModule processor(
            @Nonnull String name,
            @Nonnull EventHandlingComponents eventHandlingComponents,
            @Nonnull BiFunction<Configuration, SubscribingEventProcessorConfiguration, SubscribingEventProcessorConfiguration> customize
    ) {
        return processor(
                name,
                cfg -> eventHandlingComponents,
                customize
        );
    }

    /**
     * Registers a new {@link org.axonframework.eventhandling.SubscribingEventProcessor} with the given name and custom
     * configuration.
     * <p>
     * This method provides the most flexibility for processor configuration. You are responsible for setting all
     * necessary configuration including event handling components. The customization function receives both the Axon
     * {@link Configuration} and the processor configuration.
     *
     * @param name      The unique name for the event processor.
     * @param customize A function that fully configures the {@link SubscribingEventProcessorConfiguration} for this
     *                  processor.
     * @return This module instance for method chaining.
     */
    public SubscribingEventProcessorsModule processor(
            @Nonnull String name,
            @Nonnull ComponentBuilder<EventHandlingComponents> eventHandlingComponentsBuilder,
            @Nonnull BiFunction<Configuration, SubscribingEventProcessorConfiguration, SubscribingEventProcessorConfiguration> customize
    ) {
        return processor(
                EventProcessorModule.subscribing(name)
                                    .eventHandlingComponents(eventHandlingComponentsBuilder)
                                    .customize(config -> customization -> customize.apply(config, customization))
        );
    }
}
