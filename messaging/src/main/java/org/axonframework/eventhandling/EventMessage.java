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
 * A {@link Message} wrapping an event, which is represented by its {@link #getPayload() payload}.
 * <p>
 * An event is a representation of an occurrence of an event (i.e. anything that happened any might be of importance to
 * any other component) in the application. It contains the data relevant for components that need to act based on that
 * event.
 *
 * @param <P> The type of {@link #getPayload() payload} contained in this {@link EventMessage}.
 * @author Allard Buijze
 * @see DomainEventMessage
 * @since 2.0.0
 */
public interface EventMessage<P> extends Message<P> {

    /**
     * Returns the identifier of this {@link EventMessage event}.
     * <p>
     * The identifier is used to define the uniqueness of an event. Two events may contain similar (or equal)
     * {@link #getPayload() payloads} and {@link #getTimestamp() timestamp}, if the event identifiers are different,
     * they both represent a different occurrence of an Event.
     * <p>
     * If two messages have the same identifier, they both represent the same unique occurrence of an event, even though
     * the resulting view may be different. You may not assume two messages are equal (i.e. interchangeable) if their
     * identifier is equal.
     * <p/>
     * For example, an {@code AddressChangeEvent} may occur twice for the same event, because someone moved back to the
     * previous address. In that case, the event payload is equal for both {@code EventMessage} instances, but the event
     * identifier is different for both.
     *
     * @return The identifier of this {@link EventMessage event}.
     */
    @Override
    String getIdentifier();

    /**
     * Returns the timestamp of this {@link EventMessage event}.
     * <p>
     * The timestamp is set to the date and time the event was reported.
     *
     * @return The timestamp of this {@link EventMessage event}.
     */
    Instant getTimestamp();

    @Override
    EventMessage<P> withMetaData(@Nonnull Map<String, ?> metaData);

    @Override
    EventMessage<P> andMetaData(@Nonnull Map<String, ?> metaData);

    /**
     * Returns a copy of this EventMessage with its payload converted using given {@code conversion} function. If the
     * function returns an equal payload, this Event Message instance is returned.
     *
     * @param conversion The function to apply to the payload of this message
     * @param <C>        The type of payload returned by the conversion
     * @return a copy of this message with the payload converted
     */
    default <C> EventMessage<C> withConvertedPayload(@Nonnull Function<P, C> conversion) {
        P payload = getPayload();
        if (Objects.equals(payload, conversion.apply(payload))) {
            //noinspection unchecked
            return (EventMessage<C>) this;
        }
        throw new UnsupportedOperationException("To be implemented");
    }
}
