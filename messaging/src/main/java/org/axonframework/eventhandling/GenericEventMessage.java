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
import jakarta.annotation.Nullable;
import org.axonframework.common.ObjectUtils;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDecorator;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.CachingSupplier;
import org.axonframework.serialization.Converter;

import java.lang.reflect.Type;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Generic implementation of the {@link EventMessage} interface.
 *
 * @author Allard Buijze
 * @author Rene de Waele
 * @author Steven van Beelen
 * @since 2.0.0
 */
public class GenericEventMessage extends MessageDecorator implements EventMessage {

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
     * Constructs a {@code GenericEventMessage} for the given {@code type} and {@code payload}.
     * <p>
     * The {@link MetaData} defaults to an empty instance.
     *
     * @param type    The {@link MessageType type} for this {@link EventMessage}.
     * @param payload The payload for this {@link EventMessage}.
     */
    public GenericEventMessage(@Nonnull MessageType type,
                               @Nullable Object payload) {
        this(type, payload, MetaData.emptyInstance());
    }

    /**
     * Constructs a {@code GenericEventMessage} for the given {@code type}, {@code payload} and {@code metaData}.
     *
     * @param type     The {@link MessageType type} for this {@link EventMessage}.
     * @param payload  The payload for this {@link EventMessage}.
     * @param metaData The metadata for this {@link EventMessage}.
     */
    public GenericEventMessage(@Nonnull MessageType type,
                               @Nullable Object payload,
                               @Nonnull Map<String, String> metaData) {
        this(new GenericMessage(type, payload, metaData), clock.instant());
    }

    /**
     * Constructs a {@code GenericEventMessage} for the given {@code identifier}, {@code type}, {@code payload},
     * {@code metaData}, and {@code timestamp}.
     *
     * @param identifier The identifier of this {@link EventMessage}.
     * @param type       The {@link MessageType type} for this {@link EventMessage}.
     * @param payload    The payload for this {@link EventMessage}.
     * @param metaData   The metadata for this {@link EventMessage}.
     * @param timestamp  The {@link Instant timestamp} of this {@link EventMessage EventMessage's} creation.
     */
    public GenericEventMessage(@Nonnull String identifier,
                               @Nonnull MessageType type,
                               @Nullable Object payload,
                               @Nonnull Map<String, String> metaData,
                               @Nonnull Instant timestamp) {
        this(new GenericMessage(identifier, type, payload, metaData), timestamp);
    }

    /**
     * Constructs a {@code GenericEventMessage} for the given {@code delegate} and {@code timestampSupplier}, intended
     * to reconstruct another {@link EventMessage}.
     * <p>
     * The timestamp of the event is supplied lazily through the given {@code timestampSupplier} to prevent unnecessary
     * deserialization of the timestamp.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate          The {@link Message} containing {@link Message#payload() payload},
     *                          {@link Message#type() type}, {@link Message#identifier() identifier} and
     *                          {@link Message#metaData() metadata} for the {@link EventMessage} to reconstruct.
     * @param timestampSupplier {@link Supplier} for the {@link Instant timestamp} of the
     *                          {@link EventMessage EventMessage's} creation.
     */
    public GenericEventMessage(@Nonnull Message delegate,
                               @Nonnull Supplier<Instant> timestampSupplier) {
        super(delegate);
        this.timestampSupplier = CachingSupplier.of(timestampSupplier);
    }

    /**
     * Constructs a {@code GenericEventMessage} with given {@code delegate} and {@code timestamp}.
     * <p>
     * The {@code delegate} will be used supply the {@link Message#payload() payload}, {@link Message#type() type},
     * {@link Message#metaData() metadata} and {@link Message#identifier() identifier} of the resulting
     * {@code GenericEventMessage}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate  The {@link Message} containing {@link Message#payload() payload}, {@link Message#type() type},
     *                  {@link Message#identifier() identifier} and {@link Message#metaData() metadata} for the
     *                  {@link EventMessage} to reconstruct.
     * @param timestamp The {@link Instant timestamp} of this {@link EventMessage GenericEventMessage's} creation.
     */
    protected GenericEventMessage(@Nonnull Message delegate,
                                  @Nonnull Instant timestamp) {
        this(delegate, CachingSupplier.of(timestamp));
    }

    @Override
    @Nonnull
    public Instant timestamp() {
        return timestampSupplier.get();
    }

    @Override
    @Nonnull
    public EventMessage withMetaData(@Nonnull Map<String, String> metaData) {
        if (metaData().equals(metaData)) {
            return this;
        }
        return new GenericEventMessage(delegate().withMetaData(metaData), timestampSupplier);
    }

    @Override
    @Nonnull
    public EventMessage andMetaData(@Nonnull Map<String, String> metaData) {
        //noinspection ConstantConditions
        if (metaData == null || metaData.isEmpty() || metaData().equals(metaData)) {
            return this;
        }
        return new GenericEventMessage(delegate().andMetaData(metaData), timestampSupplier);
    }

    @Override
    @Nonnull
    public EventMessage withConvertedPayload(@Nonnull Type type, @Nonnull Converter converter) {
        Object convertedPayload = payloadAs(type, converter);
        if (ObjectUtils.nullSafeTypeOf(convertedPayload).isAssignableFrom(payloadType())) {
            return this;
        }
        Message delegate = delegate();
        Message converted = new GenericMessage(delegate.identifier(),
                                               delegate.type(),
                                               convertedPayload,
                                               delegate.metaData());
        return new GenericEventMessage(converted, timestamp());
    }

    @Override
    protected void describeTo(StringBuilder stringBuilder) {
        super.describeTo(stringBuilder);
        stringBuilder.append(", timestamp='")
                     .append(timestamp());
    }

    @Override
    protected String describeType() {
        return "GenericEventMessage";
    }
}
