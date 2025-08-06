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
import org.axonframework.messaging.MetaData;

import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Generic implementation of the {@link DomainEventMessage} interface.
 *
 * @param <P> The type of {@link #payload() payload} contained in this {@link EventMessage}.
 * @author Allard Buijze
 * @author Rene de Waele
 * @author Steven van Beelen
 * @since 2.0.0
 */
public class GenericDomainEventMessage<P> extends GenericEventMessage<P> implements DomainEventMessage<P> {

    private final String aggregateType;
    private final String aggregateIdentifier;
    private final long sequenceNumber;

    /**
     * Constructs a {@code GenericDomainEventMessage} originating from an aggregate of the given {@code aggregateType}
     * with the given {@code aggregateIdentifier}, {@code sequenceNumber}, {@code type}, and {@code payload}.
     * <p>
     * The {@link MetaData} defaults to an empty instance.
     *
     * @param aggregateType       The domain type generating this {@link DomainEventMessage}.
     * @param aggregateIdentifier The identifier of the aggregate generating this {@link DomainEventMessage}.
     * @param sequenceNumber      The {@link DomainEventMessage DomainEventMessage's} sequence number.
     * @param type                The {@link MessageType type} for this {@link DomainEventMessage}.
     * @param payload             The payload of type {@code P} for this {@link DomainEventMessage}.
     */
    public GenericDomainEventMessage(String aggregateType,
                                     String aggregateIdentifier,
                                     long sequenceNumber,
                                     @Nonnull MessageType type,
                                     @Nonnull P payload) {
        this(aggregateType, aggregateIdentifier, sequenceNumber, type, payload, MetaData.emptyInstance());
    }

    /**
     * Constructs a {@code GenericDomainEventMessage} originating from an aggregate of the given {@code aggregateType}
     * with the given {@code aggregateIdentifier}, {@code sequenceNumber}, {@code type}, {@code payload}, and
     * {@code metaData}.
     *
     * @param aggregateType       The domain type generating this {@link DomainEventMessage}.
     * @param aggregateIdentifier The identifier of the aggregate generating this {@link DomainEventMessage}.
     * @param sequenceNumber      The {@link DomainEventMessage DomainEventMessage's} sequence number.
     * @param type                The {@link MessageType type} for this {@link DomainEventMessage}.
     * @param payload             The payload of type {@code P} for this {@link DomainEventMessage}.
     * @param metaData            The metadata for this {@link DomainEventMessage}.
     */
    public GenericDomainEventMessage(String aggregateType,
                                     String aggregateIdentifier,
                                     long sequenceNumber,
                                     @Nonnull MessageType type,
                                     @Nonnull P payload,
                                     @Nonnull Map<String, String> metaData) {
        this(aggregateType,
             aggregateIdentifier,
             sequenceNumber,
             new GenericMessage<>(type, payload, metaData),
             clock.instant());
    }

    /**
     * Constructs a {@code GenericDomainEventMessage} originating from an aggregate of the given {@code aggregateType}
     * with the given {@code aggregateIdentifier}, {@code sequenceNumber}, {@code messageIdentifier}, {@code type},
     * {@code payload}, {@code metaData}, and {@code timestamp}.
     *
     * @param aggregateType       The domain type generating this {@link DomainEventMessage}.
     * @param aggregateIdentifier The identifier of the aggregate generating this {@link DomainEventMessage}.
     * @param sequenceNumber      The {@link DomainEventMessage DomainEventMessage's} sequence number.
     * @param messageIdentifier   The identifier of this {@link DomainEventMessage}.
     * @param type                The {@link MessageType type} for this {@link DomainEventMessage}.
     * @param payload             The payload of type {@code P} for this {@link DomainEventMessage}.
     * @param metaData            The metadata for this {@link DomainEventMessage}.
     * @param timestamp           The {@link Instant timestamp} of this {@link DomainEventMessage DomainEventMessage's}
     *                            creation.
     */
    public GenericDomainEventMessage(String aggregateType,
                                     String aggregateIdentifier,
                                     long sequenceNumber,
                                     @Nonnull String messageIdentifier,
                                     @Nonnull MessageType type,
                                     @Nonnull P payload,
                                     @Nonnull Map<String, String> metaData,
                                     @Nonnull Instant timestamp) {
        this(aggregateType,
             aggregateIdentifier,
             sequenceNumber,
             new GenericMessage<>(messageIdentifier, type, payload, metaData),
             timestamp);
    }

    /**
     * Constructs a {@code GenericDomainEventMessage} originating from an aggregate of the given {@code aggregateType}
     * with the given {@code aggregateIdentifier}, {@code sequenceNumber}, {@code delegate}, and
     * {@code timestampSupplier}, intended to reconstruct another {@link DomainEventMessage}.
     * <p>
     * The timestamp of the event is supplied lazily through the given {@code timestampSupplier} to prevent unnecessary
     * deserialization of the timestamp.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param aggregateType       The domain type generating this {@link DomainEventMessage}.
     * @param aggregateIdentifier The identifier of the aggregate generating this {@link DomainEventMessage}.
     * @param sequenceNumber      The {@link DomainEventMessage DomainEventMessage's} sequence number.
     * @param delegate            The {@link Message} containing {@link Message#payload() payload},
     *                            {@link Message#type() type}, {@link Message#identifier() identifier} and
     *                            {@link Message#metaData() metadata} for the {@link DomainEventMessage} to
     *                            reconstruct.
     * @param timestampSupplier   The {@link Instant timestampSupplier} of this
     *                            {@link DomainEventMessage GenericDomainEventMessage's} creation.
     */
    public GenericDomainEventMessage(String aggregateType,
                                     String aggregateIdentifier,
                                     long sequenceNumber,
                                     @Nonnull Message<P> delegate,
                                     @Nonnull Supplier<Instant> timestampSupplier) {
        super(delegate, timestampSupplier);
        this.aggregateType = aggregateType;
        this.aggregateIdentifier = aggregateIdentifier;
        this.sequenceNumber = sequenceNumber;
    }

    /**
     * Constructs a {@code GenericDomainEventMessage} originating from an aggregate of the given {@code aggregateType}
     * with the given {@code aggregateIdentifier}, {@code sequenceNumber}, {@code delegate}, and {@code timestamp},
     * intended to reconstruct another {@link DomainEventMessage}.
     * <p>
     * The {@code delegate} will be used supply the {@link Message#payload() payload}, {@link Message#type() type},
     * {@link Message#metaData() metadata} and {@link Message#identifier() identifier} of the resulting
     * {@code GenericEventMessage}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param aggregateType       The domain type generating this {@link DomainEventMessage}.
     * @param aggregateIdentifier The identifier of the aggregate generating this {@link DomainEventMessage}.
     * @param sequenceNumber      The {@link DomainEventMessage DomainEventMessage's} sequence number.
     * @param delegate            The {@link Message} containing {@link Message#payload() payload},
     *                            {@link Message#type() type}, {@link Message#identifier() identifier} and
     *                            {@link Message#metaData() metadata} for the {@link DomainEventMessage} to
     *                            reconstruct.
     * @param timestamp           The {@link Instant timestamp} of this {@link DomainEventMessage DomainEventMessage's}
     *                            creation.
     */
    public GenericDomainEventMessage(String aggregateType,
                                     String aggregateIdentifier,
                                     long sequenceNumber,
                                     @Nonnull Message<P> delegate,
                                     @Nonnull Instant timestamp) {
        super(delegate, timestamp);
        this.aggregateType = aggregateType;
        this.aggregateIdentifier = aggregateIdentifier;
        this.sequenceNumber = sequenceNumber;
    }

    @Override
    public String getType() {
        return aggregateType;
    }

    @Override
    public String getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    @Override
    public long getSequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public GenericDomainEventMessage<P> withMetaData(@Nonnull Map<String, String> metaData) {
        if (metaData().equals(metaData)) {
            return this;
        }
        return new GenericDomainEventMessage<>(aggregateType,
                                               aggregateIdentifier,
                                               sequenceNumber,
                                               delegate().withMetaData(metaData),
                                               timestamp());
    }

    @Override
    public GenericDomainEventMessage<P> andMetaData(@Nonnull Map<String, String> metaData) {
        //noinspection ConstantConditions
        if (metaData == null || metaData.isEmpty() || metaData().equals(metaData)) {
            return this;
        }
        return new GenericDomainEventMessage<>(aggregateType,
                                               aggregateIdentifier,
                                               sequenceNumber,
                                               delegate().andMetaData(metaData),
                                               timestamp());
    }

    @Override
    protected void describeTo(StringBuilder stringBuilder) {
        super.describeTo(stringBuilder);
        stringBuilder.append('\'').append(", aggregateType='")
                     .append(getType()).append('\'')
                     .append(", aggregateIdentifier='")
                     .append(getAggregateIdentifier()).append('\'')
                     .append(", sequenceNumber=")
                     .append(getSequenceNumber());
    }

    @Override
    protected String describeType() {
        return "GenericDomainEventMessage";
    }
}
