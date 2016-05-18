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

package org.axonframework.eventstore.jpa.legacy;

import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventstore.SerializedTrackedEventData;
import org.axonframework.eventstore.TrackingToken;
import org.axonframework.eventstore.legacy.AbstractLegacyDomainEventEntry;
import org.axonframework.eventstore.legacy.LegacyTrackingToken;
import org.axonframework.serializer.Serializer;

import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;

/**
 * @author Rene de Waele
 */
@Entity
@Table(name = "DomainEventEntry", indexes = {@Index(columnList = "timeStamp,sequenceNumber,aggregateIdentifier")})
public class LegacyDomainEventEntry extends AbstractLegacyDomainEventEntry<byte[]> implements SerializedTrackedEventData<byte[]> {

    public LegacyDomainEventEntry(DomainEventMessage<?> eventMessage, Serializer serializer) {
        super(eventMessage, serializer, byte[].class);
    }

    protected LegacyDomainEventEntry() {
    }

    @Override
    public TrackingToken trackingToken() {
        return new LegacyTrackingToken(getTimestamp(), getAggregateIdentifier(), getSequenceNumber());
    }

}
