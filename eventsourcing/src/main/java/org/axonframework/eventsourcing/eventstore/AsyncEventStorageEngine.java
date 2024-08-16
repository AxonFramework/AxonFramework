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

import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.MessageStream;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

import static java.util.Arrays.asList;

/**
 * Provides a mechanism to append as well as retrieve events from an underlying storage mechanism.
 *
 * @author Allard Buijze
 * @author Rene de Waele
 * @author Milan Savic
 * @author Steven van Beelen
 * @since 3.0
 */
// Concrete impls like in-mem, JPA, JDBC, AxonServer, R2DBC
// Layered impls like Converting (serializing & upcasting)
public interface AsyncEventStorageEngine extends DescribableComponent {

    /**
     * Append one or more events to the event storage. Events will be appended in the order that they are offered in.
     * <p>
     * Note that all events should have a unique event identifier. When storing {@link DomainEventMessage domain events}
     * events should also have a unique combination of aggregate id and sequence number.
     * <p>
     * By default this method creates a list of the offered events and then invokes
     * {@link #appendEvents(AppendCondition, List)}.
     *
     * @param events Events to append to the event storage
     */
    default CompletableFuture<Long> appendEvents(@Nonnull AppendCondition condition,
                                                 @Nonnull EventMessage<?>... events) {
        return appendEvents(condition, asList(events));
    }

    /**
     * Append a list of events to the event storage. Events will be appended in the order that they are offered in.
     * <p>
     * Note that all events should have a unique event identifier. When storing {@link DomainEventMessage domain events}
     * events should also have a unique combination of aggregate id and sequence number.
     *
     * @param events Events to append to the event storage
     */
    CompletableFuture<Long> appendEvents(@Nonnull AppendCondition condition,
                                         @Nonnull List<? extends EventMessage<?>> events);

    /**
     * Get a {@link DomainEventStream} containing all events published by the aggregate with given
     * {@code aggregateIdentifier}. By default calling this method is shorthand for an invocation of
     * {@link #source(String, long)} with a sequence number of 0.
     * <p>
     * The returned stream is finite, i.e. it should not block to wait for further events if the end of the event stream
     * of the aggregate is reached.
     *
     * @param condition The identifier of the aggregate to return an event stream for
     * @return A non-blocking DomainEventStream of the given aggregate
     */
    MessageStream<TrackedEventMessage<?>> source(@Nonnull SourcingCondition condition);
    // TODO finite
    // TODO we need a different object, as we should return a stream **and** the position of the last event, as that is the consistency marker to use when appending new events with the same condition

    /**
     * Open an event stream containing all events stored since given tracking token. The returned stream is comprised of
     * events from aggregates as well as other application events. Pass a {@code trackingToken} of {@code null} to open
     * a stream containing all available events.
     * <p>
     * If the value of the given {@code mayBlock} is {@code true} the returned stream is allowed to block while waiting
     * for new event messages if the end of the stream is reached.
     *
     * @param condition Object describing the global index of the last processed event or {@code null} to create a
     *                  stream of all events in the store
     * @return A stream containing all tracked event messages stored since the given tracking token
     */
    MessageStream<TrackedEventMessage<?>> stream(@Nonnull StreamingCondition condition);
    // TODO infinite

    /**
     * Creates a token that is at the tail of an event stream - that tracks events from the beginning of time.
     *
     * @return a tracking token at the tail of an event stream, if event stream is empty {@code null} is returned
     */
    CompletableFuture<TrackingToken> tailToken();

    /**
     * Creates a token that is at the head of an event stream - that tracks all new events.
     *
     * @return a tracking token at the head of an event stream, if event stream is empty {@code null} is returned
     */
    CompletableFuture<TrackingToken> headToken();

    /**
     * Creates a token that tracks all events after given {@code dateTime}. If there is an event exactly at the given
     * {@code dateTime}, it will be tracked too.
     *
     * @param at The date and time for determining criteria how the tracking token should be created. A tracking token
     *           should point to very first event before this date and time.
     * @return a tracking token at the given {@code dateTime}, if there aren't events matching this criteria
     * {@code null} is returned
     */
    CompletableFuture<TrackingToken> tokenAt(@Nonnull Instant at);
}
