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

import jakarta.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import static org.axonframework.eventsourcing.eventstore.AppendEventsTransactionRejectedException.conflictingEventsDetected;

/**
 * Utility class for handling various operations related to the Legacy Aggregate-based Event Storage Engine.
 *
 * @author Mateusz Nowak
 * @author Allard Buijze
 * @since 5.0.0
 */
public class LegacyAggregateBasedEventStorageEngineUtils {

    /**
     * Validates the tags associated with a list of event messages. Ensures that no event has more than one tag, as the
     * Event Storage engine in Aggregate mode only supports a single tag per event.
     *
     * @param events the list of tagged event messages to validate
     * @throws TooManyTagsOnEventMessageException if any event has more than one tag
     */
    public static void assertValidTags(List<TaggedEventMessage<?>> events) {
        for (TaggedEventMessage<?> taggedEvent : events) {
            if (taggedEvent.tags().size() > 1) {
                throw new TooManyTagsOnEventMessageException(
                        "An Event Storage engine in Aggregate mode does not support multiple tags per event",
                        taggedEvent.event(),
                        taggedEvent.tags());
            }
        }
    }

    /**
     * Resolves the aggregate identifier from the provided set of tags.
     * The set must contain exactly one tag, and its value will be returned as the aggregate identifier.
     *
     * @param tags the set of tags to resolve the aggregate identifier from
     * @return the aggregate identifier, or {@code null} if the set is empty
     * @throws IllegalArgumentException if the set contains more than one tag
     */
    @Nullable
    public static String resolveAggregateIdentifier(Set<Tag> tags) {
        if (tags.isEmpty()) {
            return null;
        } else if (tags.size() > 1) {
            throw new IllegalArgumentException("Condition must provide exactly one tag");
        } else {
            return tags.iterator().next().value();
        }
    }

    /**
     * Resolves the aggregate type from the provided set of tags.
     * The set must contain exactly one tag, and its key will be returned as the aggregate type.
     *
     * @param tags the set of tags to resolve the aggregate type from
     * @return the aggregate type, or {@code null} if the set is empty
     * @throws IllegalArgumentException if the set contains more than one tag
     */
    @Nullable
    public static String resolveAggregateType(Set<Tag> tags) {
        if (tags.isEmpty()) {
            return null;
        } else if (tags.size() > 1) {
            throw new IllegalArgumentException("Condition must provide exactly one tag");
        } else {
            return tags.iterator().next().key();
        }
    }

    /**
     * Translates a conflict exception into an {@link AppendEventsTransactionRejectedException}
     * if the provided exception is identified as a conflict.
     * If the exception is not a conflict, it recursively checks the cause of the exception.
     *
     * @param consistencyMarker the consistency marker used to identify conflicting events
     * @param e the exception to translate
     * @param isConflictException a predicate used to check if the exception is a conflict
     * @return the translated exception
     */
    public static Throwable translateConflictException(
            ConsistencyMarker consistencyMarker,
            Throwable e,
            Predicate<Throwable> isConflictException
    ) {
        if (isConflictException.test(e)) {
            AppendEventsTransactionRejectedException translated = conflictingEventsDetected(consistencyMarker);
            translated.addSuppressed(e);
            return translated;
        }
        if (e.getCause() != null) {
            Throwable translatedCause = translateConflictException(consistencyMarker,
                                                                   e.getCause(),
                                                                   isConflictException);
            if (translatedCause != e.getCause()) {
                return translatedCause;
            }
        }
        return e;
    }

    /**
     * Helper class that tracks the sequence of events for different aggregates and
     * manages the consistency marker for the aggregates.
     */
    public static final class AggregateSequencer {

        private final Map<String, AtomicLong> aggregateSequences;
        private AggregateBasedConsistencyMarker consistencyMarker;

        /**
         * Constructs a new {@code AggregateSequencer} with the specified aggregate sequences and consistency marker.
         *
         * @param aggregateSequences a map of aggregate identifiers to atomic sequences
         * @param consistencyMarker  the consistency marker for this sequencer
         */
        private AggregateSequencer(Map<String, AtomicLong> aggregateSequences,
                                   AggregateBasedConsistencyMarker consistencyMarker) {
            this.aggregateSequences = aggregateSequences;
            this.consistencyMarker = consistencyMarker;
        }

        /**
         * Creates a new {@code AggregateSequencer} with the provided consistency marker.
         *
         * @param consistencyMarker the consistency marker for the new sequencer
         * @return a new {@code AggregateSequencer}
         */
        public static AggregateSequencer with(AggregateBasedConsistencyMarker consistencyMarker) {
            return new AggregateSequencer(new HashMap<>(), consistencyMarker);
        }

        /**
         * Advances the consistency marker by resolving and forwarding the state of aggregate sequences.
         *
         * @return the new consistency marker after forwarding
         */
        public AggregateBasedConsistencyMarker forwarded() {
            var newConsistencyMarker = consistencyMarker;
            for (var aggSeq : aggregateSequences.entrySet()) {
                newConsistencyMarker = newConsistencyMarker
                        .forwarded(aggSeq.getKey(), aggSeq.getValue().get());
            }
            consistencyMarker = newConsistencyMarker;
            return newConsistencyMarker;
        }

        /**
         * Resolves the sequence for the given aggregate identifier. If the aggregate does not exist, it is initialized
         * with the consistency marker's position for that identifier.
         *
         * @param aggregateIdentifier the identifier of the aggregate to resolve the sequence for
         * @return the atomic long sequence for the aggregate
         */
        public AtomicLong resolveBy(String aggregateIdentifier) {
            return aggregateSequences.computeIfAbsent(aggregateIdentifier,
                                                      i -> new AtomicLong(consistencyMarker.positionOf(i)));
        }
    }

    /**
     * Represents an empty append transaction. This transaction does nothing and always succeeds.
     * It is used when there are no events to persist.
     *
     * @param appendCondition will be returned as commit result
     */
    public record EmptyAppendTransaction(AppendCondition appendCondition)
            implements AsyncEventStorageEngine.AppendTransaction {

        /**
         * Commits the empty append transaction. Always completes successfully with the provided consistency marker.
         *
         * @return a completed future with the consistency marker
         */
        @Override
        public CompletableFuture<ConsistencyMarker> commit() {
            return CompletableFuture.completedFuture(AggregateBasedConsistencyMarker.from(appendCondition));
        }

        /**
         * Rolls back the empty append transaction. This does nothing as the transaction has no effect.
         */
        @Override
        public void rollback() {
            // No action needed
        }
    }
}
