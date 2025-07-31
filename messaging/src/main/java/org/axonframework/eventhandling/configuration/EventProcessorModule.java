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
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.Module;
import org.axonframework.configuration.ModuleBuilder;
import org.axonframework.eventhandling.EventProcessorConfiguration;
import org.axonframework.eventhandling.SubscribingEventProcessorConfiguration;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessorConfiguration;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessorModule;
import org.axonframework.eventhandling.subscribing.SubscribingEventProcessorModule;

import java.util.function.UnaryOperator;

/**
 * TBD
 * <p>
 * Note that users do not have to invoke {@link #build()} themselves when using this interface, as the
 * {@link org.axonframework.configuration.ApplicationConfigurer} takes care of that.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public interface EventProcessorModule extends Module, ModuleBuilder<EventProcessorModule> {

    /**
     * Start building a SubscribingEventProcessor module with the given processor processorName. The subscribing event
     * processor will register with a message source to receive events.
     *
     * @param processorName The processor processorName, must not be null or empty.
     * @return A builder phase to configure a subscribing event processor.
     */
    static CustomizationPhase<SubscribingEventProcessorModule, SubscribingEventProcessorConfiguration> subscribing(String processorName) {
        return new SubscribingEventProcessorModule(processorName);
    }

    /**
     * Start building a PooledStreamingEventProcessor module with the given processor name. The pooled streaming
     * processor manages multiple segments to process events from a stream.
     *
     * @param processorName The processor name, must not be null or empty.
     * @return A builder phase to configure a pooled streaming event processor.
     */
    static CustomizationPhase<PooledStreamingEventProcessorModule, PooledStreamingEventProcessorConfiguration> pooledStreaming(
            String processorName) {
        return new PooledStreamingEventProcessorModule(processorName);
    }

    interface CustomizationPhase<P extends EventProcessorModule, C extends EventProcessorConfiguration> {

        P configure(@Nonnull ComponentBuilder<C> configurationBuilder);

        P customize(@Nonnull ComponentBuilder<UnaryOperator<C>> customizationBuilder);
    }

}
