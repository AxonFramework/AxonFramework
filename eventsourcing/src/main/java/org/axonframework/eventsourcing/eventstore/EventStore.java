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

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.DomainEventSequenceAware;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Optional;
import javax.annotation.Nonnull;

/**
 * Provides a mechanism to open streams from events in the the underlying event storage.
 * <p/>
 * The EventStore provides access to both the global event stream comprised of all domain and application events, as
 * well as streams containing only events of a single aggregate.
 *
 * @author Allard Buijze
 * @author Rene de Waele
 */
public interface EventStore
        extends EventBus, StreamableMessageSource<TrackedEventMessage<?>>, DomainEventSequenceAware {

    /**
     * Open an event stream containing all domain events belonging to the given {@code aggregateIdentifier}.
     * <p>
     * The returned stream is <em>finite</em>, ending with the last known event of the aggregate. If the event store
     * holds no events of the given aggregate an empty stream is returned.
     *
     * @param aggregateIdentifier the identifier of the aggregate whose events to fetch
     * @return a stream of all currently stored events of the aggregate
     */
    DomainEventStream readEvents(@Nonnull String aggregateIdentifier);

    /**
     * Open an event stream containing all domain events belonging to the given {@code aggregateIdentifier}.
     * <p>
     * The returned stream is <em>finite</em>, ending with the last known event of the aggregate. If the event store
     * holds no events of the given aggregate an empty stream is returned.
     * <p>
     * The default implementation invokes {@link #readEvents(String)} and then filters out events with a sequence number
     * smaller than {@code firstSequenceNumber}.
     *
     * @param aggregateIdentifier the identifier of the aggregate whose events to fetch
     * @param firstSequenceNumber the expected sequence number of the first event in the returned stream
     * @return a stream of all currently stored events of the aggregate
     */
    default DomainEventStream readEvents(@Nonnull String aggregateIdentifier, long firstSequenceNumber) {
        DomainEventStream wholeStream = readEvents(aggregateIdentifier);
        return DomainEventStream
                .of(wholeStream.asStream().filter(event -> event.getSequenceNumber() >= firstSequenceNumber),
                    wholeStream::getLastSequenceNumber);
    }

    /**
     * Stores the given (temporary) {@code snapshot} event. This snapshot replaces the segment of the event stream
     * identified by the {@code snapshot}'s {@link DomainEventMessage#getAggregateIdentifier() Aggregate Identifier} up
     * to (and including) the event with the {@code snapshot}'s {@link DomainEventMessage#getSequenceNumber() sequence
     * number}.
     * <p>
     * These snapshots will only affect the {@link DomainEventStream} returned by the {@link #readEvents(String)}
     * method. They do not change the events returned by {@link EventStore#openStream(TrackingToken)} or those received
     * by using {@link #subscribe(java.util.function.Consumer)}.
     * <p>
     * Note that snapshots are considered a temporary replacement for Events, and are used as performance optimization.
     * Event Store implementations may choose to ignore or delete snapshots.
     *
     * @param snapshot The snapshot to replace part of the DomainEventStream.
     */
    void storeSnapshot(@Nonnull DomainEventMessage<?> snapshot);

    @Override
    default Optional<Long> lastSequenceNumberFor(String aggregateIdentifier) {
        return readEvents(aggregateIdentifier).asStream().map(DomainEventMessage::getSequenceNumber)
                                              .max(Long::compareTo);
    }

    /**
     * Retrieves the {@link AppendEventTransaction transaction for appending events} for the given
     * {@code processingContext}. If no transaction is available, a new, empty transaction is created.
     *
     * @param processingContext The context for which to retrieve the {@link AppendEventTransaction}.
     * @return The {@link AppendEventTransaction}, existing or newly created, for the given {@code processingContext}.
     */
    default AppendEventTransaction currentTransaction(ProcessingContext processingContext) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
