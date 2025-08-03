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

package org.axonframework.eventhandling.subscribing;

import jakarta.annotation.Nonnull;
import org.axonframework.common.FutureUtils;
import org.axonframework.configuration.BaseModule;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.ComponentDefinition;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.LifecycleRegistry;
import org.axonframework.eventhandling.EventProcessorConfiguration;
import org.axonframework.eventhandling.MonitoringEventHandlingComponent;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.eventhandling.SubscribingEventProcessorConfiguration;
import org.axonframework.eventhandling.SubscribingEventProcessorsModule;
import org.axonframework.eventhandling.TracingEventHandlingComponent;
import org.axonframework.eventhandling.configuration.EventHandlingComponents;
import org.axonframework.eventhandling.configuration.EventProcessorCustomization;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventhandling.interceptors.InterceptingEventHandlingComponent;
import org.axonframework.eventhandling.interceptors.MessageHandlerInterceptors;
import org.axonframework.lifecycle.Phase;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

/**
 * A configuration module for configuring and registering a single {@link SubscribingEventProcessor} component.
 * <p>
 * The main capabilities provided by this module include:
 * <ul>
 * <li>Event handling component decoration with tracing, monitoring, and interceptors</li>
 * <li>Integration with shared configuration customizations from parent modules</li>
 * <li>Lifecycle management for the created processor</li>
 * <li>Automatic subscription to the configured message source</li>
 * </ul>
 * <p>
 * This module is typically not instantiated directly but created through
 * {@link EventProcessorModule#subscribing(String)} or registered via
 * {@link SubscribingEventProcessorsModule#processor(String, List)} methods.
 * <p>
 * The module applies shared defaults from {@link SubscribingEventProcessorsModule} and
 * {@link org.axonframework.eventhandling.configuration.NewEventProcessingModule} before applying
 * processor-specific customizations.
 * <p>
 * Example configuration:
 * <pre>{@code
 * EventProcessorModule.subscribing("notification-processor")
 *     .customize(config -> processorConfig -> processorConfig
 *         .eventHandlingComponents(List.of(notificationHandler))
 *         .messageSource(customMessageSource)
 *         .errorHandler(customErrorHandler)
 *     );
 * }</pre>
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class SubscribingEventProcessorModule extends BaseModule<SubscribingEventProcessorModule>
        implements EventProcessorModule,
        EventProcessorModule.EventHandlingPhase<SubscribingEventProcessorModule, SubscribingEventProcessorConfiguration>,
        EventProcessorModule.CustomizationPhase<SubscribingEventProcessorModule, SubscribingEventProcessorConfiguration> {

    private final String processorName;
    private ComponentBuilder<EventHandlingComponents> eventHandlingComponentsBuilder;
    private ComponentBuilder<SubscribingEventProcessorConfiguration> configurationBuilder;

    // TODO #3103 - Rewrite with Event Handling interceptors support. Should be configurable.
    private final MessageHandlerInterceptors messageHandlerInterceptors = new MessageHandlerInterceptors();

    /**
     * Constructs a module with the given processor name.
     * <p>
     * The processor name will be used as the module name and as the unique identifier for the
     * {@link SubscribingEventProcessor} component created by this module.
     *
     * @param processorName The unique name for the subscribing event processor.
     */
    public SubscribingEventProcessorModule(String processorName) {
        super(processorName);
        this.processorName = processorName;
    }

    @Override
    public Configuration build(@Nonnull Configuration parent, @Nonnull LifecycleRegistry lifecycleRegistry) {
        var configuration = configurationBuilder.build(parent);

        var spanFactory = configuration.spanFactory();
        var messageMonitor = configuration.messageMonitor();

        var eventHandlingComponents = eventHandlingComponentsBuilder.build(parent);
        // TODO #3098 - Move it somewhere else! Like a decorator if certain enhancer applied.
        var decoratedEventHandlingComponents = eventHandlingComponents
                .decorated(c -> new TracingEventHandlingComponent(
                        (event) -> spanFactory.createProcessEventSpan(false, event),
                        new MonitoringEventHandlingComponent(
                                messageMonitor,
                                new InterceptingEventHandlingComponent(
                                        messageHandlerInterceptors,
                                        c
                                )
                        )
                )).toList();

        var processor = new SubscribingEventProcessor(
                processorName,
                decoratedEventHandlingComponents,
                configuration
        );

        var processorComponentDefinition = ComponentDefinition
                .ofTypeAndName(SubscribingEventProcessor.class, processorName)
                .withBuilder(c -> processor)
                .onStart(Phase.INBOUND_EVENT_CONNECTORS, (cfg, component) -> {
                    component.start();
                    return FutureUtils.emptyCompletedFuture();
                }).onShutdown(Phase.INBOUND_EVENT_CONNECTORS, (cfg, component) -> {
                    return component.shutdownAsync();
                });

        componentRegistry(cr -> cr.registerComponent(processorComponentDefinition));

        return super.build(parent, lifecycleRegistry);
    }


    /**
     * Configures this module with a complete {@link SubscribingEventProcessorConfiguration}.
     * <p>
     * This method provides the most direct way to set the processor configuration. The configuration builder receives
     * the Axon {@link Configuration} and should return a fully configured
     * {@link SubscribingEventProcessorConfiguration} instance.
     * <p>
     * <strong>Important:</strong> This method does not respect parent configurations and will fully override any
     * shared defaults from {@link SubscribingEventProcessorsModule} or
     * {@link org.axonframework.eventhandling.configuration.NewEventProcessingModule}. Use
     * {@link #customize(ComponentBuilder)} instead to apply processor-specific customizations while preserving shared
     * defaults.
     *
     * @param configurationBuilder A builder that creates the complete processor configuration.
     * @return This module instance for method chaining.
     */
    @Override
    public SubscribingEventProcessorModule configure(
            @Nonnull ComponentBuilder<SubscribingEventProcessorConfiguration> configurationBuilder
    ) {
        this.configurationBuilder = configurationBuilder;
        return this;
    }

    @Override
    public CustomizationPhase<SubscribingEventProcessorModule, SubscribingEventProcessorConfiguration> eventHandlingComponents(
            ComponentBuilder<EventHandlingComponents> eventHandlingComponentsBuilder
    ) {
        this.eventHandlingComponentsBuilder = eventHandlingComponentsBuilder;
        return this;
    }

    /**
     * Customizes the processor configuration by applying modifications to the default configuration.
     * <p>
     * This method allows you to provide processor-specific customizations that will be applied on top of any shared
     * defaults from parent modules. The customization builder receives the Axon {@link Configuration} and should return
     * a function that modifies the processor configuration.
     * <p>
     * The customization is applied after shared defaults from {@link SubscribingEventProcessorsModule} and
     * {@link org.axonframework.eventhandling.configuration.NewEventProcessingModule}.
     *
     * @param customizationBuilder A builder that creates a customization function for the processor configuration.
     * @return This module instance for method chaining.
     */
    @Override
    public SubscribingEventProcessorModule customize(
            @Nonnull ComponentBuilder<UnaryOperator<SubscribingEventProcessorConfiguration>> customizationBuilder
    ) {
        configure(
                cfg -> sharedCustomizationOrNoOp(cfg).apply(
                        cfg,
                        customizationBuilder.build(cfg).apply(defaultEventProcessorsConfiguration(cfg))
                )
        );
        return this;
    }

    @Nonnull
    private static SubscribingEventProcessorConfiguration defaultEventProcessorsConfiguration(Configuration cfg) {
        return new SubscribingEventProcessorConfiguration(
                parentSharedCustomizationOrDefault(cfg).apply(cfg,
                                                              new EventProcessorConfiguration())
        );
    }

    private static SubscribingEventProcessorModule.Customization sharedCustomizationOrNoOp(
            Configuration cfg
    ) {
        return cfg.getOptionalComponent(SubscribingEventProcessorModule.Customization.class,
                                        "subscribingEventProcessorCustomization")
                  .orElseGet(SubscribingEventProcessorModule.Customization::noOp);
    }

    private static EventProcessorCustomization parentSharedCustomizationOrDefault(
            Configuration cfg
    ) {
        return cfg.getOptionalComponent(EventProcessorCustomization.class)
                  .orElseGet(EventProcessorCustomization::noOp);
    }

    @Override
    public EventProcessorModule build() {
        return this;
    }

    /**
     * Allows customizing the {@link SubscribingEventProcessorConfiguration}.
     * <p>
     * The interface provides composition capabilities through
     * {@link #andThen(SubscribingEventProcessorModule.Customization)} to allow chaining multiple customizations in a
     * specific order.
     *
     * @author Mateusz Nowak
     * @since 5.0.0
     */
    @FunctionalInterface
    public interface Customization extends
            BiFunction<Configuration, SubscribingEventProcessorConfiguration, SubscribingEventProcessorConfiguration> {

        /**
         * Creates a no-operation customization that returns the processor configuration unchanged.
         *
         * @return A customization that applies no changes to the processor configuration.
         */
        static Customization noOp() {
            return (config, pConfig) -> pConfig;
        }

        /**
         * Returns a composed customization that applies this customization first, then applies the other
         * customization.
         * <p>
         * This allows for chaining multiple customizations together, with each subsequent customization receiving the
         * result of the previous one.
         *
         * @param other The customization to apply after this one.
         * @return A composed customization that applies both customizations in sequence.
         */
        default Customization andThen(Customization other) {
            return (config, pConfig) -> other.apply(config, this.apply(config, pConfig));
        }
    }
}
