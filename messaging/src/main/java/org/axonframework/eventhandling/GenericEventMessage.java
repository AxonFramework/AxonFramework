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
import org.axonframework.messaging.*;
import org.axonframework.serialization.CachingSupplier;

import java.io.Serial;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Generic implementation of the {@link EventMessage} interface.
 *
 * @param <P> The type of {@link #getPayload() payload} contained in this {@link EventMessage}.
 * @author Allard Buijze
 * @author Rene de Waele
 * @author Steven van Beelen
 * @since 2.0.0
 */
public class GenericEventMessage<P> extends MessageDecorator<P> implements EventMessage<P> {

    @Serial
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
     * Returns the given event as an EventMessage. If {@code event} already implements EventMessage, it is returned
     * as-is. If it is a Message, a new EventMessage will be created using the payload and meta data of the given
     * message. Otherwise, the given {@code event} is wrapped into a GenericEventMessage as its payload.
     *
     * @param event the event to wrap as EventMessage
     * @param <P>   The generic type of the expected payload of the resulting object
     * @return an EventMessage containing given {@code event} as payload, or {@code event} if it already implements
     * EventMessage.
     * @deprecated In favor of using the constructor, as we intend to enforce thinking about the
     * {@link QualifiedName type}.
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public static <P> EventMessage<P> asEventMessage(@Nonnull Object event) {
        if (event instanceof EventMessage) {
            return (EventMessage<P>) event;
        } else if (event instanceof Message) {
            Message<P> message = (Message<P>) event;
            return new GenericEventMessage<>(message, clock.instant());
        }
        return new GenericEventMessage<>(
                new GenericMessage<>(QualifiedName.className(event.getClass()), (P) event),
                clock.instant()
        );
    }

    /**
     * Constructs a {@link GenericEventMessage} for the given {@code type} and {@code payload}.
     * <p>
     * The {@link MetaData} defaults to an empty instance.
     *
     * @param type    The {@link QualifiedName type} for this {@link EventMessage}.
     * @param payload The payload of type {@code P} for this {@link EventMessage}.
     * @see #asEventMessage(Object)
     */
    public GenericEventMessage(@Nonnull QualifiedName type,
                               @Nonnull P payload) {
        this(type, payload, MetaData.emptyInstance());
    }

    /**
     * Constructs a {@link GenericEventMessage} for the given {@code type}, {@code payload} and {@code metaData}.
     *
     * @param type     The {@link QualifiedName type} for this {@link EventMessage}.
     * @param payload  The payload of type {@code P} for this {@link EventMessage}.
     * @param metaData The metadata for this {@link EventMessage}.
     * @see #asEventMessage(Object)
     */
    public GenericEventMessage(@Nonnull QualifiedName type,
                               @Nonnull P payload,
                               @Nonnull Map<String, ?> metaData) {
        this(new GenericMessage<>(type, payload, metaData), clock.instant());
    }

    /**
     * Constructs a {@link GenericEventMessage} for the given {@code identifier}, {@code type}, {@code payload},
     * {@code metaData}, and {@code timestamp}.
     *
     * @param identifier The identifier of this {@link EventMessage}.
     * @param type       The {@link QualifiedName type} for this {@link EventMessage}.
     * @param payload    The payload of type {@code P} for this {@link EventMessage}.
     * @param metaData   The metadata for this {@link EventMessage}.
     * @param timestamp  The {@link Instant timestamp} of this {@link EventMessage EventMessage's} creation.
     * @see #asEventMessage(Object)
     */
    public GenericEventMessage(@Nonnull String identifier,
                               @Nonnull QualifiedName type,
                               @Nonnull P payload,
                               @Nonnull Map<String, ?> metaData,
                               @Nonnull Instant timestamp) {
        this(new GenericMessage<>(identifier, type, payload, metaData), timestamp);
    }

    /**
     * Constructs a {@link GenericEventMessage} for the given {@code delegate} and {@code timestampSupplier}, intended
     * to reconstruct another {@link EventMessage}.
     * <p>
     * The timestamp of the event is supplied lazily through the given {@code timestampSupplier} to prevent unnecessary
     * deserialization of the timestamp.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate          The {@link Message} containing {@link Message#getPayload() payload},
     *                          {@link Message#type() type}, {@link Message#getIdentifier() identifier} and
     *                          {@link Message#getMetaData() metadata} for the {@link EventMessage} to reconstruct.
     * @param timestampSupplier {@link Supplier} for the {@link Instant timestamp} of the
     *                          {@link EventMessage EventMessage's} creation.
     */
    public GenericEventMessage(@Nonnull Message<P> delegate,
                               @Nonnull Supplier<Instant> timestampSupplier) {
        super(delegate);
        this.timestampSupplier = CachingSupplier.of(timestampSupplier);
    }

    /**
     * Constructs a {@link GenericEventMessage} with given {@code delegate} and {@code timestamp}.
     * <p>
     * The {@code delegate} will be used supply the {@link Message#getPayload() payload}, {@link Message#type() type},
     * {@link Message#getMetaData() metadata} and {@link Message#getIdentifier() identifier} of the resulting
     * {@code GenericEventMessage}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate  The {@link Message} containing {@link Message#getPayload() payload},
     *                  {@link Message#type() type}, {@link Message#getIdentifier() identifier} and
     *                  {@link Message#getMetaData() metadata} for the {@link EventMessage} to reconstruct.
     * @param timestamp The {@link Instant timestamp} of this {@link EventMessage GenericEventMessage's} creation.
     */
    protected GenericEventMessage(@Nonnull Message<P> delegate,
                                  @Nonnull Instant timestamp) {
        this(delegate, CachingSupplier.of(timestamp));
    }

    @Nonnull
    @Override
    public Instant getTimestamp() {
        return timestampSupplier.get();
    }

    @Override
    public GenericEventMessage<P> withMetaData(@Nonnull Map<String, ?> metaData) {
        if (getMetaData().equals(metaData)) {
            return this;
        }
        return new GenericEventMessage<>(getDelegate().withMetaData(metaData), timestampSupplier);
    }

    @Override
    public GenericEventMessage<P> andMetaData(@Nonnull Map<String, ?> metaData) {
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
