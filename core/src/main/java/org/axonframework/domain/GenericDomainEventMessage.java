/*
 * Copyright (c) 2010-2014. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.domain;

import org.joda.time.DateTime;

import java.util.Map;

/**
 * Generic implementation of the DomainEventMessage interface. It simply keeps a reference to the payload and MetaData,
 * as well as the aggregate identifier and sequence number.
 *
 * @param <T> The type of payload contained in this Message
 * @author Allard Buijze
 * @since 2.0
 */
public class GenericDomainEventMessage<T> extends GenericEventMessage<T> implements DomainEventMessage<T> {

    private static final long serialVersionUID = 5751346338145616886L;

    private final Object aggregateIdentifier;
    private final long sequenceNumber;

    /**
     * Initialize a DomainEventMessage originating from an Aggregate with the given <code>aggregateIdentifier</code>,
     * with given <code>sequenceNumber</code> and <code>payload</code>. The MetaData of the message is empty.
     *
     * @param aggregateIdentifier The identifier of the aggregate generating this message
     * @param sequenceNumber      The message's sequence number
     * @param payload             The application-specific payload of the message
     */
    public GenericDomainEventMessage(Object aggregateIdentifier, long sequenceNumber, T payload) {
        this(aggregateIdentifier, sequenceNumber, payload, MetaData.emptyInstance());
    }

    /**
     * Initialize a DomainEventMessage originating from an Aggregate with the given <code>aggregateIdentifier</code>,
     * with given <code>sequenceNumber</code>, <code>metaData</code> and <code>payload</code>.
     *
     * @param aggregateIdentifier The identifier of the aggregate generating this message
     * @param sequenceNumber      The message's sequence number
     * @param payload             The application-specific payload of the message
     * @param metaData            The MetaData to attach to the message
     */
    public GenericDomainEventMessage(Object aggregateIdentifier, long sequenceNumber,
                                     T payload, Map<String, ?> metaData) {
        super(payload, metaData);
        this.aggregateIdentifier = aggregateIdentifier;
        this.sequenceNumber = sequenceNumber;
    }

    /**
     * Constructor to reconstruct a DomainEventMessage using existing data.
     *
     * @param identifier          The identifier of the Message
     * @param timestamp           The timestamp of the Message creation
     * @param aggregateIdentifier The identifier of the aggregate from which the message originates
     * @param sequenceNumber      The sequence number of the message withing the originating aggregate
     * @param payload             The payload of the message
     * @param metaData            The meta data of the message
     */
    public GenericDomainEventMessage(String identifier, DateTime timestamp, Object aggregateIdentifier,
                                     long sequenceNumber, T payload, Map<String, ?> metaData) {
        super(identifier, timestamp, payload, metaData);
        this.aggregateIdentifier = aggregateIdentifier;
        this.sequenceNumber = sequenceNumber;
    }

    /**
     * Copy constructor that allows creation of a new GenericDomainEventMessage with modified metaData. All information
     * from the <code>original</code> is copied, except for the metaData.
     *
     * @param original The original message
     * @param metaData The MetaData for the new message
     */
    private GenericDomainEventMessage(GenericDomainEventMessage<T> original, Map<String, ?> metaData) {
        super(original.getIdentifier(), original.getTimestamp(), original.getPayload(), metaData);
        this.aggregateIdentifier = original.getAggregateIdentifier();
        this.sequenceNumber = original.getSequenceNumber();
    }

    @Override
    public long getSequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public Object getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    @Override
    public GenericDomainEventMessage<T> withMetaData(Map<String, ?> newMetaData) {
        if (getMetaData().equals(newMetaData)) {
            return this;
        }
        return new GenericDomainEventMessage<T>(this, newMetaData);
    }

    @Override
    public GenericDomainEventMessage<T> andMetaData(Map<String, ?> additionalMetaData) {
        if (additionalMetaData.isEmpty()) {
            return this;
        }
        return new GenericDomainEventMessage<T>(this, getMetaData().mergedWith(additionalMetaData));
    }

    @Override
    public String toString() {
        return String.format("GenericDomainEventMessage[%s]", getPayload().toString());
    }
}
