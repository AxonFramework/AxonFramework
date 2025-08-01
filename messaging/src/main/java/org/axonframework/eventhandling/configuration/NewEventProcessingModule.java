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
import org.axonframework.configuration.SubscribingEventProcessorsModule;
import org.axonframework.eventhandling.EventProcessorConfiguration;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessorsModule;
import org.axonframework.messaging.unitofwork.TransactionalUnitOfWorkFactory;

import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

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

    public NewEventProcessingModule defaults(
            @Nonnull BiFunction<Configuration, EventProcessorConfiguration, EventProcessorConfiguration> configureDefaults) {
        this.processorsDefaultCustomization = this.processorsDefaultCustomization.andThen(configureDefaults::apply);
        return this;
    }

    public NewEventProcessingModule defaults(@Nonnull UnaryOperator<EventProcessorConfiguration> configureDefaults) {
        this.processorsDefaultCustomization = this.processorsDefaultCustomization.andThen(
                (axonConfig, pConfig) -> configureDefaults.apply(pConfig)
        );
        return this;
    }

    public NewEventProcessingModule pooledStreaming(
            @Nonnull UnaryOperator<PooledStreamingEventProcessorsModule> processorsModuleTask
    ) {
        processorsModuleTask.apply(pooledStreamingEventProcessorsModule);
        return this;
    }

    public NewEventProcessingModule subscribing(
            @Nonnull UnaryOperator<SubscribingEventProcessorsModule> processorsModuleTask
    ) {
        processorsModuleTask.apply(subscribingEventProcessorsModule);
        return this;
    }
}
