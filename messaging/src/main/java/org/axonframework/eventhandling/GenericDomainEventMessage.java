/*
 * Copyright (c) 2010-2023. Axon Framework
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
import org.axonframework.messaging.MetaData;

import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

/**
 * Generic implementation of a {@link DomainEventMessage}.
 *
 * @param <T> The type of payload contained in this Message
 */
public class GenericDomainEventMessage<T> extends GenericEventMessage<T> implements DomainEventMessage<T> {

    private static final long serialVersionUID = -1222000190977419970L;
    private final String type;
    private final String aggregateIdentifier;
    private final long sequenceNumber;

    /**
     * Initialize a DomainEventMessage originating from an Aggregate with the given {@code aggregateIdentifier},
     * with given {@code sequenceNumber} and {@code payload}. The MetaData of the message is empty.
     *
     * @param type                The domain type
     * @param aggregateIdentifier The identifier of the aggregate generating this message
     * @param sequenceNumber      The message's sequence number
     * @param payload             The application-specific payload of the message
     */
    public GenericDomainEventMessage(String type, String aggregateIdentifier, long sequenceNumber, T payload) {
        this(type, aggregateIdentifier, sequenceNumber, payload, MetaData.emptyInstance());
    }

    /**
     * Initialize a DomainEventMessage originating from an Aggregate with the given {@code aggregateIdentifier},
     * with given {@code sequenceNumber} and {@code payload} and {@code metaData}.
     *
     * @param type                The domain type
     * @param aggregateIdentifier The identifier of the aggregate generating this message
     * @param sequenceNumber      The message's sequence number
     * @param payload             The application-specific payload of the message
     * @param metaData            The MetaData to attach to the message
     */
    public GenericDomainEventMessage(String type, String aggregateIdentifier, long sequenceNumber, T payload,
                                     Map<String, ?> metaData) {
        this(type, aggregateIdentifier, sequenceNumber, new GenericMessage<>(payload, metaData), clock.instant());
    }

    /**
     * Initialize a DomainEventMessage originating from an Aggregate using existing data.
     *
     * @param type                The domain type
     * @param aggregateIdentifier The identifier of the aggregate generating this message
     * @param sequenceNumber      The message's sequence number
     * @param payload             The application-specific payload of the message
     * @param metaData            The MetaData to attach to the message
     * @param messageIdentifier   The message identifier
     * @param timestamp           The event's timestamp
     */
    public GenericDomainEventMessage(String type, String aggregateIdentifier, long sequenceNumber, T payload,
                                     Map<String, ?> metaData, String messageIdentifier, Instant timestamp) {
        this(type, aggregateIdentifier, sequenceNumber, new GenericMessage<>(messageIdentifier, payload, metaData),
             timestamp);
    }

    /**
     * Initialize a DomainEventMessage originating from an Aggregate using existing data. The timestamp of the event is
     * supplied lazily to prevent unnecessary deserialization of the timestamp.
     *
     * @param type                The domain type
     * @param aggregateIdentifier The identifier of the aggregate generating this message
     * @param sequenceNumber      The message's sequence number
     * @param delegate            The delegate message providing the payload, metadata and identifier of the event
     * @param timestamp           The event's timestamp supplier
     */
    public GenericDomainEventMessage(String type, String aggregateIdentifier, long sequenceNumber, Message<T> delegate,
                                     Supplier<Instant> timestamp) {
        super(delegate, timestamp);
        this.type = type;
        this.aggregateIdentifier = aggregateIdentifier;
        this.sequenceNumber = sequenceNumber;
    }

    /**
     * Initialize a DomainEventMessage originating from an Aggregate with the given {@code aggregateIdentifier},
     * with given {@code sequenceNumber} and {@code payload}, {@code metaData} and {@code timestamp}.
     *
     * @param type                the aggregate type
     * @param aggregateIdentifier the aggregate identifier
     * @param sequenceNumber      the sequence number of the event
     * @param delegate            The delegate message providing the payload, metadata and identifier of the event
     * @param timestamp           the timestamp of the event
     */
    public GenericDomainEventMessage(String type, String aggregateIdentifier, long sequenceNumber,
                                     Message<T> delegate, Instant timestamp) {
        super(delegate, timestamp);
        this.type = type;
        this.aggregateIdentifier = aggregateIdentifier;
        this.sequenceNumber = sequenceNumber;
    }

    @Override
    public String getType() {
        return type;
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
    public GenericDomainEventMessage<T> withMetaData(@Nonnull Map<String, ?> metaData) {
        if (getMetaData().equals(metaData)) {
            return this;
        }
        return new GenericDomainEventMessage<>(type, aggregateIdentifier, sequenceNumber,
                                               getDelegate().withMetaData(metaData), getTimestamp());
    }

    @Override
    public GenericDomainEventMessage<T> andMetaData(@Nonnull Map<String, ?> metaData) {
        //noinspection ConstantConditions
        if (metaData == null || metaData.isEmpty() || getMetaData().equals(metaData)) {
            return this;
        }
        return new GenericDomainEventMessage<>(type, aggregateIdentifier, sequenceNumber,
                                               getDelegate().andMetaData(metaData), getTimestamp());
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
