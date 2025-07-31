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
import org.axonframework.eventhandling.EventProcessorConfiguration;
import org.axonframework.eventhandling.configuration.EventProcessorCustomization;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessorsModule;

import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

public class EventProcessingModule extends BaseModule<EventProcessingModule> {

    private final PooledStreamingEventProcessorsModule pooledStreamingEventProcessorsModule = new PooledStreamingEventProcessorsModule(
            "pooledStreamingProcessors");
    private final SubscribingEventProcessorsModule subscribingEventProcessorsModule = new SubscribingEventProcessorsModule(
            "subscribingProcessors");

    private EventProcessorCustomization processorsDefaultCustomization = EventProcessorCustomization.noOp();

    @Internal
    public EventProcessingModule(@Nonnull String name) {
        super(name);
    }

    @Override
    public Configuration build(@Nonnull Configuration parent, @Nonnull LifecycleRegistry lifecycleRegistry) {
        componentRegistry(
                cr -> cr.registerComponent(
                        EventProcessorCustomization.class,
                        cfg -> processorsDefaultCustomization
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

    public EventProcessingModule defaults(
            @Nonnull BiFunction<Configuration, EventProcessorConfiguration, EventProcessorConfiguration> configureDefaults) {
        this.processorsDefaultCustomization = this.processorsDefaultCustomization.andThen(configureDefaults::apply);
        return this;
    }

    public EventProcessingModule defaults(@Nonnull UnaryOperator<EventProcessorConfiguration> configureDefaults) {
        this.processorsDefaultCustomization = this.processorsDefaultCustomization.andThen(
                (axonConfig, pConfig) -> configureDefaults.apply(pConfig)
        );
        return this;
    }

    public EventProcessingModule pooledStreaming(
            @Nonnull UnaryOperator<PooledStreamingEventProcessorsModule> processorsModuleTask
    ) {
        processorsModuleTask.apply(pooledStreamingEventProcessorsModule);
        return this;
    }

    public EventProcessingModule subscribing(
            @Nonnull UnaryOperator<SubscribingEventProcessorsModule> processorsModuleTask
    ) {
        processorsModuleTask.apply(subscribingEventProcessorsModule);
        return this;
    }

}
