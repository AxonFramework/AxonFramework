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

package org.axonframework.eventsourcing;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

/**
 * Interface describing a handler of events that may return a {@link MessageStream.Single single stream} as a result of
 * handling the event.
 *
 * @author Mateusz Nowak
 * @author Steven van Beelen
 * @since 5.0.0
 */
@FunctionalInterface
interface IEventSourcingHandler extends EventHandler {

    /**
     * Handles the given {@code event} within the given {@code context}.
     * <p>
     * The result of handling is an {@link MessageStream.Single single stream}.
     *
     * @param event   The event to handle.
     * @param context The context to the given {@code event} is handled in.
     * @return An {@link MessageStream.Single empty stream} containing the response for the event.
     */
    @Nonnull
    MessageStream.Single<? extends Message<?>> source(@Nonnull EventMessage<?> event,
                                                      @Nonnull ProcessingContext context);

    @Nonnull
    @Override
    default MessageStream.Empty<Message<Void>> handle(@Nonnull EventMessage<?> event,
                                                      @Nonnull ProcessingContext context) {
        return source(event, context).ignoreEntries().cast();
    }
}
