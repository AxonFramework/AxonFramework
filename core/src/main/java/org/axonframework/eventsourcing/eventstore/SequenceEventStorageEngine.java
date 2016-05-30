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

/**
 * TODO This implementation assumes no overlap between the historic and active storage. That ok?
 *
 * @author Rene de Waele
 */
public class SequenceEventStorageEngine implements EventStorageEngine {

    private final EventStorageEngine historicStorage, activeStorage;

    public SequenceEventStorageEngine(EventStorageEngine historicStorage, EventStorageEngine activeStorage) {
        this.historicStorage = historicStorage;
        this.activeStorage = activeStorage;
    }

    @Override
    public void appendEvents(List<? extends EventMessage<?>> events) {
        activeStorage.appendEvents(events);
    }

    @Override
    public void storeSnapshot(DomainEventMessage<?> snapshot) {
        activeStorage.storeSnapshot(snapshot);
    }

    @Override
    public Stream<? extends TrackedEventMessage<?>> readEvents(TrackingToken trackingToken, boolean mayBlock) {
        return Stream.concat(historicStorage.readEvents(trackingToken, mayBlock),
                             activeStorage.readEvents(trackingToken, mayBlock));
    }

    @Override
    public DomainEventStream readEvents(String aggregateIdentifier, long firstSequenceNumber) {
        return DomainEventStream.concat(historicStorage.readEvents(aggregateIdentifier, firstSequenceNumber),
                                        activeStorage.readEvents(aggregateIdentifier, firstSequenceNumber));
    }

    @Override
    public Optional<DomainEventMessage<?>> readSnapshot(String aggregateIdentifier) {
        return Optional.ofNullable(activeStorage.readSnapshot(aggregateIdentifier).orElseGet(
                () -> historicStorage.readSnapshot(aggregateIdentifier).orElse(null)));
    }
}
