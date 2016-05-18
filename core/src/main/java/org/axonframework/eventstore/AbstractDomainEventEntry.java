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

package org.axonframework.eventstore;

import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.serializer.Serializer;

import javax.persistence.Basic;
import javax.persistence.MappedSuperclass;
import java.time.Instant;
import java.time.temporal.TemporalAccessor;

/**
 * @author Rene de Waele
 */
@MappedSuperclass
public abstract class AbstractDomainEventEntry<T> extends AbstractEventEntry<T> implements SerializedDomainEventData<T> {

    @Basic(optional = false)
    private String type;
    @Basic(optional = false)
    private String aggregateIdentifier;
    @Basic(optional = false)
    private long sequenceNumber;
    @Basic(optional = false)
    private Long timeStamp;

    public AbstractDomainEventEntry(DomainEventMessage<?> eventMessage, Serializer serializer, Class<T> contentType) {
        super(eventMessage, serializer, contentType);
        timeStamp = eventMessage.getTimestamp().toEpochMilli();
        type = eventMessage.getType();
        aggregateIdentifier = eventMessage.getAggregateIdentifier();
        sequenceNumber = eventMessage.getSequenceNumber();
    }

    public AbstractDomainEventEntry(String type, String aggregateIdentifier, long sequenceNumber,
                                    String eventIdentifier, Object timestamp, String payloadType,
                                    String payloadRevision, T payload, T metaData) {
        super(eventIdentifier, payloadType, payloadRevision, payload, metaData);
        if (timestamp instanceof TemporalAccessor) {
            this.timeStamp = Instant.from((TemporalAccessor) timestamp).toEpochMilli();
        } else if (timestamp instanceof CharSequence) {
            this.timeStamp = Instant.parse((CharSequence) timestamp).toEpochMilli();
        } else {
            this.timeStamp = (Long) timestamp;
        }
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

    @Override
    public Instant getTimestamp() {
        return Instant.ofEpochMilli(timeStamp);
    }
}
