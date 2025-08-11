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

package org.axonframework.modelling.event;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.configuration.MessageHandler;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.StateManager;

import jakarta.annotation.Nonnull;

/**
 * Interface describing a stateful handler of {@link EventMessage events}. Receives a {@link StateManager} as
 * parameter which can be used to load state during the execution of the event handler. Only state registered with the
 * {@link StateManager} can be loaded.
 *
 * @author Mitchell Herrijgers
 * @see StateManager
 * @see StatefulEventHandlerRegistry
 * @since 5.0.0
 */
@FunctionalInterface
public interface StatefulEventHandler extends MessageHandler {

    /**
     * Handles the given {@code event} within the given {@code context}. The {@code state} parameter provides access
     * to state that can be loaded based on type and id, through the ability to call
     * {@link StateManager#loadEntity(Class, Object, ProcessingContext)} and
     * {@link StateManager#loadManagedEntity(Class, Object, ProcessingContext)}.
     * <p>
     * The result of handling is an {@link MessageStream.Empty empty stream}.
     *
     * @param event   The event to handle.
     * @param state   The state manager to load state during the execution of the event handler.
     * @param context The context to the given {@code event} is handled in.
     * @return An {@link MessageStream.Empty empty stream} containing nothing.
     */
    @Nonnull
    MessageStream.Empty<Message<Void>> handle(@Nonnull EventMessage<?> event,
                                              @Nonnull StateManager state,
                                              @Nonnull ProcessingContext context);
}