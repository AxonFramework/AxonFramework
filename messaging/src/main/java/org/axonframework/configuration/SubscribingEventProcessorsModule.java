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
import org.axonframework.eventhandling.SubscribingEventProcessorConfiguration;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventhandling.subscribing.SubscribingEventProcessorModule;
import org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

public class SubscribingEventProcessorsModule extends BaseModule<SubscribingEventProcessorsModule> {

    private final SubscribingEventProcessorConfiguration INITIAL_EVENT_PROCESSOR_DEFAULTS =
            new SubscribingEventProcessorConfiguration()
                    .unitOfWorkFactory(new SimpleUnitOfWorkFactory());

    private ComponentBuilder<SubscribingEventProcessorConfiguration> eventProcessorDefaultsBuilder = c -> INITIAL_EVENT_PROCESSOR_DEFAULTS;
    private final List<ModuleBuilder<SubscribingEventProcessorModule>> moduleBuilders = new ArrayList<>();

    @Internal
    public SubscribingEventProcessorsModule(@Nonnull String name) {
        super(name);
    }

    @Override
    public Configuration build(@Nonnull Configuration parent, @Nonnull LifecycleRegistry lifecycleRegistry) {
        componentRegistry(
                cr -> cr.registerComponent(
                        SubscribingEventProcessorConfiguration.class,
                        eventProcessorDefaultsBuilder
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
            @Nonnull BiFunction<Configuration, SubscribingEventProcessorConfiguration, SubscribingEventProcessorConfiguration> configureDefaults) {
        this.eventProcessorDefaultsBuilder = config -> {
            var defaults = INITIAL_EVENT_PROCESSOR_DEFAULTS;
            return configureDefaults.apply(config, defaults);
        };
        return this;
    }

    public SubscribingEventProcessorsModule defaults(
            @Nonnull UnaryOperator<SubscribingEventProcessorConfiguration> configureDefaults) {
        this.eventProcessorDefaultsBuilder = config -> {
            var defaults = INITIAL_EVENT_PROCESSOR_DEFAULTS;
            return configureDefaults.apply(defaults);
        };
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
            @Nonnull BiFunction<Configuration, SubscribingEventProcessorConfiguration, SubscribingEventProcessorConfiguration> customize
    ) {
        return processor(
                EventProcessorModule.subscribing(name)
                                    .customize(config -> customization -> customize.apply(config, customization))
        );
    }
}
