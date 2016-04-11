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

package org.axonframework.eventstore.jpa;

import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventstore.AbstractTrackedDomainEventEntry;
import org.axonframework.serializer.Serializer;

import javax.persistence.Entity;

/**
 * @author Rene de Waele
 */
@Entity
public class DomainEventEntry extends AbstractTrackedDomainEventEntry<byte[]> {

    public DomainEventEntry(DomainEventMessage<?> eventMessage, Serializer serializer) {
        super(eventMessage, serializer);
    }

    public DomainEventEntry(long globalIndex, String eventIdentifier, String type, String aggregateIdentifier,
                            long sequenceNumber, Object timeStamp, String payloadType, String payloadRevision,
                            byte[] payload, byte[] metaData) {
        super(globalIndex, eventIdentifier, timeStamp, payloadType, payloadRevision, payload, metaData, type,
              aggregateIdentifier, sequenceNumber);
    }

    protected DomainEventEntry() {
    }

    @Override
    protected Class<byte[]> getContentType() {
        return byte[].class;
    }
}
