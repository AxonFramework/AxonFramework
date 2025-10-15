/*
 * Copyright (c) 2010-2025. Axon Framework
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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.annotations.Internal;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TerminalEventMessage;
import org.axonframework.eventhandling.processors.streaming.token.TrackingToken;
import org.axonframework.eventstreaming.StreamingCondition;
import org.axonframework.eventstreaming.Tag;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.ProcessingLifecycle;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.Arrays.asList;

/**
 * Provides a mechanism to {@link #appendEvents(AppendCondition, TaggedEventMessage[])}  append} as well as retrieve
 * {@link EventMessage events} from an underlying storage mechanism.
 * <p>
 * Retrieval can be done either through {@link #source(SourcingCondition) sourcing} or
 * {@link #stream(StreamingCondition) streaming}. The former generates a <b>finite</b> stream intended to event source
 * (for example) a model. The latter provides an <b>infinite</b> stream.
 *
 * @author Allard Buijze
 * @author Milan Savić
 * @author Rene de Waele
 * @author Steven van Beelen
 * @since 3.0
 */
@Internal
public interface EventStorageEngine extends DescribableComponent {

    /**
     * Append one or more {@link EventMessage events} to the underlying storage solution.
     * <p>
     * Events will be appended in the order that they are offered in, validating the given {@code condition} before
     * being stored. Note that all events should have a unique event identifier. {@link Tag Tags} paired with the
     * {@code events} will be stored as well.
     * <p>
     * By default, this method creates a {@link List} of the offered events and then invokes
     * {@link #appendEvents(AppendCondition, List)}.
     * <p>
     * Called during the {@link ProcessingLifecycle.DefaultPhases#PREPARE_COMMIT PREPARE_COMMIT} phase.
     *
     * @param condition The condition describing the transactional requirements for the append transaction
     * @param context   The current {@link ProcessingContext}, if any.
     * @param events    One or more {@link EventMessage events} to append to the underlying storage solution.
     * @return A {@link AppendTransaction transaction} instance that can be committed or rolled back.
     */
    default CompletableFuture<AppendTransaction<?>> appendEvents(@Nonnull AppendCondition condition,
                                                                 @Nullable ProcessingContext context,
                                                                 @Nonnull TaggedEventMessage<?>... events) {
        return appendEvents(condition, context, asList(events));
    }

    /**
     * Appends a {@link List} of {@link EventMessage events} to the underlying storage solution.
     * <p>
     * Events will be appended in the order that they are offered in, validating the given {@code condition} before
     * being stored. Note that all events should have a unique event identifier. {@link Tag Tags} paired with the
     * {@code events} will be stored as well.
     * <p>
     * Implementations may be able to detect conflicts during the append stage. In such case, the returned completable
     * future will complete exceptionally, indicating such conflict. Other implementations may delay such checks until
     * the {@link AppendTransaction#commit()} is called.
     * <p>
     * Called during the {@link ProcessingLifecycle.DefaultPhases#PREPARE_COMMIT PREPARE_COMMIT} phase.
     *
     * @param condition The condition describing the transactional requirements for the append transaction
     * @param context   The current {@link ProcessingContext}, if any.
     * @param events    The {@link List} of {@link EventMessage events} to append to the underlying storage solution.
     * @return A {@link AppendTransaction transaction} instance that can be committed or rolled back.
     */
    CompletableFuture<AppendTransaction<?>> appendEvents(@Nonnull AppendCondition condition,
                                                         @Nullable ProcessingContext context,
                                                         @Nonnull List<TaggedEventMessage<?>> events);

    /**
     * Creates a <b>finite</b> {@link MessageStream} of {@link EventMessage events} matching the given
     * {@code condition}.
     * <p>
     * The final entry of the stream <b>always</b> contains a {@link ConsistencyMarker} in the
     * {@link MessageStream.Entry}'s resources, paired with a {@link TerminalEventMessage}. This
     * {@code ConsistencyMarker} should be used to construct the {@link AppendCondition} when
     * {@link #appendEvents(AppendCondition, List) appending events}.
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
     * @param context   The current {@link ProcessingContext}, if any.
     * @return A <b>finite</b> {@link MessageStream} of {@link EventMessage events} matching the given
     * {@code condition}.
     */
    MessageStream<EventMessage> source(@Nonnull SourcingCondition condition, @Nullable ProcessingContext context);

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
     * @param context   The current {@link ProcessingContext}, if any.
     * @return An <b>infinite</b> {@link MessageStream} of {@link EventMessage events} matching the given
     * {@code condition}.
     */
    MessageStream<EventMessage> stream(@Nonnull StreamingCondition condition, @Nullable ProcessingContext context);

    /**
     * Creates a {@link TrackingToken} that is at the first position of an event stream.
     * <p>
     * In other words, a token that tracks events from the beginning of time.
     *
     * @param context The current {@link ProcessingContext}, if any.
     * @return A {@link CompletableFuture} of a {@link TrackingToken} at the first position of an event stream.
     */
    CompletableFuture<TrackingToken> firstToken(@Nullable ProcessingContext context);

    /**
     * Creates a {@link TrackingToken} that is at the latest position of an event stream.
     * <p>
     * In other words, a token that tracks all <b>new</b> events from this point forward.
     *
     * @param context The current {@link ProcessingContext}, if any.
     * @return A {@link CompletableFuture} of a {@link TrackingToken} at the latest position of an event stream.
     */
    CompletableFuture<TrackingToken> latestToken(@Nullable ProcessingContext context);

    /**
     * Creates a {@link TrackingToken} that tracks all {@link EventMessage events} after the given {@code at}.
     * <p>
     * If there is an event exactly at the given {@code at}, it will be tracked too.
     *
     * @param at      The {@link Instant} determining how the {@link TrackingToken} should be created. A tracking token
     *                should point to very first event before this {@link Instant}.
     * @param context The current {@link ProcessingContext}, if any.
     * @return A {@link CompletableFuture} of a {@link TrackingToken} at the given {@code at}, if there aren't events
     * matching this criteria {@code null} is returned
     */
    CompletableFuture<TrackingToken> tokenAt(@Nonnull Instant at, @Nullable ProcessingContext context);

    /**
     * Interface representing the transaction of an appendEvents invocation.
     * <p>
     * Events may only be visible to consumers after the invocation of {@link #commit()}.
     *
     * @param <R> the type of the commit result
     */
    interface AppendTransaction<R> {

        /**
         * Commit any underlying transactions to make the appended events visible to consumers.
         * <p>
         * Called during the {@link ProcessingLifecycle.DefaultPhases#COMMIT COMMIT} phase.
         *
         * @param context The current {@link ProcessingContext}, if any.
         * @return A {@code CompletableFuture} to complete the commit asynchrously, returning a value
         *     for {@link #afterCommit(R, ProcessingContext) afterCommit}.
         */
        CompletableFuture<R> commit(@Nullable ProcessingContext context);

        /**
         * Rolls back any events that have been appended, permanently making them unavailable for consumers.
         *
         * @param context The current {@link ProcessingContext}, if any.
         */
        void rollback(@Nullable ProcessingContext context);

        /**
         * Returns a {@code CompletableFuture} to calculate the consistency marker. This is called only after the
         * transaction has been committed with the result of {@link #commit(ProcessingContext) commit}.
         * <p>
         * Called during the {@link ProcessingLifecycle.DefaultPhases#AFTER_COMMIT AFTER_COMMIT} phase.
         *
         * @param commitResult The result returned from the commit call.
         * @param context The current {@link ProcessingContext}, if any.
         * @return A {@code CompletableFuture} that completes with the new consistency marker for the transaction. If
         *     the transaction was empty (without events to append) then returned consistency marker is always
         *     {@link ConsistencyMarker#ORIGIN}.
         */
        CompletableFuture<ConsistencyMarker> afterCommit(R commitResult, @Nullable ProcessingContext context);
    }
}
