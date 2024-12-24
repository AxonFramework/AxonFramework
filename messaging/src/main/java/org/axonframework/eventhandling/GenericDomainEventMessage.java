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
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;

import java.io.Serial;
import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Generic implementation of the {@link DomainEventMessage} interface.
 *
 * @param <P> The type of {@link #getPayload() payload} contained in this {@link EventMessage}.
 * @author Allard Buijze
 * @author Rene de Waele
 * @author Steven van Beelen
 * @since 2.0.0
 */
public class GenericDomainEventMessage<P> extends GenericEventMessage<P> implements DomainEventMessage<P> {

    @Serial
    private static final long serialVersionUID = -1222000190977419970L;

    private final String aggregateType;
    private final String aggregateIdentifier;
    private final long sequenceNumber;

    /**
     * Constructs a {@link GenericDomainEventMessage} originating from an aggregate of the given {@code aggregateType}
     * with the given {@code aggregateIdentifier}, {@code sequenceNumber}, {@code name}, and {@code payload}.
     * <p>
     * The {@link MetaData} defaults to an empty instance.
     *
     * @param aggregateType       The domain type generating this {@link DomainEventMessage}.
     * @param aggregateIdentifier The identifier of the aggregate generating this {@link DomainEventMessage}.
     * @param sequenceNumber      The {@link DomainEventMessage DomainEventMessage's} sequence number.
     * @param name                The {@link QualifiedName name} for this {@link DomainEventMessage}.
     * @param payload             The payload of type {@code P} for this {@link DomainEventMessage}.
     */
    public GenericDomainEventMessage(String aggregateType,
                                     String aggregateIdentifier,
                                     long sequenceNumber,
                                     @Nonnull QualifiedName name,
                                     @Nonnull P payload) {
        this(aggregateType, aggregateIdentifier, sequenceNumber, name, payload, MetaData.emptyInstance());
    }

    /**
     * Constructs a {@link GenericDomainEventMessage} originating from an aggregate of the given {@code aggregateType}
     * with the given {@code aggregateIdentifier}, {@code sequenceNumber}, {@code name}, {@code payload}, and
     * {@code metaData}.
     *
     * @param aggregateType       The domain type generating this {@link DomainEventMessage}.
     * @param aggregateIdentifier The identifier of the aggregate generating this {@link DomainEventMessage}.
     * @param sequenceNumber      The {@link DomainEventMessage DomainEventMessage's} sequence number.
     * @param name                The {@link QualifiedName name} for this {@link DomainEventMessage}.
     * @param payload             The payload of type {@code P} for this {@link DomainEventMessage}.
     * @param metaData            The metadata for this {@link DomainEventMessage}.
     */
    public GenericDomainEventMessage(String aggregateType,
                                     String aggregateIdentifier,
                                     long sequenceNumber,
                                     @Nonnull QualifiedName name,
                                     @Nonnull P payload,
                                     @Nonnull Map<String, ?> metaData) {
        this(aggregateType,
             aggregateIdentifier,
             sequenceNumber,
             new GenericMessage<>(name, payload, metaData),
             clock.instant());
    }

    /**
     * Constructs a {@link GenericDomainEventMessage} originating from an aggregate of the given {@code aggregateType}
     * with the given {@code aggregateIdentifier}, {@code sequenceNumber}, {@code messageIdentifier}, {@code name},
     * {@code payload}, {@code metaData}, and {@code timestamp}.
     *
     * @param aggregateType       The domain type generating this {@link DomainEventMessage}.
     * @param aggregateIdentifier The identifier of the aggregate generating this {@link DomainEventMessage}.
     * @param sequenceNumber      The {@link DomainEventMessage DomainEventMessage's} sequence number.
     * @param messageIdentifier   The identifier of this {@link DomainEventMessage}.
     * @param name                The {@link QualifiedName name} for this {@link DomainEventMessage}.
     * @param payload             The payload of type {@code P} for this {@link DomainEventMessage}.
     * @param metaData            The metadata for this {@link DomainEventMessage}.
     * @param timestamp           The {@link Instant timestamp} of this {@link DomainEventMessage DomainEventMessage's}
     *                            creation.
     */
    public GenericDomainEventMessage(String aggregateType,
                                     String aggregateIdentifier,
                                     long sequenceNumber,
                                     @Nonnull String messageIdentifier,
                                     @Nonnull QualifiedName name,
                                     @Nonnull P payload,
                                     @Nonnull Map<String, ?> metaData,
                                     @Nonnull Instant timestamp) {
        this(aggregateType,
             aggregateIdentifier,
             sequenceNumber,
             new GenericMessage<>(messageIdentifier, name, payload, metaData),
             timestamp);
    }

    /**
     * Constructs a {@link GenericDomainEventMessage} originating from an aggregate of the given {@code aggregateType}
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
     * @param delegate            The {@link Message} containing {@link Message#getPayload() payload},
     *                            {@link Message#name() name}, {@link Message#getIdentifier() identifier} and
     *                            {@link Message#getMetaData() metadata} for the {@link DomainEventMessage} to
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
     * Constructs a {@link GenericDomainEventMessage} originating from an aggregate of the given {@code aggregateType}
     * with the given {@code aggregateIdentifier}, {@code sequenceNumber}, {@code delegate}, and {@code timestamp},
     * intended to reconstruct another {@link DomainEventMessage}.
     * <p>
     * The {@code delegate} will be used supply the {@link Message#getPayload() payload}, {@link Message#name() name},
     * {@link Message#getMetaData() metadata} and {@link Message#getIdentifier() identifier} of the resulting
     * {@code GenericEventMessage}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param aggregateType       The domain type generating this {@link DomainEventMessage}.
     * @param aggregateIdentifier The identifier of the aggregate generating this {@link DomainEventMessage}.
     * @param sequenceNumber      The {@link DomainEventMessage DomainEventMessage's} sequence number.
     * @param delegate            The {@link Message} containing {@link Message#getPayload() payload},
     *                            {@link Message#name() name}, {@link Message#getIdentifier() identifier} and
     *                            {@link Message#getMetaData() metadata} for the {@link DomainEventMessage} to
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
    public GenericDomainEventMessage<P> withMetaData(@Nonnull Map<String, ?> metaData) {
        if (getMetaData().equals(metaData)) {
            return this;
        }
        return new GenericDomainEventMessage<>(aggregateType,
                                               aggregateIdentifier,
                                               sequenceNumber,
                                               getDelegate().withMetaData(metaData),
                                               getTimestamp());
    }

    @Override
    public GenericDomainEventMessage<P> andMetaData(@Nonnull Map<String, ?> metaData) {
        //noinspection ConstantConditions
        if (metaData == null || metaData.isEmpty() || getMetaData().equals(metaData)) {
            return this;
        }
        return new GenericDomainEventMessage<>(aggregateType,
                                               aggregateIdentifier,
                                               sequenceNumber,
                                               getDelegate().andMetaData(metaData),
                                               getTimestamp());
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
