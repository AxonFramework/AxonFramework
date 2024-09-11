/*
 * Copyright (c) 2010-2024. Axon Framework
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

import jakarta.validation.constraints.NotNull;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.MessageStream;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.Arrays.asList;

/**
 * Provides a mechanism to {@link #appendEvents(AppendCondition, EventMessage[]) append} as well as retrieve
 * {@link EventMessage events} from an underlying storage mechanism.
 * <p>
 * Retrieval can be done either through {@link #source(SourcingCondition) sourcing} or
 * {@link #stream(StreamingCondition) streaming}. The former generates a <b>finite</b> stream intended to event source
 * (for example) a model. The latter provides an <b>infinite</b> stream.
 *
 * @author Allard Buijze
 * @author Milan Savic
 * @author Rene de Waele
 * @author Steven van Beelen
 * @since 3.0
 */ // TODO Rename to EventStorageEngine once fully integrated
public interface AsyncEventStorageEngine extends DescribableComponent {

    /**
     * Append one or more {@link EventMessage events} to the underlying storage solution.
     * <p>
     * Events will be appended in the order that they are offered in, validating the given {@code condition} before
     * being stored. Note that all events should have a unique event identifier. When storing
     * {@link IndexedEventMessage indexed events} the {@link IndexedEventMessage#indices() indices} will be stored as
     * well.
     * <p>
     * By default, this method creates a {@link List} of the offered events and then invokes
     * {@link #appendEvents(AppendCondition, List)}.
     *
     * @param events One or more {@link EventMessage events} to append to the underlying storage solution.
     * @return A {@link CompletableFuture} of {@link Long} returning the position of the last event to be appended.
     */
    default CompletableFuture<Long> appendEvents(@NotNull AppendCondition condition,
                                                 @NotNull EventMessage<?>... events) {
        return appendEvents(condition, asList(events));
    }

    /**
     * Appends a {@link List} of {@link EventMessage events} to the underlying storage solution.
     * <p>
     * Events will be appended in the order that they are offered in, validating the given {@code condition} before
     * being stored. Note that all events should have a unique event identifier. When storing
     * {@link IndexedEventMessage indexed events} the {@link IndexedEventMessage#indices() indices} will be stored as
     * well.
     *
     * @param events The {@link List} of {@link EventMessage events} to append to the underlying storage solution.
     * @return A {@link CompletableFuture} of {@link Long} returning the position of the last event in the given
     * {@link List} to be appended.
     */
    CompletableFuture<Long> appendEvents(@NotNull AppendCondition condition,
                                         @NotNull List<? extends EventMessage<?>> events);

    /**
     * Creates a <b>finite</b> {@link MessageStream} of {@link EventMessage events} matching the given
     * {@code condition}.
     * <p>
     * The {@code condition} dictates the sequence to load based on the {@link SourcingCondition#criteria()}.
     * Additionally, an optional {@link SourcingCondition#start()} and {@link SourcingCondition#end()} position may be
     * provided.
     * <p>
     * The returned stream is finite, i.e. it should not block to wait for further events if the end of the event stream
     * of the aggregate is reached.
     *
     * @param condition The {@link SourcingCondition} dictating the {@link MessageStream stream} of
     *                  {@link EventMessage events} to source.
     * @return A <b>finite</b> {@link MessageStream} of {@link EventMessage events} matching the given
     * {@code condition}.
     */
    MessageStream<EventMessage<?>> source(@NotNull SourcingCondition condition);

    /**
     * Creates an <b>infinite</b> {@link MessageStream} of {@link EventMessage events} matching the given
     * {@code condition}.
     * <p>
     * The {@code condition} may dictate the {@link StreamingCondition#position()} to start streaming from, as well as
     * define {@link StreamingCondition#criteria() filter criteria} for the returned {@code MessageStream}.
     *
     * @param condition The {@link StreamingCondition} dictating the {@link StreamingCondition#position()} to start
     *                  streaming from, as well as the {@link StreamingCondition#criteria() filter criteria} used for
     *                  the returned {@link MessageStream}.
     * @return An <b>infinite</b> {@link MessageStream} of {@link EventMessage events} matching the given
     * {@code condition}.
     */
    MessageStream<TrackedEventMessage<?>> stream(@NotNull StreamingCondition condition);

    /**
     * Creates a {@link TrackingToken} that is at the tail of an event stream.
     * <p>
     * In other words, a token that tracks events from the beginning of time.
     *
     * @return A {@link CompletableFuture} of a {@link TrackingToken} at the tail of an event stream.
     */
    CompletableFuture<TrackingToken> tailToken();

    /**
     * Creates a {@link TrackingToken} that is at the head of an event stream.
     * <p>
     * In other words, a token that tracks all <b>new</b> events from this point forward.
     *
     * @return A {@link CompletableFuture} of a {@link TrackingToken} at the head of an event stream.
     */
    CompletableFuture<TrackingToken> headToken();

    /**
     * Creates a {@link TrackingToken} that tracks all {@link EventMessage events} after the given {@code at}.
     * <p>
     * If there is an event exactly at the given {@code at}, it will be tracked too.
     *
     * @param at The {@link Instant} determining how the {@link TrackingToken} should be created. A tracking token
     *           should point to very first event before this {@link Instant}.
     * @return A {@link CompletableFuture} of a {@link TrackingToken} at the given {@code at}, if there aren't events
     * matching this criteria {@code null} is returned
     */
    CompletableFuture<TrackingToken> tokenAt(@NotNull Instant at);


    /**
     * Creates a {@link TrackingToken} tracking all {@link EventMessage events} since the given {@code since}.
     * <p>
     * If there is an {@link EventMessage} exactly at that time (before given {@code since}), it will be tracked too.
     *
     * @param since The {@link Duration} determining how the {@link TrackingToken} should be created. The returned token
     *              points at very first event before this {@code Duration}.
     * @return A {@link CompletableFuture} of {@link TrackingToken} pointing at a position before the given
     * {@code duration}.
     */
    CompletableFuture<TrackingToken> tokenSince(@NotNull Duration since);
}
