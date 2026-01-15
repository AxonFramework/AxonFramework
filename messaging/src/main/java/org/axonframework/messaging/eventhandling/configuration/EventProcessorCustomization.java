/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling.configuration;


import jakarta.annotation.Nonnull;
import org.axonframework.common.configuration.Configuration;

import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Allows customizing the {@link EventProcessorConfiguration}.
 * <p>
 * The interface provides composition capabilities through {@link #andThen(EventProcessorCustomization)} to allow chaining
 * multiple customizations in a specific order.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@FunctionalInterface
public interface EventProcessorCustomization extends
        BiFunction<Configuration, EventProcessorConfiguration, EventProcessorConfiguration> {

    /**
     * Creates a no-operation customization that returns the processor configuration unchanged.
     *
     * @return A customization that applies no changes to the processor configuration.
     */
    static EventProcessorCustomization noOp() {
        return (axonConfig, processorConfig) -> processorConfig;
    }

    /**
     * Returns a composed customization that applies this customization first, then applies the other customization.
     * <p>
     * This allows for chaining multiple customizations together, with each subsequent customization receiving
     * the result of the previous one.
     *
     * @param other The customization to apply after this one.
     * @return A composed customization that applies both customizations in sequence.
     */
    default EventProcessorCustomization andThen(@Nonnull EventProcessorCustomization other) {
        Objects.requireNonNull(other, "other may not be null");
        return (axonConfig, processorConfig) -> other.apply(axonConfig, this.apply(axonConfig, processorConfig));
    }
}
