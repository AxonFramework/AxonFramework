/*
 * Copyright (c) 2010-2024. Axon Framework
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
import org.axonframework.messaging.Message;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Represents a Message wrapping an Event, which is represented by its payload. An Event is a representation of an
 * occurrence of an event (i.e. anything that happened any might be of importance to any other component) in the
 * application. It contains the data relevant for components that need to act based on that event.
 *
 * @param <T> The type of payload contained in this Message
 * @author Allard Buijze
 * @see DomainEventMessage
 * @since 2.0
 */
public interface EventMessage<T> extends Message<T> {

    /**
     * Returns the identifier of this event. The identifier is used to define the uniqueness of an event. Two events may
     * contain similar (or equal) payload and timestamp, if the EventIdentifiers are different, they both represent a
     * different occurrence of an Event. If two messages have the same identifier, they both represent the same unique
     * occurrence of an event, even though the resulting view may be different. You may not assume two messages are
     * equal (i.e. interchangeable) if their identifier is equal.
     * <p/>
     * For example, an AddressChangeEvent may occur twice for the same Event, because someone moved back to the previous
     * address. In that case, the Event payload is equal for both EventMessage instances, but the Event Identifier is
     * different for both.
     *
     * @return the identifier of this event.
     */
    @Override
    String getIdentifier();

    /**
     * Returns the timestamp of this event. The timestamp is set to the date and time the event was reported.
     *
     * @return the timestamp of this event.
     */
    Instant getTimestamp();

    /**
     * Returns a copy of this EventMessage with the given {@code metaData}. The payload,
     * {@link #getTimestamp() Timestamp} and {@link #getIdentifier() Identifier} remain unchanged.
     *
     * @param metaData The new MetaData for the Message
     * @return a copy of this message with the given MetaData
     */
    @Override
    EventMessage<T> withMetaData(@Nonnull Map<String, ?> metaData);

    /**
     * Returns a copy of this EventMessage with it MetaData merged with the given {@code metaData}. The payload,
     * {@link #getTimestamp() Timestamp} and {@link #getIdentifier() Identifier} remain unchanged.
     *
     * @param metaData The MetaData to merge with
     * @return a copy of this message with the given MetaData
     */
    @Override
    EventMessage<T> andMetaData(@Nonnull Map<String, ?> metaData);

    /**
     * Returns a copy of this EventMessage with its payload converted using given {@code conversion} function. If the
     * function returns an equal payload, this Event Message instance is returned.
     *
     * @param conversion The function to apply to the payload of this message
     * @param <C>        The type of payload returned by the conversion
     * @return a copy of this message with the payload converted
     */
    default <C> EventMessage<C> withConvertedPayload(@Nonnull Function<T, C> conversion) {
        T payload = getPayload();
        if (Objects.equals(payload, conversion.apply(payload))) {
            return (EventMessage<C>) this;
        }
        throw new UnsupportedOperationException("To be implemented");
    }
}
