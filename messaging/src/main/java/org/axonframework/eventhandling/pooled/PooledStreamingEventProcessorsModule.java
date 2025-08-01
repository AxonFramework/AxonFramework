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

package org.axonframework.eventhandling.pooled;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.configuration.BaseModule;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.LifecycleRegistry;
import org.axonframework.configuration.ModuleBuilder;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.eventstreaming.StreamableEventSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

public class PooledStreamingEventProcessorsModule extends BaseModule<PooledStreamingEventProcessorsModule> {

    private PooledStreamingEventProcessorModule.Customization processorsDefaultCustomization = PooledStreamingEventProcessorModule.Customization.noOp();
    private final List<ModuleBuilder<PooledStreamingEventProcessorModule>> moduleBuilders = new ArrayList<>();

    @Internal
    public PooledStreamingEventProcessorsModule(@Nonnull String name) {
        super(name);
    }

    @Override
    public Configuration build(@Nonnull Configuration parent, @Nonnull LifecycleRegistry lifecycleRegistry) {
        componentRegistry(cr -> cr.registerIfNotPresent(TokenStore.class, cfg -> new InMemoryTokenStore()));
        componentRegistry(
                cr -> cr.registerComponent(
                        PooledStreamingEventProcessorModule.Customization.class,
                        "pooledStreamingEventProcessorCustomization",
                        cfg ->
                                PooledStreamingEventProcessorModule.Customization.noOp().andThen(
                                        (axonConfig, processorConfig) -> {
                                            cfg.getOptionalComponent(TokenStore.class)
                                               .ifPresent(processorConfig::tokenStore);
                                            cfg.getOptionalComponent(StreamableEventSource.class)
                                               .ifPresent(processorConfig::eventSource);
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

    public PooledStreamingEventProcessorsModule defaults(
            @Nonnull BiFunction<Configuration, PooledStreamingEventProcessorConfiguration, PooledStreamingEventProcessorConfiguration> configureDefaults
    ) {
        this.processorsDefaultCustomization = this.processorsDefaultCustomization.andThen(configureDefaults::apply);
        return this;
    }

    public PooledStreamingEventProcessorsModule defaults(
            @Nonnull UnaryOperator<PooledStreamingEventProcessorConfiguration> configureDefaults
    ) {
        this.processorsDefaultCustomization = this.processorsDefaultCustomization.andThen(
                (axonConfig, pConfig) -> configureDefaults.apply(pConfig)
        );
        return this;
    }

    public PooledStreamingEventProcessorsModule processor(PooledStreamingEventProcessorModule module) {
        moduleBuilders.add(() -> module);
        return this;
    }

    public PooledStreamingEventProcessorsModule processor(
            ModuleBuilder<PooledStreamingEventProcessorModule> moduleBuilder
    ) {
        moduleBuilders.add(moduleBuilder);
        return this;
    }

    public PooledStreamingEventProcessorsModule processor(
            @Nonnull String name,
            @Nonnull BiFunction<Configuration, PooledStreamingEventProcessorConfiguration, PooledStreamingEventProcessorConfiguration> customize
    ) {
        return processor(
                EventProcessorModule.pooledStreaming(name)
                                    .customize(config -> customization -> customize.apply(config, customization))
        );
    }
}
