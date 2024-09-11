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

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDecorator;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.CachingSupplier;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

/**
 * Generic implementation of the EventMessage interface.
 *
 * @param <T> The type of payload contained in this Message
 */
public class GenericEventMessage<T> extends MessageDecorator<T> implements EventMessage<T> {
    private static final long serialVersionUID = -8296350547944518544L;
    private final Supplier<Instant> timestampSupplier;

    /**
     * {@link Clock} instance used to set the time on new events. To fix the time while testing set this value to a
     * constant value.
     *
     * @deprecated #3083 - Configure application wide Clock
     */
    @Deprecated // TODO #3083 - Configure application wide Clock
    public static Clock clock = Clock.systemUTC();

    /**
     * Returns the given event as an EventMessage. If {@code event} already implements EventMessage, it is
     * returned as-is. If it is a Message, a new EventMessage will be created using the payload and meta data of the
     * given message. Otherwise, the given {@code event} is wrapped into a GenericEventMessage as its payload.
     *
     * @param event the event to wrap as EventMessage
     * @param <T>   The generic type of the expected payload of the resulting object
     * @return an EventMessage containing given {@code event} as payload, or {@code event} if it already implements
     * EventMessage.
     */
    @SuppressWarnings("unchecked")
    public static <T> EventMessage<T> asEventMessage(@Nonnull Object event) {
        if (EventMessage.class.isInstance(event)) {
            return (EventMessage<T>) event;
        } else if (event instanceof Message) {
            Message<T> message = (Message<T>) event;
            return new GenericEventMessage<>(message, clock.instant());
        }
        return new GenericEventMessage<>(new GenericMessage<>((T) event), clock.instant());
    }

    /**
     * Creates a GenericEventMessage with given {@code payload}, and an empty MetaData.
     *
     * @param payload The payload for the message
     * @see #asEventMessage(Object)
     */
    public GenericEventMessage(T payload) {
        this(payload, MetaData.emptyInstance());
    }

    /**
     * Creates a GenericEventMessage with given {@code payload} and given {@code metaData}.
     *
     * @param payload  The payload of the EventMessage
     * @param metaData The MetaData for the EventMessage
     * @see #asEventMessage(Object)
     */
    public GenericEventMessage(T payload, @Nonnull Map<String, ?> metaData) {
        this(new GenericMessage<>(payload, metaData), clock.instant());
    }

    /**
     * Constructor to reconstruct an EventMessage using existing data.
     *
     * @param identifier The identifier of the Message
     * @param timestamp  The timestamp of the Message creation
     * @param payload    The payload of the message
     * @param metaData   The metadata of the message
     */
    public GenericEventMessage(@Nonnull String identifier, T payload, @Nonnull Map<String, ?> metaData,
                               @Nonnull Instant timestamp) {
        this(new GenericMessage<>(identifier, payload, metaData), timestamp);
    }

    /**
     * Constructor to reconstruct an EventMessage using existing data. The timestamp of the event is supplied lazily to
     * prevent unnecessary deserialization of the timestamp.
     *
     * @param delegate          The message containing payload, identifier and metadata
     * @param timestampSupplier Supplier for the timestamp of the Message creation
     */
    public GenericEventMessage(@Nonnull Message<T> delegate, @Nonnull Supplier<Instant> timestampSupplier) {
        super(delegate);
        this.timestampSupplier = CachingSupplier.of(timestampSupplier);
    }

    /**
     * Initializes a {@link GenericEventMessage} with given message as delegate and given {@code timestamp}. The given
     * message will be used supply the payload, metadata and identifier of the resulting event message.
     *
     * @param delegate  the message that will be used as delegate
     * @param timestamp the timestamp of the resulting event message
     */
    protected GenericEventMessage(@Nonnull Message<T> delegate, @Nonnull Instant timestamp) {
        this(delegate, CachingSupplier.of(timestamp));
    }

    @Nonnull
    @Override
    public Instant getTimestamp() {
        return timestampSupplier.get();
    }

    @Override
    public GenericEventMessage<T> withMetaData(@Nonnull Map<String, ?> metaData) {
        if (getMetaData().equals(metaData)) {
            return this;
        }
        return new GenericEventMessage<>(getDelegate().withMetaData(metaData), timestampSupplier);
    }

    @Override
    public GenericEventMessage<T> andMetaData(@Nonnull Map<String, ?> metaData) {
        //noinspection ConstantConditions
        if (metaData == null || metaData.isEmpty() || getMetaData().equals(metaData)) {
            return this;
        }
        return new GenericEventMessage<>(getDelegate().andMetaData(metaData), timestampSupplier);
    }

    @Override
    protected void describeTo(StringBuilder stringBuilder) {
        super.describeTo(stringBuilder);
        stringBuilder.append(", timestamp='")
                .append(getTimestamp());
    }

    @Override
    protected String describeType() {
        return "GenericEventMessage";
    }
}
