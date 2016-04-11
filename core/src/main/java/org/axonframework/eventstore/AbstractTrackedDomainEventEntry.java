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

import javax.persistence.*;

/**
 * @author Rene de Waele
 */
@MappedSuperclass
@Table(uniqueConstraints = {@UniqueConstraint(columnNames = {"type", "aggregateIdentifier", "sequenceNumber"})})
public abstract class AbstractTrackedDomainEventEntry<T> extends AbstractDomainEventEntry<T> implements SerializedTrackedEventData<T> {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private long globalIndex;

    public AbstractTrackedDomainEventEntry(DomainEventMessage<?> eventMessage, Serializer serializer) {
        super(eventMessage, serializer);
    }

    public AbstractTrackedDomainEventEntry(long globalIndex, String eventIdentifier, Object timeStamp,
                                           String payloadType, String payloadRevision, T payload, T metaData,
                                           String type, String aggregateIdentifier, long sequenceNumber) {
        super(type, aggregateIdentifier, sequenceNumber, eventIdentifier, timeStamp, payloadType, payloadRevision,
              payload, metaData);
        this.globalIndex = globalIndex;
    }

    protected AbstractTrackedDomainEventEntry() {
    }

    @Override
    public TrackingToken trackingToken() {
        return new GlobalIndexTrackingToken(globalIndex);
    }

}
