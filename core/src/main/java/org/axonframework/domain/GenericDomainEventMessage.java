/*
 * Copyright (c) 2010-2011. Axon Framework
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

/**
 * Generic implementation of the DomainEventMessage interface. It simply keeps a reference to the payload and MetaData,
 * as well as the aggregate identifier and sequence number.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class GenericDomainEventMessage<T> extends GenericEventMessage<T> implements DomainEventMessage<T> {

    private final AggregateIdentifier aggregateIdentifier;
    private final long sequenceNumber;

    /**
     * Initialize a DomainEventMessage originating from an Aggregate with the given <code>aggregateIdentifier</code>,
     * with given <code>sequenceNumber</code> and <code>payload</code>. The MetaData of the message is empty.
     *
     * @param aggregateIdentifier The identifier of the aggregate generating this message
     * @param sequenceNumber      The message's sequence number
     * @param payload             The application-specific payload of the message
     */
    public GenericDomainEventMessage(AggregateIdentifier aggregateIdentifier, long sequenceNumber,
                                     T payload) {
        this(aggregateIdentifier, sequenceNumber, MetaData.emptyInstance(), payload);
    }

    /**
     * Initialize a DomainEventMessage originating from an Aggregate with the given <code>aggregateIdentifier</code>,
     * with given <code>sequenceNumber</code>, <code>metaData</code> and <code>payload</code>.
     *
     * @param aggregateIdentifier The identifier of the aggregate generating this message
     * @param sequenceNumber      The message's sequence number
     * @param metaData            The MetaData to attach to the message
     * @param payload             The application-specific payload of the message
     */
    public GenericDomainEventMessage(AggregateIdentifier aggregateIdentifier, long sequenceNumber,
                                     MetaData metaData, T payload) {
        super(metaData, payload);
        this.aggregateIdentifier = aggregateIdentifier;
        this.sequenceNumber = sequenceNumber;
    }

    @Override
    public Long getSequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public AggregateIdentifier getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    public String toString() {
        return String.format("GenericDomainEventMessage[%s]", getPayload().toString());
    }
}
