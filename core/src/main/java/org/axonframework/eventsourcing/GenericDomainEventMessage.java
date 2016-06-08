/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.metadata.MetaData;

import java.time.Instant;
import java.util.Map;

/**
 * @author Rene de Waele
 */
public class GenericDomainEventMessage<T> extends GenericEventMessage<T> implements DomainEventMessage<T> {

    private final String type;
    private final String aggregateIdentifier;
    private final long sequenceNumber;

    public GenericDomainEventMessage(String type, String aggregateIdentifier, long sequenceNumber,
                                     EventMessage<T> delegate) {
        this(type, aggregateIdentifier, sequenceNumber, delegate, delegate.getTimestamp());
    }

    public GenericDomainEventMessage(String type, String aggregateIdentifier, long sequenceNumber, T payload) {
        this(type, aggregateIdentifier, sequenceNumber, payload, MetaData.emptyInstance());
    }

    public GenericDomainEventMessage(String type, String aggregateIdentifier, long sequenceNumber, T payload,
                                     Map<String, ?> metaData) {
        this(type, aggregateIdentifier, sequenceNumber, new GenericMessage<>(payload, metaData), Instant.now());
    }

    public GenericDomainEventMessage(String type, String aggregateIdentifier, long sequenceNumber, T payload,
                                     Map<String, ?> metaData, String messageIdentifier, Instant timestamp) {
        this(type, aggregateIdentifier, sequenceNumber, new GenericMessage<>(messageIdentifier, payload, metaData),
             timestamp);
    }

    public GenericDomainEventMessage(String type, String aggregateIdentifier, long sequenceNumber, Message<T> delegate,
                                     Instant timestamp) {
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
    public GenericDomainEventMessage<T> withMetaData(Map<String, ?> metaData) {
        return new GenericDomainEventMessage<>(type, aggregateIdentifier, sequenceNumber,
                                               getDelegate().withMetaData(metaData), getTimestamp());
    }

    @Override
    public GenericDomainEventMessage<T> andMetaData(Map<String, ?> metaData) {
        return new GenericDomainEventMessage<>(type, aggregateIdentifier, sequenceNumber,
                                               getDelegate().andMetaData(metaData), getTimestamp());
    }
}
