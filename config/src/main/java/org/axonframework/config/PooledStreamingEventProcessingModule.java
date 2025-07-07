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

/**
 * An expansion of the {@link EventProcessingModule}, specifically for pooled streaming event processors.
 * When constructed, either {@link #annotated(String) annotated} or {@link #declarative(String) declarative}, 
 * it provides specialized configuration for {@link PooledStreamingEventProcessor} instances.
 *
 * <h2>Declarative building</h2>
 * Pooled streaming event processors can be built using the {@link #declarative(String)} method. This allows for 
 * a more manual approach to building the event processor, where the user can provide the required components.
 * <p>
 * There are several phases of the building process of the declarative pooled streaming event processing module:
 *     <ul>
 *         <li> {@link EventSourcePhase} - Provides the {@link StreamableEventSource} for the event processor.</li>
 *         <li> {@link TokenStorePhase} - Provides the {@link TokenStore} for the event processor.</li>
 *         <li> {@link ProcessorConfigurationPhase} - Provides additional configuration for the processor.</li>
 *     </ul>
 *
 * <h2>Module hierarchy</h2>
 * This module can be registered as a child module to provide specialized event processing capabilities
 * while inheriting the general event handler registration from a parent EventProcessingModule.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public interface PooledStreamingEventProcessingModule extends EventProcessingModule {

    /**
     * Starts building a pooled streaming event processing module with the given {@code moduleName}.
     *
     * @param moduleName The name of the pooled streaming event processing module.
     * @return The {@link EventSourcePhase} phase of this builder, for a fluent API.
     */
    static EventSourcePhase declarative(@Nonnull String moduleName) {
        return new SimplePooledStreamingEventProcessingModule(moduleName);
    }

    /**
     * Creates the module for an annotated pooled streaming event processing module with the given {@code moduleName}.
     * The module will use default configuration for the pooled streaming event processor.
     *
     * @param moduleName The name of the annotated pooled streaming event processing module.
     * @return The finished module.
     */
    static PooledStreamingEventProcessingModule annotated(@Nonnull String moduleName) {
        return new SimplePooledStreamingEventProcessingModule(moduleName)
                .eventSource(config -> config.getComponent(org.axonframework.eventsourcing.eventstore.EventStore.class))
                .tokenStore(config -> config.getComponent(TokenStore.class))
                .build();
    }

    /**
     * Phase of the module's building process in which the user should define the event source for the
     * pooled streaming event processor being built.
     */
    interface EventSourcePhase {

        /**
         * Registers the given {@link ComponentBuilder} of a {@link StreamableEventSource} as the event source for the
         * pooled streaming event processor being built.
         *
         * @param eventSourceBuilder A {@link ComponentBuilder} constructing the {@link StreamableEventSource} for the
         *                          event processor.
         * @return The {@link TokenStorePhase} phase of this builder, for a fluent API.
         */
        TokenStorePhase eventSource(@Nonnull ComponentBuilder<StreamableEventSource<? extends EventMessage<?>>> eventSourceBuilder);
    }

    /**
     * Phase of the module's building process in which a {@link ComponentBuilder} for a {@link TokenStore} should
     * be provided. The {@code TokenStore} is used to store and retrieve event processing tokens.
     */
    interface TokenStorePhase {

        /**
         * Registers the given {@link ComponentBuilder} of a {@link TokenStore} as the token store for the
         * pooled streaming event processor being built.
         *
         * @param tokenStoreBuilder A {@link ComponentBuilder} constructing the {@link TokenStore} for the
         *                         event processor.
         * @return The {@link ProcessorConfigurationPhase} phase of this builder, for a fluent API.
         */
        ProcessorConfigurationPhase tokenStore(@Nonnull ComponentBuilder<TokenStore> tokenStoreBuilder);
    }

    /**
     * Phase of the module's building process in which additional configuration for the pooled streaming event processor
     * can be provided. This phase also allows building the module.
     */
    interface ProcessorConfigurationPhase {

        /**
         * Configures the pooled streaming event processor using the provided builder.
         *
         * @param processorConfigurer A consumer that configures the PooledStreamingEventProcessor.Builder.
         * @return The processor configuration phase of this module, for a fluent API.
         */
        ProcessorConfigurationPhase configure(@Nonnull Consumer<PooledStreamingEventProcessor.Builder> processorConfigurer);

        /**
         * Configures the name of the event processor.
         *
         * @param processorName The name of the event processor.
         * @return The processor configuration phase of this module, for a fluent API.
         */
        ProcessorConfigurationPhase processorName(@Nonnull String processorName);

        /**
         * Builds the pooled streaming event processing module.
         *
         * @return The built pooled streaming event processing module.
         */
        PooledStreamingEventProcessingModule build();
    }
} 