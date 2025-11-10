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
import org.axonframework.common.CollectionUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * {@link ConsistencyMarker} implementation that keeps track of a position per aggregate identifier. A single
 * {@code AggregateBasedConsistencyMarker} can track the positions of multiple Aggregates.
 *
 * @author Allard Buijze
 * @since 5.0.0
 */
public class AggregateBasedConsistencyMarker extends AbstractConsistencyMarker<AggregateBasedConsistencyMarker> {

    private final Map<String, Long> aggregatePositions;

    /**
     * Construct a new consistency marker for given {@code aggregateIdentifier} and {@code sequenceNumber}.
     *
     * @param aggregateIdentifier The identifier of the aggregate to contain in the marker.
     * @param sequenceNumber      The sequence number of the last seen event of the aggregate.
     */
    public AggregateBasedConsistencyMarker(String aggregateIdentifier, long sequenceNumber) {
        this(Map.of(aggregateIdentifier, sequenceNumber));
    }

    private AggregateBasedConsistencyMarker(Map<String, Long> aggregatePositions) {
        this.aggregatePositions = aggregatePositions;
    }

    /**
     * Constructs an {@code AggregateBasedConsistencyMarker} based of the given {@code appendCondition}. If the
     * consistency marker of the given {@code appendCondition} is not an instance of
     * {@code AggregateBasedConsistencyMarker}, it will attempt to create an instance that respects the condition. If
     * such conversion is not possible, the method throws an {@link IllegalArgumentException}.
     *
     * @param appendCondition The condition to create an {@code AggregateBasedConsistencyMarker} for
     * @return an {@code AggregateBasedConsistencyMarker} that represents the given {@code appendCondition}
     * @throws IllegalArgumentException when the consistency marker of given append condition cannot be safely converted
     *                                  to an {@code AggregateBasedConsistencyMarker}.
     */
    public static AggregateBasedConsistencyMarker from(AppendCondition appendCondition) {
        if (appendCondition.consistencyMarker() instanceof AggregateBasedConsistencyMarker abcm) {
            return abcm;
        }
        if (appendCondition.criteria().hasCriteria() && appendCondition.consistencyMarker() == INFINITY) {
            throw new IllegalArgumentException("Consistency marker must not be infinity when criteria are provided");
        } else if (appendCondition.consistencyMarker() == ORIGIN || appendCondition.consistencyMarker() == INFINITY) {
            return new AggregateBasedConsistencyMarker(Map.of());
        }
        throw new IllegalArgumentException("Unsupported consistency marker: " + appendCondition.consistencyMarker());
    }

    @Override
    public AggregateBasedConsistencyMarker doLowerBound(AggregateBasedConsistencyMarker other) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public AggregateBasedConsistencyMarker doUpperBound(AggregateBasedConsistencyMarker other) {
        Map<String, Long> newPositions = new HashMap<>(aggregatePositions);
        other.aggregatePositions.forEach((id, seq) -> {
            if (!newPositions.containsKey(id) || newPositions.get(id) < seq) {
                newPositions.put(id, seq);
            }
        });
        return new AggregateBasedConsistencyMarker(newPositions);
    }

    @Override
    public Position position() {
        if (aggregatePositions.size() > 1) {
            throw new IllegalStateException("ConsistencyMarker contains multiple positions, unable to convert to single position: " + this);
        }

        return new AggregateSequenceNumberPosition(aggregatePositions.values().iterator().next());
    }

    /**
     * Returns a new {@code AggregateBasedConsistencyMarker} with the sequence of given {@code aggregateIdentifier}
     * forwarded to the given {@code newPosition}. If a position for this {@code aggregateIdentifier} has already been
     * recorded in this marker, and that position is further than given {@code newPosition}, an
     * {@link IllegalArgumentException} is thrown.
     * <p/>
     * This method is practically the same as {@link #upperBound(ConsistencyMarker)}, except that it takes a single
     * aggregate identifier and position, and additionally validates that the returned marker is at or before
     * given position for given aggregateIdentifier.
     *
     * @param aggregateIdentifier The identifier of the aggregate to forward the position for
     * @param newSequence         The new position
     * @return an AggregateBasedConsistencyMarker that represents the forwarded position
     */
    private AggregateBasedConsistencyMarker forwarded(String aggregateIdentifier, long newSequence) {
        long current = aggregatePositions.getOrDefault(aggregateIdentifier, -1L);

        if (current > newSequence) {
            throw new IllegalArgumentException(
                    "Aggregate " + aggregateIdentifier + " is already beyond provided position. Current position: "
                            + current + ", provided: " + newSequence);
        } else if (current == newSequence) {
            // no forwarding required
            return this;
        }
        Map<String, Long> newMap = CollectionUtils.mapWith(aggregatePositions, aggregateIdentifier, newSequence);
        return new AggregateBasedConsistencyMarker(newMap);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AggregateBasedConsistencyMarker that)) {
            return false;
        }
        return Objects.equals(aggregatePositions, that.aggregatePositions);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(aggregatePositions);
    }

    @Override
    public String toString() {
        return "AggregateBasedConsistencyMarker{" +
                "aggregatePositions=" + aggregatePositions +
                '}';
    }

    /**
     * Create an {@link AggregateSequencer}, initialized with values from this marker, which can
     * increment and provide sequence numbers of multiple aggregates, and integrate the results
     * into a new {@link AggregateBasedConsistencyMarker}.
     *
     * @return an {@link AggregateSequencer} initialized with values from this marker, never {@code null}
     */
    public AggregateSequencer createSequencer() {
        return new AggregateSequencer();
    }

    /**
     * Helper class that tracks the sequence of events for different aggregates and manages the consistency marker for
     * the aggregates.
     */
    public final class AggregateSequencer {
        private final Map<String, Long> aggregateSequences = new HashMap<>();

        private AggregateSequencer() {
        }

        /**
         * Creates a new {@link AggregateBasedConsistencyMarker} with the current state of aggregate
         * sequences tracked by this sequencer.
         *
         * @return a new {@link AggregateBasedConsistencyMarker}, never {@code null}
         */
        public AggregateBasedConsistencyMarker toMarker() {
            AggregateBasedConsistencyMarker newConsistencyMarker = AggregateBasedConsistencyMarker.this;
            for (Map.Entry<String, Long> e : aggregateSequences.entrySet()) {
                newConsistencyMarker = newConsistencyMarker
                        .forwarded(e.getKey(), e.getValue());
            }

            return newConsistencyMarker;
        }

        /**
         * Get and increment the sequence for the given aggregate identifier.
         *
         * @param aggregateIdentifier The identifier of the aggregate to get and increment the sequence for
         * @return The sequence number for the aggregate
         */
        public long incrementAndGetSequenceOf(String aggregateIdentifier) {
            return aggregateSequences.compute(
                    aggregateIdentifier,
                    (key, oldValue) -> oldValue == null ? positionOf(key) + 1 : oldValue + 1
            );
        }

        private long positionOf(@Nonnull String aggregateIdentifier) {
            return aggregatePositions.getOrDefault(aggregateIdentifier, -1L);
        }
    }
}
