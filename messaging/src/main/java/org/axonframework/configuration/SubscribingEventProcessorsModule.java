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

package org.axonframework.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.SubscribingEventProcessorConfiguration;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventhandling.subscribing.SubscribingEventProcessorModule;
import org.axonframework.messaging.SubscribableMessageSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

public class SubscribingEventProcessorsModule extends BaseModule<SubscribingEventProcessorsModule> {

    public static final String DEFAULT_NAME = "subscribingProcessors";

    private SubscribingEventProcessorModule.Customization processorsDefaultCustomization = SubscribingEventProcessorModule.Customization.noOp();
    private final List<ModuleBuilder<SubscribingEventProcessorModule>> moduleBuilders = new ArrayList<>();

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

    public SubscribingEventProcessorsModule defaults(
            @Nonnull BiFunction<Configuration, SubscribingEventProcessorConfiguration, SubscribingEventProcessorConfiguration> configureDefaults
    ) {
        this.processorsDefaultCustomization = this.processorsDefaultCustomization.andThen(configureDefaults::apply);
        return this;
    }

    public SubscribingEventProcessorsModule defaults(
            @Nonnull UnaryOperator<SubscribingEventProcessorConfiguration> configureDefaults
    ) {
        this.processorsDefaultCustomization = this.processorsDefaultCustomization.andThen(
                (axonConfig, pConfig) -> configureDefaults.apply(pConfig)
        );
        return this;
    }

    public SubscribingEventProcessorsModule processor(SubscribingEventProcessorModule module) {
        moduleBuilders.add(() -> module);
        return this;
    }

    public SubscribingEventProcessorsModule processor(
            ModuleBuilder<SubscribingEventProcessorModule> moduleBuilder) {
        moduleBuilders.add(moduleBuilder);
        return this;
    }

    public SubscribingEventProcessorsModule processor(
            @Nonnull String name,
            @Nonnull List<EventHandlingComponent> eventHandlingComponents
    ) {
        return processor(
                name,
                eventHandlingComponents,
                (cfg, c) -> c
        );
    }

    public SubscribingEventProcessorsModule processor(
            @Nonnull String name,
            @Nonnull List<EventHandlingComponent> eventHandlingComponents,
            @Nonnull BiFunction<Configuration, SubscribingEventProcessorConfiguration, SubscribingEventProcessorConfiguration> customize
    ) {
        return processor(
                name,
                (cfg, customization) -> customize.apply(cfg,
                                                        customization.eventHandlingComponents(eventHandlingComponents))
        );
    }

    public SubscribingEventProcessorsModule processor(
            @Nonnull String name,
            @Nonnull BiFunction<Configuration, SubscribingEventProcessorConfiguration, SubscribingEventProcessorConfiguration> customize
    ) {
        return processor(
                EventProcessorModule.subscribing(name)
                                    .customize(config -> customization -> customize.apply(config, customization))
        );
    }
}
