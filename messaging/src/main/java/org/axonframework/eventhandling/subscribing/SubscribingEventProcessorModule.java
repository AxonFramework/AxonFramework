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
import org.axonframework.configuration.ModuleBuilder;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.configuration.EventProcessorConfiguration;
import org.axonframework.eventhandling.MonitoringEventHandlingComponent;
import org.axonframework.eventhandling.SequenceCachingEventHandlingComponent;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.eventhandling.SubscribingEventProcessorConfiguration;
import org.axonframework.eventhandling.SubscribingEventProcessorsConfigurer;
import org.axonframework.eventhandling.TracingEventHandlingComponent;
import org.axonframework.eventhandling.configuration.DefaultEventHandlingComponentsConfigurer;
import org.axonframework.eventhandling.configuration.EventHandlingComponentsConfigurer;
import org.axonframework.eventhandling.configuration.EventProcessingConfigurer;
import org.axonframework.eventhandling.configuration.EventProcessorCustomization;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventhandling.interceptors.InterceptingEventHandlingComponent;
import org.axonframework.lifecycle.Phase;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.IntStream;

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
 * {@link SubscribingEventProcessorsConfigurer#defaultProcessor} methods.
 * <p>
 * The module applies shared defaults from {@link SubscribingEventProcessorsConfigurer} and
 * {@link EventProcessingConfigurer} before applying
 * processor-specific customizations.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class SubscribingEventProcessorModule extends BaseModule<SubscribingEventProcessorModule>
        implements EventProcessorModule, ModuleBuilder<SubscribingEventProcessorModule>,
        EventProcessorModule.EventHandlingPhase<SubscribingEventProcessorModule, SubscribingEventProcessorConfiguration>,
        EventProcessorModule.CustomizationPhase<SubscribingEventProcessorModule, SubscribingEventProcessorConfiguration> {

    private final String processorName;
    private List<ComponentBuilder<EventHandlingComponent>> eventHandlingComponentBuilders;
    private ComponentBuilder<SubscribingEventProcessorConfiguration> customizedProcessorConfigurationBuilder;

    /**
     * Constructs a module with the given processor name.
     * <p>
     * The processor name will be used as the module name and as the unique identifier for the
     * {@link SubscribingEventProcessor} component created by this module.
     *
     * @param processorName The unique name for the subscribing event processor.
     */
    public SubscribingEventProcessorModule(@Nonnull String processorName) {
        super(processorName);
        this.processorName = processorName;
    }

    @Override
    public SubscribingEventProcessorModule build() {
        registerCustomizedConfiguration();
        registerEventHandlingComponents();
        registerEventProcessor();
        return this;
    }

    private void registerCustomizedConfiguration() {
        componentRegistry(cr -> cr.registerComponent(
                ComponentDefinition.ofType(SubscribingEventProcessorConfiguration.class)
                                   .withBuilder(cfg -> customizedProcessorConfigurationBuilder.build(cfg))
        ));
    }

    private void registerEventProcessor() {
        var processorComponentDefinition = ComponentDefinition
                .ofTypeAndName(SubscribingEventProcessor.class, processorName)
                .withBuilder(cfg -> new SubscribingEventProcessor(
                        processorName,
                        getEventHandlingComponents(cfg),
                        cfg.getComponent(SubscribingEventProcessorConfiguration.class)
                ))
                .onStart(Phase.INBOUND_EVENT_CONNECTORS, (cfg, processor) -> {
                    processor.start();
                    return FutureUtils.emptyCompletedFuture();
                }).onShutdown(Phase.INBOUND_EVENT_CONNECTORS, (cfg, processor) -> {
                    return processor.shutdownAsync();
                });

        componentRegistry(cr -> cr.registerComponent(processorComponentDefinition));
    }

    private void registerEventHandlingComponents() {
        for (int i = 0; i < eventHandlingComponentBuilders.size(); i++) {
            var componentBuilder = eventHandlingComponentBuilders.get(i);
            var componentName = processorEventHandlingComponentName(i);
            componentRegistry(
                    cr -> cr.registerComponent(
                            EventHandlingComponent.class,
                            componentName,
                            cfg -> {
                                var component = componentBuilder.build(cfg);
                                var configuration = cfg.getComponent(SubscribingEventProcessorConfiguration.class);
                                return withDefaultDecoration(component, configuration);
                            }));
        }
    }

    private List<EventHandlingComponent> getEventHandlingComponents(Configuration configuration) {
        return IntStream.range(0, eventHandlingComponentBuilders.size())
                        .mapToObj(i -> {
                            String componentName = processorEventHandlingComponentName(i);
                            return configuration.getComponent(EventHandlingComponent.class, componentName);
                        })
                        .toList();
    }

    @Nonnull
    private String processorEventHandlingComponentName(int index) {
        return "EventHandlingComponents[" + processorName + "][" + index + "]";
    }

    // TODO #3098 - Move it somewhere else! Like a decorator if certain enhancer applied.
    @Nonnull
    private static TracingEventHandlingComponent withDefaultDecoration(
            EventHandlingComponent c,
            EventProcessorConfiguration configuration
    ) {
        return new TracingEventHandlingComponent(
                (event) -> configuration.spanFactory().createProcessEventSpan(false, event),
                new MonitoringEventHandlingComponent(
                        configuration.messageMonitor(),
                        new InterceptingEventHandlingComponent(
                                configuration.interceptors(),
                                new SequenceCachingEventHandlingComponent(c)
                        )
                )
        );
    }

    @Override
    public SubscribingEventProcessorModule customized(
            @Nonnull BiFunction<Configuration, SubscribingEventProcessorConfiguration, SubscribingEventProcessorConfiguration> instanceCustomization
    ) {
        this.customizedProcessorConfigurationBuilder = cfg -> {
            var typeCustomization = typeSpecificCustomizationOrNoOp(cfg).apply(cfg,
                                                                               defaultEventProcessorsConfiguration(
                                                                                       cfg));
            return instanceCustomization.apply(cfg, typeCustomization);
        };
        return this;
    }

    @Override
    public SubscribingEventProcessorModule notCustomized() {
        if (customizedProcessorConfigurationBuilder == null) {
            customized((cfg, config) -> config);
        }
        return this;
    }

    @Nonnull
    private static SubscribingEventProcessorConfiguration defaultEventProcessorsConfiguration(Configuration cfg) {
        return new SubscribingEventProcessorConfiguration(
                parentSharedCustomizationOrDefault(cfg).apply(cfg,
                                                              new EventProcessorConfiguration())
        );
    }

    private static SubscribingEventProcessorModule.Customization typeSpecificCustomizationOrNoOp(
            Configuration cfg
    ) {
        return cfg.getOptionalComponent(SubscribingEventProcessorModule.Customization.class)
                  .orElseGet(SubscribingEventProcessorModule.Customization::noOp);
    }

    private static EventProcessorCustomization parentSharedCustomizationOrDefault(
            Configuration cfg
    ) {
        return cfg.getOptionalComponent(EventProcessorCustomization.class)
                  .orElseGet(EventProcessorCustomization::noOp);
    }

    @Override
    public CustomizationPhase<SubscribingEventProcessorModule, SubscribingEventProcessorConfiguration> eventHandlingComponents(
            @Nonnull Function<EventHandlingComponentsConfigurer.RequiredComponentPhase, EventHandlingComponentsConfigurer.CompletePhase> configurerTask
    ) {
        Objects.requireNonNull(configurerTask, "configurerTask may not be null");
        var componentsConfigurer = new DefaultEventHandlingComponentsConfigurer();
        this.eventHandlingComponentBuilders = configurerTask.apply(componentsConfigurer).toList();
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
