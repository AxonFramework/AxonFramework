/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore.inmemory;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nonnull;

import static org.axonframework.eventhandling.EventUtils.asTrackedEventMessage;

/**
 * Thread-safe event storage engine that stores events and snapshots in memory.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public class InMemoryEventStorageEngine implements EventStorageEngine {

    @SuppressWarnings("SortedCollectionWithNonComparableKeys")
    private final NavigableMap<TrackingToken, TrackedEventMessage<?>> events = new ConcurrentSkipListMap<>();
    private final Map<String, List<DomainEventMessage<?>>> snapshots = new ConcurrentHashMap<>();
    private final long offset;

    /**
     * Initializes an InMemoryEventStorageEngine. The engine will be empty, and there is no offset for the first token.
     */
    public InMemoryEventStorageEngine() {
        this(0L);
    }

    /**
     * Initializes an InMemoryEventStorageEngine using given {@code offset} to initialize the tokens with.
     *
     * @param offset The value to use for the token of the first event appended
     */
    public InMemoryEventStorageEngine(long offset) {
        this.offset = offset;
    }

    @Override
    public void appendEvents(@Nonnull List<? extends EventMessage<?>> events) {
        if (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().onPrepareCommit(uow -> storeEvents(events));
        } else {
            storeEvents(events);
        }
    }

    private void storeEvents(List<? extends EventMessage<?>> events) {
        synchronized (this.events) {
            GlobalSequenceTrackingToken trackingToken = nextTrackingToken();
            this.events.putAll(
                    IntStream.range(0, events.size())
                             .mapToObj(i -> asTrackedEventMessage(
                                     (EventMessage<?>) events.get(i), trackingToken.offsetBy(i)
                             ))
                             .collect(Collectors.toMap(TrackedEventMessage::trackingToken, Function.identity()))
            );
        }
    }

    @Override
    public void storeSnapshot(@Nonnull DomainEventMessage<?> snapshot) {
        snapshots.compute(snapshot.getAggregateIdentifier(), (aggregateId, snapshotsSoFar) -> {
            if (snapshotsSoFar == null) {
                CopyOnWriteArrayList<DomainEventMessage<?>> newSnapshots = new CopyOnWriteArrayList<>();
                newSnapshots.add(snapshot);
                return newSnapshots;
            }
            snapshotsSoFar.add(snapshot);
            return snapshotsSoFar;
        });
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation produces non-blocking event streams.
     */
    @Override
    public Stream<? extends TrackedEventMessage<?>> readEvents(TrackingToken trackingToken, boolean mayBlock) {
        return StreamSupport.stream(new MapEntrySpliterator(events, trackingToken), false);
    }

    @Override
    public DomainEventStream readEvents(@Nonnull String aggregateIdentifier, long firstSequenceNumber) {
        AtomicReference<Long> sequenceNumber = new AtomicReference<>();
        Stream<? extends DomainEventMessage<?>> stream =
                events.values().stream().filter(event -> event instanceof DomainEventMessage<?>)
                      .map(event -> (DomainEventMessage<?>) event)
                      .filter(event -> aggregateIdentifier.equals(event.getAggregateIdentifier())
                              && event.getSequenceNumber() >= firstSequenceNumber)
                      .peek(event -> sequenceNumber.set(event.getSequenceNumber()));
        return DomainEventStream.of(stream, sequenceNumber::get);
    }

    @Override
    public Optional<DomainEventMessage<?>> readSnapshot(@Nonnull String aggregateIdentifier) {
        return snapshots.getOrDefault(aggregateIdentifier, Collections.emptyList())
                        .stream()
                        .max(Comparator.comparingLong(DomainEventMessage::getSequenceNumber));
    }

    @Override
    public TrackingToken createTailToken() {
        if (events.size() == 0) {
            return null;
        }
        GlobalSequenceTrackingToken firstToken = (GlobalSequenceTrackingToken) events.firstKey();
        return new GlobalSequenceTrackingToken(firstToken.getGlobalIndex() - 1);
    }

    @Override
    public TrackingToken createHeadToken() {
        if (events.size() == 0) {
            return null;
        }
        return events.lastKey();
    }

    @Override
    public TrackingToken createTokenAt(@Nonnull Instant dateTime) {
        return events.values()
                     .stream()
                     .filter(event -> event.getTimestamp().equals(dateTime) || event.getTimestamp().isAfter(dateTime))
                     .min(Comparator.comparingLong(e -> ((GlobalSequenceTrackingToken) e.trackingToken())
                             .getGlobalIndex()))
                     .map(TrackedEventMessage::trackingToken)
                     .map(tt -> (GlobalSequenceTrackingToken) tt)
                     .map(tt -> new GlobalSequenceTrackingToken(tt.getGlobalIndex() - 1))
                     .map(tt -> (TrackingToken) tt)
                     .orElseGet(this::createHeadToken);
    }

    /**
     * Returns the tracking token to use for the next event to be stored.
     *
     * @return the tracking token for the next event
     */
    protected GlobalSequenceTrackingToken nextTrackingToken() {
        return events.isEmpty()
                ? new GlobalSequenceTrackingToken(offset)
                : ((GlobalSequenceTrackingToken) events.lastKey()).next();
    }

    private static class MapEntrySpliterator extends Spliterators.AbstractSpliterator<TrackedEventMessage<?>> {

        private final NavigableMap<TrackingToken, TrackedEventMessage<?>> source;
        private volatile TrackingToken lastToken;

        public MapEntrySpliterator(NavigableMap<TrackingToken, TrackedEventMessage<?>> source,
                                   TrackingToken trackingToken) {
            super(Long.MAX_VALUE, Spliterator.ORDERED);
            this.source = source;
            this.lastToken = trackingToken;
        }

        @Override
        public boolean tryAdvance(Consumer<? super TrackedEventMessage<?>> action) {
            Map.Entry<TrackingToken, TrackedEventMessage<?>> next;
            if (lastToken != null) {
                next = source.higherEntry(lastToken);
            } else {
                next = source.firstEntry();
            }
            if (next != null) {
                lastToken = next.getKey();
                action.accept(next.getValue());
            }
            return next != null;
        }
    }
}
