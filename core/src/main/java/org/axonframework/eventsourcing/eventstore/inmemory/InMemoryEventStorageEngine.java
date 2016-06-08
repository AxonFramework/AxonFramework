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

package org.axonframework.eventsourcing.eventstore.inmemory;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.GlobalIndexTrackingToken;
import org.axonframework.eventsourcing.eventstore.TrackingToken;

import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.axonframework.eventsourcing.eventstore.EventUtils.asTrackedEventMessage;

/**
 * @author Rene de Waele
 */
public class InMemoryEventStorageEngine implements EventStorageEngine {

    private final NavigableMap<TrackingToken, TrackedEventMessage<?>> events = new ConcurrentSkipListMap<>();
    private final Map<String, DomainEventMessage<?>> snapshots = new ConcurrentHashMap<>();

    @Override
    public void appendEvents(List<? extends EventMessage<?>> events) {
        synchronized (this.events) {
            GlobalIndexTrackingToken trackingToken = nextTrackingToken();
            this.events.putAll(IntStream.range(0, events.size()).mapToObj(
                    i -> asTrackedEventMessage((EventMessage<?>) events.get(i), trackingToken.offsetBy(i))).collect(
                    Collectors.toMap(TrackedEventMessage::trackingToken, Function.identity())));
        }
    }

    @Override
    public void storeSnapshot(DomainEventMessage<?> snapshot) {
        snapshots.put(snapshot.getAggregateIdentifier(), snapshot);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation produces non-blocking event streams.
     */
    @Override
    public Stream<? extends TrackedEventMessage<?>> readEvents(TrackingToken trackingToken, boolean mayBlock) {
        if (trackingToken == null) {
            return events.values().stream();
        }
        return events.tailMap(trackingToken, false).values().stream();
    }

    @Override
    public DomainEventStream readEvents(String aggregateIdentifier, long firstSequenceNumber) {
        Stream<? extends DomainEventMessage<?>> stream = events.values().stream()
                .filter(event -> event instanceof DomainEventMessage<?>)
                .map(event -> (DomainEventMessage<?>) event)
                .filter(event -> aggregateIdentifier.equals(event.getAggregateIdentifier()) && event
                        .getSequenceNumber() >= firstSequenceNumber);
        return DomainEventStream.of(stream.iterator());
    }

    @Override
    public Optional<DomainEventMessage<?>> readSnapshot(String aggregateIdentifier) {
        return Optional.ofNullable(snapshots.get(aggregateIdentifier));
    }

    protected GlobalIndexTrackingToken nextTrackingToken() {
        return events.isEmpty() ? new GlobalIndexTrackingToken(0) : ((GlobalIndexTrackingToken) events.lastKey()).next();
    }
}
