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
import org.axonframework.eventstreaming.EventCriteria;
import org.axonframework.eventstreaming.EventsCondition;

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
        return conditionFor(0, criteria);
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
    static SourcingCondition conditionFor(long start, @Nonnull EventCriteria criteria) {
        return new DefaultSourcingCondition(start, criteria);
    }

    /**
     * The start position in the event sequence to source. Defaults to {@code -1L} to ensure we start at the beginning
     * of the sequence's stream complying to the {@link #criteria()}.
     *
     * @return The start position in the event sequence to source.
     */
    default long start() {
        return -1L;
    }

    /**
     * Combine the {@link #criteria()} and {@link #start()} of {@code this SourcingCondition} with the {@code criteria}
     * and {@code start} of the given {@code other SourcingCondition}.
     * <p>
     * Any event that would have been sourced under either condition, will also be sourced under the combined condition.
     * If the conditions' start does not overlap or is not contingent, some event may be returned under the combined
     * condition that would not have been returned under either this or the other individual conditions.
     * <p>
     * Typically, the minimum value of the {@code start} value will be part of the end result of using
     * {@code this SourcingCondition}.
     *
     * @param other The {@code SourcingCondition} to combine with {@code this SourcingCondition}.
     * @return A combined {@code SourcingCondition} based on {@code this SourcingCondition} and the given {@code other}.
     */
    SourcingCondition or(@Nonnull SourcingCondition other);
}
