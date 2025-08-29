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
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.sequencing.SequencingPolicy;
import org.axonframework.messaging.QualifiedName;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Builder interface for configuring {@link EventHandlingComponent} instances using a fluent API.
 * <p>
 * Provides type-safe builder phases to ensure proper configuration order: optional sequencing policy,
 * required event handler registration, optional additional handlers and decorators, and final building.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public interface EventHandlingComponentBuilder {

    /**
     * Initial builder phase allowing optional sequencing policy configuration.
     */
    interface SequencingPolicyPhase extends RequiredEventHandlerPhase {

        /**
         * Configures a sequencing policy for event processing order.
         *
         * @param sequencingPolicy The {@link SequencingPolicy} for event sequencing.
         * @return The next builder phase for event handler registration.
         */
        @Nonnull
        RequiredEventHandlerPhase sequencingPolicy(@Nonnull SequencingPolicy sequencingPolicy);

        /**
         * Configures event sequencing using a simple identifier function.
         *
         * @param sequencingPolicy Function to extract sequence identifiers from events.
         * @return The next builder phase for event handler registration.
         */
        @Nonnull
        default RequiredEventHandlerPhase sequenceIdentifier(
                @Nonnull Function<EventMessage, Object> sequencingPolicy
        ) {
            return sequencingPolicy((event, context) -> Optional.of(sequencingPolicy.apply(event)));
        }
    }

    /**
     * Builder phase requiring at least one event handler registration.
     */
    interface RequiredEventHandlerPhase {

        /**
         * Registers an event handler for a specific event type.
         *
         * @param name         The event type name to handle.
         * @param eventHandler The handler for the event type.
         * @return The next builder phase for additional handlers or completion.
         */
        @Nonnull
        AdditionalEventHandlerPhase handles(@Nonnull QualifiedName name, @Nonnull EventHandler eventHandler);

        /**
         * Registers an event handler for multiple event types.
         *
         * @param names        The event type names to handle.
         * @param eventHandler The handler for the event types.
         * @return The next builder phase for additional handlers or completion.
         */
        @Nonnull
        AdditionalEventHandlerPhase handles(@Nonnull Set<QualifiedName> names, @Nonnull EventHandler eventHandler);
    }

    /**
     * Builder phase allowing additional event handler registration.
     */
    interface AdditionalEventHandlerPhase extends CompletePhase {

        /**
         * Registers an additional event handler for a specific event type.
         *
         * @param name         The event type name to handle.
         * @param eventHandler The handler for the event type.
         * @return This builder phase for further handler registration.
         */
        @Nonnull
        AdditionalEventHandlerPhase handles(@Nonnull QualifiedName name, @Nonnull EventHandler eventHandler);

        /**
         * Registers an additional event handler for multiple event types.
         *
         * @param names        The event type names to handle.
         * @param eventHandler The handler for the event types.
         * @return This builder phase for further handler registration.
         */
        @Nonnull
        AdditionalEventHandlerPhase handles(@Nonnull Set<QualifiedName> names, @Nonnull EventHandler eventHandler);
    }

    /**
     * Final builder phase allowing decoration and component building.
     */
    interface CompletePhase {

        /**
         * Applies a decorator to enhance the component with additional functionality.
         *
         * @param decorator Function to decorate the component.
         * @return This builder phase for further decoration or building.
         */
        @Nonnull
        CompletePhase decorated(@Nonnull UnaryOperator<EventHandlingComponent> decorator);

        /**
         * Builds and returns the configured {@link EventHandlingComponent}.
         *
         * @return The configured event handling component.
         */
        @Nonnull
        EventHandlingComponent build();
    }
}
