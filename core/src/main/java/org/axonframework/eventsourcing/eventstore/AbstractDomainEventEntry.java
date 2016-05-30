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

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.serialization.Serializer;

import javax.persistence.Basic;
import javax.persistence.MappedSuperclass;

/**
 * @author Rene de Waele
 */
@MappedSuperclass
public abstract class AbstractDomainEventEntry<T> extends AbstractEventEntry<T> implements DomainEventData<T> {

    @Basic(optional = false)
    private String type;
    @Basic(optional = false)
    private String aggregateIdentifier;
    @Basic(optional = false)
    private long sequenceNumber;

    public AbstractDomainEventEntry(DomainEventMessage<?> eventMessage, Serializer serializer, Class<T> contentType) {
        super(eventMessage, serializer, contentType);
        type = eventMessage.getType();
        aggregateIdentifier = eventMessage.getAggregateIdentifier();
        sequenceNumber = eventMessage.getSequenceNumber();
    }

    public AbstractDomainEventEntry(String type, String aggregateIdentifier, long sequenceNumber,
                                    String eventIdentifier, Object timestamp, String payloadType,
                                    String payloadRevision, T payload, T metaData) {
        super(eventIdentifier, timestamp, payloadType, payloadRevision, payload, metaData);
        this.type = type;
        this.aggregateIdentifier = aggregateIdentifier;
        this.sequenceNumber = sequenceNumber;
    }

    protected AbstractDomainEventEntry() {
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
}
