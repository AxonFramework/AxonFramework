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
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;

/**
 * Test utilities when dealing with events.
 *
 * @author Mateusz Nowak
 * @author Steven van Beelen
 */
public abstract class EventTestUtils {

    private EventTestUtils() {
        // Utility class
    }

    /**
     * Constructs an {@link EventMessage} with the given {@code seq} as the {@link EventMessage#getPayload() payload}.
     *
     * @param seq The payload for the message to construct.
     * @return An {@link EventMessage} with the given {@code seq} as the {@link EventMessage#getPayload() payload}.
     */
    public static EventMessage<?> eventMessage(int seq) {
        return EventTestUtils.asEventMessage("Event[" + seq + "]");
    }

    /**
     * Returns the given {@code event} wrapped in an {@link EventMessage}.
     * <p>
     * If {@code event} already implements {@code EventMessage}, it is returned as-is. If it is a {@link Message}, a new
     * {@code EventMessage} will be created using the payload and metadata of the given message. Otherwise, the given
     * {@code event} is wrapped into a {@link GenericEventMessage} as its payload.
     *
     * @param event The event to wrap as {@link EventMessage}.
     * @param <P>   The generic type of the expected payload of the resulting object.
     * @return An {@link EventMessage} containing given {@code event} as payload, or {@code event} if it already
     * implements {@code EventMessage}.
     */
    @SuppressWarnings("unchecked")
    public static <P> EventMessage<P> asEventMessage(@Nonnull Object event) {
        if (event instanceof EventMessage) {
            return (EventMessage<P>) event;
        } else if (event instanceof Message) {
            Message<P> message = (Message<P>) event;
            return new GenericEventMessage<>(message, GenericEventMessage.clock.instant());
        }
        return new GenericEventMessage<>(
                new GenericMessage<>(new MessageType(event.getClass()), (P) event),
                GenericEventMessage.clock.instant()
        );
    }
}
