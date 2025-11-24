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

package org.axonframework.messaging.eventhandling.replay;

import org.axonframework.messaging.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventHandler;

/**
 * Event handling component with reset capability. Extends {@link EventHandlingComponent} to add
 * reset handling support.
 * <p>
 * This interface combines event handling with reset handling, making it clear that reset is an
 * optional extension of event handling, not a separate concern.
 * <p>
 * Components implementing this interface can:
 * <ul>
 *   <li>Handle events via {@link EventHandlingComponent#handle(EventMessage, ProcessingContext)}</li>
 *   <li>Handle reset via {@link ResetHandler#handle(ResetContext, ProcessingContext)}</li>
 *   <li>Subscribe event handlers via {@link EventHandlingComponent#subscribe(QualifiedName, EventHandler)}</li>
 *   <li>Subscribe reset handlers via {@link ResetHandlerRegistry#subscribe(ResetHandler)}</li>
 * </ul>
 * <p>
 * During event replay, streaming event processors will skip components that do NOT implement
 * this interface, ensuring only reset-capable components receive replayed events.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Wrap existing event component with reset capability
 * EventHandlingComponent events = new SimpleEventHandlingComponent();
 * events.subscribe(new QualifiedName("OrderPlaced"), orderHandler);
 *
 * ResetEventHandlingComponent resetable = new SimpleResetEventHandlingComponent(events);
 * resetable.subscribe((resetContext, context) -> {
 *     repository.deleteAll();
 *     return MessageStream.empty();
 * });
 * }</pre>
 *
 * @author Mateusz Nowak
 * @see EventHandlingComponent
 * @see ResetHandler
 * @see ResetHandlerRegistry
 * @see SimpleResetEventHandlingComponent
 * @since 5.0.0
 */
public interface ResetEventHandlingComponent
        extends EventHandlingComponent, ResetHandler, ResetHandlerRegistry {

    /**
     * Indicates whether this component supports reset operations.
     * <p>
     * Default implementation returns {@code true}, as components implementing this interface
     * are assumed to support reset. Implementations can override this to conditionally support
     * reset, for example when no reset handlers have been subscribed.
     *
     * @return {@code true} if reset is supported, {@code false} otherwise
     */
    default boolean supportsReset() {
        return true;
    }
}
