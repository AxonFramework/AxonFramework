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
import org.axonframework.eventhandling.ErrorHandler;
import org.axonframework.eventhandling.PropagatingErrorHandler;
import org.axonframework.eventhandling.configuration.EventProcessorModule;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Internal configurer for the MessagingConfigurer, which is used to configure the event processing.
 */
@Internal
public class EventProcessingConfigurer implements ApplicationConfigurer {

    // TODO:
    // configureDefaultStreamableMessageSource
    // configureDefaultSubscribableMessageSource

    private final MessagingConfigurer delegate;
    private SharedEventProcessorConfiguration sharedConfiguration = SharedEventProcessorConfiguration.defaults();

    private EventProcessingConfigurer(MessagingConfigurer delegate) {
        this.delegate = delegate;
    }

    public static EventProcessingConfigurer enhance(@Nonnull MessagingConfigurer messagingConfigurer) {
        return new EventProcessingConfigurer(messagingConfigurer)
                .componentRegistry(cr -> cr
                        .registerEnhancer(new EventProcessingDefaultsEnhancer())
                );
    }

    public EventProcessingConfigurer defaults(
            @Nonnull UnaryOperator<SharedEventProcessorConfiguration> configureDefaults) {
        this.sharedConfiguration = configureDefaults.apply(sharedConfiguration);
        return this;
    }

    public EventProcessingConfigurer registerEventProcessorModule(
            ModuleBuilder<EventProcessorModule> moduleBuilder
    ) {
        componentRegistry(cr -> cr.registerModule(moduleBuilder.build()));
        return this;
    }


    @Override
    public EventProcessingConfigurer componentRegistry(@Nonnull Consumer<ComponentRegistry> componentRegistrar) {
        delegate.componentRegistry(componentRegistrar);
        return this;
    }

    @Override
    public EventProcessingConfigurer lifecycleRegistry(@Nonnull Consumer<LifecycleRegistry> lifecycleRegistrar) {
        delegate.lifecycleRegistry(lifecycleRegistrar);
        return this;
    }

    @Override
    public AxonConfiguration build() {
        componentRegistry(
                cr -> cr.registerComponent(
                        SharedEventProcessorConfiguration.class,
                        cfg -> sharedConfiguration
                )
        );
        return delegate.build();
    }


    public record SharedEventProcessorConfiguration(
            ErrorHandler errorHandler
    ) {

        static SharedEventProcessorConfiguration defaults() {
            return new SharedEventProcessorConfiguration(
                    PropagatingErrorHandler.INSTANCE
            );
        }

        public SharedEventProcessorConfiguration errorHandler(@Nonnull ErrorHandler errorHandler) {
            return new SharedEventProcessorConfiguration(errorHandler);
        }
    }
}


