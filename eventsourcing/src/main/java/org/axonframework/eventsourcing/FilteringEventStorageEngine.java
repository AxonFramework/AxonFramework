/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.eventsourcing;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

/**
 * Implementation of EventStorageEngine that delegates to another implementation, while filtering
 * events as they are appended. This prevents certain events to be stored in the Event Store.
 *
 * @author Allard Buijze
 * @since 3.1
 */
public class FilteringEventStorageEngine implements EventStorageEngine {

    private final EventStorageEngine delegate;
    private final Predicate<? super EventMessage<?>> filter;

    /**
     * Initializes the FilteringEventStorageEngine delegating all event messages matching the given {@code filter} to
     * the given {@code delegate}.
     * <p>
     * Note that this only affects events stored in the StorageEngine. The {@code EventStore} will still publish these
     * events to Subscribed event handlers. Tracking Event Processors take their events from the stored events, and
     * will therefore not receive any events blocked by this instance.
     *
     * @param delegate the EventStorageEngine to store matching messages in
     * @param filter   the predicate that event messages must match against to be stored
     */
    public FilteringEventStorageEngine(EventStorageEngine delegate, Predicate<? super EventMessage<?>> filter) {
        this.delegate = delegate;
        this.filter = filter;
    }

    @Override
    public void appendEvents(@Nonnull EventMessage<?>... events) {
        delegate.appendEvents(Arrays.stream(events).filter(filter).collect(Collectors.toList()));
    }

    @Override
    public void appendEvents(@Nonnull List<? extends EventMessage<?>> events) {
        delegate.appendEvents(events.stream().filter(filter).collect(Collectors.toList()));
    }

    @Override
    public void storeSnapshot(@Nonnull DomainEventMessage<?> snapshot) {
        delegate.storeSnapshot(snapshot);
    }

    @Override
    public Stream<? extends TrackedEventMessage<?>> readEvents(TrackingToken trackingToken, boolean mayBlock) {
        return delegate.readEvents(trackingToken, mayBlock);
    }

    @Override
    public DomainEventStream readEvents(@Nonnull String aggregateIdentifier) {
        return delegate.readEvents(aggregateIdentifier);
    }

    @Override
    public DomainEventStream readEvents(@Nonnull String aggregateIdentifier, long firstSequenceNumber) {
        return delegate.readEvents(aggregateIdentifier, firstSequenceNumber);
    }

    @Override
    public Optional<DomainEventMessage<?>> readSnapshot(@Nonnull String aggregateIdentifier) {
        return delegate.readSnapshot(aggregateIdentifier);
    }

    @Override
    public Optional<Long> lastSequenceNumberFor(@Nonnull String aggregateIdentifier) {
        return delegate.lastSequenceNumberFor(aggregateIdentifier);
    }

    @Override
    public TrackingToken createTailToken() {
        return delegate.createTailToken();
    }

    @Override
    public TrackingToken createHeadToken() {
        return delegate.createHeadToken();
    }

    @Override
    public TrackingToken createTokenAt(@Nonnull Instant dateTime) {
        return delegate.createTokenAt(dateTime);
    }
}
