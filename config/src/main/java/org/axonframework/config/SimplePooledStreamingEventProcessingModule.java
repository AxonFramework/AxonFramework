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

package org.axonframework.config;

import jakarta.annotation.Nonnull;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventstreaming.StreamableEventSource;

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * Simple implementation of the {@link PooledStreamingEventProcessingModule} that provides a fluent builder
 * for configuring pooled streaming event processors.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class SimplePooledStreamingEventProcessingModule extends SimpleEventProcessingModule
        implements PooledStreamingEventProcessingModule,
                   PooledStreamingEventProcessingModule.EventSourcePhase,
                   PooledStreamingEventProcessingModule.TokenStorePhase,
                   PooledStreamingEventProcessingModule.ProcessorConfigurationPhase {

    private ComponentBuilder<StreamableEventSource<? extends EventMessage<?>>> eventSourceBuilder;
    private ComponentBuilder<TokenStore> tokenStoreBuilder;
    private Consumer<PooledStreamingEventProcessor.Builder> processorConfigurer;
    private String processorName;

    /**
     * Creates a new {@code SimplePooledStreamingEventProcessingModule} with the given {@code moduleName}.
     *
     * @param moduleName The name of the pooled streaming event processing module.
     */
    SimplePooledStreamingEventProcessingModule(@Nonnull String moduleName) {
        super(requireNonNull(moduleName, "Module name cannot be null."));
        this.processorName = moduleName + "Processor";
    }

    @Override
    public TokenStorePhase eventSource(@Nonnull ComponentBuilder<StreamableEventSource<? extends EventMessage<?>>> eventSourceBuilder) {
        this.eventSourceBuilder = requireNonNull(eventSourceBuilder, "Event source builder cannot be null.");
        return this;
    }

    @Override
    public ProcessorConfigurationPhase tokenStore(@Nonnull ComponentBuilder<TokenStore> tokenStoreBuilder) {
        this.tokenStoreBuilder = requireNonNull(tokenStoreBuilder, "Token store builder cannot be null.");
        return this;
    }

    @Override
    public ProcessorConfigurationPhase configure(@Nonnull Consumer<PooledStreamingEventProcessor.Builder> processorConfigurer) {
        this.processorConfigurer = requireNonNull(processorConfigurer, "Processor configurer cannot be null.");
        return this;
    }

    @Override
    public ProcessorConfigurationPhase processorName(@Nonnull String processorName) {
        this.processorName = requireNonNull(processorName, "Processor name cannot be null.");
        return this;
    }

    @Override
    public PooledStreamingEventProcessingModule build() {
        registerPooledStreamingEventProcessor();
        // Call parent build to register event handlers and sagas
        super.build();
        return this;
    }

    private void registerPooledStreamingEventProcessor() {
        componentRegistry(cr -> {
            ComponentBuilder<PooledStreamingEventProcessor> processorBuilder = config -> {
                PooledStreamingEventProcessor.Builder builder = PooledStreamingEventProcessor.builder()
                        .name(processorName);

                // Set event source
                if (eventSourceBuilder != null) {
                    builder.eventSource(eventSourceBuilder.build(config));
                } else {
                    throw new IllegalStateException("Event source must be configured for pooled streaming event processor");
                }

                // Set token store
                if (tokenStoreBuilder != null) {
                    builder.tokenStore(tokenStoreBuilder.build(config));
                } else {
                    // Default to registered token store
                    builder.tokenStore(config.getComponent(TokenStore.class));
                }

                // Apply custom configuration
                if (processorConfigurer != null) {
                    processorConfigurer.accept(builder);
                }

                return builder.build();
            };

            cr.registerComponent(PooledStreamingEventProcessor.class, processorName, processorBuilder);
        });
    }
} 