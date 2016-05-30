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

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Arrays.asList;

/**
 * @author Rene de Waele
 */
public interface EventStorageEngine {

    default void appendEvents(EventMessage<?>... events) {
        appendEvents(asList(events));
    }

    void appendEvents(List<? extends EventMessage<?>> events);

    /**
     * Replaces the previous snapshot of this aggregate
     */
    void storeSnapshot(DomainEventMessage<?> snapshot);

    Stream<? extends TrackedEventMessage<?>> readEvents(TrackingToken trackingToken, boolean mayBlock);

    default DomainEventStream readEvents(String aggregateIdentifier) {
        return readEvents(aggregateIdentifier, 0);
    }

    DomainEventStream readEvents(String aggregateIdentifier, long firstSequenceNumber);

    Optional<DomainEventMessage<?>> readSnapshot(String aggregateIdentifier);
}
