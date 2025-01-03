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
import org.axonframework.messaging.QualifiedNameUtils;

public abstract class EventTestUtils {

    private EventTestUtils() {
        // Utility class
    }

    /**
     * Returns the given event as an EventMessage. If {@code event} already implements EventMessage, it is returned
     * as-is. If it is a Message, a new EventMessage will be created using the payload and meta data of the given
     * message. Otherwise, the given {@code event} is wrapped into a GenericEventMessage as its payload.
     *
     * @param event the event to wrap as EventMessage
     * @param <P>   The generic type of the expected payload of the resulting object
     * @return an EventMessage containing given {@code event} as payload, or {@code event} if it already implements
     * EventMessage.
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
                new GenericMessage<>(QualifiedNameUtils.fromClassName(event.getClass()), (P) event),
                GenericEventMessage.clock.instant()
        );
    }
}
