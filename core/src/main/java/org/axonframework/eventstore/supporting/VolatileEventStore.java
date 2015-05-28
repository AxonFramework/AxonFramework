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

package org.axonframework.eventstore.supporting;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventstore.EventStore;
import org.axonframework.eventstore.EventVisitor;
import org.axonframework.eventstore.management.Criteria;
import org.axonframework.eventstore.management.CriteriaBuilder;
import org.axonframework.eventstore.management.EventStoreManagement;


import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Knut-Olav Hoven
 */
public class VolatileEventStore implements EventStore, EventStoreManagement {

    private final ArrayList<DomainEventMessage<?>> volatileEvents = new ArrayList<>();

    @Override
    public synchronized void visitEvents(EventVisitor visitor) {
        volatileEvents.forEach(visitor::doWithEvent);
    }

    @Override
    public synchronized void visitEvents(Criteria criteria, EventVisitor visitor) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public CriteriaBuilder newCriteriaBuilder() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public synchronized void appendEvents(List<DomainEventMessage<?>> events) {
        volatileEvents.addAll(events);
    }

    @Override
    public DomainEventStream readEvents(String identifier) {
        return readEvents(identifier, 0, Long.MAX_VALUE);
    }

    @Override
    public synchronized DomainEventStream readEvents(String identifier,
                                                     long firstSequenceNumber, long lastSequenceNumber) {
        List<DomainEventMessage<?>> selection = volatileEvents
                .stream()
                .filter(message -> identifier.equals(message.getAggregateIdentifier())
                        && message.getSequenceNumber() >= firstSequenceNumber
                        && message.getSequenceNumber() <= lastSequenceNumber)
                .collect(Collectors.toList());

        return new SimpleDomainEventStream(selection);
    }

    public TimestampCutoffReadonlyEventStore cutoff(Instant cutOffTimestamp) {
        return new TimestampCutoffReadonlyEventStore(this, this, cutOffTimestamp);
    }
}
