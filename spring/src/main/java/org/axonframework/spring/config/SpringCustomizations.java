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

package org.axonframework.spring.config;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.common.annotation.Internal;
import org.axonframework.configuration.Configuration;
import org.axonframework.eventhandling.processors.streaming.pooled.PooledStreamingEventProcessorConfiguration;
import org.axonframework.eventhandling.processors.streaming.pooled.PooledStreamingEventProcessorModule;
import org.axonframework.eventhandling.processors.streaming.token.store.TokenStore;
import org.axonframework.eventhandling.processors.streaming.token.store.inmemory.InMemoryTokenStore;
import org.axonframework.eventhandling.processors.subscribing.SubscribingEventProcessorConfiguration;
import org.axonframework.eventhandling.processors.subscribing.SubscribingEventProcessorModule;
import org.axonframework.eventstreaming.StreamableEventSource;
import org.axonframework.messaging.SubscribableMessageSource;

import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Holder for Spring customizations based on settings.
 *
 * @author Simon Zambrovski
 * @since 5.0.0
 */
interface SpringCustomizations {

    /**
     * Creates customizations for a pooled streaming event processing module.
     *
     * @param name     Module name.
     * @param settings Settings of the module.
     * @return Customizations for the module.
     */
    static PooledStreamingEventProcessorModule.Customization pooledStreamingCustomizations(
            String name,
            EventProcessorSettings.PooledEventProcessorSettings settings
    ) {
        return new SpringPooledStreamingEventProcessingModuleCustomization(name, settings);
    }

    /**
     * Creates customizations for a subscribing event processing module.
     *
     * @param settings Settings of the module.
     * @return Customizations for the module.
     */
    static SubscribingEventProcessorModule.Customization subscribingCustomizations(
            EventProcessorSettings.SubscribingEventProcessorSettings settings) {
        return new SpringSubscribingEventProcessingModuleCustomization(settings);
    }

    /**
     * Customization executed based on the {@link EventProcessorSettings.SubscribingEventProcessorSettings}.
     */
    class SpringSubscribingEventProcessingModuleCustomization implements SubscribingEventProcessorModule.Customization {

        private final EventProcessorSettings.SubscribingEventProcessorSettings settings;

        SpringSubscribingEventProcessingModuleCustomization(
                EventProcessorSettings.SubscribingEventProcessorSettings settings) {
            this.settings = settings;
        }

        @Override
        public SubscribingEventProcessorConfiguration apply(Configuration configuration,
                                                            SubscribingEventProcessorConfiguration subscribingEventProcessorConfiguration) {
            //noinspection unchecked
            return subscribingEventProcessorConfiguration
                    .messageSource(
                            getComponent(configuration,
                                         SubscribableMessageSource.class,
                                         settings.source(),
                                         null
                            )
                    );
        }
    }


    /**
     * Customization executed based on the {@link EventProcessorSettings.PooledEventProcessorSettings}.
     */
    class SpringPooledStreamingEventProcessingModuleCustomization
            implements PooledStreamingEventProcessorModule.Customization {

        private final EventProcessorSettings.PooledEventProcessorSettings settings;
        private final String name;

        SpringPooledStreamingEventProcessingModuleCustomization(
                String name,
                EventProcessorSettings.PooledEventProcessorSettings settings
        ) {
            this.settings = settings;
            this.name = name;
        }

        @Override
        public PooledStreamingEventProcessorConfiguration apply(
                Configuration configuration,
                PooledStreamingEventProcessorConfiguration eventProcessorConfiguration) {
            String executorName = "WorkPackage[" + name + "]";
            var scheduledExecutorService = Executors.newScheduledThreadPool(
                    settings.threadCount(),
                    new AxonThreadFactory(executorName)
            );
            //noinspection unchecked
            return eventProcessorConfiguration
                    .workerExecutor(scheduledExecutorService)
                    .tokenClaimInterval(settings.tokenClaimIntervalInMillis())
                    .batchSize(settings.batchSize())
                    .initialSegmentCount(settings.initialSegmentCount())
                    .eventSource(
                            getComponent(configuration,
                                         StreamableEventSource.class,
                                         settings.source(),
                                         null)
                    )
                    .tokenStore(
                            getComponent(configuration,
                                         TokenStore.class,
                                         null,
                                         InMemoryTokenStore::new
                            )
                    );
        }
    }

    /**
     * Retrieves component from configuration.
     *
     * @param configuration The configuration holding the component registry.
     * @param type          The type of the component.
     * @param name          An optional component name, if omitted only type is used.
     * @param supplier      An optional supplier, if omitted replaced by the null supplier.
     * @param <T>           type of the component.
     * @return a component of given type and name, if found or supplied by the supplier.
     */
    static <T> T getComponent(@Nonnull Configuration configuration, @Nonnull Class<T> type,
                              @Nullable String name,
                              @Nullable Supplier<T> supplier) {
        Supplier<T> safeSupplier = (supplier != null) ? supplier : () -> null;
        if (name != null) {
            return configuration.getComponent(type, name, safeSupplier);
        } else {
            return configuration.getComponent(type, safeSupplier);
        }
    }
}