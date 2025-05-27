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

package org.axonframework.eventhandling.gateway;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.MetaData;

/**
 * Utility class for the {@link EventGateway} and {@link EventAppender} implementations.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
class EventPublishingUtils {

    private EventPublishingUtils() {
        // Prevent instantiation as this is a utility class
    }

    /**
     * Converts the given {@code event} to an {@link EventMessage}. If the event is already an {@link EventMessage}, it
     * is returned as is. If the event is a {@link Message}, it is wrapped in a {@link GenericEventMessage}. Otherwise,
     * a new {@link GenericEventMessage} is created with the given event and the type resolved by the given
     * {@link MessageTypeResolver}.
     *
     * @param event               The event to convert.
     * @param messageTypeResolver The {@link MessageTypeResolver} to resolve the type of the event.
     * @param <E>                 The type of the event.
     * @return The event as an {@link EventMessage}.
     */
    @SuppressWarnings("unchecked")
    static <E> EventMessage<E> asEventMessage(@Nonnull Object event, MessageTypeResolver messageTypeResolver) {
        if (event instanceof EventMessage<?>) {
            return (EventMessage<E>) event;
        } else if (event instanceof Message<?>) {
            Message<E> message = (Message<E>) event;
            return new GenericEventMessage<>(message, () -> GenericEventMessage.clock.instant());
        }
        return new GenericEventMessage<>(
                messageTypeResolver.resolveOrThrow(event),
                (E) event,
                MetaData.emptyInstance()
        );
    }
}
