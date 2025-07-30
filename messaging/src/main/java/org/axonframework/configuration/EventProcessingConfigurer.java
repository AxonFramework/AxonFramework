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
import org.axonframework.eventhandling.SubscribingEventProcessorConfiguration;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessorConfiguration;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.eventstreaming.StreamableEventSource;
import org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
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

    private static final EventProcessorDefaults INITIAL_EVENT_PROCESSOR_DEFAULTS = new EventProcessorDefaults(
            new EventProcessorConfiguration(), // validate if I need to pass it to others!?
            new SubscribingEventProcessorConfiguration()
                    .unitOfWorkFactory(new SimpleUnitOfWorkFactory()),
            new PooledStreamingEventProcessorConfiguration()
                    .unitOfWorkFactory(new SimpleUnitOfWorkFactory())
                    .tokenStore(new InMemoryTokenStore())
    );

    private final ApplicationConfigurer parent;

    private ComponentBuilder<EventProcessorDefaults> eventProcessorDefaultsBuilder;
    private final List<ModuleBuilder<EventProcessorModule>> moduleBuilders = new ArrayList<>();

    private EventProcessingConfigurer(ApplicationConfigurer parent) {
        this.parent = parent;
    }

    public static EventProcessingConfigurer enhance(@Nonnull ApplicationConfigurer applicationConfigurer) {
        return new EventProcessingConfigurer(applicationConfigurer)
                .componentRegistry(cr -> cr
                                           .registerEnhancer(new EventProcessingDefaultsEnhancer())
                                   // todo: do not register tokenStore/unitOfWork or register and use them?
                );
    }

    public EventProcessingConfigurer defaults(
            @Nonnull BiFunction<Configuration, EventProcessorDefaults, EventProcessorDefaults> configureDefaults) {
        this.eventProcessorDefaultsBuilder = config -> {
            var defaults = INITIAL_EVENT_PROCESSOR_DEFAULTS;
            config.getOptionalComponent(TokenStore.class)
                  .ifPresent(tokenStore ->
                                     defaults.pooledStreaming(p -> p.tokenStore(tokenStore))
                  );
            config.getOptionalComponent(StreamableEventSource.class)
                  .ifPresent(eventSource ->
                                     defaults.pooledStreaming(p -> p.eventSource(eventSource))
                  );
            return configureDefaults.apply(config, defaults);
        };
        return this;
    }

    public EventProcessingConfigurer defaults(@Nonnull UnaryOperator<EventProcessorDefaults> configureDefaults) {
        this.eventProcessorDefaultsBuilder = config -> {
            var defaults = INITIAL_EVENT_PROCESSOR_DEFAULTS;
            config.getOptionalComponent(TokenStore.class)
                  .ifPresent(tokenStore ->
                                     defaults.pooledStreaming(p -> p.tokenStore(tokenStore))
                  );
            config.getOptionalComponent(StreamableEventSource.class)
                  .ifPresent(eventSource ->
                                     defaults.pooledStreaming(p -> p.eventSource(eventSource))
                  );
            return configureDefaults.apply(defaults);
        };
        return this;
    }

    public EventProcessingConfigurer registerEventProcessorModule(
            ModuleBuilder<EventProcessorModule> moduleBuilder
    ) {
        moduleBuilders.add(moduleBuilder);
        return this;
    }


    @Override
    public EventProcessingConfigurer componentRegistry(@Nonnull Consumer<ComponentRegistry> componentRegistrar) {
        parent.componentRegistry(componentRegistrar);
        return this;
    }

    @Override
    public EventProcessingConfigurer lifecycleRegistry(@Nonnull Consumer<LifecycleRegistry> lifecycleRegistrar) {
        parent.lifecycleRegistry(lifecycleRegistrar);
        return this;
    }

    @Override
    public AxonConfiguration build() {
        parent.componentRegistry(
                cr -> cr.registerComponent(
                        EventProcessorDefaults.class,
                        config -> {
                            var defaults = eventProcessorDefaultsBuilder.build(config);
                            // todo: shared should be inside and changed accordingly!
                            cr.registerComponent(PooledStreamingEventProcessorConfiguration.class,
                                                 c -> defaults.pooledStreaming);
                            cr.registerComponent(SubscribingEventProcessorConfiguration.class,
                                                 c -> defaults.subscribing);
                            return defaults;
                        }
                )
        );
        moduleBuilders.forEach(moduleBuilder ->
                                       parent.componentRegistry(cr -> cr.registerModule(
                                               moduleBuilder.build()
                                       ))
        );
        return parent.build(); // todo: weird?
    }

    public static final class EventProcessorDefaults {

        private final EventProcessorConfiguration shared;
        private final SubscribingEventProcessorConfiguration subscribing;
        private final PooledStreamingEventProcessorConfiguration pooledStreaming;

        public EventProcessorDefaults(
                EventProcessorConfiguration shared,
                SubscribingEventProcessorConfiguration subscribing,
                PooledStreamingEventProcessorConfiguration pooledStreaming
        ) {
            this.shared = shared;
            this.subscribing = subscribing;
            this.pooledStreaming = pooledStreaming;
        }

        public EventProcessorDefaults shared(@Nonnull UnaryOperator<EventProcessorConfiguration> customize) {
            return new EventProcessorDefaults(
                    customize.apply(shared),
                    subscribing,
                    pooledStreaming
            );
        }

        public EventProcessorDefaults subscribing(
                @Nonnull UnaryOperator<SubscribingEventProcessorConfiguration> customize) {
            return new EventProcessorDefaults(
                    shared,
                    customize.apply(subscribing),
                    pooledStreaming
            );
        }

        public EventProcessorDefaults pooledStreaming(
                @Nonnull UnaryOperator<PooledStreamingEventProcessorConfiguration> customize) {
            return new EventProcessorDefaults(
                    shared,
                    subscribing,
                    customize.apply(pooledStreaming)
            );
        }
    }
}


