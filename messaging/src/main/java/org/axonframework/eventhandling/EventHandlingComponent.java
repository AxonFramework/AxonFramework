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

package org.axonframework.eventhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.QualifiedName;

import java.util.Set;

/**
 * Interface describing a group of {@code EventHandlers} belonging to a single component.
 * <p>
 * As such, it allows registration of {@code EventHandlers} through the {@code EventHandlerRegistry}. Besides handling
 * and registration, it specifies which {@link #supportedEvents() events} it supports.
 *
 * @author Rene de Waele
 * @author Steven van Beelen
 * @since 3.0.0
 */
public interface EventHandlingComponent extends EventHandler, EventHandlerRegistry {

    /**
     * All supported {@link EventMessage events}, referenced through a {@link QualifiedName}.
     *
     * @return All supported {@link EventMessage events}, referenced through a {@link QualifiedName}.
     */
    Set<QualifiedName> supportedEvents();

    /**
     * Checks whether the given {@code eventName} is supported by this component.
     *
     * @param eventName The name of the event to check for support.
     * @return {@code true} if the given {@code eventName} is supported, {@code false} otherwise.
     */
    default boolean supports(@Nonnull QualifiedName eventName) {
        return supportedEvents().contains(eventName);
    }

    /**
     * Returns the sequence identifier for the given {@code event}. When two events have the same sequence identifier
     * (as defined by their equals method), they will be executed sequentially.
     * <p>
     * The default implementation returns the event's identifier, which effectively means no specific sequencing is
     * applied (full concurrency). Override this method to provide custom sequencing behavior, such as handling events
     * for the same aggregate sequentially. Or use a {@link SequenceOverridingEventHandlingComponent} if you cannot
     * inherit from a certain {@code EventHandlingComponent} implementation.
     *
     * @param event The event for which to get the sequencing identifier.
     * @return A sequence identifier for the given event.
     */
    @Nonnull
    default Object sequenceIdentifierFor(@Nonnull EventMessage<?> event) {
        return event.getIdentifier();
    }
}
