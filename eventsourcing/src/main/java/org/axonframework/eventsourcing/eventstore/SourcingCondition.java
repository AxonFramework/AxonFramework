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
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.messaging.eventstreaming.EventsCondition;

/**
 * Interface describing the condition to {@link EventStoreTransaction#source(SourcingCondition) source} events from an
 * Event Store.
 * <p>
 * The condition has a mandatory {@link #criteria()} used to retrieve the exact sequence of events to source the
 * model(s). The {@link #start()} operation defines the start point when sourcing events that the
 * {@link EventStoreTransaction} is interested in.
 *
 * @author Michal Negacz
 * @author Milan Savić
 * @author Marco Amann
 * @author Sara Pellegrini
 * @author Steven van Beelen
 * @since 5.0.0
 */
public sealed interface SourcingCondition extends EventsCondition permits DefaultSourcingCondition {

    /**
     * Construct a {@code SourcingCondition} used to source a model based on the given {@code criteria}.
     *
     * @param criteria The {@link EventCriteria} used as the {@link SourcingCondition#criteria()}.
     * @return A {@code SourcingCondition} that will retrieve an event sequence matching the given {@code criteria}.
     */
    static SourcingCondition conditionFor(@Nonnull EventCriteria criteria) {
        return conditionFor(Position.START, criteria);
    }

    /**
     * Construct a {@code SourcingCondition} used to source a model based on the given {@code criteria}.
     * <p>
     * Will start the sequence at the given {@code start} value.
     *
     * @param start    The start position in the event sequence to retrieve of the model to source.
     * @param criteria The {@link EventCriteria} used as the {@link SourcingCondition#criteria()}.
     * @return A {@code SourcingCondition} that will retrieve an event sequence matching the given {@code criteria},
     * starting at the given {@code start}.
     */
    static SourcingCondition conditionFor(@Nonnull Position start, @Nonnull EventCriteria criteria) {
        return new DefaultSourcingCondition(start, criteria);
    }

    /**
     * The start position in the event sequence to source. Defaults to {@code -1L} to ensure we start at the beginning
     * of the sequence's stream complying to the {@link #criteria()}.
     *
     * @return The start position in the event sequence to source, never {@code null}.
     */
    default @Nonnull Position start() {
        return Position.START;
    }

    /**
     * Merges {@code this SourcingCondition} with the given {@code other SourcingCondition}, by combining their
     * {@link #criteria() search criteria} and {@link #start() starting points}.
     * <p>
     * Warning: If the starting points don't overlap or connect properly, the merged condition might return some events
     * that neither of the original conditions would have returned on their own.
     * <p>
     * Usually, the earlier starting point (minimum start value) will be used in the final merged condition.
     *
     * @param other The {@code SourcingCondition} to combine with {@code this SourcingCondition}.
     * @return A combined {@code SourcingCondition} based on {@code this SourcingCondition} and the given {@code other}.
     */
    SourcingCondition or(@Nonnull SourcingCondition other);
}
